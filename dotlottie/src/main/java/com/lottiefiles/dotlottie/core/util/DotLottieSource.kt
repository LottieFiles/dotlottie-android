package com.lottiefiles.dotlottie.core.util
sealed class DotLottieSource {
    data class Url(val urlString: String) : DotLottieSource() // .json / .lottie
    data class Asset(val assetPath: String) : DotLottieSource() // .json / .lottie
    data class Data(val data: ByteArray) : DotLottieSource()
    data class Json(val jsonString: String) : DotLottieSource()
}
