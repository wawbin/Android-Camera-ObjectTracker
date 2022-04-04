package me.wawbin.cameratracker.repository

object ServoNativeImpl {

    external fun startLink(): Boolean

    // 要旋转多少度
    // return: 0 正常 -1 断连 -2 超过0-180
    external fun rotate(degrees: Int): Int

    // 要到多少度
    external fun rotateTo(degrees: Int): Int

    external fun getCurrentRotation(): Int

    external fun shutdown()

}