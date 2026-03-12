package com.lottiefiles.dotlottie.core.jni

import android.content.Context

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

    // ==================== Android Context Initialization ====================

    /**
     * Initialize the Android context required for audio support.
     * Must be called once before loading any animation that contains audio.
     * Safe to call multiple times.
     */
    @JvmStatic
    external fun nativeInitAndroid(context: Context)

    // ==================== Player Lifecycle ====================

    @JvmStatic
    external fun nativeNewPlayer(threads: Int): Long

    @JvmStatic
    external fun nativeDestroy(ptr: Long): Int

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
    external fun nativeIsLoaded(ptr: Long): Boolean

    @JvmStatic
    external fun nativeIsPlaying(ptr: Long): Boolean

    @JvmStatic
    external fun nativeIsPaused(ptr: Long): Boolean

    @JvmStatic
    external fun nativeIsStopped(ptr: Long): Boolean

    @JvmStatic
    external fun nativeIsComplete(ptr: Long): Boolean

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
    external fun nativeSegmentDuration(ptr: Long): Float

    @JvmStatic
    external fun nativeAnimationSize(ptr: Long): FloatArray

    // ==================== Buffer Management ====================

    @JvmStatic
    external fun nativeAllocateBuffer(width: Int, height: Int): Long

    @JvmStatic
    external fun nativeFreeBuffer(bufferPtr: Long)

    @JvmStatic
    external fun nativeSetSwTarget(playerPtr: Long, bufferPtr: Long, width: Int, height: Int): Int

    // ==================== Config Setters ====================

    @JvmStatic
    external fun nativeSetMode(ptr: Long, mode: Int): Int

    @JvmStatic
    external fun nativeSetSpeed(ptr: Long, speed: Float): Int

    @JvmStatic
    external fun nativeSetLoop(ptr: Long, loop: Boolean): Int

    @JvmStatic
    external fun nativeSetLoopCount(ptr: Long, count: Int): Int

    @JvmStatic
    external fun nativeSetAutoplay(ptr: Long, autoplay: Boolean): Int

    @JvmStatic
    external fun nativeSetUseFrameInterpolation(ptr: Long, enabled: Boolean): Int

    @JvmStatic
    external fun nativeSetBackgroundColor(ptr: Long, color: Int): Int

    @JvmStatic
    external fun nativeSetSegment(ptr: Long, start: Float, end: Float): Int

    @JvmStatic
    external fun nativeClearSegment(ptr: Long): Int

    @JvmStatic
    external fun nativeSetMarker(ptr: Long, marker: String?): Int

    @JvmStatic
    external fun nativeSetLayout(ptr: Long, fit: Int, alignX: Float, alignY: Float): Int

    // ==================== Config Getters ====================

    @JvmStatic
    external fun nativeGetMode(ptr: Long): Int

    @JvmStatic
    external fun nativeGetSpeed(ptr: Long): Float

    @JvmStatic
    external fun nativeGetLoop(ptr: Long): Boolean

    @JvmStatic
    external fun nativeGetLoopCount(ptr: Long): Int

    @JvmStatic
    external fun nativeGetAutoplay(ptr: Long): Boolean

    @JvmStatic
    external fun nativeGetUseFrameInterpolation(ptr: Long): Boolean

    @JvmStatic
    external fun nativeGetBackgroundColor(ptr: Long): Int

    @JvmStatic
    external fun nativeGetSegment(ptr: Long): FloatArray

    @JvmStatic
    external fun nativeGetActiveMarker(ptr: Long): String

    @JvmStatic
    external fun nativeGetLayout(ptr: Long): FloatArray

    // ==================== Manifest & Markers ====================

    @JvmStatic
    external fun nativeManifest(ptr: Long): String?

    @JvmStatic
    external fun nativeMarkersCount(ptr: Long): Int

    @JvmStatic
    external fun nativeMarker(ptr: Long, idx: Int): Array<String?>?

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

    @JvmStatic
    external fun nativeSetVectorSlot(ptr: Long, slotId: String, x: Float, y: Float): Int

    @JvmStatic
    external fun nativeSetPositionSlot(ptr: Long, slotId: String, x: Float, y: Float): Int

    @JvmStatic
    external fun nativeSetImageSlotPath(ptr: Long, slotId: String, path: String): Int

    @JvmStatic
    external fun nativeSetImageSlotDataUrl(ptr: Long, slotId: String, dataUrl: String): Int

    // ==================== Layer Bounds ====================

    @JvmStatic
    external fun nativeGetLayerBounds(ptr: Long, layerName: String): FloatArray?

    // ==================== Viewport ====================

    @JvmStatic
    external fun nativeSetViewport(ptr: Long, x: Int, y: Int, w: Int, h: Int): Int

    // ==================== Poll Events ====================

    @JvmStatic
    external fun nativePollEvent(ptr: Long): IntArray?

    // ==================== State Machine ====================

    @JvmStatic
    external fun nativeStateMachineLoad(playerPtr: Long, stateMachineId: String): Long

    @JvmStatic
    external fun nativeStateMachineLoadData(playerPtr: Long, data: String): Long

    @JvmStatic
    external fun nativeStateMachineRelease(smPtr: Long)

    @JvmStatic
    external fun nativeStateMachineStart(smPtr: Long, whitelist: String?, requireUserInteraction: Boolean): Int

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

    @JvmStatic
    external fun nativeStateMachineFrameworkSetup(smPtr: Long): Int

    @JvmStatic
    external fun nativeStateMachinePollEvent(smPtr: Long): Array<String?>?

    @JvmStatic
    external fun nativeStateMachinePollInternalEvent(smPtr: Long): String?

    @JvmStatic
    external fun nativeGetStateMachine(playerPtr: Long, id: String): String?
}
