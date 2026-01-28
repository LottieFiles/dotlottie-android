package com.dotlottie.dlplayer

import com.lottiefiles.dotlottie.core.jni.DotLottiePlayer as JNI

/**
 * DotLottiePlayer wrapper that uses JNI to communicate with the native player.
 *
 * This class provides a high-level Kotlin API for the dotlottie-rs C API.
 * It handles native pointer management and provides idiomatic Kotlin methods.
 */
class DotLottiePlayer {
    private var nativePtr: Long = 0
    private var currentConfig: Config
    private var stateMachinePtr: Long = 0

    constructor(config: Config) {
        this.currentConfig = config
        nativePtr = JNI.nativeNewPlayer(config)
    }

    // ==================== Loading ====================

    /**
     * Load animation from JSON string
     */
    fun loadAnimationData(data: String, width: UInt, height: UInt): Boolean {
        val result = JNI.nativeLoadAnimationData(nativePtr, data, width.toInt(), height.toInt())
        return result == 0
    }

    /**
     * Load animation from file path
     */
    fun loadAnimationPath(path: String, width: UInt, height: UInt): Boolean {
        val result = JNI.nativeLoadAnimationPath(nativePtr, path, width.toInt(), height.toInt())
        return result == 0
    }

    /**
     * Load animation by ID from manifest
     */
    fun loadAnimation(id: String, width: UInt, height: UInt): Boolean {
        val result = JNI.nativeLoadAnimation(nativePtr, id, width.toInt(), height.toInt())
        return result == 0
    }

    /**
     * Load dotLottie file from binary data
     */
    fun loadDotlottieData(data: ByteArray, width: UInt, height: UInt): Boolean {
        val result = JNI.nativeLoadDotLottieData(nativePtr, data, width.toInt(), height.toInt())
        return result == 0
    }

    // ==================== Playback Control ====================

    /**
     * Start playback
     */
    fun play(): Boolean {
        val result = JNI.nativePlay(nativePtr)
        return result == 0
    }

    /**
     * Pause playback
     */
    fun pause(): Boolean {
        val result = JNI.nativePause(nativePtr)
        return result == 0
    }

    /**
     * Stop playback
     */
    fun stop(): Boolean {
        val result = JNI.nativeStop(nativePtr)
        return result == 0
    }

    /**
     * Render current frame
     */
    fun render(): Boolean {
        val result = JNI.nativeRender(nativePtr)
        return result == 0
    }

    /**
     * Advance animation and render (tick)
     */
    fun tick(): Boolean {
        val result = JNI.nativeTick(nativePtr)
        return result == 0
    }

    /**
     * Request next frame number
     */
    fun requestFrame(): Float {
        return JNI.nativeRequestFrame(nativePtr)
    }

    /**
     * Set current frame
     */
    fun setFrame(frame: Float): Boolean {
        val result = JNI.nativeSetFrame(nativePtr, frame)
        return result == 0
    }

    /**
     * Seek to time
     */
    fun seek(time: Float): Boolean {
        val result = JNI.nativeSeek(nativePtr, time)
        return result == 0
    }

    /**
     * Resize the animation canvas
     */
    fun resize(width: UInt, height: UInt): Boolean {
        val result = JNI.nativeResize(nativePtr, width.toInt(), height.toInt())
        return result == 0
    }

    /**
     * Clear the canvas
     */
    fun clear(): Boolean {
        val result = JNI.nativeClear(nativePtr)
        return result == 0
    }

    // ==================== State Queries ====================

    /**
     * Check if animation is loaded
     */
    fun isLoaded(): Boolean {
        val result = JNI.nativeIsLoaded(nativePtr)
        return result == 1
    }

    /**
     * Check if animation is playing
     */
    fun isPlaying(): Boolean {
        val result = JNI.nativeIsPlaying(nativePtr)
        return result == 1
    }

    /**
     * Check if animation is paused
     */
    fun isPaused(): Boolean {
        val result = JNI.nativeIsPaused(nativePtr)
        return result == 1
    }

    /**
     * Check if animation is stopped
     */
    fun isStopped(): Boolean {
        val result = JNI.nativeIsStopped(nativePtr)
        return result == 1
    }

    /**
     * Check if animation is complete
     */
    fun isComplete(): Boolean {
        val result = JNI.nativeIsComplete(nativePtr)
        return result == 1
    }

    // ==================== Getters ====================

    /**
     * Get current frame number
     */
    fun currentFrame(): Float {
        return JNI.nativeCurrentFrame(nativePtr)
    }

