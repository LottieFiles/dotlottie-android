package com.dotlottie.dlplayer

import android.graphics.Color
import android.graphics.PointF
import androidx.annotation.ColorInt
import com.lottiefiles.dotlottie.core.jni.DotLottiePlayer as JNI
import org.json.JSONObject
import org.json.JSONArray

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
    private var nativeBufferAddress: Long = 0
    private var nativeBufferWidth: Int = 0
    private var nativeBufferHeight: Int = 0

    constructor(config: Config) {
        this.currentConfig = config
        nativePtr = JNI.nativeNewPlayer(0)
        applyConfig(config)
    }

    private constructor(config: Config, threads: UInt) {
        this.currentConfig = config
        nativePtr = JNI.nativeNewPlayer(threads.toInt())
        applyConfig(config)
    }

    private fun applyConfig(config: Config) {
        JNI.nativeSetMode(nativePtr, config.mode.value)
        JNI.nativeSetSpeed(nativePtr, config.speed)
        JNI.nativeSetLoop(nativePtr, config.loopAnimation)
        JNI.nativeSetLoopCount(nativePtr, config.loopCount.toInt())
        JNI.nativeSetAutoplay(nativePtr, config.autoplay)
        JNI.nativeSetUseFrameInterpolation(nativePtr, config.useFrameInterpolation)
        JNI.nativeSetBackgroundColor(nativePtr, config.backgroundColor.toInt())

        if (config.segment.size >= 2) {
            JNI.nativeSetSegment(nativePtr, config.segment[0], config.segment[1])
        } else {
            JNI.nativeClearSegment(nativePtr)
        }

        if (config.marker.isNotEmpty()) {
            JNI.nativeSetMarker(nativePtr, config.marker)
        } else {
            JNI.nativeSetMarker(nativePtr, null)
        }

        JNI.nativeSetLayout(
            nativePtr,
            config.layout.fit.value,
            config.layout.align[0],
            config.layout.align[1]
        )
    }

    // ==================== Loading ====================

    fun loadAnimationData(data: String, width: UInt, height: UInt): Boolean {
        val result = JNI.nativeLoadAnimationData(nativePtr, data, width.toInt(), height.toInt())
        return result == 0
    }

    fun loadAnimationPath(path: String, width: UInt, height: UInt): Boolean {
        val result = JNI.nativeLoadAnimationPath(nativePtr, path, width.toInt(), height.toInt())
        return result == 0
    }

    fun loadAnimation(id: String, width: UInt, height: UInt): Boolean {
        val result = JNI.nativeLoadAnimation(nativePtr, id, width.toInt(), height.toInt())
        return result == 0
    }

    fun loadDotlottieData(data: ByteArray, width: UInt, height: UInt): Boolean {
        val result = JNI.nativeLoadDotLottieData(nativePtr, data, width.toInt(), height.toInt())
        return result == 0
    }

    // ==================== Playback Control ====================

    fun play(): Boolean {
        val result = JNI.nativePlay(nativePtr)
        return result == 0
    }

    fun pause(): Boolean {
        val result = JNI.nativePause(nativePtr)
        return result == 0
    }

    fun stop(): Boolean {
        val result = JNI.nativeStop(nativePtr)
        return result == 0
    }

    fun render(): Boolean {
        val result = JNI.nativeRender(nativePtr)
        return result == 0
    }

    fun tick(): Boolean {
        val result = JNI.nativeTick(nativePtr)
        return result == 0
    }

    fun requestFrame(): Float {
        return JNI.nativeRequestFrame(nativePtr)
    }

    fun setFrame(frame: Float): Boolean {
        val result = JNI.nativeSetFrame(nativePtr, frame)
        return result == 0
    }

    fun seek(time: Float): Boolean {
        val result = JNI.nativeSeek(nativePtr, time)
        return result == 0
    }

    fun resize(width: UInt, height: UInt): Boolean {
        val result = JNI.nativeResize(nativePtr, width.toInt(), height.toInt())
        return result == 0
    }

    fun clear(): Boolean {
        val result = JNI.nativeClear(nativePtr)
        return result == 0
    }

    // ==================== State Queries ====================

    fun isLoaded(): Boolean {
        return JNI.nativeIsLoaded(nativePtr)
    }

    fun isPlaying(): Boolean {
        return JNI.nativeIsPlaying(nativePtr)
    }

    fun isPaused(): Boolean {
        return JNI.nativeIsPaused(nativePtr)
    }

    fun isStopped(): Boolean {
        return JNI.nativeIsStopped(nativePtr)
    }

    fun isComplete(): Boolean {
        return JNI.nativeIsComplete(nativePtr)
    }

    // ==================== Getters ====================

    fun currentFrame(): Float {
        return JNI.nativeCurrentFrame(nativePtr)
    }

    fun totalFrames(): Float {
        return JNI.nativeTotalFrames(nativePtr)
    }

    fun duration(): Float {
        return JNI.nativeDuration(nativePtr)
    }

    fun loopCount(): UInt {
        return JNI.nativeLoopCount(nativePtr).toUInt()
    }

    fun segmentDuration(): Float {
        return JNI.nativeSegmentDuration(nativePtr)
    }

    fun animationSize(): Pair<Float, Float> {
        val size = JNI.nativeAnimationSize(nativePtr)
        return Pair(size[0], size[1])
    }

    // ==================== Buffer Management ====================

    fun allocateBuffer(width: Int, height: Int): Long {
        val addr = JNI.nativeAllocateBuffer(width, height)
        nativeBufferAddress = addr
        nativeBufferWidth = width
        nativeBufferHeight = height
        return addr
    }

    fun freeBuffer(bufferPtr: Long) {
        JNI.nativeFreeBuffer(bufferPtr)
        if (bufferPtr == nativeBufferAddress) {
            nativeBufferAddress = 0
            nativeBufferWidth = 0
            nativeBufferHeight = 0
        }
    }

    fun setSwTarget(bufferPtr: Long, width: UInt, height: UInt): Boolean {
        val result = JNI.nativeSetSwTarget(nativePtr, bufferPtr, width.toInt(), height.toInt())
        return result == 0
    }

    fun bufferPtr(): Long {
        return nativeBufferAddress
    }

    fun bufferLen(): Long {
        return (nativeBufferWidth.toLong() * nativeBufferHeight.toLong())
    }

    // ==================== Theme ====================

    fun setTheme(themeId: String): Boolean {
        val result = JNI.nativeSetTheme(nativePtr, themeId)
        return result == 0
    }

    fun resetTheme(): Boolean {
        val result = JNI.nativeResetTheme(nativePtr)
        return result == 0
    }

    fun setThemeData(themeData: String): Boolean {
        val result = JNI.nativeSetThemeData(nativePtr, themeData)
        return result == 0
    }

    fun activeThemeId(): String {
        return JNI.nativeActiveThemeId(nativePtr)
    }

    fun activeAnimationId(): String {
        return JNI.nativeActiveAnimationId(nativePtr)
    }

    // ==================== Slots ====================

    fun setSlots(slotsJson: String): Boolean {
        val result = JNI.nativeSetSlotsStr(nativePtr, slotsJson)
        return result == 0
    }

    fun clearSlots(): Boolean {
        val result = JNI.nativeClearSlots(nativePtr)
        return result == 0
    }

    fun clearSlot(slotId: String): Boolean {
        val result = JNI.nativeClearSlot(nativePtr, slotId)
        return result == 0
    }

    fun setColorSlot(slotId: String, @ColorInt color: Int): Boolean {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        val result = JNI.nativeSetColorSlot(nativePtr, slotId, r, g, b)
        return result == 0
    }

    fun setScalarSlot(slotId: String, value: Float): Boolean {
        val result = JNI.nativeSetScalarSlot(nativePtr, slotId, value)
        return result == 0
    }

    fun setTextSlot(slotId: String, text: String): Boolean {
        val result = JNI.nativeSetTextSlot(nativePtr, slotId, text)
        return result == 0
    }

    fun setVectorSlot(slotId: String, vector: PointF): Boolean {
        val result = JNI.nativeSetVectorSlot(nativePtr, slotId, vector.x, vector.y)
        return result == 0
    }

    fun setPositionSlot(slotId: String, position: PointF): Boolean {
        val result = JNI.nativeSetPositionSlot(nativePtr, slotId, position.x, position.y)
        return result == 0
    }

    fun setImageSlotPath(slotId: String, path: String): Boolean {
        val result = JNI.nativeSetImageSlotPath(nativePtr, slotId, path)
        return result == 0
    }

    fun setImageSlotDataUrl(slotId: String, dataUrl: String): Boolean {
        val result = JNI.nativeSetImageSlotDataUrl(nativePtr, slotId, dataUrl)
        return result == 0
    }

    // ==================== Layer Bounds ====================

    fun getLayerBounds(layerName: String): LayerBoundingBox? {
        val arr = JNI.nativeGetLayerBounds(nativePtr, layerName) ?: return null
        return LayerBoundingBox(
            x1 = arr[0], y1 = arr[1], x2 = arr[2], y2 = arr[3],
            x3 = arr[4], y3 = arr[5], x4 = arr[6], y4 = arr[7]
        )
    }

    // ==================== Viewport ====================

    fun setViewport(x: Int, y: Int, w: Int, h: Int): Boolean {
        val result = JNI.nativeSetViewport(nativePtr, x, y, w, h)
        return result == 0
    }

    // ==================== Poll Events ====================

    fun pollEvent(): DotLottiePlayerEvent? {
        val arr = JNI.nativePollEvent(nativePtr)
        return DotLottiePlayerEvent.fromJniArray(arr)
    }

    // ==================== Config ====================

    fun config(): Config {
        val mode = Mode.fromValue(JNI.nativeGetMode(nativePtr))
        val speed = JNI.nativeGetSpeed(nativePtr)
        val loop = JNI.nativeGetLoop(nativePtr)
        val loopCount = JNI.nativeGetLoopCount(nativePtr).toUInt()
        val autoplay = JNI.nativeGetAutoplay(nativePtr)
        val useFrameInterp = JNI.nativeGetUseFrameInterpolation(nativePtr)
        val bgColor = JNI.nativeGetBackgroundColor(nativePtr).toUInt()
        val segmentArr = JNI.nativeGetSegment(nativePtr)
        val segment = if (segmentArr[0] != 0f || segmentArr[1] != 0f) {
            listOf(segmentArr[0], segmentArr[1])
        } else {
            emptyList()
        }
        val marker = JNI.nativeGetActiveMarker(nativePtr)
        val layoutArr = JNI.nativeGetLayout(nativePtr)
        val layout = Layout(
            fit = Fit.fromValue(layoutArr[0].toInt()),
            align = listOf(layoutArr[1], layoutArr[2])
        )

        currentConfig = Config(
            mode = mode,
            speed = speed,
            loopAnimation = loop,
            loopCount = loopCount,
            autoplay = autoplay,
            useFrameInterpolation = useFrameInterp,
            backgroundColor = bgColor,
            segment = segment,
            marker = marker,
            layout = layout,
            themeId = currentConfig.themeId,
            stateMachineId = currentConfig.stateMachineId,
            animationId = currentConfig.animationId
        )
        return currentConfig
    }

    fun setConfig(config: Config) {
        this.currentConfig = config
        applyConfig(config)
    }

    // ==================== Manifest & Markers ====================

    fun manifest(): Manifest {
        val jsonStr = JNI.nativeManifest(nativePtr) ?: return Manifest(
            activeAnimationId = null, animations = null, author = null,
            description = null, generator = null, keywords = null,
            revision = null, themes = null, stateMachines = null, version = null,
            customData = null
        )

        return try {
            val json = JSONObject(jsonStr)
            val animationsJson = json.optJSONArray("animations")
            val animations = if (animationsJson != null) {
                (0 until animationsJson.length()).map { i ->
                    val anim = animationsJson.getJSONObject(i)
                    Manifest.Animation(
                        id = anim.optString("id", ""),
                        name = anim.optString("name", null),
                        initialTheme = anim.optString("initialTheme", null),
                        background = anim.optString("background", null)
                    )
                }
            } else null

            val themesJson = json.optJSONArray("themes")
            val themes = if (themesJson != null) {
                (0 until themesJson.length()).map { i ->
                    val theme = themesJson.getJSONObject(i)
                    Manifest.Theme(
                        id = theme.optString("id", ""),
                        name = theme.optString("name", null)
                    )
                }
            } else null

            val stateMachinesJson = json.optJSONArray("stateMachines")
            val stateMachines = if (stateMachinesJson != null) {
                (0 until stateMachinesJson.length()).map { i ->
                    val sm = stateMachinesJson.getJSONObject(i)
                    Manifest.StateMachine(
                        id = sm.optString("id", ""),
                        name = sm.optString("name", null)
                    )
                }
            } else null

            Manifest(
                activeAnimationId = json.optString("activeAnimationId", null),
                animations = animations,
                author = json.optString("author", null),
                description = json.optString("description", null),
                generator = json.optString("generator", null),
                keywords = json.optString("keywords", null),
                revision = json.optString("revision", null)?.toUIntOrNull(),
                themes = themes,
                stateMachines = stateMachines,
                version = json.optString("version", null),
                customData = null
            )
        } catch (e: Exception) {
            Manifest(
                activeAnimationId = null, animations = null, author = null,
                description = null, generator = null, keywords = null,
                revision = null, themes = null, stateMachines = null, version = null,
                customData = null
            )
        }
    }

    fun markers(): List<Marker> {
        val count = JNI.nativeMarkersCount(nativePtr)
        if (count <= 0) return emptyList()

        val result = mutableListOf<Marker>()
        for (i in 0 until count) {
            val markerData = JNI.nativeMarker(nativePtr, i) ?: continue
            val name = markerData[0] ?: ""
            val time = markerData[1]?.toFloatOrNull() ?: 0f
            val duration = markerData[2]?.toFloatOrNull() ?: 0f
            result.add(Marker(name = name, time = time, duration = duration))
        }
        return result
    }

    // ==================== State Machine ====================

    fun loadStateMachine(stateMachineId: String): Boolean {
        releaseStateMachine()
        stateMachinePtr = JNI.nativeStateMachineLoad(nativePtr, stateMachineId)
        return stateMachinePtr != 0L
    }

    fun loadStateMachineData(data: String): Boolean {
        releaseStateMachine()
        stateMachinePtr = JNI.nativeStateMachineLoadData(nativePtr, data)
        return stateMachinePtr != 0L
    }

    fun releaseStateMachine() {
        if (stateMachinePtr != 0L) {
            JNI.nativeStateMachineRelease(stateMachinePtr)
            stateMachinePtr = 0
        }
    }

    fun stateMachineStart(openUrlPolicy: OpenUrlPolicy = OpenUrlPolicy()): Boolean {
        if (stateMachinePtr == 0L) return false
        val whitelist = if (openUrlPolicy.whitelist.isNotEmpty()) {
            openUrlPolicy.whitelist.joinToString(",")
        } else {
            null
        }
        val result = JNI.nativeStateMachineStart(
            stateMachinePtr,
            whitelist,
            openUrlPolicy.requireUserInteraction
        )
        return result == 0
    }

    fun stateMachineStop(): Boolean {
        if (stateMachinePtr == 0L) return false
        val result = JNI.nativeStateMachineStop(stateMachinePtr)
        return result == 0
    }

    fun stateMachineTick(): Boolean {
        if (stateMachinePtr == 0L) return false
        val result = JNI.nativeStateMachineTick(stateMachinePtr)
        return result == 0
    }

    fun stateMachineSetNumericInput(key: String, value: Float): Boolean {
        if (stateMachinePtr == 0L) return false
        val result = JNI.nativeStateMachineSetNumericInput(stateMachinePtr, key, value)
        return result == 0
    }

    fun stateMachineSetStringInput(key: String, value: String): Boolean {
        if (stateMachinePtr == 0L) return false
        val result = JNI.nativeStateMachineSetStringInput(stateMachinePtr, key, value)
        return result == 0
    }

    fun stateMachineSetBooleanInput(key: String, value: Boolean): Boolean {
        if (stateMachinePtr == 0L) return false
        val result = JNI.nativeStateMachineSetBooleanInput(stateMachinePtr, key, value)
        return result == 0
    }

    fun stateMachineGetNumericInput(key: String): Float {
        if (stateMachinePtr == 0L) return 0f
        return JNI.nativeStateMachineGetNumericInput(stateMachinePtr, key)
    }

    fun stateMachineGetStringInput(key: String): String {
        if (stateMachinePtr == 0L) return ""
        return JNI.nativeStateMachineGetStringInput(stateMachinePtr, key)
    }

    fun stateMachineGetBooleanInput(key: String): Boolean {
        if (stateMachinePtr == 0L) return false
        return JNI.nativeStateMachineGetBooleanInput(stateMachinePtr, key)
    }

    fun stateMachineCurrentState(): String {
        if (stateMachinePtr == 0L) return ""
        return JNI.nativeStateMachineCurrentState(stateMachinePtr)
    }

    fun stateMachineStatus(): String {
        if (stateMachinePtr == 0L) return ""
        return JNI.nativeStateMachineStatus(stateMachinePtr)
    }

    fun stateMachineFireEvent(eventName: String): Boolean {
        if (stateMachinePtr == 0L) return false
        val result = JNI.nativeStateMachineFireEvent(stateMachinePtr, eventName)
        return result == 0
    }

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

    fun stateMachinePollEvent(): StateMachinePlayerEvent? {
        if (stateMachinePtr == 0L) return null
        val arr = JNI.nativeStateMachinePollEvent(stateMachinePtr)
        return StateMachinePlayerEvent.fromJniArray(arr)
    }

    fun stateMachinePollInternalEvent(): String? {
        if (stateMachinePtr == 0L) return null
        return JNI.nativeStateMachinePollInternalEvent(stateMachinePtr)
    }

    // ==================== Backward Compatibility Aliases ====================

    fun stateMachineLoad(id: String) = loadStateMachine(id)
    fun stateMachineLoadData(data: String) = loadStateMachineData(data)

    @Deprecated("stateMachineGetInputs is not supported", ReplaceWith("emptyList()"))
    fun stateMachineGetInputs(): List<String> {
        return emptyList()
    }

    // ==================== Lifecycle ====================

    fun destroy() {
        releaseStateMachine()
        if (nativeBufferAddress != 0L) {
            JNI.nativeFreeBuffer(nativeBufferAddress)
            nativeBufferAddress = 0
        }
        if (nativePtr != 0L) {
            JNI.nativeDestroy(nativePtr)
            nativePtr = 0
        }
    }

    companion object {
        @JvmStatic
        fun withThreads(config: Config, threads: UInt): DotLottiePlayer {
            return DotLottiePlayer(config, threads)
        }
    }
}
