package me.wawbin.cameratracker.composable

import android.graphics.Paint
import android.graphics.Rect
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import me.wawbin.cameratracker.helper.MutableOffset
import me.wawbin.cameratracker.helper.SensorOrientationListener
import me.wawbin.cameratracker.viewmodel.MainActivityVM
import kotlin.math.roundToInt

@Composable
fun TrackingView(
    viewModel: MainActivityVM,
    previewView: PreviewView,
    onScale: (Float) -> Unit,
    startTracking: (MainActivityVM.ObjectMarker) -> Unit
) {
    val alpha = remember { Animatable(0F) }

    var paddingSelected by remember { mutableStateOf(MainActivityVM.ObjectMarker()) }

    val paddingRect = remember { Rect(0, 0, 0, 0) }

    val paddingInputOffset = remember { MutableOffset(0F, 0F) }

    LaunchedEffect(key1 = viewModel.currentLinearZoom) {
        alpha.snapTo(1F)
        alpha.animateTo(
            targetValue = 0F, animationSpec = tween(
                durationMillis = 2000,
                easing = FastOutSlowInEasing
            )
        )
    }

    val scope = rememberCoroutineScope()

    val state = rememberTransformableState { zoomChange, _, _ ->
        onScale(zoomChange)
    }

    Box(
        modifier = Modifier
            .transformable(state = state)
            .fillMaxSize()
    ) {
        AndroidView(modifier = Modifier
            .fillMaxSize(), factory = { previewView })
        Text(
            text = "${viewModel.currentLinearZoom.toString().take(4)}X",
            color = Color.White,
            fontSize = 50.sp,
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.Center)
                .graphicsLayer(alpha = alpha.value)
        )
        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                forEachGesture {
                    awaitPointerEventScope {
                        val down = awaitFirstDown()
                        paddingInputOffset.set(down.position)
                        var click = false
                        // modify from waitForUpOrCancellation()
                        // start
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            // 如果移动了再抬手 会先调用第二个if
                            if (event.changes.all { it.changedToUp() }) {
                                // All pointers are up
                                click = true
                                event.changes[0].consumeAllChanges()
                                break
                            }
                            // down被其它组件消费或者用户的手指移出框外了
                            if (event.changes.any {
                                    it.consumed.downChange || !paddingRect.contains(
                                        it.position.x.toInt(),
                                        it.position.y.toInt()
                                    )
                                }
                            ) break // Canceled
                            // Check for cancel by position consumption. We can look on the Final pass of the
                            // existing pointer event because it comes after the Main pass we checked above.
                            // 这里用final无论如何都会先给父组件消费
                            // 如果父组件把位移消费掉了 说明它在drag 这边就直接取消了
                            val consumeCheck = awaitPointerEvent(PointerEventPass.Final)
                            if (consumeCheck.changes.any { it.positionChangeConsumed() }) break
                        }
                        paddingInputOffset.setInvalid()
                        // end
                        if (viewModel.trackingState == TrackingState.IDLE && click) startTracking(
                            paddingSelected
                        )
                        paddingSelected = MainActivityVM.ObjectMarker()
                    }
                }
            }
        ) {
            viewModel.targets.forEach { (t, u) ->
                scope.launch {
                    if (paddingInputOffset.isValid() && paddingSelected.trackId != u.trackId && t.contains(
                            paddingInputOffset.x.toInt(),
                            paddingInputOffset.y.toInt()
                        )
                    ) {
                        paddingInputOffset.setInvalid()
                        paddingSelected = u
                        paddingRect.set(t)
                    }
                }

                trackingItem(
                    position = t,
                    marker = u,
                    sensorRotation = viewModel.sensorOrientation,
                    currentTrackingId = viewModel.currentTrackingObject.trackId,
                    paddingTrackingId = paddingSelected.trackId
                )

            }

        }

    }
}

private fun DrawScope.trackingItem(
    position: Rect,
    marker: MainActivityVM.ObjectMarker,
    sensorRotation: SensorOrientationListener.SensorRotation,
    currentTrackingId: Int,
    paddingTrackingId: Int
) {
    withTransform({ translate(position.left.toFloat(), position.top.toFloat()) }) {
        drawIntoCanvas {
            it.nativeCanvas.run {
                save()
                when (sensorRotation) {
                    SensorOrientationListener.SensorRotation.ROTATION_90 -> translate(
                        0F,
                        position.height().toFloat()
                    )
                    SensorOrientationListener.SensorRotation.ROTATION_180 -> translate(
                        position.width().toFloat(),
                        position.height().toFloat()
                    )
                    SensorOrientationListener.SensorRotation.ROTATION_270 -> translate(
                        position.width().toFloat(),
                        0F
                    )
                }
                when (sensorRotation) {
                    SensorOrientationListener.SensorRotation.ROTATION_0, SensorOrientationListener.SensorRotation.ROTATION_180 -> rotate(
                        sensorRotation.rotation.toFloat()
                    )
                    SensorOrientationListener.SensorRotation.ROTATION_90 -> rotate(270F)
                    SensorOrientationListener.SensorRotation.ROTATION_270 -> rotate(90F)
                }

                drawText(
                    "${marker.label} ${if (marker.confidence > 0) "${(marker.confidence * 100).roundToInt()}%" else ""}",
                    0F,
                    0F,
                    Paint().apply {
                        textSize = 20.sp.toPx()
                        color = android.graphics.Color.WHITE
                    })
                restore()
            }
        }
        val color = when (marker.trackId) {
            currentTrackingId -> Color.Red
            paddingTrackingId -> Color.Yellow
            else -> Color.Green
        }
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(50F, 50F),
            alpha = 0.2F,
            size = Size(position.width().toFloat(), position.height().toFloat())
        )
    }
}