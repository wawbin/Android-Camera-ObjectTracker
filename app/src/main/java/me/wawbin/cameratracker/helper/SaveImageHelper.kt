package me.wawbin.cameratracker.helper

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import java.io.FileOutputStream

// This is For Debug
fun Image.toBitmap() {
    val yBuffer = planes[0].buffer // Y
    val uBuffer = planes[1].buffer // U
    val vBuffer = planes[2].buffer // V

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    //U and V are swapped
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
    // val out = ByteArrayOutputStream()

    val out = FileOutputStream("/data/data/me.wawbin.cameratracker/files/23.png")

    yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)

    out.flush()
    out.close()

    // val imageBytes = out.toByteArray()
    // return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}