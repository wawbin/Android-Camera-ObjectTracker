package me.wawbin.cameratracker.helper

import android.content.Context
import android.widget.Toast

object ToastHelper {

    private var toast: Toast? = null

    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    fun showToast(text: String, duration: Int) {
        toast?.cancel()
        applicationContext?.let {
            Toast.makeText(it, text, duration).also { toa -> toast = toa }.show()
        } ?: throwException()
    }

    private fun throwException() {
        throw Exception("Please call ToastHelper.initialize(Context) first")
    }

}

fun String.showToast(duration: Int = Toast.LENGTH_SHORT) =
    ToastHelper.showToast(this, duration)