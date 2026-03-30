package com.lottiefiles.dotlottie.core.widget

import android.content.Context
import android.graphics.Color
import android.graphics.PointF
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLES30
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.TextureView
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
import com.lottiefiles.dotlottie.core.ExperimentalDotLottieGLApi
import com.lottiefiles.dotlottie.core.R
import com.lottiefiles.dotlottie.core.model.Config as ViewConfig
import com.lottiefiles.dotlottie.core.util.DotLottieContent
import com.lottiefiles.dotlottie.core.util.DotLottieEventListener
import com.lottiefiles.dotlottie.core.util.DotLottieSource
import com.lottiefiles.dotlottie.core.util.DotLottieUtils
import com.lottiefiles.dotlottie.core.util.LayoutUtil
import com.lottiefiles.dotlottie.core.util.SharedGlThread
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

@ExperimentalDotLottieGLApi
class DotLottieGLAnimation @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TextureView(context, attrs), TextureView.SurfaceTextureListener, SharedGlThread.RenderClient {

    private val sharedGl = SharedGlThread.instance

    private var dlPlayer: DotLottiePlayer? = null
    private var dlConfig: Config? = null
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var pendingContent: DotLottieContent? = null
    private var lastLoadedContent: DotLottieContent? = null
    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0
    @Volatile private var surfaceReady = false
    private var initialized = false
    @Volatile private var paused = false
    @Volatile private var dirtyFrame = false
    private var glTargetDirty = true

    // Intermediate render FBO — ThorVG renders here; we blit to the window surface each frame.
    // This avoids flickering caused by undefined back-buffer contents after eglSwapBuffers.
    private var renderFboId = 0
    private var renderTexId = 0

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
        isOpaque = false
        surfaceTextureListener = this

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