    /**
     * Get total number of frames
     */
    fun totalFrames(): Float {
        return JNI.nativeTotalFrames(nativePtr)
    }

    /**
     * Get animation duration in seconds
     */
    fun duration(): Float {
        return JNI.nativeDuration(nativePtr)
    }

    /**
     * Get current loop count
     */
    fun loopCount(): UInt {
        return JNI.nativeLoopCount(nativePtr).toUInt()
    }

    /**
     * Get pointer to native buffer for bitmap rendering
     */
    fun bufferPtr(): Long {
        return JNI.nativeBufferPtr(nativePtr)
    }

    /**
     * Get buffer length in bytes
     */
    fun bufferLen(): Long {
        return JNI.nativeBufferLen(nativePtr)
    }

    /**
     * Get segment duration
     */
    fun segmentDuration(): Float {
        return JNI.nativeSegmentDuration(nativePtr)
    }

    /**
     * Get animation size (width, height)
     */
    fun animationSize(): Pair<Float, Float> {
        val size = JNI.nativeAnimationSize(nativePtr)
        return Pair(size[0], size[1])
    }

    // ==================== Theme ====================

    /**
     * Set theme by ID
     */
    fun setTheme(themeId: String): Boolean {
        val result = JNI.nativeSetTheme(nativePtr, themeId)
        return result == 0
    }

    /**
     * Reset theme to default
     */
    fun resetTheme(): Boolean {
        val result = JNI.nativeResetTheme(nativePtr)
        return result == 0
    }

    /**
     * Set theme from JSON data
     */
    fun setThemeData(themeData: String): Boolean {
        val result = JNI.nativeSetThemeData(nativePtr, themeData)
        return result == 0
    }

    /**
     * Get active theme ID
     */
    fun activeThemeId(): String {
        return JNI.nativeActiveThemeId(nativePtr)
    }

    /**
     * Get active animation ID
     */
    fun activeAnimationId(): String {
        return JNI.nativeActiveAnimationId(nativePtr)
    }

    // ==================== Slots ====================

    /**
     * Set slots from JSON string
     */
    fun setSlots(slotsJson: String): Boolean {
        val result = JNI.nativeSetSlotsStr(nativePtr, slotsJson)
        return result == 0
    }

    /**
     * Clear all slots
     */
    fun clearSlots(): Boolean {
        val result = JNI.nativeClearSlots(nativePtr)
        return result == 0
    }

    /**
     * Clear a specific slot
     */
    fun clearSlot(slotId: String): Boolean {
        val result = JNI.nativeClearSlot(nativePtr, slotId)
        return result == 0
    }

    /**
     * Set a color slot (RGB values 0.0-1.0)
     */
    fun setColorSlot(slotId: String, r: Float, g: Float, b: Float): Boolean {
        val result = JNI.nativeSetColorSlot(nativePtr, slotId, r, g, b)
        return result == 0
    }

    /**
     * Set a scalar slot
     */
    fun setScalarSlot(slotId: String, value: Float): Boolean {
        val result = JNI.nativeSetScalarSlot(nativePtr, slotId, value)
        return result == 0
    }

    /**
     * Set a text slot
     */
    fun setTextSlot(slotId: String, text: String): Boolean {
        val result = JNI.nativeSetTextSlot(nativePtr, slotId, text)
        return result == 0
    }

    // ==================== Viewport ====================

    /**
     * Set viewport
     */
    fun setViewport(x: Int, y: Int, w: Int, h: Int): Boolean {
        val result = JNI.nativeSetViewport(nativePtr, x, y, w, h)
        return result == 0
    }

    // ==================== Poll Events ====================

    /**
     * Poll for next player event (SDL-style)
     * Returns null if no events are available
     */
    fun pollEvent(): DotLottiePlayerEvent? {
        val arr = JNI.nativePollEvent(nativePtr)
        return DotLottiePlayerEvent.fromJniArray(arr)
    }

    // ==================== State Machine ====================

    /**
     * Load state machine by ID
     */
    fun loadStateMachine(stateMachineId: String): Boolean {
        releaseStateMachine()
        stateMachinePtr = JNI.nativeStateMachineLoad(nativePtr, stateMachineId)
        return stateMachinePtr != 0L
    }

    /**
     * Load state machine from JSON data
     */
    fun loadStateMachineData(data: String): Boolean {
        releaseStateMachine()
        stateMachinePtr = JNI.nativeStateMachineLoadData(nativePtr, data)
        return stateMachinePtr != 0L
    }

