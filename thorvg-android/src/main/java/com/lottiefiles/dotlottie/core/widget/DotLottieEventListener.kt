package com.lottiefiles.dotlottie.core.widget

interface DotLottieEventListener {
    fun onStop() {}
    fun onFrame(frame: Int) {}
    fun onPause() {}
    fun onPlay() {}
    fun onDestroy() {}

    fun onLoop() {}
    fun onComplete() {}
    fun onLoad() {}
    fun onFreeze() {}
    fun onUnFreeze() {}
}