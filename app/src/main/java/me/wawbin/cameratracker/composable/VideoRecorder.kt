package me.wawbin.cameratracker.composable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import me.wawbin.cameratracker.R

enum class RecorderState {
    IDLE, RECORDING, PAUSE
}

@Composable
fun VideoRecorder(
    modifier: Modifier = Modifier,
    rotation: Float,
    state: RecorderState,
    onMainClick: () -> Unit,
    onSecondaryClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        CustomIconButton(
            modifier = Modifier
                .graphicsLayer(rotationZ = rotation)
                .align(Alignment.Center),
            painter = if (state == RecorderState.IDLE) R.drawable.ic_baseline_videocam_24 else R.drawable.ic_baseline_videocam_off_24,
            backgroundColor = if (state == RecorderState.IDLE) MaterialTheme.colors.primary else Color.Red,
            description = if (state == RecorderState.IDLE) "录像" else "停止录像"
        ) {
            onMainClick()
        }

        val density = LocalDensity.current
        AnimatedVisibility(
            modifier = Modifier.align(Alignment.CenterEnd),
            visible = state != RecorderState.IDLE,
            enter = slideInHorizontally { with(density) { 130.dp.roundToPx() } },
            exit = slideOutHorizontally { with(density) { 130.dp.roundToPx() } }
        ) {
            CustomIconButton(
                modifier = Modifier.graphicsLayer(rotationZ = rotation),
                painter = if (state == RecorderState.PAUSE) R.drawable.ic_baseline_play_arrow_24 else R.drawable.ic_baseline_pause_24,
                backgroundColor = if (state == RecorderState.PAUSE) Color.Gray else MaterialTheme.colors.onSecondary,
                description = if (state == RecorderState.PAUSE) "继续录像" else "暂停录像"
            ) {
                onSecondaryClick()
            }
        }
    }

}