    /**
     * Release/destroy the current state machine
     */
    fun releaseStateMachine() {
        if (stateMachinePtr != 0L) {
            JNI.nativeStateMachineRelease(stateMachinePtr)
            stateMachinePtr = 0
        }
    }

    /**
     * Start state machine
     */
    fun stateMachineStart(openUrlPolicy: OpenUrlPolicy = OpenUrlPolicy()): Boolean {
        if (stateMachinePtr == 0L) return false
        val result = JNI.nativeStateMachineStart(stateMachinePtr)
        return result == 0
    }

    /**
     * Stop state machine
     */
    fun stateMachineStop(): Boolean {
        if (stateMachinePtr == 0L) return false
        val result = JNI.nativeStateMachineStop(stateMachinePtr)
        return result == 0
    }

    /**
     * Tick state machine
     */
    fun stateMachineTick(): Boolean {
        if (stateMachinePtr == 0L) return false
        val result = JNI.nativeStateMachineTick(stateMachinePtr)
        return result == 0
    }

    /**
     * Set numeric input for state machine
     */
    fun stateMachineSetNumericInput(key: String, value: Float): Boolean {
        if (stateMachinePtr == 0L) return false
        val result = JNI.nativeStateMachineSetNumericInput(stateMachinePtr, key, value)
        return result == 0
    }

    /**
     * Set string input for state machine
     */
    fun stateMachineSetStringInput(key: String, value: String): Boolean {
        if (stateMachinePtr == 0L) return false
        val result = JNI.nativeStateMachineSetStringInput(stateMachinePtr, key, value)
        return result == 0
    }

    /**
     * Set boolean input for state machine
     */
    fun stateMachineSetBooleanInput(key: String, value: Boolean): Boolean {
        if (stateMachinePtr == 0L) return false
        val result = JNI.nativeStateMachineSetBooleanInput(stateMachinePtr, key, value)
        return result == 0
    }

    /**
     * Get numeric input value
     */
    fun stateMachineGetNumericInput(key: String): Float {
        if (stateMachinePtr == 0L) return 0f
        return JNI.nativeStateMachineGetNumericInput(stateMachinePtr, key)
    }

    /**
     * Get string input value
     */
    fun stateMachineGetStringInput(key: String): String {
        if (stateMachinePtr == 0L) return ""
        return JNI.nativeStateMachineGetStringInput(stateMachinePtr, key)
    }

    /**
     * Get boolean input value
     */
    fun stateMachineGetBooleanInput(key: String): Boolean {
        if (stateMachinePtr == 0L) return false
        return JNI.nativeStateMachineGetBooleanInput(stateMachinePtr, key)
    }

    /**
     * Get current state machine state
     */
    fun stateMachineCurrentState(): String {
        if (stateMachinePtr == 0L) return ""
        return JNI.nativeStateMachineCurrentState(stateMachinePtr)
    }

    /**
     * Get state machine status
     */
    fun stateMachineStatus(): String {
        if (stateMachinePtr == 0L) return ""
        return JNI.nativeStateMachineStatus(stateMachinePtr)
    }

    /**
     * Fire a named event input
     */
    fun stateMachineFireEvent(eventName: String): Boolean {
        if (stateMachinePtr == 0L) return false
        val result = JNI.nativeStateMachineFireEvent(stateMachinePtr, eventName)
        return result == 0
    }

    /**
     * Post event to state machine
     */
    fun stateMachinePostEvent(event: Event): Boolean {
        if (stateMachinePtr == 0L) return false
        val (x, y) = when (event) {
            is Event.PointerDown -> event.x to event.y
            is Event.PointerUp -> event.x to event.y
            is Event.PointerMove -> event.x to event.y
            is Event.PointerEnter -> event.x to event.y
            is Event.PointerExit -> event.x to event.y
            is Event.Click -> event.x to event.y
            else -> 0f to 0f
        }
        val result = JNI.nativeStateMachinePostEvent(stateMachinePtr, event.tag, x, y)
        return result == 0
    }

    /**
     * Get framework setup flags
     */
    fun stateMachineFrameworkSetup(): List<String> {
        if (stateMachinePtr == 0L) return emptyList()
        val flags = JNI.nativeStateMachineFrameworkSetup(stateMachinePtr)

        val interactions = mutableListOf<String>()
        if (flags and (1 shl 0) != 0) interactions.add("PointerUp")
        if (flags and (1 shl 1) != 0) interactions.add("PointerDown")
        if (flags and (1 shl 2) != 0) interactions.add("PointerEnter")
        if (flags and (1 shl 3) != 0) interactions.add("PointerExit")
        if (flags and (1 shl 4) != 0) interactions.add("PointerMove")
        if (flags and (1 shl 5) != 0) interactions.add("Click")
        if (flags and (1 shl 6) != 0) interactions.add("OnComplete")
        if (flags and (1 shl 7) != 0) interactions.add("OnLoopComplete")
        return interactions
    }

