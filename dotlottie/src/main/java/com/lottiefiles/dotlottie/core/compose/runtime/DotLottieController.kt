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

    private var stateMachineGestureListeners: MutableList<String> = mutableListOf()
        private set

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

    val activeThemeId: String
        get() = dlplayer?.activeThemeId() ?: ""

    val activeAnimationId: String
        get() = dlplayer?.activeAnimationId() ?: ""

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

    fun stateMachineStart(): Boolean {
        val result = dlplayer?.stateMachineStart() ?: false
        if (result) {
            if (dlplayer != null) {
                stateMachineGestureListeners =
                    dlplayer!!.stateMachineFrameworkSetup().map { it.lowercase() }.toSet().toMutableList()
            }

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

    fun stateMachineStop(): Boolean {
        return dlplayer?.stateMachineStop() ?: false
    }

    fun stateMachineLoad(stateMachineId: String): Boolean {
        return dlplayer?.stateMachineLoad(stateMachineId) ?: false
    }

    fun stateMachineLoadData(data: String): Boolean {
        return dlplayer?.stateMachineLoadData(data) ?: false
    }

    /**
     * Internal function to notify the state machine of gesture input.
     */
    fun stateMachinePostEvent(event: Event, force: Boolean = false): Int {
        var ret: Int = 1
        // Extract the event name before the parenthesis
        val eventName = event.toString().split("(").firstOrNull()?.lowercase() ?: event.toString()

        if (force) {
            ret = dlplayer?.stateMachinePostEvent(event) ?: 0
        } else if (stateMachineGestureListeners.contains(eventName)) {
            ret = dlplayer?.stateMachinePostEvent(event) ?: 0
        }

        return ret
    }

    fun stateMachineFireEvent(event: String) {
        dlplayer?.stateMachineFireEvent(event)
    }

    fun stateMachineSetNumericTrigger(key: String, value: Float): Boolean {
        return dlplayer?.stateMachineSetNumericTrigger(key, value) ?: false
    }

    fun stateMachineSetStringTrigger(key: String, value: String): Boolean {
        return dlplayer?.stateMachineSetStringTrigger(key, value) ?: false
    }

    fun stateMachineSetBooleanTrigger(key: String, value: Boolean): Boolean {
        return dlplayer?.stateMachineSetBooleanTrigger(key, value) ?: false
    }

    fun stateMachineGetNumericTrigger(key: String): Float? {
        return dlplayer?.stateMachineGetNumericTrigger(key)
    }

    fun stateMachineGetStringTrigger(key: String): String? {
        return dlplayer?.stateMachineGetStringTrigger(key)
    }

    fun stateMachineGetBooleanTrigger(key: String): Boolean? {
        return dlplayer?.stateMachineGetBooleanTrigger(key)
    }

    fun stateMachineCurrentState(): String? {
        return dlplayer?.stateMachineCurrentState()
    }

    fun stateMachineAddEventListener(listener: StateMachineEventListener) {
        stateMachineListeners.add(listener)
    }

    fun stateMachineRemoveEventListener(listener: StateMachineEventListener) {
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

    fun setTheme(themeId: String) {
        dlplayer?.setTheme(themeId)
    }

    fun setThemeData(themeData: String) {
        dlplayer?.setThemeData(themeData)
    }

    fun resetTheme() {
        dlplayer?.resetTheme()
    }

    fun setSlots(slots: String) {
        dlplayer?.setSlots(slots)
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