    // ==================== TextureView.SurfaceTextureListener ====================

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
        st.setDefaultBufferSize(width, height)
        sharedGl.handler.post {
            eglSurface = sharedGl.createWindowSurface(st)
            if (eglSurface == EGL14.EGL_NO_SURFACE) return@post

            surfaceWidth = width
            surfaceHeight = height
            surfaceReady = true
            glTargetDirty = true

            // (Re)create player
            if (initialized) {
                destroyPlayerOnGlThread()
            }
            initPlayerOnGlThread()

            val player = dlPlayer ?: return@post
            sharedGl.makeCurrent(eglSurface)

            // Create intermediate render FBO
            destroyRenderFbo()
            createRenderFbo()
            if (renderFboId == 0) return@post

            player.setGlTarget(
                sharedGl.eglDisplay.nativeHandle,
                eglSurface.nativeHandle,
                sharedGl.eglContext.nativeHandle,
                renderFboId,
                width.toUInt(),
                height.toUInt()
            )
            glTargetDirty = false

            // Load pending content or reload last content
            val content = pendingContent ?: lastLoadedContent
            if (content != null) {
                pendingContent = null
                loadContentOnGlThread(content)
            }
        }
        sharedGl.register(this)
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
        st.setDefaultBufferSize(width, height)
        sharedGl.handler.post {
            if (width == surfaceWidth && height == surfaceHeight) return@post
            surfaceWidth = width
            surfaceHeight = height
            glTargetDirty = true
            val player = dlPlayer ?: return@post

            sharedGl.makeCurrent(eglSurface)

            // Recreate FBO at new size
            destroyRenderFbo()
            createRenderFbo()
            if (renderFboId == 0) return@post

            player.setGlTarget(
                sharedGl.eglDisplay.nativeHandle,
                eglSurface.nativeHandle,
                sharedGl.eglContext.nativeHandle,
                renderFboId,
                width.toUInt(),
                height.toUInt()
            )
            glTargetDirty = false
            player.resize(width.toUInt(), height.toUInt())
            dirtyFrame = true
            sharedGl.requestRender()
        }
    }

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        surfaceReady = false
        sharedGl.handler.post {
            sharedGl.unregisterOnGlThread(this)
            destroyPlayerOnGlThread()
            destroyRenderFbo()
            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                sharedGl.destroyWindowSurface(eglSurface)
                eglSurface = EGL14.EGL_NO_SURFACE
            }
        }
        return true
    }

    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
        // No-op
    }

    // ==================== SharedGlThread.RenderClient ====================

    override fun shouldRender(): Boolean {
        if (paused || !surfaceReady) return false
        if (dirtyFrame) return true
        val player = dlPlayer ?: return false
        return player.isPlaying() || player.stateMachineIsActive
    }

    override fun onRenderFrame() {
        val player = dlPlayer ?: return
        if (!surfaceReady) return
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return
        if (renderFboId == 0) return

        dirtyFrame = false

        if (!sharedGl.makeCurrent(eglSurface)) return

        // Only call setGlTarget when the surface changed or another client
        // rendered in between (which changes the EGL surface binding).
        if (glTargetDirty || sharedGl.lastRenderedClient != this) {
            player.setGlTarget(
                sharedGl.eglDisplay.nativeHandle,
                eglSurface.nativeHandle,
                sharedGl.eglContext.nativeHandle,
                renderFboId,
                surfaceWidth.toUInt(),
                surfaceHeight.toUInt()
            )
            glTargetDirty = false
        }

        // ThorVG renders into the intermediate FBO
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFboId)
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)

        if (player.stateMachineIsActive) {
            player.stateMachineTick()
        } else {
            player.tick()
        }

        // Blit from render FBO to the window surface's default framebuffer
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, renderFboId)
        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, 0)
        GLES30.glBlitFramebuffer(
            0, 0, surfaceWidth, surfaceHeight,
            0, 0, surfaceWidth, surfaceHeight,
            GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_NEAREST
        )
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

        sharedGl.swapBuffers(eglSurface)

        // Poll events and dispatch to listeners on the main thread
        pollAndDispatchEvents(player)
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
        if (!initialized) return
        dlPlayer?.destroy()
        dlPlayer = null
        initialized = false
    }

    private fun createRenderFbo() {
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return

        val texArr = IntArray(1)
        GLES20.glGenTextures(1, texArr, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texArr[0])
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            surfaceWidth, surfaceHeight, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        renderTexId = texArr[0]

        val fboArr = IntArray(1)
        GLES20.glGenFramebuffers(1, fboArr, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboArr[0])
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, renderTexId, 0
        )

        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            destroyRenderFbo()
            return
        }

        renderFboId = fboArr[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    private fun destroyRenderFbo() {
        if (renderFboId != 0) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(renderFboId), 0)
            renderFboId = 0
        }
        if (renderTexId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(renderTexId), 0)
            renderTexId = 0
        }
    }

    private fun loadContentOnGlThread(content: DotLottieContent) {
        val player = dlPlayer ?: return
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return
        if (renderFboId == 0) return

        lastLoadedContent = content
        sharedGl.makeCurrent(eglSurface)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFboId)
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
        player.setGlTarget(
            sharedGl.eglDisplay.nativeHandle,
            eglSurface.nativeHandle,
            sharedGl.eglContext.nativeHandle,
            renderFboId,
            surfaceWidth.toUInt(),
            surfaceHeight.toUInt()
        )
        glTargetDirty = false

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

        // Always render at least one frame so events (e.g. Load) get polled and dispatched
        dirtyFrame = true
        sharedGl.requestRender()
    }

    private fun loadContentAsync(source: DotLottieSource, config: Config) {
        dlConfig = config
        loadJob?.cancel()
        loadJob = coroutineScope.launch {
            runCatching {
                val content = DotLottieUtils.getContent(context, source)
                sharedGl.handler.post {
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

    // ==================== Lifecycle ====================

    fun onResume() {
        paused = false
        if (surfaceReady) {
            sharedGl.requestRender()
        }
    }

    fun onPause() {
        paused = true
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
        sharedGl.handler.post {
            dlPlayer?.play()
            sharedGl.requestRender()
        }
    }

    fun pause() {
        sharedGl.handler.post {
            dlPlayer?.pause()
        }
    }

    fun stop() {
        sharedGl.handler.post {
            dlPlayer?.stop()
        }
    }

    fun requestRender() {
        sharedGl.requestRender()
    }

    fun setFrame(frame: Float) {
        sharedGl.handler.post {
            dlPlayer?.setFrame(frame)
            dirtyFrame = true
            sharedGl.requestRender()
        }
    }

    fun setSpeed(speed: Float) {
        sharedGl.handler.post {
            dlPlayer?.let {
                val config = it.config()
                config.speed = speed
                it.setConfig(config)
            }
        }
    }

    fun setLoop(loop: Boolean) {
        sharedGl.handler.post {
            dlPlayer?.let {
                val config = it.config()
                config.loopAnimation = loop
                it.setConfig(config)
            }
        }
    }

    fun setLoopCount(loopCount: UInt) {
        sharedGl.handler.post {
            dlPlayer?.let {
                val config = it.config()
                config.loopCount = loopCount
                it.setConfig(config)
            }
        }
    }

    fun setPlayMode(mode: Mode) {
        sharedGl.handler.post {
            dlPlayer?.let {
                val config = it.config()
                config.mode = mode
                it.setConfig(config)
            }
        }
    }

    fun setSegment(firstFrame: Float, lastFrame: Float) {
        sharedGl.handler.post {
            dlPlayer?.let {
                val config = it.config()
                config.segment = listOf(firstFrame, lastFrame)
                it.setConfig(config)
            }
        }
    }

    fun setUseFrameInterpolation(enable: Boolean) {
        sharedGl.handler.post {
            dlPlayer?.let {
                val config = it.config()
                config.useFrameInterpolation = enable
                it.setConfig(config)
            }
        }
    }

    fun setMarker(marker: String) {
        sharedGl.handler.post {
            dlPlayer?.let {
                val config = it.config()
                config.marker = marker
                it.setConfig(config)
            }
        }
    }

    fun setLayout(fit: Fit, alignment: LayoutUtil.Alignment) {
        sharedGl.handler.post {
            dlPlayer?.let {
                val config = it.config()
                config.layout = Layout(fit, listOf(alignment.alignment.first, alignment.alignment.second))
                it.setConfig(config)
            }
        }
    }

    fun setLayout(fit: Fit, alignment: Pair<Float, Float>) {
        sharedGl.handler.post {
            dlPlayer?.let {
                val config = it.config()
                config.layout = Layout(fit, listOf(alignment.first, alignment.second))
                it.setConfig(config)
            }
        }
    }

    fun setTheme(themeId: String) {
        sharedGl.handler.post {
            if (themeId.isEmpty()) {
                dlPlayer?.resetTheme()
            } else {
                dlPlayer?.setTheme(themeId)
            }
        }
    }

    fun setThemeData(themeData: String) {
        sharedGl.handler.post { dlPlayer?.setThemeData(themeData) }
    }

    fun resetTheme() {
        sharedGl.handler.post { dlPlayer?.resetTheme() }
    }

    fun manifest(): Manifest? = dlPlayer?.manifest()

    fun loadAnimation(animationId: String) {
        sharedGl.handler.post { dlPlayer?.loadAnimation(animationId, surfaceWidth.toUInt(), surfaceHeight.toUInt()) }
    }

    fun freeze() {
        sharedGl.handler.post { dlPlayer?.pause() }
    }

    fun unFreeze() {
        sharedGl.handler.post {
            dlPlayer?.play()
            sharedGl.requestRender()
        }
    }

    fun resize(width: Int, height: Int) {
        sharedGl.handler.post {
            surfaceWidth = width
            surfaceHeight = height
            sharedGl.makeCurrent(eglSurface)
            destroyRenderFbo()
            createRenderFbo()
            dlPlayer?.resize(width.toUInt(), height.toUInt())
            dlPlayer?.let { player ->
                if (renderFboId != 0) {
                    player.setGlTarget(
                        sharedGl.eglDisplay.nativeHandle,
                        eglSurface.nativeHandle,
                        sharedGl.eglContext.nativeHandle,
                        renderFboId,
                        width.toUInt(),
                        height.toUInt()
                    )
                    glTargetDirty = false
                }
            }
            dirtyFrame = true
            sharedGl.requestRender()
        }
    }

    fun destroy() {
        sharedGl.handler.post { destroyPlayerOnGlThread() }
    }

    // ==================== Slots ====================

    fun setSlots(slots: String) {
        sharedGl.handler.post { dlPlayer?.setSlots(slots) }
    }

    fun setColorSlot(slotId: String, @ColorInt color: Int): Boolean {
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
        android.util.Log.d("DotLottie", "Adding event listener")
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
        if (isOnGlThread()) {
            return dlPlayer?.loadStateMachine(stateMachineId) ?: false
        }
        sharedGl.handler.post { dlPlayer?.loadStateMachine(stateMachineId) }
        return true
    }

    fun stateMachineLoadData(data: String): Boolean {
        if (isOnGlThread()) {
            return dlPlayer?.loadStateMachineData(data) ?: false
        }
        sharedGl.handler.post { dlPlayer?.loadStateMachineData(data) }
        return true
    }

    fun stateMachineStart(
        urlConfig: OpenUrlPolicy = createDefaultOpenUrlPolicy(),
        onOpenUrl: ((url: String) -> Unit)? = null
    ): Boolean {
        onOpenUrlCallback = onOpenUrl
        if (isOnGlThread()) {
            return startStateMachineOnGlThread(urlConfig)
        }
        sharedGl.handler.post { startStateMachineOnGlThread(urlConfig) }
        return true
    }

    private fun startStateMachineOnGlThread(urlConfig: OpenUrlPolicy): Boolean {
        val result = dlPlayer?.stateMachineStart(urlConfig) ?: false
        if (result) {
            dlPlayer?.let {
                stateMachineGestureListeners =
                    it.stateMachineFrameworkSetup().map { s -> s.lowercase() }.toSet().toMutableList()
            }
            sharedGl.requestRender()
        }
        return result
    }

    fun stateMachineStop(): Boolean {
        onOpenUrlCallback = null
        if (isOnGlThread()) {
            return dlPlayer?.stateMachineStop() ?: false
        }
        sharedGl.handler.post {
            dlPlayer?.stateMachineStop()
        }
        return true
    }

    fun stateMachinePostEvent(event: Event) {
        val eventName = event.toString().split("(").firstOrNull()?.lowercase() ?: event.toString()
        if (stateMachineGestureListeners.contains(eventName)) {
            sharedGl.handler.post { dlPlayer?.stateMachinePostEvent(event) }
        }
    }

    fun stateMachineFireEvent(event: String) {
        sharedGl.handler.post { dlPlayer?.stateMachineFireEvent(event) }
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
        val player = dlPlayer

        if (player == null || !player.stateMachineIsActive) {
            return super.onTouchEvent(event)
        }

        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = x
                lastTouchY = y
                movedTooMuch = false
                sharedGl.handler.post { player.stateMachinePostEvent(Event.PointerDown(x, y)) }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastTouchX
                val dy = y - lastTouchY
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                if (distance > touchSlop) {
                    movedTooMuch = true
                }
                sharedGl.handler.post { player.stateMachinePostEvent(Event.PointerMove(x, y)) }
            }
            MotionEvent.ACTION_UP -> {
                sharedGl.handler.post { player.stateMachinePostEvent(Event.PointerUp(x, y)) }
                if (!movedTooMuch) {
                    performClick()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                sharedGl.handler.post { player.stateMachinePostEvent(Event.PointerUp(x, y)) }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        sharedGl.handler.post { dlPlayer?.stateMachinePostEvent(Event.Click(lastTouchX, lastTouchY)) }
        return true
    }

    // ==================== View Lifecycle ====================

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        loadJob?.cancel()
        coroutineScope.cancel()
        surfaceReady = false
        sharedGl.handler.post {
            sharedGl.unregisterOnGlThread(this)
            destroyPlayerOnGlThread()
            destroyRenderFbo()
            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                sharedGl.destroyWindowSurface(eglSurface)
                eglSurface = EGL14.EGL_NO_SURFACE
            }
        }
    }

    // ==================== Internal: Player Instance Access ====================

    internal fun getPlayerInstance(): DotLottiePlayer? = dlPlayer

    internal fun getPlayerConfig(): Config? = dlConfig

    internal fun setOnPlayerCreated(callback: (DotLottiePlayer, Config) -> Unit) {
        if (dlPlayer != null && dlConfig != null) {
            callback(dlPlayer!!, dlConfig!!)
        }
        playerCreatedCallback = callback
    }

    private var playerCreatedCallback: ((DotLottiePlayer, Config) -> Unit)? = null

    private fun isOnGlThread(): Boolean {
        return Thread.currentThread() == sharedGl.handler.looper.thread
    }

    companion object {
        private const val TAG = "DotLottieGLAnimation"
    }
}
