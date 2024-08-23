package com.lottiefiles.dotlottie.core.util

interface DotLottieEventListener {
    fun onLoop(loopCount: Int) {}
    fun onRender(frameNo: Float) {}
    fun onStop() {}
    fun onFrame(frame: Float) {}
    fun onPause() {}
    fun onPlay() {}
    fun onComplete() {}
    fun onLoad() {}
    fun onLoadError() {}
    fun onLoadError(error: Throwable) {}
    fun onFreeze() {}
    fun onUnFreeze() {}
    fun onDestroy() {}
}