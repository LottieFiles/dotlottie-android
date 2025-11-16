package com.lottiefiles.sample

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.Log
import android.widget.FrameLayout
import com.dotlottie.dlplayer.Mode
import com.lottiefiles.dotlottie.core.model.Config
import com.lottiefiles.dotlottie.core.util.DotLottieEventListener
import com.lottiefiles.dotlottie.core.util.DotLottieSource
import com.lottiefiles.dotlottie.core.util.StateMachineEventListener
import com.lottiefiles.dotlottie.core.widget.DotLottieAnimation

/**
 * DotLottieView - A wrapper component around DotLottieAnimation
 *
 * This component demonstrates how to programmatically use the DotLottieAnimation widget
 * in a reusable component, similar to the Flutter DotLottiePlatformView example.
 *
 * Features:
 * - Programmatic configuration
 * - Event listener management
 * - State machine support
 * - Method delegation to underlying widget
 * - Easy integration in any layout
 *
 * Usage:
 * ```kotlin
 * val dotLottieView = DotLottieView(context)
 * dotLottieView.configure(
 *     source = DotLottieSource.Url("https://..."),
 *     autoplay = true,
 *     loop = true
 * )
 * parentLayout.addView(dotLottieView)
 * ```
 */
class DotLottieView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val TAG = "DotLottieView"

    // The underlying DotLottieAnimation widget
    private val dotLottieAnimation: DotLottieAnimation = DotLottieAnimation(context)

    // Event listener collections
    private val eventListeners = mutableListOf<DotLottieEventListener>()
    private val stateMachineListeners = mutableListOf<StateMachineEventListener>()

    // Configuration state
    private var isConfigured = false

    init {
        // Add the DotLottieAnimation widget as a child
        addView(
            dotLottieAnimation,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        )

        // Enable touch events
        isClickable = true
        isFocusable = true

        Log.d(TAG, "DotLottieView initialized")
    }

    /**
     * Configure the animation with parameters similar to Flutter example
     */
    fun configure(
        source: DotLottieSource,
        autoplay: Boolean = true,
        loop: Boolean = true,
        speed: Float = 1f,
        mode: Mode = Mode.FORWARD,
        useFrameInterpolation: Boolean = true,
        backgroundColor: String? = null,
        stateMachineId: String? = null,
        themeId: String? = null,
        marker: String? = null,
        threads: UInt? = null,
        loopCount: UInt = 0u
    ) {
        Log.d(TAG, "Configuring animation...")
        Log.d(TAG, "  Autoplay: $autoplay")
        Log.d(TAG, "  Loop: $loop")
        Log.d(TAG, "  Speed: $speed")
        Log.d(TAG, "  Mode: $mode")
        Log.d(TAG, "  State Machine: $stateMachineId")

        // Build configuration
        val configBuilder = Config.Builder()
            .autoplay(autoplay)
            .loop(loop)
            .speed(speed)
            .source(source)
            .playMode(mode)
            .loopCount(loopCount)
            .useFrameInterpolation(useFrameInterpolation)

        // Add optional parameters
        stateMachineId?.let { configBuilder.stateMachineId(it) }
        themeId?.let { configBuilder.themeId(it) }
        marker?.let { configBuilder.marker(it) }
        threads?.let { configBuilder.threads(it) }

        val config = configBuilder.build()

        // Apply background color if provided
        backgroundColor?.let {
            try {
                val color = parseColor(it)
                dotLottieAnimation.setBackgroundColor(color)
                Log.d(TAG, "  Background color set: $it")
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing background color: $it", e)
            }
        }

        // Load the animation
        dotLottieAnimation.load(config)
        isConfigured = true

        Log.d(TAG, "Configuration complete")
    }

    /**
     * Parse color string (supports #RGB, #RRGGBB, #AARRGGBB formats)
     */
    private fun parseColor(colorString: String): Int {
        var hex = colorString.trim().replace("#", "")

        return when (hex.length) {
            6 -> Color.parseColor("#$hex")
            8 -> Color.parseColor("#$hex")
            else -> throw IllegalArgumentException("Invalid color format: $colorString")
        }
    }

    // ============================================
    // Playback Control Methods
    // ============================================

    fun play() {
        Log.d(TAG, "play()")
        dotLottieAnimation.play()
    }

    fun pause() {
        Log.d(TAG, "pause()")
        dotLottieAnimation.pause()
    }

    fun stop() {
        Log.d(TAG, "stop()")
        dotLottieAnimation.stop()
    }

    fun freeze() {
        Log.d(TAG, "freeze()")
        dotLottieAnimation.freeze()
    }

    fun unFreeze() {
        Log.d(TAG, "unFreeze()")
        dotLottieAnimation.unFreeze()
    }

    // ============================================
    // Property Getters
    // ============================================

    val isPlaying: Boolean
        get() = dotLottieAnimation.isPlaying

    val isPaused: Boolean
        get() = dotLottieAnimation.isPaused

    val isStopped: Boolean
        get() = dotLottieAnimation.isStopped

    val isLoaded: Boolean
        get() = dotLottieAnimation.isLoaded

    val currentFrame: Float
        get() = dotLottieAnimation.currentFrame

    val totalFrames: Float
        get() = dotLottieAnimation.totalFrames

    val duration: Float
        get() = dotLottieAnimation.duration

    val loopCount: UInt
        get() = dotLottieAnimation.loopCount

    val speed: Float
        get() = dotLottieAnimation.speed

    val loop: Boolean
        get() = dotLottieAnimation.loop

    val autoplay: Boolean
        get() = dotLottieAnimation.autoplay

    val useFrameInterpolation: Boolean
        get() = dotLottieAnimation.useFrameInterpolation

    val playMode: Mode
        get() = dotLottieAnimation.playMode

    val segment: Pair<Float, Float>
        get() = dotLottieAnimation.segment

    val marker: String
        get() = dotLottieAnimation.marker

    val activeThemeId: String
        get() = dotLottieAnimation.activeThemeId

    val activeAnimationId: String
        get() = dotLottieAnimation.activeAnimationId

    val currentProgress: Float
        get() = if (totalFrames > 0f) currentFrame / totalFrames else 0f

    // ============================================
    // Property Setters
    // ============================================

    fun setSpeed(speed: Float) {
        Log.d(TAG, "setSpeed($speed)")
        dotLottieAnimation.setSpeed(speed)
    }

    fun setLoop(loop: Boolean) {
        Log.d(TAG, "setLoop($loop)")
        dotLottieAnimation.setLoop(loop)
    }

    fun setFrame(frame: Float) {
        Log.d(TAG, "setFrame($frame)")
        dotLottieAnimation.setFrame(frame)
    }

    fun setProgress(progress: Float) {
        val frame = progress * totalFrames
        Log.d(TAG, "setProgress($progress) -> frame $frame")
        dotLottieAnimation.setFrame(frame)
    }

    fun setSegment(start: Float, end: Float) {
        Log.d(TAG, "setSegment($start, $end)")
        dotLottieAnimation.setSegment(start, end)
    }

    fun setPlayMode(mode: Mode) {
        Log.d(TAG, "setPlayMode($mode)")
        dotLottieAnimation.setPlayMode(mode)
    }

    fun setUseFrameInterpolation(enable: Boolean) {
        Log.d(TAG, "setUseFrameInterpolation($enable)")
        dotLottieAnimation.setUseFrameInterpolation(enable)
    }

    fun setMarker(marker: String) {
        Log.d(TAG, "setMarker($marker)")
        dotLottieAnimation.setMarker(marker)
    }

    fun setLoopCount(loopCount: UInt) {
        Log.d(TAG, "setLoopCount($loopCount)")
        dotLottieAnimation.setLoopCount(loopCount)
    }

    // ============================================
    // Advanced Features
    // ============================================

    fun loadAnimation(animationId: String) {
        Log.d(TAG, "loadAnimation($animationId)")
        dotLottieAnimation.loadAnimation(animationId)
    }

    fun setTheme(themeId: String) {
        Log.d(TAG, "setTheme($themeId)")
        dotLottieAnimation.setTheme(themeId)
    }

    fun setThemeData(themeData: String) {
        Log.d(TAG, "setThemeData(...)")
        dotLottieAnimation.setThemeData(themeData)
    }

    fun resetTheme() {
        Log.d(TAG, "resetTheme()")
        dotLottieAnimation.resetTheme()
    }

    fun setSlots(slots: String) {
        Log.d(TAG, "setSlots(...)")
        dotLottieAnimation.setSlots(slots)
    }

    fun resize(width: Int, height: Int) {
        Log.d(TAG, "resize($width, $height)")
        dotLottieAnimation.resize(width, height)
    }

    fun getMarkers() = dotLottieAnimation.markers

    fun getManifest() = dotLottieAnimation.manifest()

    // ============================================
    // State Machine Methods
    // ============================================

    fun stateMachineLoad(stateMachineId: String): Boolean {
        Log.d(TAG, "stateMachineLoad($stateMachineId)")
        return dotLottieAnimation.stateMachineLoad(stateMachineId)
    }

    fun stateMachineStart(): Boolean {
        Log.d(TAG, "stateMachineStart()")
        return dotLottieAnimation.stateMachineStart()
    }

    fun stateMachineStop(): Boolean {
        Log.d(TAG, "stateMachineStop()")
        return dotLottieAnimation.stateMachineStop()
    }

    fun stateMachineSetNumericInput(key: String, value: Float): Boolean {
        Log.d(TAG, "stateMachineSetNumericInput($key, $value)")
        return dotLottieAnimation.stateMachineSetNumericInput(key, value)
    }

    fun stateMachineSetStringInput(key: String, value: String): Boolean {
        Log.d(TAG, "stateMachineSetStringInput($key, $value)")
        return dotLottieAnimation.stateMachineSetStringInput(key, value)
    }

    fun stateMachineSetBooleanInput(key: String, value: Boolean): Boolean {
        Log.d(TAG, "stateMachineSetBooleanInput($key, $value)")
        return dotLottieAnimation.stateMachineSetBooleanInput(key, value)
    }

    fun stateMachineGetNumericInput(key: String): Float? {
        return dotLottieAnimation.stateMachineGetNumericInput(key)
    }

    fun stateMachineGetStringInput(key: String): String? {
        return dotLottieAnimation.stateMachineGetStringInput(key)
    }

    fun stateMachineGetBooleanInput(key: String): Boolean? {
        return dotLottieAnimation.stateMachineGetBooleanInput(key)
    }

    fun stateMachineGetInputs(): List<String>? {
        return dotLottieAnimation.stateMachineGetInputs()
    }

    fun stateMachineCurrentState(): String? {
        return dotLottieAnimation.stateMachineCurrentState()
    }

    fun stateMachineFireEvent(event: String) {
        Log.d(TAG, "stateMachineFireEvent($event)")
        dotLottieAnimation.stateMachineFireEvent(event)
    }

    // ============================================
    // Event Listener Management
    // ============================================

    fun addEventListener(listener: DotLottieEventListener) {
        if (!eventListeners.contains(listener)) {
            eventListeners.add(listener)
            dotLottieAnimation.addEventListener(listener)
            Log.d(TAG, "Event listener added")
        }
    }

    fun removeEventListener(listener: DotLottieEventListener) {
        eventListeners.remove(listener)
        dotLottieAnimation.removeEventListener(listener)
        Log.d(TAG, "Event listener removed")
    }

    fun clearEventListeners() {
        eventListeners.clear()
        dotLottieAnimation.clearEventListeners()
        Log.d(TAG, "All event listeners cleared")
    }

    fun addStateMachineEventListener(listener: StateMachineEventListener) {
        if (!stateMachineListeners.contains(listener)) {
            stateMachineListeners.add(listener)
            dotLottieAnimation.addStateMachineEventListener(listener)
            Log.d(TAG, "State machine event listener added")
        }
    }

    fun removeStateMachineEventListener(listener: StateMachineEventListener) {
        stateMachineListeners.remove(listener)
        dotLottieAnimation.removeStateMachineEventListener(listener)
        Log.d(TAG, "State machine event listener removed")
    }

    // ============================================
    // Lifecycle Management
    // ============================================

    fun destroy() {
        Log.d(TAG, "destroy() - Cleaning up resources")
        clearEventListeners()
        stateMachineListeners.clear()
        dotLottieAnimation.destroy()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Log.d(TAG, "onDetachedFromWindow()")
        // Note: Don't call destroy() here automatically - let the user manage lifecycle
    }
}
