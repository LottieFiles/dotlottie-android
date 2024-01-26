package com.lottiefiles.dotlottie.core.widget

interface DotLottieEventListener {
    fun onStop() {}
    fun onFrame(frame: Float) {}
    fun onPause() {}
    fun onPlay() {}
    fun onDestroy() {}
    fun onLoop() {}
    fun onComplete() {}
    fun onLoopComplete() {}
    fun onLoad() {}
    fun onLoadError(error: Throwable) {}

    fun onFreeze() {}
    fun onUnFreeze() {}
}