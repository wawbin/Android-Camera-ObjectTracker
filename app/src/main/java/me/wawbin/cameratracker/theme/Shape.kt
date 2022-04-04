package me.wawbin.cameratracker.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Shapes
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    small = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(percent = 10)
)