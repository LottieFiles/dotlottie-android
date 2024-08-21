package com.lottiefiles.dotlottie.core.compose.runtime

import com.dotlottie.dlplayer.DotLottiePlayer
import com.dotlottie.dlplayer.Event
import com.dotlottie.dlplayer.Fit
import com.dotlottie.dlplayer.Layout
import com.dotlottie.dlplayer.Manifest
import com.dotlottie.dlplayer.Marker
import com.dotlottie.dlplayer.Mode
import com.dotlottie.dlplayer.Observer
import com.dotlottie.dlplayer.StateMachineObserver
import com.lottiefiles.dotlottie.core.util.DotLottieEventListener
import com.lottiefiles.dotlottie.core.util.LayoutUtil
import com.lottiefiles.dotlottie.core.util.StateMachineEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DotLottiePlayerState {
    PLAYING,
    PAUSED,
    STOPPED,
    COMPLETED,
    INITIAL,
    LOADED,
    ERROR,
    DRAW,
}

class DotLottieController {
    private var dlplayer: DotLottiePlayer? = null
    private var observer: Observer? = null

    private val _currentState = MutableStateFlow(DotLottiePlayerState.INITIAL)
    val currentState: StateFlow<DotLottiePlayerState> = _currentState.asStateFlow()

    private val _width = MutableStateFlow(0u)
    val width: StateFlow<UInt> = _width.asStateFlow()

    private val _height = MutableStateFlow(0u)
    val height: StateFlow<UInt> = _height.asStateFlow()

    var stateMachineListeners: MutableList<StateMachineEventListener> = mutableListOf()
        private set
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

    val segment: Pair<Float, Float>?
        get() {
            if (dlplayer?.config()?.segment!!.isEmpty() || dlplayer?.config()?.segment?.size != 2) return null
            return Pair(dlplayer?.config()?.segment!![0], dlplayer!!.config().segment[1])
        }

    val duration: Float
        get() = dlplayer?.duration() ?: 0f

    val loopCount: UInt
        get() = dlplayer?.loopCount() ?: 0u

    val useFrameInterpolation: Boolean
        get() = dlplayer?.config()?.useFrameInterpolation ?: false

    val markers: List<Marker>
        get() = dlplayer?.markers() ?: emptyList()

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
                _currentState.value = DotLottiePlayerState.COMPLETED
                eventListeners.forEach(DotLottieEventListener::onComplete)
            }

            override fun onFrame(frameNo: Float) {
                eventListeners.forEach { it.onFrame(frameNo) }
            }

            override fun onPause() {
                _currentState.value = DotLottiePlayerState.PAUSED
                eventListeners.forEach(DotLottieEventListener::onPause)
            }

            override fun onStop() {
                _currentState.value = DotLottiePlayerState.STOPPED
                eventListeners.forEach(DotLottieEventListener::onStop)
            }

            override fun onPlay() {
                _currentState.value = DotLottiePlayerState.PLAYING
                eventListeners.forEach(DotLottieEventListener::onPlay)
            }

            override fun onLoad() {
                _currentState.value = DotLottiePlayerState.LOADED
                eventListeners.forEach(DotLottieEventListener::onLoad)
            }

            override fun onLoop(loopCount: UInt) {
                eventListeners.forEach { it.onLoop(loopCount.toInt()) }
            }

            override fun onRender(frameNo: Float) {
                eventListeners.forEach { it.onRender(frameNo) }
            }

            override fun onLoadError() {
                _currentState.value = DotLottiePlayerState.ERROR
                eventListeners.forEach(DotLottieEventListener::onLoadError)
            }
        }
        dlplayer?.subscribe(observer!!)
    }

    fun startStateMachine(): Boolean {
        val result = dlplayer?.startStateMachine() ?: false
        if (result) {
            if (this.isPlaying) {
                this.play()
            }

            dlplayer?.stateMachineSubscribe(object : StateMachineObserver {
                override fun onStateEntered(enteringState: String) {
                    stateMachineListeners.forEach { it.onStateEntered(enteringState) }
                }

                override fun onStateExit(leavingState: String) {
                    stateMachineListeners.forEach { it.onStateExit(leavingState) }
                }

                override fun onTransition(previousState: String, newState: String) {
                    stateMachineListeners.forEach { it.onTransition(previousState, newState) }
                }
            })
        }
        return result
    }

    fun stopStateMachine(): Boolean {
        return dlplayer?.stopStateMachine() ?: false
    }

    fun loadStateMachine(stateMachineId: String): Boolean {
        return dlplayer?.loadStateMachine(stateMachineId) ?: false
    }

    fun postEvent(event: Event): Int {
        val result = dlplayer?.postEvent(event) ?: 0
        when (result) {
            1 -> {
                eventListeners.forEach { it.onError(Throwable("Error posting event: $event")) }
            }

            2 -> {
                this.play()
            }

            3 -> {
                this.pause()
            }

            4 -> {
                _currentState.value = DotLottiePlayerState.DRAW
            }
        }

        return result
    }

    fun setStateMachineNumericContext(key: String, value: Float): Boolean {
        return dlplayer?.setStateMachineNumericContext(key, value) ?: false
    }

    fun setStateMachineStringContext(key: String, value: String): Boolean {
        return dlplayer?.setStateMachineStringContext(key, value) ?: false
    }

    fun setStateMachineBooleanContext(key: String, value: Boolean): Boolean {
        return dlplayer?.setStateMachineBooleanContext(key, value) ?: false
    }

    fun addStateMachineEventListener(listener: StateMachineEventListener) {
        stateMachineListeners.add(listener)
    }

    fun removeStateMachineEventListener(listener: StateMachineEventListener) {
        stateMachineListeners.remove(listener)
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

    fun setSegment(firstFrame: Float, lastFrame: Float) {
        dlplayer?.let {
            val config = it.config()
            config.segment = listOf(firstFrame, lastFrame);
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

    fun setMarker(marker: String) {
        dlplayer?.let {
            val config = it.config()
            config.marker = marker
            it.setConfig(config)
        }
    }

    fun setLayout(fit: Fit, alignment: LayoutUtil.Alignment) {
        dlplayer?.let {
            val config = it.config()
            config.layout =
                Layout(fit, listOf(alignment.alignment.first, alignment.alignment.second))
            it.setConfig(config)
        }
    }

    fun setLayout(fit: Fit, alignment: Pair<Float, Float>) {
        dlplayer?.let {
            val config = it.config()
            config.layout = Layout(fit, listOf(alignment.first, alignment.second))
            it.setConfig(config)
        }
    }

    fun loadTheme(themeId: String) {
        dlplayer?.loadTheme(themeId)
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
    ) {
        dlplayer?.loadAnimation(animationId, this._width.value, this._height.value)
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