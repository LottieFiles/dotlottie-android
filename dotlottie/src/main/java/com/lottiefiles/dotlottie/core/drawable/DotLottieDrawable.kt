package com.lottiefiles.dotlottie.core.drawable

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.Choreographer
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import com.lottiefiles.dotlottie.core.util.DotLottieEventListener
import com.dotlottie.dlplayer.DotLottiePlayer
import com.dotlottie.dlplayer.Config
import com.dotlottie.dlplayer.Event
import com.dotlottie.dlplayer.Layout
import com.dotlottie.dlplayer.Manifest
import com.dotlottie.dlplayer.Marker
import com.dotlottie.dlplayer.Mode
import com.dotlottie.dlplayer.OpenUrlPolicy
import com.dotlottie.dlplayer.createDefaultOpenUrlPolicy
import com.lottiefiles.dotlottie.core.util.DotLottieContent
import com.lottiefiles.dotlottie.core.util.StateMachineEventListener
import com.dotlottie.dlplayer.Pointer
import androidx.core.graphics.createBitmap
import com.dotlottie.dlplayer.DotLottiePlayerEvent
import com.dotlottie.dlplayer.StateMachinePlayerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val BYTES_PER_PIXEL = 4

class DotLottieDrawable(
    private val animationData: DotLottieContent,
    private var width: Int = 0,
    private var height: Int = 0,
    private val dotLottieEventListener: MutableList<DotLottieEventListener>,
    private var config: Config,
    private val threads: UInt? = null
) : Drawable(), Animatable {

    private var nativeBuffer: Pointer? = null
    private var nativeBufferAddress: Long = 0
    private var bufferBytes: java.nio.ByteBuffer? = null
    private var bitmapBuffer: Bitmap? = null
    private var dlPlayer: DotLottiePlayer? = null
    private var stateMachineListeners: MutableList<StateMachineEventListener> = mutableListOf()
    private var stateMachineGestureListeners: MutableList<String> = mutableListOf()
    private var stateMachineIsActive = false
    private var onOpenUrlCallback: ((url: String) -> Unit)? = null

    var freeze: Boolean = false
        set(value) {
            val player = dlPlayer ?: return
            if (value) {
                dotLottieEventListener.forEach(DotLottieEventListener::onFreeze)
                choreographer.removeFrameCallback(frameCallback)
                player.pause()
            } else {
                dotLottieEventListener.forEach(DotLottieEventListener::onUnFreeze)
                player.play()
                scheduleFrame()
            }
            field = value
        }

    var duration: Float = 0.0f
        get() = dlPlayer?.duration() ?: 0f

    var loopCount: UInt = 0u
        get() = dlPlayer?.loopCount() ?: 0u

    val segment: Pair<Float, Float>?
        get() {
            val seg = dlPlayer?.config()?.segment ?: return null
            if (seg.isEmpty()) return null
            return Pair(seg[0], seg[1])
        }

    private val choreographer by lazy(LazyThreadSafetyMode.NONE) { Choreographer.getInstance() }
    private val renderScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val renderMutex = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val singleThreadDispatcher = Dispatchers.Default.limitedParallelism(1)
    private var forceUpdateBitmap = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val player = dlPlayer ?: return
            val bytes = bufferBytes ?: return
            val bmp = bitmapBuffer ?: return

            // Non-blocking: skip if previous render still in progress
            if (!renderMutex.tryLock()) {
                if (player.isPlaying() || stateMachineIsActive) {
                    choreographer.postFrameCallback(this)
                }
                return
            }

            // stateMachineTick() calls player tick() internally (player is borrowed by state machine)
            val ticked = if (stateMachineIsActive) {
                player.stateMachineTick()
            } else {
                player.tick()
            }

            // Poll and dispatch events on main thread
            pollAndDispatchPlayerEvents()
            pollAndDispatchStateMachineEvents()

            var lockHandedToCoroutine = false

            if ((ticked || forceUpdateBitmap) && !bmp.isRecycled) {
                val shouldResetFlag = !ticked && forceUpdateBitmap

                // Capture references for background thread
                val capturedBytes = bytes
                val capturedBmp = bmp

                // Hand lock ownership to the coroutine — it will unlock in its finally block.
                // This prevents a resize from freeing the buffer between unlock and coroutine start.
                lockHandedToCoroutine = true
                renderScope.launch(singleThreadDispatcher) {
                    try {
                        if (!capturedBmp.isRecycled) {
                            capturedBytes.rewind()
                            capturedBmp.copyPixelsFromBuffer(capturedBytes)

                            withContext(Dispatchers.Main) {
                                if (shouldResetFlag) {
                                    forceUpdateBitmap = false
                                }
                                invalidateSelf()
                            }
                        }
                    } finally {
                        renderMutex.unlock()
                    }
                }
            }

            if (player.isPlaying() || stateMachineIsActive) {
                choreographer.postFrameCallback(this)
            }

            if (!lockHandedToCoroutine) {
                renderMutex.unlock()
            }
        }
    }

    /**
     * Schedule a frame callback to tick + render on the next vsync.
     * @param forceUpdate If true, forces a buffer copy even if tick() reports no new frame.
     */
    private fun scheduleFrame(forceUpdate: Boolean = false) {
        if (forceUpdate) forceUpdateBitmap = true
        choreographer.postFrameCallback(frameCallback)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun getIntrinsicWidth(): Int {
        return width
    }

    override fun getIntrinsicHeight(): Int {
        return height
    }

    var useFrameInterpolation: Boolean
        get() = dlPlayer?.config()?.useFrameInterpolation ?: false
        set(value) {
            config.useFrameInterpolation = value
            dlPlayer?.setConfig(config)
        }

    val playMode: Mode
        get() = dlPlayer?.config()?.mode ?: Mode.FORWARD
    val totalFrame: Float
        get() = dlPlayer?.totalFrames() ?: 0f

    val autoplay: Boolean
        get() = dlPlayer?.config()?.autoplay ?: false

    val currentFrame: Float
        get() = dlPlayer?.currentFrame() ?: 0f

    var loop: Boolean
        get() = dlPlayer?.config()?.loopAnimation ?: false
        set(value) {
            config.loopAnimation = value
            dlPlayer?.setConfig(config)
        }

    var marker: String
        get() = dlPlayer?.config()?.marker ?: ""
        set(value) {
            config.marker = value
            dlPlayer?.setConfig(config)
        }

    val markers: List<Marker>
        get() = dlPlayer?.markers() ?: emptyList()

    var layout: Layout
        get() = dlPlayer?.config()?.layout ?: config.layout
        set(value) {
            config.layout = value
            dlPlayer?.setConfig(config)
        }

    val activeThemeId: String
        get() = dlPlayer?.activeThemeId() ?: ""

    val activeAnimationId: String
        get() = dlPlayer?.activeAnimationId() ?: ""

    @get:FloatRange(from = 0.0)
    var speed: Float
        get() = dlPlayer?.config()?.speed ?: 1f
        set(speed) {
            config.speed = speed
            dlPlayer?.setConfig(config)
        }

    val isLoaded: Boolean
        get() = dlPlayer?.isLoaded() ?: false


    private var initialized = false

    /**
     * Initialize the native player and load the animation.
     * MUST be called on the main thread (ThorVG has thread affinity for rendering).
     */
    private fun ensureInitialized() {
        if (initialized) return
        if (width <= 0 || height <= 0) return
        initialized = true

        try {
            dlPlayer = if (threads != null) {
                DotLottiePlayer.withThreads(config, threads)
            } else {
                DotLottiePlayer(config)
            }
            setupBufferAndLoad()

            // Load and start state machine if configured
            if (config.stateMachineId.isNotEmpty()) {
                stateMachineLoad(config.stateMachineId)
                stateMachineStart()
            }

            scheduleFrame(forceUpdate = true)
        } catch (e: Throwable) {
            dotLottieEventListener.forEach { it.onLoadError(e) }
        }
    }

    private fun setupBufferAndLoad() {
        val player = dlPlayer ?: return

        // 1. Allocate caller-managed buffer and register SW target (required before load)
        nativeBufferAddress = player.allocateBuffer(width, height)
        nativeBuffer = Pointer(nativeBufferAddress)
        player.setSwTarget(nativeBufferAddress, width.toUInt(), height.toUInt())

        // 2. Load animation
        when (animationData) {
            is DotLottieContent.Json -> {
                player.loadAnimationData(
                    animationData.jsonString,
                    width.toUInt(),
                    height.toUInt()
                )
            }

            is DotLottieContent.Binary -> {
                player.loadDotlottieData(animationData.data, width.toUInt(), height.toUInt())
            }
        }

        bitmapBuffer = createBitmap(width, height)
        bufferBytes = nativeBuffer!!.getByteBuffer(0, player.bufferLen().toLong() * BYTES_PER_PIXEL)
    }

    fun release() {
        choreographer.removeFrameCallback(frameCallback)
        renderScope.cancel()
        val player = dlPlayer ?: return
        if (nativeBufferAddress != 0L) {
            player.freeBuffer(nativeBufferAddress)
            nativeBufferAddress = 0
            nativeBuffer = null
        }
        player.destroy()
        dlPlayer = null
        dotLottieEventListener.forEach(DotLottieEventListener::onDestroy)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            bitmapBuffer?.recycle()
        }
        bitmapBuffer = null
    }

    fun resize(w: Int, h: Int) {
        width = w
        height = h

        // If not yet initialized, just store dimensions — ensureInitialized() will use them
        val player = dlPlayer ?: return

        // Coordinate with render loop via mutex
        renderScope.launch(Dispatchers.Main) {
            renderMutex.withLock {
                val oldBitmap = bitmapBuffer
                bitmapBuffer = createBitmap(width, height)

                // Free old buffer, allocate new, set SW target BEFORE resize
                if (nativeBufferAddress != 0L) {
                    player.freeBuffer(nativeBufferAddress)
                }
                nativeBufferAddress = player.allocateBuffer(width, height)
                nativeBuffer = Pointer(nativeBufferAddress)
                player.setSwTarget(nativeBufferAddress, width.toUInt(), height.toUInt())
                player.resize(width.toUInt(), height.toUInt())
                bufferBytes =
                    nativeBuffer!!.getByteBuffer(0, player.bufferLen().toLong() * BYTES_PER_PIXEL)

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    oldBitmap?.recycle()
                }
            }

            if (!player.isPlaying()) {
                scheduleFrame(forceUpdate = true)
            }
        }
    }

    override fun isRunning(): Boolean {
        return dlPlayer?.isPlaying() ?: false
    }

    fun play() {
        dlPlayer?.play()
        scheduleFrame()
    }

    override fun start() {
        play()
    }

    fun setPlayMode(playMode: Mode) {
        config.mode = playMode
        dlPlayer?.setConfig(config)
    }

    override fun stop() {
        dlPlayer?.stop()
        choreographer.removeFrameCallback(frameCallback)
        invalidateSelf()
    }

    fun isPaused(): Boolean {
        return dlPlayer?.isPaused() ?: false
    }

    fun isStopped(): Boolean {
        return dlPlayer?.isStopped() ?: true
    }

    fun setCurrentFrame(frame: Float) {
        dlPlayer?.setFrame(frame)
        scheduleFrame(forceUpdate = true)
    }

    fun setSegment(first: Float, second: Float) {
        config.segment = listOf(first, second)
        dlPlayer?.setConfig(config)
        scheduleFrame(forceUpdate = true)
    }

    fun loadAnimation(
        animationId: String,
    ) {
        val result = dlPlayer?.loadAnimation(animationId, width.toUInt(), height.toUInt())
        if (result == true) {
            scheduleFrame(forceUpdate = true)
        }
    }

    fun setTheme(themeId: String) {
        dlPlayer?.setTheme(themeId)
        scheduleFrame(forceUpdate = true)
    }

    fun setThemeData(themeData: String) {
        dlPlayer?.setThemeData(themeData)
    }

    fun resetTheme() {
        dlPlayer?.resetTheme()
        scheduleFrame(forceUpdate = true)
    }

    fun setSlots(slots: String) {
        dlPlayer?.setSlots(slots)
        scheduleFrame(forceUpdate = true)
    }

    fun setColorSlot(slotId: String, @ColorInt color: Int): Boolean {
        val result = dlPlayer?.setColorSlot(slotId, color) ?: false
        if (result) scheduleFrame(forceUpdate = true)
        return result
    }

    fun setScalarSlot(slotId: String, value: Float): Boolean {
        val result = dlPlayer?.setScalarSlot(slotId, value) ?: false
        if (result) scheduleFrame(forceUpdate = true)
        return result
    }

    fun setTextSlot(slotId: String, text: String): Boolean {
        val result = dlPlayer?.setTextSlot(slotId, text) ?: false
        if (result) scheduleFrame(forceUpdate = true)
        return result
    }

    fun setVectorSlot(slotId: String, vector: PointF): Boolean {
        val result = dlPlayer?.setVectorSlot(slotId, vector) ?: false
        if (result) scheduleFrame(forceUpdate = true)
        return result
    }

    fun setPositionSlot(slotId: String, position: PointF): Boolean {
        val result = dlPlayer?.setPositionSlot(slotId, position) ?: false
        if (result) scheduleFrame(forceUpdate = true)
        return result
    }

    fun setImageSlotPath(slotId: String, path: String): Boolean {
        val result = dlPlayer?.setImageSlotPath(slotId, path) ?: false
        if (result) scheduleFrame(forceUpdate = true)
        return result
    }

    fun setImageSlotDataUrl(slotId: String, dataUrl: String): Boolean {
        val result = dlPlayer?.setImageSlotDataUrl(slotId, dataUrl) ?: false
        if (result) scheduleFrame(forceUpdate = true)
        return result
    }

    fun clearSlots(): Boolean {
        val result = dlPlayer?.clearSlots() ?: false
        if (result) scheduleFrame(forceUpdate = true)
        return result
    }

    fun clearSlot(slotId: String): Boolean {
        val result = dlPlayer?.clearSlot(slotId) ?: false
        if (result) scheduleFrame(forceUpdate = true)
        return result
    }

    fun manifest(): Manifest? {
        return dlPlayer?.manifest()
    }

    fun addEventListenter(listener: DotLottieEventListener) {
        if (!dotLottieEventListener.contains(listener)) {
            dotLottieEventListener.add(listener)
        }
    }

    fun removeEventListener(listener: DotLottieEventListener) {
        dotLottieEventListener.remove(listener)
    }

    fun clearEventListeners() {
        dotLottieEventListener.clear()
    }

    fun pause() {
        dlPlayer?.pause()
        choreographer.removeFrameCallback(frameCallback)
    }

    fun stateMachineStart(
        openUrl: OpenUrlPolicy = createDefaultOpenUrlPolicy(),
        onOpenUrl: ((url: String) -> Unit)? = null
    ): Boolean {
        val result = dlPlayer?.stateMachineStart(openUrl) ?: false

        if (result) {
            stateMachineIsActive = true
            onOpenUrlCallback = onOpenUrl
            if (dlPlayer != null) {
                stateMachineGestureListeners =
                    dlPlayer!!.stateMachineFrameworkSetup().map { it.lowercase() }.toSet()
                        .toMutableList()
            }

            scheduleFrame()
        }
        return result
    }

    fun stateMachineStop(): Boolean {
        stateMachineIsActive = false
        onOpenUrlCallback = null
        val result = dlPlayer?.stateMachineStop() ?: false
        choreographer.removeFrameCallback(frameCallback)
        invalidateSelf()
        return result
    }

    fun stateMachineLoad(stateMachineId: String): Boolean {
        val result = dlPlayer?.stateMachineLoad(stateMachineId) ?: false
        if (result) {
            scheduleFrame()
        }
        return result
    }

    fun stateMachineLoadData(data: String): Boolean {
        val result = dlPlayer?.stateMachineLoadData(data) ?: false
        if (result) {
            scheduleFrame()
        }
        return result
    }

    fun stateMachinePostEvent(event: Event, force: Boolean = false) {
        val eventName = event.toString().split("(").firstOrNull()?.lowercase() ?: event.toString()

        if (force) {
            dlPlayer?.stateMachinePostEvent(event)
        } else if (stateMachineGestureListeners.contains(eventName)) {
            dlPlayer?.stateMachinePostEvent(event)
        }
    }

    fun stateMachineAddEventListener(listener: StateMachineEventListener) {
        stateMachineListeners.add(listener)
    }

    fun stateMachineRemoveEventListener(listener: StateMachineEventListener) {
        stateMachineListeners.remove(listener)
    }

    fun stateMachineSetNumericInput(key: String, value: Float): Boolean {
        return dlPlayer?.stateMachineSetNumericInput(key, value) ?: false
    }

    fun stateMachineSetStringInput(key: String, value: String): Boolean {
        return dlPlayer?.stateMachineSetStringInput(key, value) ?: false
    }

    fun stateMachineSetBooleanInput(key: String, value: Boolean): Boolean {
        return dlPlayer?.stateMachineSetBooleanInput(key, value) ?: false
    }

    fun stateMachineGetNumericInput(key: String): Float? {
        return dlPlayer?.stateMachineGetNumericInput(key)
    }

    fun stateMachineGetStringInput(key: String): String? {
        return dlPlayer?.stateMachineGetStringInput(key)
    }

    fun stateMachineGetBooleanInput(key: String): Boolean? {
        return dlPlayer?.stateMachineGetBooleanInput(key)
    }

    fun stateMachineCurrentState(): String? {
        return dlPlayer?.stateMachineCurrentState()
    }

    fun stateMachineFireEvent(event: String) {
        dlPlayer?.stateMachineFireEvent(event)
    }

    override fun draw(canvas: Canvas) {
        // Lazy init on main thread (ThorVG requires thread affinity)
        ensureInitialized()

        bitmapBuffer?.let { bmp ->
            if (!bmp.isRecycled) {
                canvas.drawBitmap(bmp, 0f, 0f, Paint())
            }
        }
    }

    private fun pollAndDispatchPlayerEvents() {
        val player = dlPlayer ?: return
        var event = player.pollEvent()
        while (event != null) {
            val e = event
            when (e) {
                is DotLottiePlayerEvent.Load -> {
                    dotLottieEventListener.forEach(DotLottieEventListener::onLoad)
                }

                is DotLottiePlayerEvent.LoadError -> {
                    dotLottieEventListener.forEach { listener ->
                        listener.onLoadError()
                        listener.onLoadError(Throwable("Load error occurred"))
                    }
                }

                is DotLottiePlayerEvent.Play -> {
                    dotLottieEventListener.forEach(DotLottieEventListener::onPlay)
                }

                is DotLottiePlayerEvent.Pause -> {
                    dotLottieEventListener.forEach(DotLottieEventListener::onPause)
                }

                is DotLottiePlayerEvent.Stop -> {
                    dotLottieEventListener.forEach(DotLottieEventListener::onStop)
                }

                is DotLottiePlayerEvent.Frame -> {
                    dotLottieEventListener.forEach { it.onFrame(e.frameNo) }
                }

                is DotLottiePlayerEvent.Render -> {
                    dotLottieEventListener.forEach { it.onRender(e.frameNo) }
                }

                is DotLottiePlayerEvent.Loop -> {
                    dotLottieEventListener.forEach { it.onLoop(e.loopCount.toInt()) }
                }

                is DotLottiePlayerEvent.Complete -> {
                    dotLottieEventListener.forEach(DotLottieEventListener::onComplete)
                }
            }
            event = player.pollEvent()
        }
    }

    private fun pollAndDispatchStateMachineEvents() {
        val player = dlPlayer ?: return
        var smEvent = player.stateMachinePollEvent()
        while (smEvent != null) {
            val e = smEvent
            when (e) {
                is StateMachinePlayerEvent.Start -> {
                    stateMachineListeners.forEach { it.onStart() }
                }

                is StateMachinePlayerEvent.Stop -> {
                    stateMachineListeners.forEach { it.onStop() }
                }

                is StateMachinePlayerEvent.Transition -> {
                    stateMachineListeners.forEach { it.onTransition(e.previousState, e.newState) }
                }

                is StateMachinePlayerEvent.StateEntered -> {
                    stateMachineListeners.forEach { it.onStateEntered(e.state) }
                }

                is StateMachinePlayerEvent.StateExit -> {
                    stateMachineListeners.forEach { it.onStateExit(e.state) }
                }

                is StateMachinePlayerEvent.CustomEvent -> {
                    stateMachineListeners.forEach { it.onCustomEvent(e.message) }
                }

                is StateMachinePlayerEvent.Error -> {
                    stateMachineListeners.forEach { it.onError(e.message) }
                }

                is StateMachinePlayerEvent.StringInputChange -> {
                    stateMachineListeners.forEach {
                        it.onStringInputValueChange(
                            e.name,
                            e.oldValue,
                            e.newValue
                        )
                    }
                }

                is StateMachinePlayerEvent.NumericInputChange -> {
                    stateMachineListeners.forEach {
                        it.onNumericInputValueChange(
                            e.name,
                            e.oldValue,
                            e.newValue
                        )
                    }
                }

                is StateMachinePlayerEvent.BooleanInputChange -> {
                    stateMachineListeners.forEach {
                        it.onBooleanInputValueChange(
                            e.name,
                            e.oldValue,
                            e.newValue
                        )
                    }
                }

                is StateMachinePlayerEvent.InputFired -> {
                    stateMachineListeners.forEach { it.onInputFired(e.name) }
                }
            }
            smEvent = player.stateMachinePollEvent()
        }
        // Poll internal events
        var internalEvent = player.stateMachinePollInternalEvent()
        while (internalEvent != null) {
            if (internalEvent.startsWith("OpenUrl: ")) {
                val payload = internalEvent.substringAfter("OpenUrl: ")
                val url = if (payload.contains(" | Target: ")) {
                    payload.substringBefore(" | Target: ")
                } else {
                    payload
                }
                onOpenUrlCallback?.invoke(url)
            }
            internalEvent = player.stateMachinePollInternalEvent()
        }
    }

    companion object {

        private const val TAG = "DotLottieDrawable"

        private const val LOTTIE_INFO_FRAME_COUNT = 0
        private const val LOTTIE_INFO_DURATION = 1
        private const val LOTTIE_INFO_COUNT = 2
    }
}
