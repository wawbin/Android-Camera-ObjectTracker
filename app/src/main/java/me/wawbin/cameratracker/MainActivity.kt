package me.wawbin.cameratracker

import android.annotation.SuppressLint
import android.content.ContentValues
import android.hardware.camera2.CameraCharacteristics
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.OrientationEventListener.ORIENTATION_UNKNOWN
import android.view.Surface
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.*
import androidx.camera.core.AspectRatio.RATIO_16_9
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import me.wawbin.cameratracker.composable.FeatureThatRequiresCameraPermission
import me.wawbin.cameratracker.composable.RecorderState
import me.wawbin.cameratracker.composable.VideoCaptureAction
import me.wawbin.cameratracker.helper.MatrixHelper
import me.wawbin.cameratracker.helper.SensorOrientationListener
import me.wawbin.cameratracker.helper.SensorOrientationListener.SensorRotation
import me.wawbin.cameratracker.helper.ToastHelper
import me.wawbin.cameratracker.helper.showToast
import me.wawbin.cameratracker.theme.CameraTrackerTheme
import me.wawbin.cameratracker.viewmodel.MainActivityVM
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView

    // 切换相机的时候用
    private lateinit var fullUseCaseGroup: UseCaseGroup
    private lateinit var cameraProvider: ProcessCameraProvider

    private var camera: Camera? = null
    private var recording: Recording? = null

    private val objectDetector: ObjectDetector
    private val viewModel: MainActivityVM by viewModels()

    init {
        val localModel = LocalModel.Builder()
            .setAssetFilePath("lite-model_efficientnet_lite4_uint8_2.tflite")
            .build()
        objectDetector = ObjectDetection.getClient(
            CustomObjectDetectorOptions.Builder(localModel)
                .setDetectorMode(CustomObjectDetectorOptions.STREAM_MODE)
                .enableClassification()
                .enableMultipleObjects()
                .setClassificationConfidenceThreshold(0.2f)
                .setMaxPerObjectLabelCount(1)
                .build()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        previewView = PreviewView(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ToastHelper.initialize(applicationContext)
        setContent {
            CameraTrackerTheme {
                LaunchedEffect(key1 = camera) {
                    snapshotFlow { viewModel.currentLinearZoom }
                        .distinctUntilChanged()
                        .filter { it in 0..100 }
                        .collect {
                            camera?.cameraControl?.setLinearZoom(it.toFloat() / 100)
                        }
                }

                FeatureThatRequiresCameraPermission(viewModel, previewView, {
                    camera?.let { cam ->
                        val currentZoom =
                            cam.cameraInfo.zoomState.value?.linearZoom?.times(100) ?: return@let
                        val newZoom = (currentZoom + 30F) * it - 30F
                        viewModel.currentLinearZoom = newZoom.roundToInt().coerceIn(0, 100)
                    }
                }, ::reBindCamera, ::initCamera, ::handleVideoCaptureEvent)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleVideoCaptureEvent(videoCaptureAction: VideoCaptureAction) {
        when (videoCaptureAction) {
            VideoCaptureAction.START -> {
                val name = "Track-Recording-" +
                        SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.CHINESE)
                            .format(System.currentTimeMillis()) + ".mp4"
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, name)
                }
                val mediaStoreOutput = MediaStoreOutputOptions.Builder(
                    this.contentResolver,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                )
                    .setContentValues(contentValues)
                    .build()
                recording =
                    ((fullUseCaseGroup.useCases.first { vc -> vc is VideoCapture<*> } as VideoCapture<*>).output as Recorder)
                        .prepareRecording(this, mediaStoreOutput)
                        .withAudioEnabled()
                        .start(ContextCompat.getMainExecutor(this)) {
                            viewModel.recorderState = when (it) {
                                is VideoRecordEvent.Start, is VideoRecordEvent.Resume -> RecorderState.RECORDING
                                is VideoRecordEvent.Finalize -> {
                                    if (it.hasError()) "error:${it.cause?.message}".showToast()
                                    else "视频已保存至系统图库".showToast()
                                    recording = null
                                    RecorderState.IDLE
                                }
                                is VideoRecordEvent.Pause -> RecorderState.PAUSE
                                else -> {
                                    Log.e("VideoRecordEvent", "$it")
                                    viewModel.recorderState
                                }
                            }
                        }
            }
            VideoCaptureAction.PAUSE -> recording?.pause()
            VideoCaptureAction.RESUME -> recording?.resume()
            VideoCaptureAction.STOP -> recording?.stop()
        }
    }

    private fun registerSensorListener() {
        val sensorOrientationListener = SensorOrientationListener(this) { orientation ->
            val analyzer =
                fullUseCaseGroup.useCases.firstOrNull { it is ImageAnalysis } as? ImageAnalysis
            if (orientation == ORIENTATION_UNKNOWN || analyzer == null) {
                return@SensorOrientationListener
            }
            analyzer.targetRotation = when (orientation) {
                in 45 until 135 -> Surface.ROTATION_270
                in 135 until 225 -> Surface.ROTATION_180
                in 225 until 315 -> Surface.ROTATION_90
                else -> Surface.ROTATION_0
            }
            viewModel.sensorOrientation = when (orientation) {
                in 45 until 135 -> SensorRotation.ROTATION_90
                in 135 until 225 -> SensorRotation.ROTATION_180
                in 225 until 315 -> SensorRotation.ROTATION_270
                else -> SensorRotation.ROTATION_0
            }
        }
        lifecycle.addObserver(sensorOrientationListener)
    }

    private fun initCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindPreview()
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("CheckResult")
    private fun bindPreview() {
        val preview = Preview.Builder().setTargetAspectRatio(RATIO_16_9).build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val analyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply { setAnalyzer(Dispatchers.Default.asExecutor(), FaceAnalyzer()) }

        val qualitySelector = QualitySelector.fromOrderedList(
            listOf(
                Quality.UHD,
                Quality.FHD,
                Quality.HD,
                Quality.SD
            ), FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
        )

        val recorder = Recorder.Builder().setExecutor(Dispatchers.IO.asExecutor())
            .setQualitySelector(qualitySelector).build()
        val videoCapture = VideoCapture.withOutput(recorder)
        // 指定UseCaseGroup并不会改变用例的大小
        // 而是在用例中附着一个CropRect指示一片所有用例中内容相同的区域(分辨率可能不同)
        // CameraX 可保证一个组中的所有用例的剪裁矩形都指向摄像头传感器中的同一个区域
        // 如imageProxy.cropRect
        // 如果这里set了 imageProxy.cropRect会对应preview的剪裁空间
        // 但是MLKit输出的是整幅图片的坐标 所以这里setViewPort后面还得把MLKit输出的坐标+-Rect并进行分辨率转换
        // 以上:已实现
        fullUseCaseGroup = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(analyzer)
            .addUseCase(videoCapture)
            .setViewPort(previewView.viewPort!!)
            .build()
        reBindCamera()
        registerSensorListener()
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun isLevel3Device(
        cameraProvider: ProcessCameraProvider,
        cameraSelector: CameraSelector
    ) =
        cameraSelector.filter(cameraProvider.availableCameraInfos)
            .firstOrNull()
            ?.let { Camera2CameraInfo.from(it) }
            ?.getCameraCharacteristic(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ==
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3

    private fun reBindCamera() {
        cameraProvider.unbindAll()
        val cameraSelector =
            if (viewModel.isBackCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        val isLevel3 = isLevel3Device(cameraProvider, cameraSelector)
        viewModel.isSupportVideoCapture = isLevel3
        val useCaseGroup = if (isLevel3) fullUseCaseGroup else
            UseCaseGroup.Builder().apply {
                fullUseCaseGroup.useCases.forEach {
                    if (it !is VideoCapture<*>) addUseCase(it)
                }
                setViewPort(fullUseCaseGroup.viewPort!!)
            }.build()
        camera = cameraProvider.bindToLifecycle(
            this@MainActivity,
            cameraSelector,
            useCaseGroup
        )
        viewModel.isBackCamera = !viewModel.isBackCamera
    }

    private inner class FaceAnalyzer : ImageAnalysis.Analyzer {

        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            imageProxy.image?.let {
                val image = InputImage.fromMediaImage(it, imageProxy.imageInfo.rotationDegrees)
                objectDetector.process(image)
                    .addOnSuccessListener { detectedObjects ->
                        MatrixHelper.updateMatrix(
                            imageProxy,
                            previewView,
                            viewModel.sensorOrientation,
                            !viewModel.isBackCamera
                        )
                        viewModel.analyze(
                            detectedObjects,
                            previewView.width,
                            previewView.height
                        )
                    }
                    .addOnFailureListener { e ->
                        Log.e("addOnFailureListener", e.message.toString())
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } ?: imageProxy.close()
        }
    }

    companion object {
        init {
            System.loadLibrary("cameratracker-native")
        }
    }

}