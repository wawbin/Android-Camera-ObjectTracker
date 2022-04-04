package me.wawbin.cameratracker.composable

import androidx.annotation.DrawableRes
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import me.wawbin.cameratracker.R
import me.wawbin.cameratracker.viewmodel.MainActivityVM

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun FeatureThatRequiresCameraPermission(
    viewModel: MainActivityVM,
    previewView: PreviewView,
    onScale: (Float) -> Unit,
    switchCamera: () -> Unit,
    onPermissionGranted: suspend () -> Unit,
    onVideoCapture: (VideoCaptureAction) -> Unit
) {

    // Camera permission state
    val cameraPermissionState = rememberMultiplePermissionsState(
        listOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO
        )
    )

    when {
        cameraPermissionState.allPermissionsGranted -> {
            LaunchedEffect(Unit) {
                onPermissionGranted()
            }
            MainView(
                viewModel = viewModel,
                previewView = previewView,
                onScale = onScale,
                switchCamera = switchCamera,
                onVideoCapture = onVideoCapture
            )
        }
        else -> Notice(isRationale = cameraPermissionState.shouldShowRationale) {
            cameraPermissionState.launchMultiplePermissionRequest()
        }
    }
}

@Composable
private fun Notice(isRationale: Boolean, onClick: () -> Unit) {
    Scaffold(topBar = { CustomTopAppBar(tittle = "权限请求") }) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 8.dp),
                text = if (isRationale) "需要相机与麦克风权限进行物体识别与视频录制" else "请到系统设置界面手动授予本应用相关权限",
                style = MaterialTheme.typography.h5,
                textAlign = TextAlign.Center
            )

            if (isRationale) {
                Spacer(modifier = Modifier.height(80.dp))
                CustomIconButton(
                    painter = R.drawable.ic_baseline_east_24,
                    backgroundColor = MaterialTheme.colors.primary,
                    description = "进入"
                ) {
                    onClick()
                }
            }
        }
    }
}

@Composable
fun CustomIconButton(
    modifier: Modifier = Modifier,
    @DrawableRes painter: Int,
    backgroundColor: Color,
    description: String,
    onClick: () -> Unit
) {
    Icon(
        modifier = modifier
            .padding(top = 15.dp)
            .size(70.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = false, radius = 35.dp),
                onClick = { onClick() }
            )
            .background(color = backgroundColor, shape = CircleShape)
            .padding(16.dp),
        tint = Color.White,
        painter = painterResource(id = painter),
        contentDescription = description
    )
}