    /**
     * Poll for next state machine event (SDL-style)
     */
    fun stateMachinePollEvent(): StateMachinePlayerEvent? {
        if (stateMachinePtr == 0L) return null
        val arr = JNI.nativeStateMachinePollEvent(stateMachinePtr)
        return StateMachinePlayerEvent.fromJniArray(arr)
    }

    /**
     * Poll for internal state machine event
     */
    fun stateMachinePollInternalEvent(): String? {
        if (stateMachinePtr == 0L) return null
        return JNI.nativeStateMachinePollInternalEvent(stateMachinePtr)
    }

    // ==================== Backward Compatibility Aliases ====================

    // These aliases maintain API compatibility with the UniFFI version
    fun stateMachineLoad(id: String) = loadStateMachine(id)
    fun stateMachineLoadData(data: String) = loadStateMachineData(data)

    // ==================== Config ====================

    /**
     * Get current configuration
     */
    fun config(): Config {
        return currentConfig
    }

    /**
     * Update configuration
     */
    fun setConfig(config: Config) {
        this.currentConfig = config
        // Note: Some config changes may require recreating the player
    }

    // ==================== Manifest & Markers ====================

    /**
     * Get manifest information
     */
    fun manifest(): Manifest {
        // TODO: Implement manifest retrieval from native
        return Manifest(
            activeAnimationId = null,
            animations = null,
            author = null,
            description = null,
            generator = null,
            keywords = null,
            revision = null,
            themes = null,
            states = null,
            version = null,
            customData = null
        )
    }

    /**
     * Get animation markers
     */
    fun markers(): List<Marker> {
        // TODO: Implement markers retrieval from native
        return emptyList()
    }

    // ==================== Observer Pattern (via polling) ====================

    /**
     * Subscribe to player events.
     * Note: With C API, use pollEvent() in your render loop instead.
     */
    @Deprecated("Use pollEvent() instead", ReplaceWith("pollEvent()"))
    fun subscribe(observer: Observer) {
        // No-op: Use pollEvent() in render loop
    }

    /**
     * Unsubscribe from player events.
     */
    @Deprecated("Use pollEvent() instead")
    fun unsubscribe(observer: Observer) {
        // No-op
    }

    /**
     * Subscribe to state machine events.
     * Note: With C API, use stateMachinePollEvent() in your render loop instead.
     */
    @Deprecated("Use stateMachinePollEvent() instead", ReplaceWith("stateMachinePollEvent()"))
    fun stateMachineSubscribe(observer: StateMachineObserver) {
        // No-op: Use stateMachinePollEvent() in render loop
    }

    /**
     * Unsubscribe from state machine events.
     */
    @Deprecated("Use stateMachinePollEvent() instead")
    fun stateMachineUnsubscribe(observer: StateMachineObserver) {
        // No-op
    }

    /**
     * Subscribe to internal state machine events.
     * Note: With C API, use stateMachinePollInternalEvent() in your render loop instead.
     */
    @Deprecated("Use stateMachinePollInternalEvent() instead", ReplaceWith("stateMachinePollInternalEvent()"))
    fun stateMachineInternalSubscribe(observer: StateMachineInternalObserver) {
        // No-op: Use stateMachinePollInternalEvent() in render loop
    }

    /**
     * Get list of state machine input names.
     * Note: This is a placeholder - implement via native if needed.
     */
    fun stateMachineGetInputs(): List<String> {
        // TODO: Implement via native if C API supports it
        return emptyList()
    }

    // ==================== Lifecycle ====================

    /**
     * Destroy the native player and free resources
     */
    fun destroy() {
        releaseStateMachine()
        if (nativePtr != 0L) {
            JNI.nativeDestroy(nativePtr)
            nativePtr = 0
        }
    }

    companion object {
        /**
         * Create player with custom thread count
         */
        @JvmStatic
        fun withThreads(config: Config, threads: UInt): DotLottiePlayer {
            // Thread configuration would need to be passed to native player
            // For now, create regular player
            return DotLottiePlayer(config)
        }
    }
}
