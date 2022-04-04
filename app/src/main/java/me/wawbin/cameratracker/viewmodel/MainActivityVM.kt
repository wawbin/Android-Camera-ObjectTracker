package me.wawbin.cameratracker.viewmodel

import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.graphics.toRect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.objects.DetectedObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.wawbin.cameratracker.composable.RecorderState
import me.wawbin.cameratracker.composable.TrackingState
import me.wawbin.cameratracker.helper.MatrixHelper
import me.wawbin.cameratracker.helper.SensorOrientationListener
import me.wawbin.cameratracker.repository.ServoNativeImpl

class MainActivityVM : ViewModel() {

    data class ObjectMarker(
        val label: String = "",
        val confidence: Float = 0F,
        val trackId: Int = -1
    )

    var targets = mutableStateMapOf<Rect, ObjectMarker>()
        private set

    var currentTrackingObject by mutableStateOf(ObjectMarker())
        private set

    var sensorOrientation by mutableStateOf(SensorOrientationListener.SensorRotation.ROTATION_0)

    var currentLinearZoom by mutableStateOf(0)

    var trackingState by mutableStateOf(TrackingState.IDLE)

    // LEVEL-3 相机才支持同时进行预览和视频录制
    var isSupportVideoCapture by mutableStateOf(true)

    // 当前视频录制状态
    var recorderState by mutableStateOf(RecorderState.IDLE)

    var isBackCamera = false

    // 重新定位前舵机的旋转角度
    private var servoRotationWhenTryToReTrack = 0
    private var countWhenReadyToRotate = 0

    private val rect = RectF()

    fun analyze(
        detectedObjects: List<DetectedObject>,
        screenWidth: Int,
        screenHeight: Int
    ) {
        var trackingRect: RectF? = null
        targets.clear()
        for (detectedObject in detectedObjects) {
            val boundingBox = detectedObject.boundingBox
            if (trackingState == TrackingState.TRACKING) {
                if (detectedObject.trackingId != currentTrackingObject.trackId) continue
                else {
                    MatrixHelper.mapRect(rect, boundingBox)
                    trackingRect = rect
                }
            } else MatrixHelper.mapRect(rect, boundingBox)
            val label = detectedObject.labels.firstOrNull()
            val marker = ObjectMarker(
                label?.text ?: "unknown",
                label?.confidence ?: 0F,
                detectedObject.trackingId ?: -1
            )
            if (trackingState == TrackingState.TRACKING && marker.label != "unknown") currentTrackingObject =
                marker
            else if (trackingState == TrackingState.TRY_TO_RE_TRACK && detectedObject.labels.firstOrNull()?.text == currentTrackingObject.label) {
                // 成功找回丢失的目标 虽然这时可能不是同一个目标
                currentTrackingObject = marker
                trackingState = TrackingState.TRACKING
                return
            }
            targets[rect.toRect()] = marker
        }
        if (trackingState == TrackingState.TRACKING) {
            trackingRect?.let {
                trackIt(it, screenWidth, screenHeight)
            } ?: run {
                // 尝试目标丢失 根据label重新寻找 label为unknown的话直接返回失败
                if (currentTrackingObject.label == "unknown") {
                    trackingState = TrackingState.TRACKING_MISS
                } else {
                    trackingState = TrackingState.TRY_TO_RE_TRACK
                    servoRotationWhenTryToReTrack = ServoNativeImpl.getCurrentRotation()
                    countWhenReadyToRotate = 0
                }
            }
        } else if (trackingState == TrackingState.TRY_TO_RE_TRACK && ++countWhenReadyToRotate % WaitFramePerRotate == 0) {
            // 每次旋转RotateModulus度慢慢找 停顿WaitFramePerRotate帧
            if (ServoNativeImpl.getCurrentRotation() == servoRotationWhenTryToReTrack && countWhenReadyToRotate != WaitFramePerRotate) {
                // 转了一圈都找不到了 没办法
                trackingState = TrackingState.TRACKING_MISS_AFTER_RE_TRACK
                countWhenReadyToRotate = 0
            } else
                viewModelScope.launch(Dispatchers.IO) {
                    when (ServoNativeImpl.rotate(RotateModulus)) {
                        -1 -> trackingState = TrackingState.LINKING_ERROR
                        // 已经到180度了 直接回到0都重新找
                        -2 -> ServoNativeImpl.rotateTo(0)
                    }
                }
        }
    }

    fun startTrack(target: ObjectMarker) {
        viewModelScope.launch {
            // 屏蔽重复请求
            if (trackingState != TrackingState.IDLE && target.trackId > 0) return@launch
            trackingState = TrackingState.LINKING
            // 连接至开发板
            val linkResult = withContext(Dispatchers.IO) {
                ServoNativeImpl.startLink()
            }
            if (!linkResult) {
                trackingState = TrackingState.LINKING_ERROR
                return@launch
            }
            // 连接成功
            trackingState = TrackingState.TRACKING
            currentTrackingObject = target
        }
    }

    // 0-1F
    private fun trackIt(targetRect: RectF, screenWidth: Int, screenHeight: Int) {
        if (trackingState != TrackingState.TRACKING) return
        val w = targetRect.width() / screenWidth
        val h = targetRect.height() / screenHeight
        currentLinearZoom += when {
            // too far
            (w + h) < 0.6F && currentLinearZoom < 100 -> 1
            // too near
            (w + h) > 1F && currentLinearZoom > 0 -> -1
            else -> 0
        }
        // 转太快的话分析器跟不上
        if (++countWhenReadyToRotate < WaitFramePerRotate) return
        countWhenReadyToRotate = 0
        var rotateDirection = when (sensorOrientation) {
            // 手机放竖着 看的是左右
            SensorOrientationListener.SensorRotation.ROTATION_0, SensorOrientationListener.SensorRotation.ROTATION_180 ->
                when {
                    targetRect.left / screenWidth < RotateSlop -> 1
                    1 - targetRect.right / screenWidth < RotateSlop -> -1
                    else -> 0
                }
            // 手机放横着 看的是上下
            SensorOrientationListener.SensorRotation.ROTATION_90, SensorOrientationListener.SensorRotation.ROTATION_270 ->
                when {
                    targetRect.top / screenHeight < RotateSlop -> 1
                    1 - targetRect.bottom / screenHeight < RotateSlop -> -1
                    else -> 0
                }
        }
        rotateDirection *= when (sensorOrientation) {
            SensorOrientationListener.SensorRotation.ROTATION_90, SensorOrientationListener.SensorRotation.ROTATION_180 -> -1
            else -> 1
        }
        rotateDirection *= if (isBackCamera) 1 else -1
        viewModelScope.launch(Dispatchers.IO) {
            if (ServoNativeImpl.rotate(RotateModulus * rotateDirection) == -1) trackingState =
                TrackingState.LINKING_ERROR
        }
    }

    fun stop() {
        viewModelScope.launch(Dispatchers.IO) {
            ServoNativeImpl.shutdown()
            currentTrackingObject = ObjectMarker()
            trackingState = TrackingState.IDLE
        }
    }

    companion object {
        // 每次转多少度 必须是180的因数
        private const val RotateModulus = 15

        // 物体距离屏幕边角多少时旋转 归一
        private const val RotateSlop = 0.15

        // 每次旋转停留多少帧(流经分析器)等待
        private const val WaitFramePerRotate = 100
    }
}