package com.lottiefiles.dotlottie.core.compose.runtime

import com.dotlottie.dlplayer.Config
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat.startActivity
import com.dotlottie.dlplayer.DotLottiePlayer
import com.dotlottie.dlplayer.Event
import com.dotlottie.dlplayer.Fit
import com.dotlottie.dlplayer.Layout
import com.dotlottie.dlplayer.Manifest
import com.dotlottie.dlplayer.Marker
import com.dotlottie.dlplayer.Mode
import com.dotlottie.dlplayer.Observer
import com.dotlottie.dlplayer.OpenUrlPolicy
import com.dotlottie.dlplayer.StateMachineObserver
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
import androidx.core.net.toUri
import com.dotlottie.dlplayer.StateMachineInternalObserver

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
    private var observer: Observer? = null
    private var config: Config = createDefaultConfig()

    private val _currentState = MutableStateFlow(DotLottiePlayerState.INITIAL)
    val currentState: StateFlow<DotLottiePlayerState> = _currentState.asStateFlow()

    private var shouldPlayOnInit = false

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

    var stateMachineIsActive: Boolean = false
        get() = field

    fun play() {
        dlplayer?.play()
        shouldPlayOnInit = true
    }

    fun pause() {
        dlplayer?.pause()
        shouldPlayOnInit = false
    }

    fun stop() {
        dlplayer?.stop()
        shouldPlayOnInit = false
    }

    private fun subscribe() {
        observer = object : Observer {
            override fun onComplete() {
                _currentState.update { DotLottiePlayerState.COMPLETED }
                eventListeners.forEach(DotLottieEventListener::onComplete)
            }

            override fun onFrame(frameNo: Float) {
                eventListeners.forEach { it.onFrame(frameNo) }
            }

            override fun onPause() {
                _currentState.update { DotLottiePlayerState.PAUSED }
                eventListeners.forEach(DotLottieEventListener::onPause)
            }

            override fun onStop() {
                _currentState.update { DotLottiePlayerState.STOPPED }
                eventListeners.forEach(DotLottieEventListener::onStop)
            }

            override fun onPlay() {
                _currentState.update { DotLottiePlayerState.PLAYING }
                eventListeners.forEach(DotLottieEventListener::onPlay)
            }

            override fun onLoad() {
                _currentState.update { DotLottiePlayerState.LOADED }
                eventListeners.forEach(DotLottieEventListener::onLoad)
            }

            override fun onLoop(loopCount: UInt) {
                eventListeners.forEach { it.onLoop(loopCount.toInt()) }
            }

            override fun onRender(frameNo: Float) {
                eventListeners.forEach { it.onRender(frameNo) }
            }

            override fun onLoadError() {
                _currentState.update { DotLottiePlayerState.ERROR }
                eventListeners.forEach { listener ->
                    listener.onLoadError()
                    listener.onLoadError(Throwable("Load error occurred"))
                }
            }
        }
        dlplayer?.subscribe(observer!!)
    }

    fun stateMachineStart(openUrl: OpenUrlPolicy = createDefaultOpenUrlPolicy(), context: Context? = null): Boolean {
        val result = dlplayer?.stateMachineStart(openUrl) ?: false
        if (result) {
            stateMachineIsActive = true

            if (dlplayer != null) {
                stateMachineGestureListeners =
                    dlplayer!!.stateMachineFrameworkSetup().map { it.lowercase() }.toSet().toMutableList()
            }

            if (this.isPlaying) {
                this.play()
            }

            // For the users' observers
            dlplayer?.stateMachineSubscribe(object : StateMachineObserver {
                override fun onBooleanInputValueChange(
                    inputName: String,
                    oldValue: Boolean,
                    newValue: Boolean
                ) {
                    stateMachineListeners.forEach { it.onBooleanInputValueChange(inputName, oldValue, newValue) }
                }

                override fun onCustomEvent(message: String) {
                    stateMachineListeners.forEach { it.onCustomEvent(message) }
                }

                override fun onError(message: String) {
                    stateMachineListeners.forEach { it.onError(message) }
                }

                override fun onNumericInputValueChange(
                    inputName: String,
                    oldValue: Float,
                    newValue: Float
                ) {
                    stateMachineListeners.forEach { it.onNumericInputValueChange(inputName, oldValue, newValue) }
                }

                override fun onStart() {
                    stateMachineListeners.forEach { it.onStart() }
                }

                override fun onStateEntered(enteringState: String) {
                    stateMachineListeners.forEach { it.onStateEntered(enteringState) }
                }

                override fun onStateExit(leavingState: String) {
                    stateMachineListeners.forEach { it.onStateExit(leavingState) }
                }

                override fun onStop() {
                    stateMachineListeners.forEach { it.onStop() }
                }

                override fun onStringInputValueChange(
                    inputName: String,
                    oldValue: String,
                    newValue: String
                ) {
                    stateMachineListeners.forEach { it.onStringInputValueChange(inputName, oldValue, newValue) }
                }

                override fun onTransition(previousState: String, newState: String) {
                    stateMachineListeners.forEach { it.onTransition(previousState, newState) }
                }

                override fun onInputFired(inputName: String) {
                    stateMachineListeners.forEach { it.onInputFired(inputName) }
                }
            })

            // For internal observer
            dlplayer?.stateMachineInternalSubscribe(object : StateMachineInternalObserver {
                override fun onMessage(message: String) {
                    if (message.startsWith("OpenUrl: ")) {
                        if (context != null) {
                            // Extract the URL part after "OpenUrl: "
                            val url = message.substringAfter("OpenUrl: ")

                            // Create and launch the intent to open the URL
                            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    }
                }
            })
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
        subscribe()
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
        shouldPlayOnInit = false
        eventListeners.forEach(DotLottieEventListener::onFreeze)
    }

    fun unFreeze() {
        dlplayer?.play()
        shouldPlayOnInit = true
        eventListeners.forEach(DotLottieEventListener::onUnFreeze)
    }

    fun setSpeed(speed: Float) {
        config.speed = speed
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
        dlplayer?.loadAnimation(animationId, this._width.value, this._height.value)
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