package com.lottiefiles.dotlottie.core.compose.runtime

import com.dotlottie.dlplayer.Config
import com.dotlottie.dlplayer.DotLottiePlayer
import com.dotlottie.dlplayer.Event
import com.dotlottie.dlplayer.Fit
import com.dotlottie.dlplayer.Layout
import com.dotlottie.dlplayer.Manifest
import com.dotlottie.dlplayer.Marker
import com.dotlottie.dlplayer.Mode
import com.dotlottie.dlplayer.OpenUrlPolicy
import com.dotlottie.dlplayer.createDefaultOpenUrlPolicy
import com.dotlottie.dlplayer.createDefaultConfig
import com.lottiefiles.dotlottie.core.util.DotLottieEventListener
import com.lottiefiles.dotlottie.core.util.InternalDotLottieApi
import com.lottiefiles.dotlottie.core.util.LayoutUtil
import com.lottiefiles.dotlottie.core.util.StateMachineEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class DotLottiePlayerState {
    PLAYING,
    PAUSED,
    STOPPED,
    COMPLETED,
    INITIAL,
    LOADED,
    ERROR
}

class DotLottieController {
    private var dlplayer: DotLottiePlayer? = null
    private var config: Config = createDefaultConfig()

    private val _currentState = MutableStateFlow(DotLottiePlayerState.INITIAL)
    val currentState: StateFlow<DotLottiePlayerState> = _currentState.asStateFlow()

    private var shouldPlayOnInit = false

    private val _width = MutableStateFlow(0u)
    val width: StateFlow<UInt> = _width.asStateFlow()

    private val _height = MutableStateFlow(0u)
    val height: StateFlow<UInt> = _height.asStateFlow()

    var stateMachineGestureListeners: MutableList<String> = mutableListOf()

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

    var stateMachineIsActive: Boolean = false
        get() = field

    private val _bufferNeedsUpdate = MutableStateFlow(false)
    val bufferNeedsUpdate: StateFlow<Boolean> = _bufferNeedsUpdate.asStateFlow()

    fun markBufferUpdated() {
        _bufferNeedsUpdate.value = false
    }

    @InternalDotLottieApi
    fun updateState(state: DotLottiePlayerState) {
        _currentState.update { state }
    }

    // Signal to trigger bitmap update after setFrame() when not playing
    private val _frameUpdateRequested = MutableStateFlow(0L)
    val frameUpdateRequested: StateFlow<Long> = _frameUpdateRequested.asStateFlow()

    fun play() {
        dlplayer?.play()
        _currentState.update { DotLottiePlayerState.PLAYING }
        shouldPlayOnInit = true
    }

    fun pause() {
        dlplayer?.pause()
        _currentState.update { DotLottiePlayerState.PAUSED }
        shouldPlayOnInit = false
    }

    fun stop() {
        dlplayer?.stop()
        _currentState.update { DotLottiePlayerState.STOPPED }
        shouldPlayOnInit = false
    }

    fun stateMachineStart(
        openUrl: OpenUrlPolicy = createDefaultOpenUrlPolicy(),
        onOpenUrl: ((url: String) -> Unit)? = null
    ): Boolean {
        val result = dlplayer?.stateMachineStart(openUrl) ?: false
        if (result) {
            stateMachineIsActive = true
            // Handles states that are in paused state
            _frameUpdateRequested.value++

            if (dlplayer != null) {
                stateMachineGestureListeners =
                    dlplayer!!.stateMachineFrameworkSetup().map { it.lowercase() }.toSet()
                        .toMutableList()
            }

        }
        return result
    }

