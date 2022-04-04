package me.wawbin.cameratracker.helper

import android.content.Context
import android.view.OrientationEventListener
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class SensorOrientationListener(
    private val context: Context,
    private val onChanged: (Int) -> Unit
) :
    DefaultLifecycleObserver {

    enum class SensorRotation(val rotation: Int) {
        ROTATION_0(0), ROTATION_90(90), ROTATION_180(180), ROTATION_270(270)
    }

    private val orientationEventListener =
        object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                onChanged(orientation)
            }
        }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        orientationEventListener.enable()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        orientationEventListener.disable()
    }

}