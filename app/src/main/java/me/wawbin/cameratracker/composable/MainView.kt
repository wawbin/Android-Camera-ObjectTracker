package me.wawbin.cameratracker.composable

import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.statusBarsPadding
import me.wawbin.cameratracker.R
import me.wawbin.cameratracker.helper.showToast
import me.wawbin.cameratracker.viewmodel.MainActivityVM

enum class VideoCaptureAction {
    START, STOP, PAUSE, RESUME
}

@Composable
fun MainView(
    viewModel: MainActivityVM, previewView: PreviewView, onScale: (Float) -> Unit,
    switchCamera: () -> Unit, onVideoCapture: (VideoCaptureAction) -> Unit
) {
    val view = LocalView.current
    // 防止锁屏
    DisposableEffect(key1 = view) {
        // view is androidx.compose.ui.platform.AndroidComposeView
        // 大致看了下,activity的setContent会建一个composeView的viewGroup
        // 它的setContent方法最终会往这个viewGroup里面add一个AndroidComposeView
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
        }
    }

    val ro = viewModel.sensorOrientation.rotation.let {
        if (it == 90 || it == 270) it - 180 else it
    }
    val rotationState by animateFloatAsState(ro.toFloat())
    Box(modifier = Modifier.fillMaxSize()) {
        val currentLabel = viewModel.currentTrackingObject.label
        TrackingView(viewModel, previewView, onScale) {
            viewModel.startTrack(it)
        }
        TopTitle(
            title = currentLabel.ifEmpty { "IDLE" },
            tracking = viewModel.trackingState != TrackingState.IDLE,
            rotation = rotationState
        ) { stop ->
            if (stop) viewModel.stop()
            else switchCamera()
        }
        LaunchedEffect(key1 = viewModel.isSupportVideoCapture) {
            if (!viewModel.isSupportVideoCapture) "您的设备相机未达到LEVEL-3,暂不支持录像".showToast()
        }
        AnimatedVisibility(
            visible = viewModel.isSupportVideoCapture,
            modifier = Modifier
                .padding(bottom = 30.dp)
                .align(Alignment.BottomCenter),
            exit = slideOutVertically(targetOffsetY = { it + 30 })
        ) {
            VideoRecorder(
                rotation = rotationState,
                state = viewModel.recorderState,
                onMainClick = {
                    onVideoCapture(if (viewModel.recorderState == RecorderState.IDLE) VideoCaptureAction.START else VideoCaptureAction.STOP)
                }) {
                onVideoCapture(if (viewModel.recorderState == RecorderState.RECORDING) VideoCaptureAction.PAUSE else VideoCaptureAction.RESUME)
            }
        }
        Tips(
            trackingState = viewModel.trackingState,
            targetLabel = currentLabel
        ) {
            viewModel.stop()
        }
    }

}

@Composable
private fun TopTitle(
    modifier: Modifier = Modifier,
    show: Boolean = true,
    title: String,
    tracking: Boolean,
    rotation: Float,
    onClick: (Boolean) -> Unit
) {
    val insets = LocalWindowInsets.current

    AnimatedVisibility(
        visible = show,
        modifier = Modifier
            .statusBarsPadding()
            .then(modifier),
        enter = slideInVertically(initialOffsetY = { -it - insets.statusBars.top }),
        exit = slideOutVertically(targetOffsetY = { -it - insets.statusBars.top })
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(
                modifier = Modifier.align(Alignment.Center),
                text = title,
                fontSize = 20.sp,
                color = Color.White
            )
            IconButton(
                onClick = { onClick(tracking) },
                modifier = Modifier
                    .graphicsLayer(rotationZ = rotation)
                    .align(Alignment.CenterEnd)
            ) {
                Icon(
                    painter = painterResource(id = if (tracking) R.drawable.ic_baseline_gps_off_24 else R.drawable.ic_baseline_flip_camera_ios_24),
                    contentDescription = if (tracking) "Stop" else "Switch Camera",
                    tint = Color.White
                )
            }
        }
    }
}