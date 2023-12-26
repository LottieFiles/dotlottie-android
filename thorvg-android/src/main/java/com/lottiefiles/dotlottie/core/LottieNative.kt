package com.lottiefiles.dotlottie.core

import android.graphics.Bitmap

object LottieNative {
    init {
        System.loadLibrary("thorvg-android")
    }

    @JvmStatic
    external fun nCreateLottie(content: String?, length: Int, outValues: IntArray?): Long
    @JvmStatic
    external fun nSetLottieBufferSize(lottiePtr: Long, bitmap: Bitmap?, width: Float, height: Float)
    @JvmStatic
    external fun nDrawLottieFrame(lottiePtr: Long, bitmap: Bitmap?, frame: Int)
    @JvmStatic
    external fun nDestroyLottie(lottiePtr: Long)
}