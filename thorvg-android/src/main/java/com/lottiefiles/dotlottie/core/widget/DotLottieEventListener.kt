package com.lottiefiles.dotlottie.core.widget

interface DotLottieEventListener {
    fun onStop() {}
    fun onFrame(frame: Double) {}
    fun onPause() {}
    fun onPlay() {}
    fun onDestroy() {}
    fun onLoop() {}
    fun onComplete() {}
    fun onLoad() {}
    fun onLoadError(error: Throwable) {}

    fun onFreeze() {}
    fun onUnFreeze() {}
}