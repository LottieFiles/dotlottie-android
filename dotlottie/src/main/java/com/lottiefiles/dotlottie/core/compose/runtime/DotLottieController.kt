package com.lottiefiles.dotlottie.core.compose.runtime

import com.dotlottie.dlplayer.DotLottiePlayer
import com.dotlottie.dlplayer.Manifest
import com.dotlottie.dlplayer.Mode
import com.dotlottie.dlplayer.Observer
import com.lottiefiles.dotlottie.core.util.DotLottieEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DotLottieController {
    private var dlplayer: DotLottiePlayer? = null
    private var observer: Observer? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _width = MutableStateFlow(0u)
    val width: StateFlow<UInt> = _width.asStateFlow()

    private val _height = MutableStateFlow(0u)
    val height: StateFlow<UInt> = _height.asStateFlow()

    var eventListeners = mutableListOf<DotLottieEventListener>()
        private set

    val isPlaying: Boolean
        get() = dlplayer?.isPlaying() ?: false

    val isLoaded: Boolean
        get() = dlplayer?.isLoaded() ?: false

    val isComplete: Boolean
        get() = dlplayer?.isComplete() ?: false

    val isStopped: Boolean
        get() = dlplayer?.isStopped() ?: false

    val isPaused: Boolean
        get() = dlplayer?.isPaused() ?: false

    val speed: Float
        get() = dlplayer?.config()?.speed ?: 1f
    val loop: Boolean
        get() = dlplayer?.config()?.loopAnimation ?: false

    val autoplay: Boolean
        get() = dlplayer?.config()?.autoplay ?: false

    val totalFrames: Float
        get() = dlplayer?.totalFrames() ?: 0f

    val currentFrame: Float
        get() = dlplayer?.currentFrame() ?: 0f

    val playMode: Mode
        get() = dlplayer?.config()?.mode ?: Mode.FORWARD

    val segments: Pair<Float, Float>?
        get() {
            if (dlplayer?.config()?.segments!!.isEmpty() || dlplayer?.config()?.segments?.size != 2) return null
            return Pair(dlplayer?.config()?.segments!![0], dlplayer!!.config().segments[1])
        }

    val duration: Float
        get() = dlplayer?.duration() ?: 0f

    val loopCount: UInt
        get() = dlplayer?.loopCount() ?: 0u

    val useFrameInterpolation: Boolean
        get() = dlplayer?.config()?.useFrameInterpolation ?: false

    fun play() {
        dlplayer?.play()
    }

    fun pause() {
        dlplayer?.pause()
    }

    fun stop() {
        dlplayer?.stop()
    }

    private fun subscribe() {
        observer = object : Observer {
            override fun onComplete() {
                _isRunning.value = false
                eventListeners.forEach(DotLottieEventListener::onComplete)
            }

            override fun onFrame(frameNo: Float) {
                eventListeners.forEach { it.onFrame(frameNo) }
            }

            override fun onPause() {
                _isRunning.value = false
                eventListeners.forEach(DotLottieEventListener::onPause)
            }

            override fun onStop() {
                _isRunning.value = false
                eventListeners.forEach(DotLottieEventListener::onStop)
            }

            override fun onPlay() {
                _isRunning.value = true
                eventListeners.forEach(DotLottieEventListener::onPlay)
            }

            override fun onLoad() {
                _isRunning.value = dlplayer?.isPlaying() ?: false
                eventListeners.forEach(DotLottieEventListener::onLoad)
            }

            override fun onLoop(loopCount: UInt) {
                eventListeners.forEach { it.onLoop(loopCount) }
            }

            override fun onRender(frameNo: Float) {
                eventListeners.forEach { it.onRender(frameNo) }
            }

            override fun onLoadError() {
                eventListeners.forEach(DotLottieEventListener::onLoadError)
            }
        }
        dlplayer?.subscribe(observer!!)
    }

    fun setPlayerInstance(player: DotLottiePlayer) {
        dlplayer?.destroy()
        dlplayer = player
        subscribe()
    }

    fun resize(width: UInt, height: UInt) {
        _width.value = width
        _height.value = height
    }

    fun setFrame(frame: Float) {
        dlplayer?.setFrame(frame)
    }

    fun setUseFrameInterpolation(enable: Boolean) {
        dlplayer?.let {
            val config = it.config()
            config.useFrameInterpolation = enable;
            it.setConfig(config)
        }

    }

    fun setSegments(firstFrame: Float, lastFrame: Float) {
        dlplayer?.let {
            val config = it.config()
            config.segments = listOf(firstFrame, lastFrame);
            it.setConfig(config)
        }
    }

    fun setLoop(loop: Boolean) {
        dlplayer?.let {
            val config = it.config()
            config.loopAnimation = loop
            it.setConfig(config)
        }
    }

    fun freeze() {
        dlplayer?.pause()
        eventListeners.forEach(DotLottieEventListener::onFreeze)
    }

    fun unFreeze() {
        dlplayer?.play()
        eventListeners.forEach(DotLottieEventListener::onUnFreeze)
    }

    fun setSpeed(speed: Float) {
        dlplayer?.let {
            val config = it.config()
            config.speed = speed
            it.setConfig(config)
        }
    }

    fun setPlayMode(mode: Mode) {
        dlplayer?.let {
            val config = it.config()
            config.mode = mode
            it.setConfig(config)
        }
    }

    fun loadAnimation(
        animationId: String,
        width: UInt = this.width.value,
        height: UInt = this.height.value
    ) {
        this._width.value  = width
        this._height.value = height
        dlplayer?.loadAnimation(animationId, width, height)
    }

    fun manifest(): Manifest? {
        return dlplayer?.manifest()
    }

    fun addEventListener(listener: DotLottieEventListener) {
        eventListeners.add(listener)
    }

    fun removeEventListener(listener: DotLottieEventListener) {
        eventListeners.remove(listener)
    }
}