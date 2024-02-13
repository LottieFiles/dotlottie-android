package com.lottiefiles.dotlottie.core.util

sealed class DotLottieContent {
    data class Json(val jsonString: String) : DotLottieContent()
    data class Binary(val data: ByteArray) : DotLottieContent()
}