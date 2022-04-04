package me.wawbin.cameratracker.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.wawbin.cameratracker.helper.showToast

enum class TrackingState {
    IDLE, LINKING, TRACKING, TRY_TO_RE_TRACK, LINKING_ERROR, TRACKING_MISS_AFTER_RE_TRACK, TRACKING_MISS
}

enum class TrackAction {
    CONFIRM_TRACKING_MISS, CONFIRM_LINKING_ERROR
}

@Composable
fun Tips(trackingState: TrackingState, targetLabel: String, onAction: (TrackAction) -> Unit) {
    when (trackingState) {
        TrackingState.LINKING -> {
            Dialog(
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                ), onDismissRequest = { }) {
                Surface(color = Color.White, shape = RoundedCornerShape(25.dp)) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentSize(
                                    Alignment.Center
                                )
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = "正在初始化设备", modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentSize(
                                    Alignment.Center
                                )
                        )
                    }
                }
            }
        }

        TrackingState.LINKING_ERROR -> {
            GenericAlertDialog(title = "连接超时", text = "请检查是否已连接到开发板的WIFI") {
                onAction(TrackAction.CONFIRM_LINKING_ERROR)
            }
        }

        TrackingState.TRACKING_MISS_AFTER_RE_TRACK -> {
            GenericAlertDialog(title = "目标${targetLabel}已丢失", text = "重寻失败,请重新选择一个目标进行跟踪") {
                onAction(TrackAction.CONFIRM_TRACKING_MISS)
            }
        }

        TrackingState.TRACKING_MISS -> {
            GenericAlertDialog(
                title = "目标${targetLabel}已丢失",
                text = "unknown目标不支持重寻,请重新选择一个目标进行跟踪"
            ) {
                onAction(TrackAction.CONFIRM_TRACKING_MISS)
            }
        }

        TrackingState.TRACKING -> LaunchedEffect(Unit) {
            "开始跟踪${targetLabel}".showToast()
        }

        TrackingState.TRY_TO_RE_TRACK -> LaunchedEffect(Unit) {
            "目标${targetLabel}已丢失,正在尝试自动重寻相似物体...".showToast()
        }
    }

}

@Composable
private fun GenericAlertDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = { onDismiss() },
        buttons = {
            Text(text = "OK",
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(align = Alignment.End)
                    .clickable { onDismiss() }
                    .padding(20.dp)
            )
        },
        title = { Text(text = title) },
        text = { Text(text = text) })
}