    fun stateMachineStop(): Boolean {
        stateMachineIsActive = false
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
    fun stateMachinePostEvent(event: Event, force: Boolean = false) {
        // Extract the event name before the parenthesis
        val eventName = event.toString().split("(").firstOrNull()?.lowercase() ?: event.toString()

        if (force) {
            dlplayer?.stateMachinePostEvent(event)
        } else if (stateMachineGestureListeners.contains(eventName)) {
            dlplayer?.stateMachinePostEvent(event)
        }
    }

    fun stateMachineFire(event: String) {
        dlplayer?.stateMachineFireEvent(event)
    }

    fun stateMachineSetNumericInput(key: String, value: Float): Boolean {
        return dlplayer?.stateMachineSetNumericInput(key, value) ?: false
    }

    fun stateMachineSetStringInput(key: String, value: String): Boolean {
        return dlplayer?.stateMachineSetStringInput(key, value) ?: false
    }

    fun stateMachineSetBooleanInput(key: String, value: Boolean): Boolean {
        return dlplayer?.stateMachineSetBooleanInput(key, value) ?: false
    }

    fun stateMachineGetNumericInput(key: String): Float? {
        return dlplayer?.stateMachineGetNumericInput(key)
    }

    fun stateMachineGetStringInput(key: String): String? {
        return dlplayer?.stateMachineGetStringInput(key)
    }

    fun stateMachineGetBooleanInput(key: String): Boolean? {
        return dlplayer?.stateMachineGetBooleanInput(key)
    }

    fun stateMachineGetInputs(): List<String>? {
        return dlplayer?.stateMachineGetInputs()
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

    @InternalDotLottieApi
    fun setPlayerInstance(player: DotLottiePlayer, config: Config) {
        dlplayer?.destroy()
        dlplayer = player
        this.config = config
    }

    @InternalDotLottieApi
    fun init() {
        dlplayer?.setConfig(config)

        if (shouldPlayOnInit) {
            this.play()
            shouldPlayOnInit = false
        } else if (dlplayer?.isPlaying() == false) {
            _currentState.update { DotLottiePlayerState.PAUSED }
        }
    }

    fun resize(width: UInt, height: UInt) {
        _width.value = width
        _height.value = height
    }

    fun setFrame(frame: Float) {
        dlplayer?.setFrame(frame)
        // Signal animation to update bitmap
        _frameUpdateRequested.value++
    }

    fun setUseFrameInterpolation(enable: Boolean) {
        dlplayer?.let {
            val config = it.config()
            config.useFrameInterpolation = enable
            it.setConfig(config)
        }

    }

    fun setSegment(firstFrame: Float, lastFrame: Float) {
        config.segment = listOf(firstFrame, lastFrame)
        dlplayer?.setConfig(config)
    }

    fun setLoop(loop: Boolean) {
        config.loopAnimation = loop
        dlplayer?.setConfig(config)
    }

    fun freeze() {
        dlplayer?.pause()
        _currentState.update { DotLottiePlayerState.PAUSED }
        shouldPlayOnInit = false
        eventListeners.forEach(DotLottieEventListener::onFreeze)
    }

    fun unFreeze() {
        dlplayer?.play()
        _currentState.update { DotLottiePlayerState.PLAYING }
        shouldPlayOnInit = true
        eventListeners.forEach(DotLottieEventListener::onUnFreeze)
    }

    fun setSpeed(speed: Float) {
        config.speed = speed
        dlplayer?.setConfig(config)
    }

    fun setLoopCount(loopCount: UInt) {
        config.loopCount = loopCount
        dlplayer?.setConfig(config)
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
        val result =
            dlplayer?.loadAnimation(animationId, this._width.value, this._height.value) ?: false

        if (result) {
            _bufferNeedsUpdate.value = true
        }
    }

    fun manifest(): Manifest? {
        return dlplayer?.manifest()
    }

    fun addEventListener(listener: DotLottieEventListener) {
        if (!eventListeners.contains(listener)) {
            eventListeners.add(listener)
        }
    }

    fun removeEventListener(listener: DotLottieEventListener) {
        eventListeners.remove(listener)
    }

    fun clearEventListeners() {
        eventListeners.clear()
    }
}
