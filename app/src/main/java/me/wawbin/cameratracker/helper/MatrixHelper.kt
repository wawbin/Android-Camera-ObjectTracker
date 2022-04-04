package me.wawbin.cameratracker.helper

import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.util.Size
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.core.graphics.toRectF

object MatrixHelper {

    private val matrix = object {
        val currentMatrix = Matrix()
        var proxyImageSize = Size(0, 0)
        var previewSize = Size(0, 0)
        val cropRect = Rect()
        var sensorRotation = SensorOrientationListener.SensorRotation.ROTATION_0
        var isMirror = false
    }

    fun updateMatrix(
        imageProxy: ImageProxy,
        previewView: PreviewView,
        sensorOrientation: SensorOrientationListener.SensorRotation,
        isMirror: Boolean
    ) {
        // 条件都一样就没必要更新了
        if (matrix.proxyImageSize.width == imageProxy.width && matrix.proxyImageSize.height == imageProxy.height &&
            matrix.previewSize.height == previewView.height && matrix.previewSize.width == previewView.width &&
            matrix.sensorRotation == sensorOrientation && matrix.isMirror == isMirror && matrix.cropRect == imageProxy.cropRect
        ) return

        val previewWidth = previewView.width.toFloat()
        val previewHeight = previewView.height.toFloat()
        // 左上角开始顺时针
        val screen = floatArrayOf(
            0f,
            0f,
            previewWidth,
            0f,
            previewWidth,
            previewHeight,
            0f,
            previewHeight
        )
        // 0和180度imageProxy的width和height要交换 因为ImageAnalysis在此时得到的是竖向的图片(摄像头反装)
        // 重点注意setPolyToPoly函数的作用 生成把一个矩形中全部的点映射到另外一个矩形中的矩阵 注意坐标轴是钉死的
        val imageProxyWidth = imageProxy.cropRect.width().toFloat()
        val imageProxyHeight = imageProxy.cropRect.height().toFloat()

        val source = when (sensorOrientation) {
            // 水平+垂直镜像
            SensorOrientationListener.SensorRotation.ROTATION_90, SensorOrientationListener.SensorRotation.ROTATION_270 -> floatArrayOf(
                imageProxyWidth,
                imageProxyHeight,
                0F,
                imageProxyHeight,
                0F,
                0F,
                imageProxyWidth,
                0F
            )
            // 倒立,水平+垂直镜像
            SensorOrientationListener.SensorRotation.ROTATION_180 -> floatArrayOf(
                imageProxyHeight,
                imageProxyWidth,
                0F,
                imageProxyWidth,
                0F,
                0F,
                imageProxyHeight,
                0F
            )
            // 0度 不需要特殊处理
            SensorOrientationListener.SensorRotation.ROTATION_0 -> floatArrayOf(
                0F,
                0F,
                imageProxyHeight,
                0F,
                imageProxyHeight,
                imageProxyWidth,
                0F,
                imageProxyWidth
            )
        }
        if (sensorOrientation == SensorOrientationListener.SensorRotation.ROTATION_90 || sensorOrientation == SensorOrientationListener.SensorRotation.ROTATION_270) {
            val vertexSize = 2
            val shiftOffset = sensorOrientation.rotation / 90 * vertexSize
            val tempArray = screen.clone()
            for (toIndex in source.indices) {
                val fromIndex = (toIndex + shiftOffset) % source.size
                screen[toIndex] = tempArray[fromIndex]
            }
        }

        matrix.proxyImageSize = Size(imageProxy.width, imageProxy.height)
        matrix.previewSize = Size(previewView.width, previewView.height)
        matrix.sensorRotation = sensorOrientation
        matrix.isMirror = isMirror
        matrix.cropRect.set(imageProxy.cropRect)

        // 更新
        matrix.currentMatrix.apply {
            reset()
            setPolyToPoly(source, 0, screen, 0, 4)
            if (isMirror) {
                val scaleX =
                    when (sensorOrientation) {
                        SensorOrientationListener.SensorRotation.ROTATION_0, SensorOrientationListener.SensorRotation.ROTATION_180 -> -1
                        else -> 1
                    }
                postScale(scaleX * 1F, scaleX * -1F, previewWidth / 2, previewHeight / 2)
            }
        }
    }

    fun mapRect(dst: RectF, src: Rect) {
        if (matrix.previewSize.width == 0 || matrix.previewSize.height == 0)
            throw IllegalStateException("Please Call MatrixHelper::updateMatrix First")

        val srcF = src.apply {
            // 移动到cropRect里面
            when (matrix.sensorRotation) {
                SensorOrientationListener.SensorRotation.ROTATION_0, SensorOrientationListener.SensorRotation.ROTATION_180 ->
                    offset(-matrix.cropRect.top, -matrix.cropRect.left)
                else -> offset(-matrix.cropRect.left, -matrix.cropRect.top)
            }
        }.toRectF()
        matrix.currentMatrix.mapRect(dst, srcF)
    }
}