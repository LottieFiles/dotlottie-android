package com.lottiefiles.dotlottie.core.jni

/**
 * JNI interface to the native DotLottie player.
 * This class uses RegisterNatives() to map Kotlin native methods directly to C API functions.
 */
object DotLottiePlayer {

    init {
        // Load Rust library first (dependency)
        System.loadLibrary("dotlottie_player")
        // Then load JNI bridge
        System.loadLibrary("dlplayer")
    }

    // ==================== Player Lifecycle ====================

    @JvmStatic
    external fun nativeNewPlayer(config: Any?): Long

    @JvmStatic
    external fun nativeDestroy(ptr: Long): Int

    // ==================== Config ====================

    @JvmStatic
    external fun nativeInitConfig(config: Any?): Int

    // ==================== Loading ====================

    @JvmStatic
    external fun nativeLoadAnimationData(ptr: Long, data: String, width: Int, height: Int): Int

    @JvmStatic
    external fun nativeLoadAnimationPath(ptr: Long, path: String, width: Int, height: Int): Int

    @JvmStatic
    external fun nativeLoadAnimation(ptr: Long, id: String, width: Int, height: Int): Int

    @JvmStatic
    external fun nativeLoadDotLottieData(ptr: Long, data: ByteArray, width: Int, height: Int): Int

    // ==================== Playback Control ====================

    @JvmStatic
    external fun nativePlay(ptr: Long): Int

    @JvmStatic
    external fun nativePause(ptr: Long): Int

    @JvmStatic
    external fun nativeStop(ptr: Long): Int

    @JvmStatic
    external fun nativeRender(ptr: Long): Int

    @JvmStatic
    external fun nativeTick(ptr: Long): Int

    @JvmStatic
    external fun nativeRequestFrame(ptr: Long): Float

    @JvmStatic
    external fun nativeSetFrame(ptr: Long, frame: Float): Int

    @JvmStatic
    external fun nativeSeek(ptr: Long, time: Float): Int

    @JvmStatic
    external fun nativeResize(ptr: Long, width: Int, height: Int): Int

    @JvmStatic
    external fun nativeClear(ptr: Long): Int

    // ==================== State Queries ====================

    @JvmStatic
    external fun nativeIsLoaded(ptr: Long): Int

    @JvmStatic
    external fun nativeIsPlaying(ptr: Long): Int

    @JvmStatic
    external fun nativeIsPaused(ptr: Long): Int

    @JvmStatic
    external fun nativeIsStopped(ptr: Long): Int

    @JvmStatic
    external fun nativeIsComplete(ptr: Long): Int

    // ==================== Getters ====================

    @JvmStatic
    external fun nativeCurrentFrame(ptr: Long): Float

    @JvmStatic
    external fun nativeTotalFrames(ptr: Long): Float

    @JvmStatic
    external fun nativeDuration(ptr: Long): Float

    @JvmStatic
    external fun nativeLoopCount(ptr: Long): Int

    @JvmStatic
    external fun nativeBufferPtr(ptr: Long): Long

    @JvmStatic
    external fun nativeBufferLen(ptr: Long): Long

    @JvmStatic
    external fun nativeSegmentDuration(ptr: Long): Float

    @JvmStatic
    external fun nativeAnimationSize(ptr: Long): FloatArray

    // ==================== Theme ====================

    @JvmStatic
    external fun nativeSetTheme(ptr: Long, themeId: String): Int

    @JvmStatic
    external fun nativeResetTheme(ptr: Long): Int

    @JvmStatic
    external fun nativeSetThemeData(ptr: Long, themeData: String): Int

    @JvmStatic
    external fun nativeActiveThemeId(ptr: Long): String

    @JvmStatic
    external fun nativeActiveAnimationId(ptr: Long): String

    // ==================== Slots ====================

    @JvmStatic
    external fun nativeSetSlotsStr(ptr: Long, slotsJson: String): Int

    @JvmStatic
    external fun nativeClearSlots(ptr: Long): Int

    @JvmStatic
    external fun nativeClearSlot(ptr: Long, slotId: String): Int

    @JvmStatic
    external fun nativeSetColorSlot(ptr: Long, slotId: String, r: Float, g: Float, b: Float): Int

    @JvmStatic
    external fun nativeSetScalarSlot(ptr: Long, slotId: String, value: Float): Int

    @JvmStatic
    external fun nativeSetTextSlot(ptr: Long, slotId: String, text: String): Int

    // ==================== Viewport ====================

    @JvmStatic
    external fun nativeSetViewport(ptr: Long, x: Int, y: Int, w: Int, h: Int): Int

    // ==================== Poll Events ====================

    /**
     * Poll for next player event.
     * Returns IntArray [eventType, data1, data2] or null if no events.
     */
    @JvmStatic
    external fun nativePollEvent(ptr: Long): IntArray?

    // ==================== State Machine ====================

    /**
     * Load state machine by ID. Returns state machine pointer (0 on failure).
     */
    @JvmStatic
    external fun nativeStateMachineLoad(playerPtr: Long, stateMachineId: String): Long

    /**
     * Load state machine from JSON data. Returns state machine pointer (0 on failure).
     */
    @JvmStatic
    external fun nativeStateMachineLoadData(playerPtr: Long, data: String): Long

    /**
     * Release/destroy state machine.
     */
    @JvmStatic
    external fun nativeStateMachineRelease(smPtr: Long)

    @JvmStatic
    external fun nativeStateMachineStart(smPtr: Long): Int

    @JvmStatic
    external fun nativeStateMachineStop(smPtr: Long): Int

    @JvmStatic
    external fun nativeStateMachineTick(smPtr: Long): Int

    @JvmStatic
    external fun nativeStateMachineSetNumericInput(smPtr: Long, key: String, value: Float): Int

    @JvmStatic
    external fun nativeStateMachineSetStringInput(smPtr: Long, key: String, value: String): Int

    @JvmStatic
    external fun nativeStateMachineSetBooleanInput(smPtr: Long, key: String, value: Boolean): Int

    @JvmStatic
    external fun nativeStateMachineGetNumericInput(smPtr: Long, key: String): Float

    @JvmStatic
    external fun nativeStateMachineGetStringInput(smPtr: Long, key: String): String

    @JvmStatic
    external fun nativeStateMachineGetBooleanInput(smPtr: Long, key: String): Boolean

    @JvmStatic
    external fun nativeStateMachineCurrentState(smPtr: Long): String

    @JvmStatic
    external fun nativeStateMachineStatus(smPtr: Long): String

    @JvmStatic
    external fun nativeStateMachineFireEvent(smPtr: Long, eventName: String): Int

    @JvmStatic
    external fun nativeStateMachinePostEvent(smPtr: Long, eventTag: Int, x: Float, y: Float): Int

    /**
     * Get framework setup flags (bit flags for required interactions)
     */
    @JvmStatic
    external fun nativeStateMachineFrameworkSetup(smPtr: Long): Int

    /**
     * Poll for next state machine event.
     * Returns String array or null if no events.
     */
    @JvmStatic
    external fun nativeStateMachinePollEvent(smPtr: Long): Array<String?>?

    /**
     * Poll for internal state machine event.
     * Returns message string or null if no events.
     */
    @JvmStatic
    external fun nativeStateMachinePollInternalEvent(smPtr: Long): String?
}
