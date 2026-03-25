package com.lottiefiles.dotlottie.core.widget

import android.content.Context
import android.graphics.Color
import android.graphics.PointF
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import com.dotlottie.dlplayer.Config
import com.dotlottie.dlplayer.DotLottiePlayer
import com.dotlottie.dlplayer.DotLottiePlayerEvent
import com.dotlottie.dlplayer.Event
import com.dotlottie.dlplayer.Fit
import com.dotlottie.dlplayer.Layout
import com.dotlottie.dlplayer.Manifest
import com.dotlottie.dlplayer.Marker
import com.dotlottie.dlplayer.Mode
import com.dotlottie.dlplayer.OpenUrlPolicy
import com.dotlottie.dlplayer.StateMachinePlayerEvent
import com.dotlottie.dlplayer.createDefaultLayout
import com.dotlottie.dlplayer.createDefaultOpenUrlPolicy
import com.lottiefiles.dotlottie.core.R
import com.lottiefiles.dotlottie.core.model.Config as ViewConfig
import com.lottiefiles.dotlottie.core.util.DotLottieContent
import com.lottiefiles.dotlottie.core.util.DotLottieEventListener
import com.lottiefiles.dotlottie.core.util.DotLottieSource
import com.lottiefiles.dotlottie.core.util.DotLottieUtils
import com.lottiefiles.dotlottie.core.util.LayoutUtil
import com.lottiefiles.dotlottie.core.util.StateMachineEventListener
import com.lottiefiles.dotlottie.core.util.dispatchPlayerEvent
import com.lottiefiles.dotlottie.core.util.dispatchStateMachineEvent
import com.lottiefiles.dotlottie.core.util.handleInternalEvent
import com.lottiefiles.dotlottie.core.util.isUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class DotLottieGLAnimation @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : GLSurfaceView(context, attrs), GLSurfaceView.Renderer {

    private var dlPlayer: DotLottiePlayer? = null
    private var dlConfig: Config? = null
    private var pendingContent: DotLottieContent? = null
    private var lastLoadedContent: DotLottieContent? = null
    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0
    private var surfaceReady = false
    private var initialized = false
    private var stateMachineIsActive = false
    private var glThread: Thread? = null

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loadJob: Job? = null

    private val eventListeners = mutableListOf<DotLottieEventListener>()
    private val stateMachineListeners = mutableListOf<StateMachineEventListener>()
    private var stateMachineGestureListeners = mutableListOf<String>()
    private var onOpenUrlCallback: ((url: String) -> Unit)? = null

    // Touch tracking
    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f
    private var movedTooMuch: Boolean = false
    private val touchSlop: Float = 20f

    // XML attributes
    private var attrSrc: String = ""
    private var attrLoop: Boolean = false
    private var attrAutoplay: Boolean = false
    private var attrSpeed: Float = 1f
    private var attrPlayMode: Int = 1
    private var attrUseFrameInterpolation: Boolean = false
    private var attrMarker: String? = null
    private var attrThemeId: String? = null
    private var attrStateMachineId: String? = null
    private var attrAnimationId: String? = null
    private var attrThreads: UInt? = null
    private var attrLoopCount: UInt = 0u
    private var attrBackgroundColor: Int = Color.TRANSPARENT

    // ==================== Properties ====================

    @get:FloatRange(from = 0.0)
    val speed: Float
        get() = dlPlayer?.config()?.speed ?: 1f

    val loop: Boolean
        get() = dlPlayer?.config()?.loopAnimation ?: false

    val autoplay: Boolean
        get() = dlPlayer?.config()?.autoplay ?: false

    val isPlaying: Boolean
        get() = dlPlayer?.isPlaying() ?: false

    val isPaused: Boolean
        get() = dlPlayer?.isPaused() ?: false

    val isStopped: Boolean
        get() = dlPlayer?.isStopped() ?: false

    val isLoaded: Boolean
        get() = dlPlayer?.isLoaded() ?: false

    val totalFrames: Float
        get() = dlPlayer?.totalFrames() ?: 0f

    val currentFrame: Float
        get() = dlPlayer?.currentFrame() ?: 0f

    val playMode: Mode
        get() = dlPlayer?.config()?.mode ?: Mode.FORWARD

    val segment: Pair<Float, Float>
        get() {
            val seg = dlPlayer?.config()?.segment
            return if (seg != null && seg.size >= 2) Pair(seg[0], seg[1]) else Pair(0f, 0f)
        }

    val duration: Float
        get() = dlPlayer?.duration() ?: 0f

    val loopCount: UInt
        get() = dlPlayer?.loopCount() ?: 0u

    val useFrameInterpolation: Boolean
        get() = dlPlayer?.config()?.useFrameInterpolation ?: false

    val marker: String
        get() = dlPlayer?.config()?.marker ?: ""

    val markers: List<Marker>
        get() = dlPlayer?.markers() ?: emptyList()

    val activeThemeId: String
        get() = dlPlayer?.activeThemeId() ?: ""

    val activeAnimationId: String
        get() = dlPlayer?.activeAnimationId() ?: ""

    init {
        retrieveAttributes(attrs)
        setupGL()

        if (attrSrc.isNotBlank()) {
            val source = if (attrSrc.isUrl()) {
                DotLottieSource.Url(attrSrc)
            } else {
                DotLottieSource.Asset(attrSrc)
            }
            loadContentAsync(source, Config(
                autoplay = attrAutoplay,
                loopAnimation = attrLoop,
                mode = if (attrPlayMode == 1) Mode.FORWARD else Mode.REVERSE,
                speed = attrSpeed,
                useFrameInterpolation = attrUseFrameInterpolation,
                backgroundColor = attrBackgroundColor.toUInt(),
                segment = listOf(),
                marker = attrMarker ?: "",
                layout = createDefaultLayout(),
                themeId = attrThemeId ?: "",
                stateMachineId = attrStateMachineId ?: "",
                animationId = attrAnimationId ?: "",
                loopCount = attrLoopCount
            ))
        }
    }

    private fun retrieveAttributes(attrs: AttributeSet?) {
        if (attrs == null) return
        context.theme.obtainStyledAttributes(attrs, R.styleable.DotLottieAnimation, 0, 0).apply {
            try {
                attrSrc = getString(R.styleable.DotLottieAnimation_dotLottie_src) ?: ""
                attrLoop = getBoolean(R.styleable.DotLottieAnimation_dotLottie_loop, false)
                attrAutoplay = getBoolean(R.styleable.DotLottieAnimation_dotLottie_autoplay, false)
                attrSpeed = getFloat(R.styleable.DotLottieAnimation_dotLottie_speed, 1f)
                attrPlayMode = getInt(R.styleable.DotLottieAnimation_dotLottie_playMode, 1)
                attrUseFrameInterpolation = getBoolean(
                    R.styleable.DotLottieAnimation_dotLottie_useFrameInterpolation, false
                )
                attrMarker = getString(R.styleable.DotLottieAnimation_dotLottie_marker)
                attrThemeId = getString(R.styleable.DotLottieAnimation_dotLottie_themeId)
                attrStateMachineId = getString(R.styleable.DotLottieAnimation_dotLottie_stateMachineId)
                attrAnimationId = getString(R.styleable.DotLottieAnimation_dotLottie_animationId)
                attrBackgroundColor = getColor(
                    R.styleable.DotLottieAnimation_dotLottie_backgroundColor,
                    Color.TRANSPARENT
                )
                if (hasValue(R.styleable.DotLottieAnimation_dotLottie_threads)) {
                    attrThreads = getInt(R.styleable.DotLottieAnimation_dotLottie_threads, 0).toUInt()
                }
                if (hasValue(R.styleable.DotLottieAnimation_dotLottie_loop_count)) {
                    attrLoopCount = getInt(R.styleable.DotLottieAnimation_dotLottie_loop_count, 0).toUInt()
                }
            } finally {
                recycle()
            }
        }
    }

    private fun setupGL() {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        setZOrderOnTop(true)
        preserveEGLContextOnPause = true
        setRenderer(this)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    // ==================== GLSurfaceView.Renderer ====================

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        glThread = Thread.currentThread()
        // EGL context was (re-)created. Recreate the player if needed.
        if (initialized) {
            // Context was lost and recreated — rebuild player and reload content
            destroyPlayerOnGlThread()
        }
        initPlayerOnGlThread()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        surfaceReady = true

        val player = dlPlayer ?: return
        player.setGlTarget(0, width.toUInt(), height.toUInt())

        // If content is pending, load it now
        val content = pendingContent
        if (content != null && !player.isLoaded()) {
            pendingContent = null
            loadContentOnGlThread(content)
        } else if (player.isLoaded()) {
            player.resize(width.toUInt(), height.toUInt())
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        val player = dlPlayer ?: return

        if (stateMachineIsActive) {
            player.stateMachineTick()
        } else {
            player.tick()
        }

        // Poll events and dispatch to listeners on the main thread
        pollAndDispatchEvents(player)

        // Update render mode based on playback state
        val desiredMode = if (player.isPlaying() || stateMachineIsActive) RENDERMODE_CONTINUOUSLY else RENDERMODE_WHEN_DIRTY
        if (desiredMode != renderMode) {
            post { renderMode = desiredMode }
        }
    }

    // ==================== Player Management ====================

    private fun initPlayerOnGlThread() {
        val config = dlConfig ?: Config(
            autoplay = attrAutoplay,
            loopAnimation = attrLoop,
            mode = if (attrPlayMode == 1) Mode.FORWARD else Mode.REVERSE,
            speed = attrSpeed,
            useFrameInterpolation = attrUseFrameInterpolation,
            backgroundColor = attrBackgroundColor.toUInt(),
            segment = listOf(),
            marker = attrMarker ?: "",
            layout = createDefaultLayout(),
            themeId = attrThemeId ?: "",
            stateMachineId = attrStateMachineId ?: "",
            animationId = attrAnimationId ?: "",
            loopCount = attrLoopCount
        )

        dlPlayer = if (attrThreads != null) {
            DotLottiePlayer.withThreads(config, attrThreads!!)
        } else {
            DotLottiePlayer(config)
        }
        dlConfig = config
        initialized = true

        playerCreatedCallback?.invoke(dlPlayer!!, config)
    }

    private fun destroyPlayerOnGlThread() {
        stateMachineIsActive = false
        dlPlayer?.destroy()
        dlPlayer = null
        initialized = false
    }

    private fun loadContentOnGlThread(content: DotLottieContent) {
        val player = dlPlayer ?: return
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return

        lastLoadedContent = content
        player.setGlTarget(0, surfaceWidth.toUInt(), surfaceHeight.toUInt())

        when (content) {
            is DotLottieContent.Json -> {
                player.loadAnimationData(
                    content.jsonString,
                    surfaceWidth.toUInt(),
                    surfaceHeight.toUInt()
                )
            }
            is DotLottieContent.Binary -> {
                player.loadDotlottieData(
                    content.data,
                    surfaceWidth.toUInt(),
                    surfaceHeight.toUInt()
                )
            }
        }

        // Load state machine if configured
        val smId = dlConfig?.stateMachineId
        if (!smId.isNullOrEmpty()) {
            stateMachineLoad(smId)
            stateMachineStart()
        }

        // Trigger continuous rendering if playing
        post {
            renderMode = if (player.isPlaying() || stateMachineIsActive) {
                RENDERMODE_CONTINUOUSLY
            } else {
                RENDERMODE_WHEN_DIRTY
            }
        }
    }

    private fun loadContentAsync(source: DotLottieSource, config: Config) {
        dlConfig = config
        loadJob?.cancel()
        loadJob = coroutineScope.launch {
            runCatching {
                val content = DotLottieUtils.getContent(context, source)
                queueEvent {
                    if (surfaceReady && dlPlayer != null) {
                        loadContentOnGlThread(content)
                    } else {
                        pendingContent = content
                    }
                }
            }.onFailure { e ->
                post { eventListeners.forEach { it.onLoadError(e) } }
            }
        }
    }

    // ==================== Event Polling ====================

    private fun pollAndDispatchEvents(player: DotLottiePlayer) {
        // Poll events on the GL thread, dispatch to listeners on the main thread via post{}
        var event = player.pollEvent()
        while (event != null) {
            val e = event
            post { dispatchPlayerEvent(e, eventListeners) }
            event = player.pollEvent()
        }

        var smEvent = player.stateMachinePollEvent()
        while (smEvent != null) {
            val e = smEvent
            post { dispatchStateMachineEvent(e, stateMachineListeners) }
            smEvent = player.stateMachinePollEvent()
        }

        var internalEvent = player.stateMachinePollInternalEvent()
        while (internalEvent != null) {
            val msg = internalEvent
            post { handleInternalEvent(msg, onOpenUrlCallback) }
            internalEvent = player.stateMachinePollInternalEvent()
        }
    }

    // ==================== Public API ====================

    fun load(config: ViewConfig) {
        val newDlConfig = Config(
            autoplay = config.autoplay,
            loopAnimation = config.loop,
            mode = config.playMode,
            speed = config.speed,
            useFrameInterpolation = config.useFrameInterpolator,
            backgroundColor = Color.TRANSPARENT.toUInt(),
            segment = listOf(),
            marker = config.marker,
            layout = config.layout,
            themeId = config.themeId,
            stateMachineId = config.stateMachineId,
            animationId = config.animationId,
            loopCount = config.loopCount
        )
        loadContentAsync(config.source, newDlConfig)
    }

    fun play() {
        queueEvent {
            dlPlayer?.play()
            post { renderMode = RENDERMODE_CONTINUOUSLY }
        }
    }

    fun pause() {
        queueEvent {
            dlPlayer?.pause()
            post { renderMode = RENDERMODE_WHEN_DIRTY }
        }
    }

    fun stop() {
        queueEvent {
            dlPlayer?.stop()
            post { renderMode = RENDERMODE_WHEN_DIRTY }
        }
    }

    fun setFrame(frame: Float) {
        queueEvent {
            dlPlayer?.setFrame(frame)
            requestRender()
        }
    }

    fun setSpeed(speed: Float) {
        queueEvent {
            dlPlayer?.let {
                val config = it.config()
                config.speed = speed
                it.setConfig(config)
            }
        }
    }

    fun setLoop(loop: Boolean) {
        queueEvent {
            dlPlayer?.let {
                val config = it.config()
                config.loopAnimation = loop
                it.setConfig(config)
            }
        }
    }

    fun setLoopCount(loopCount: UInt) {
        queueEvent {
            dlPlayer?.let {
                val config = it.config()
                config.loopCount = loopCount
                it.setConfig(config)
            }
        }
    }

    fun setPlayMode(mode: Mode) {
        queueEvent {
            dlPlayer?.let {
                val config = it.config()
                config.mode = mode
                it.setConfig(config)
            }
        }
    }

    fun setSegment(firstFrame: Float, lastFrame: Float) {
        queueEvent {
            dlPlayer?.let {
                val config = it.config()
                config.segment = listOf(firstFrame, lastFrame)
                it.setConfig(config)
            }
        }
    }

    fun setUseFrameInterpolation(enable: Boolean) {
        queueEvent {
            dlPlayer?.let {
                val config = it.config()
                config.useFrameInterpolation = enable
                it.setConfig(config)
            }
        }
    }

    fun setMarker(marker: String) {
        queueEvent {
            dlPlayer?.let {
                val config = it.config()
                config.marker = marker
                it.setConfig(config)
            }
        }
    }

    fun setLayout(fit: Fit, alignment: LayoutUtil.Alignment) {
        queueEvent {
            dlPlayer?.let {
                val config = it.config()
                config.layout = Layout(fit, listOf(alignment.alignment.first, alignment.alignment.second))
                it.setConfig(config)
            }
        }
    }

    fun setLayout(fit: Fit, alignment: Pair<Float, Float>) {
        queueEvent {
            dlPlayer?.let {
                val config = it.config()
                config.layout = Layout(fit, listOf(alignment.first, alignment.second))
                it.setConfig(config)
            }
        }
    }

    fun setTheme(themeId: String) {
        queueEvent {
            if (themeId.isEmpty()) {
                dlPlayer?.resetTheme()
            } else {
                dlPlayer?.setTheme(themeId)
            }
        }
    }

    fun setThemeData(themeData: String) {
        queueEvent { dlPlayer?.setThemeData(themeData) }
    }

    fun resetTheme() {
        queueEvent { dlPlayer?.resetTheme() }
    }

    fun manifest(): Manifest? = dlPlayer?.manifest()

    fun loadAnimation(animationId: String) {
        queueEvent { dlPlayer?.loadAnimation(animationId, surfaceWidth.toUInt(), surfaceHeight.toUInt()) }
    }

    fun freeze() {
        queueEvent { dlPlayer?.pause() }
        post { renderMode = RENDERMODE_WHEN_DIRTY }
    }

    fun unFreeze() {
        queueEvent { dlPlayer?.play() }
        post { renderMode = RENDERMODE_CONTINUOUSLY }
    }

    fun resize(width: Int, height: Int) {
        queueEvent {
            dlPlayer?.resize(width.toUInt(), height.toUInt())
            dlPlayer?.setGlTarget(0, width.toUInt(), height.toUInt())
        }
    }

    fun destroy() {
        queueEvent { destroyPlayerOnGlThread() }
    }

    // ==================== Slots ====================

    fun setSlots(slots: String) {
        queueEvent { dlPlayer?.setSlots(slots) }
    }

    fun setColorSlot(slotId: String, @ColorInt color: Int): Boolean {
        // This must run synchronously for return value — best-effort on calling thread
        return dlPlayer?.setColorSlot(slotId, color) ?: false
    }

    fun setScalarSlot(slotId: String, value: Float): Boolean {
        return dlPlayer?.setScalarSlot(slotId, value) ?: false
    }

    fun setTextSlot(slotId: String, text: String): Boolean {
        return dlPlayer?.setTextSlot(slotId, text) ?: false
    }

    fun setVectorSlot(slotId: String, vector: PointF): Boolean {
        return dlPlayer?.setVectorSlot(slotId, vector) ?: false
    }

    fun setPositionSlot(slotId: String, position: PointF): Boolean {
        return dlPlayer?.setPositionSlot(slotId, position) ?: false
    }

    fun setImageSlotPath(slotId: String, path: String): Boolean {
        return dlPlayer?.setImageSlotPath(slotId, path) ?: false
    }

    fun setImageSlotDataUrl(slotId: String, dataUrl: String): Boolean {
        return dlPlayer?.setImageSlotDataUrl(slotId, dataUrl) ?: false
    }

    fun clearSlots(): Boolean {
        return dlPlayer?.clearSlots() ?: false
    }

    fun clearSlot(slotId: String): Boolean {
        return dlPlayer?.clearSlot(slotId) ?: false
    }

    // ==================== Event Listeners ====================

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

    // ==================== State Machine ====================

    fun stateMachineLoad(stateMachineId: String): Boolean {
        // If called from the GL thread (e.g. during onSurfaceChanged), execute directly
        if (Thread.currentThread() == glThread) {
            return dlPlayer?.loadStateMachine(stateMachineId) ?: false
        }
        // Otherwise queue to the GL thread
        queueEvent { dlPlayer?.loadStateMachine(stateMachineId) }
        return true
    }

    fun stateMachineLoadData(data: String): Boolean {
        if (Thread.currentThread() == glThread) {
            return dlPlayer?.loadStateMachineData(data) ?: false
        }
        queueEvent { dlPlayer?.loadStateMachineData(data) }
        return true
    }

    fun stateMachineStart(
        urlConfig: OpenUrlPolicy = createDefaultOpenUrlPolicy(),
        onOpenUrl: ((url: String) -> Unit)? = null
    ): Boolean {
        onOpenUrlCallback = onOpenUrl
        if (Thread.currentThread() == glThread) {
            return startStateMachineOnGlThread(urlConfig)
        }
        queueEvent { startStateMachineOnGlThread(urlConfig) }
        return true
    }

    private fun startStateMachineOnGlThread(urlConfig: OpenUrlPolicy): Boolean {
        val result = dlPlayer?.stateMachineStart(urlConfig) ?: false
        if (result) {
            stateMachineIsActive = true
            dlPlayer?.let {
                stateMachineGestureListeners =
                    it.stateMachineFrameworkSetup().map { s -> s.lowercase() }.toSet().toMutableList()
            }
            post { renderMode = RENDERMODE_CONTINUOUSLY }
        }
        return result
    }

    fun stateMachineStop(): Boolean {
        stateMachineIsActive = false
        onOpenUrlCallback = null
        if (Thread.currentThread() == glThread) {
            val result = dlPlayer?.stateMachineStop() ?: false
            post { renderMode = RENDERMODE_WHEN_DIRTY }
            return result
        }
        queueEvent {
            dlPlayer?.stateMachineStop()
            post { renderMode = RENDERMODE_WHEN_DIRTY }
        }
        return true
    }

    fun stateMachinePostEvent(event: Event) {
        val eventName = event.toString().split("(").firstOrNull()?.lowercase() ?: event.toString()
        if (stateMachineGestureListeners.contains(eventName)) {
            queueEvent { dlPlayer?.stateMachinePostEvent(event) }
        }
    }

    fun stateMachineFireEvent(event: String) {
        queueEvent { dlPlayer?.stateMachineFireEvent(event) }
    }

    fun addStateMachineEventListener(listener: StateMachineEventListener) {
        stateMachineListeners.add(listener)
    }

    fun removeStateMachineEventListener(listener: StateMachineEventListener) {
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

    // ==================== Touch Events ====================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = x
                lastTouchY = y
                movedTooMuch = false
                queueEvent { dlPlayer?.stateMachinePostEvent(Event.PointerDown(x, y)) }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastTouchX
                val dy = y - lastTouchY
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                if (distance > touchSlop) {
                    movedTooMuch = true
                }
                queueEvent { dlPlayer?.stateMachinePostEvent(Event.PointerMove(x, y)) }
                return true
            }
            MotionEvent.ACTION_UP -> {
                queueEvent { dlPlayer?.stateMachinePostEvent(Event.PointerUp(x, y)) }
                if (!movedTooMuch) {
                    performClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                queueEvent { dlPlayer?.stateMachinePostEvent(Event.PointerUp(x, y)) }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        queueEvent { dlPlayer?.stateMachinePostEvent(Event.Click(lastTouchX, lastTouchY)) }
        return true
    }

    // ==================== Lifecycle ====================

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        loadJob?.cancel()
        coroutineScope.cancel()
        queueEvent { destroyPlayerOnGlThread() }
    }

    // ==================== Internal: Player Instance Access ====================

    internal fun getPlayerInstance(): DotLottiePlayer? = dlPlayer

    internal fun getPlayerConfig(): Config? = dlConfig

    internal fun setOnPlayerCreated(callback: (DotLottiePlayer, Config) -> Unit) {
        if (dlPlayer != null && dlConfig != null) {
            callback(dlPlayer!!, dlConfig!!)
        }
        // Also set for future recreation
        playerCreatedCallback = callback
    }

    private var playerCreatedCallback: ((DotLottiePlayer, Config) -> Unit)? = null

    companion object {
        private const val TAG = "DotLottieGLAnimation"
    }
}
