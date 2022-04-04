package me.wawbin.cameratracker.helper

import androidx.compose.ui.geometry.Offset

data class MutableOffset(
    var x: Float,
    var y: Float
) {
    fun set(src: Offset) {
        x = src.x
        y = src.y
    }

    fun isValid() = x > 0F && y > 0F
    fun setInvalid() {
        x = 0F
        y = 0F
    }
}