package com.lottiefiles.dotlottie.core.util

import com.dotlottie.dlplayer.Observer

interface DotLottieEventListener : Observer {
    override fun onLoop(loopCount: UInt) {}
    override fun onRender(frameNo: Float) {}
    override fun onStop() {}
    override fun onFrame(frame: Float) {}
    override fun onPause() {}
    override fun onPlay() { }
    override fun onComplete() { }
    override fun onLoad() { }
    override fun onLoadError() { }
    fun onLoadError(error: Throwable) {}
    fun onFreeze() {}
    fun onUnFreeze() {}
    fun onDestroy() {}
    fun onLoop() {}
}