package com.lottiefiles.dotlottie.core.util

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.hardware.HardwareBuffer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import androidx.annotation.RequiresApi
import com.dotlottie.dlplayer.Config
import com.dotlottie.dlplayer.DotLottiePlayer
import com.dotlottie.dlplayer.DotLottiePlayerEvent
import com.dotlottie.dlplayer.StateMachinePlayerEvent
import com.lottiefiles.dotlottie.core.jni.DotLottiePlayer as DotLottieJNI

/**
 * Manages GL rendering on a dedicated HandlerThread using AHardwareBuffer-backed FBOs.
 * Produces hardware-backed Bitmaps for direct Compose drawing via Skia (near-zero-copy GPU path).
 *
 * Requires API 31+ for Bitmap.wrapHardwareBuffer().
 */
@RequiresApi(Build.VERSION_CODES.S)
internal class GlHardwareRenderer {

    // Callback to deliver frames + events to the main thread
    interface FrameCallback {
        fun onFrame(bitmap: Bitmap)
        fun onPlayerEvent(event: DotLottiePlayerEvent)
        fun onStateMachineEvent(event: StateMachinePlayerEvent)
        fun onStateMachineInternalEvent(message: String)
    }

    private var frameCallback: FrameCallback? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // GL thread
    private var glThread: HandlerThread? = null
    private var glHandler: Handler? = null
    private var choreographer: Choreographer? = null

    // EGL state (created on GL thread)
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    // Double-buffered HardwareBuffers
    private var bufferWidth = 0
    private var bufferHeight = 0
    private val hwBuffers = arrayOfNulls<HardwareBuffer>(2)
    private val fboIds = IntArray(2)
    private val texIds = IntArray(2)
    private val eglImagePtrs = LongArray(2)
    private var backIndex = 0
    private var buffersValid = false

    // Player
    @Volatile
    private var dlPlayer: DotLottiePlayer? = null
    private var dlConfig: Config? = null
    var stateMachineIsActive: Boolean = false
        private set

    // Frame loop state
    private var running = false
    private var contentLoaded = false

    // Player/config ready callback (for wiring controller from main thread)
    private var onPlayerReady: ((DotLottiePlayer, Config) -> Unit)? = null

    private val choreographerCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            renderFrame()
            if (running) {
                choreographer?.postFrameCallback(this)
            }
        }
    }

    fun setFrameCallback(callback: FrameCallback) {
        frameCallback = callback
    }

    /**
     * Register a callback that fires once the player is created on the GL thread.
     * Safe to call from the main thread before start().
     */
    fun setOnPlayerReady(callback: (DotLottiePlayer, Config) -> Unit) {
        val player = dlPlayer
        val config = dlConfig
        if (player != null && config != null) {
            callback(player, config)
        } else {
            onPlayerReady = callback
        }
    }

    fun start(config: Config, threads: UInt? = null) {
        if (glThread != null) return

        dlConfig = config
        val thread = HandlerThread("DotLottieGL").apply { start() }
        glThread = thread
        val handler = Handler(thread.looper)
        glHandler = handler

        handler.post {
            initEgl()
            val player = if (threads != null) {
                DotLottiePlayer.withThreads(config, threads)
            } else {
                DotLottiePlayer(config)
            }
            dlPlayer = player
            choreographer = Choreographer.getInstance()

            // Notify main thread that player is ready
            val readyCb = onPlayerReady
            if (readyCb != null) {
                onPlayerReady = null
                mainHandler.post { readyCb(player, config) }
            }
        }
    }

    fun loadContent(content: DotLottieContent) {
        glHandler?.post {
            val player = dlPlayer ?: run {
                Log.e(TAG, "loadContent: player is null")
                return@post
            }
            if (bufferWidth <= 0 || bufferHeight <= 0) {
                Log.e(TAG, "loadContent: invalid buffer size ${bufferWidth}x${bufferHeight}")
                return@post
            }
            if (!buffersValid) {
                Log.e(TAG, "loadContent: buffers not valid")
                return@post
            }
            if (!makeCurrent()) {
                Log.e(TAG, "loadContent: makeCurrent failed")
                return@post
            }

            // Bind the back buffer FBO and set GL target
            val fbo = fboIds[backIndex]
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
            GLES20.glViewport(0, 0, bufferWidth, bufferHeight)

            val glTargetOk = player.setGlTarget(fbo, bufferWidth.toUInt(), bufferHeight.toUInt())
            if (!glTargetOk) {
                Log.e(TAG, "loadContent: setGlTarget FAILED")
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
                return@post
            }

            val loaded = when (content) {
                is DotLottieContent.Json -> {
                    player.loadAnimationData(
                        content.jsonString,
                        bufferWidth.toUInt(),
                        bufferHeight.toUInt()
                    )
                }
                is DotLottieContent.Binary -> {
                    player.loadDotlottieData(
                        content.data,
                        bufferWidth.toUInt(),
                        bufferHeight.toUInt()
                    )
                }
            }
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

            if (!loaded) {
                Log.e(TAG, "loadContent: animation load FAILED")
                return@post
            }

            contentLoaded = true

            // Start frame loop if not already running
            if (!running) {
                running = true
                choreographer?.postFrameCallback(choreographerCallback)
            }
        }
    }

    fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        glHandler?.post {
            if (width == bufferWidth && height == bufferHeight) return@post
            if (!makeCurrent()) {
                Log.e(TAG, "resize: makeCurrent failed")
                return@post
            }
            destroyBuffers()
            bufferWidth = width
            bufferHeight = height
            createBuffers()

            val player = dlPlayer ?: return@post
            player.resize(width.toUInt(), height.toUInt())
            if (buffersValid) {
                val fbo = fboIds[backIndex]
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
                GLES20.glViewport(0, 0, width, height)
                player.setGlTarget(fbo, width.toUInt(), height.toUInt())
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            }

            // If not running but content is loaded, kick the frame loop
            if (!running && contentLoaded) {
                running = true
                choreographer?.postFrameCallback(choreographerCallback)
            }
        }
    }

    fun getPlayer(): DotLottiePlayer? = dlPlayer

    fun getConfig(): Config? = dlConfig

    // ==================== Playback Control (posted to GL thread) ====================

    fun play() {
        glHandler?.post {
            dlPlayer?.play()
            ensureRunning()
        }
    }

    fun pause() {
        glHandler?.post { dlPlayer?.pause() }
    }

    fun stop() {
        glHandler?.post { dlPlayer?.stop() }
    }

    fun setFrame(frame: Float) {
        glHandler?.post {
            dlPlayer?.setFrame(frame)
            ensureRunning()
        }
    }

    fun setConfig(config: Config) {
        dlConfig = config
        glHandler?.post {
            dlPlayer?.setConfig(config)
            ensureRunning()
        }
    }

    fun stateMachineLoad(id: String) {
        glHandler?.post { dlPlayer?.loadStateMachine(id) }
    }

    fun stateMachineLoadData(data: String) {
        glHandler?.post { dlPlayer?.loadStateMachineData(data) }
    }

    fun stateMachineStart(): Boolean {
        glHandler?.post {
            val result = dlPlayer?.stateMachineStart() ?: false
            if (result) {
                stateMachineIsActive = true
                ensureRunning()
            }
        }
        return true
    }

    fun stateMachineStop() {
        glHandler?.post {
            dlPlayer?.stateMachineStop()
            stateMachineIsActive = false
        }
    }

    fun stateMachinePostEvent(event: com.dotlottie.dlplayer.Event) {
        glHandler?.post { dlPlayer?.stateMachinePostEvent(event) }
    }

    fun stateMachineFireEvent(event: String) {
        glHandler?.post { dlPlayer?.stateMachineFireEvent(event) }
    }

    fun requestRender() {
        glHandler?.post { ensureRunning() }
    }

    // ==================== Lifecycle ====================

    fun stopRendering() {
        running = false
        glHandler?.post {
            choreographer?.removeFrameCallback(choreographerCallback)
        }
    }

    fun release() {
        running = false
        val handler = glHandler ?: return
        handler.post {
            choreographer?.removeFrameCallback(choreographerCallback)
            stateMachineIsActive = false

            dlPlayer?.destroy()
            dlPlayer = null

            destroyBuffers()
            destroyEgl()

            glThread?.quitSafely()
            glThread = null
            glHandler = null
            choreographer = null
        }
    }

    // ==================== Private: EGL ====================

    private fun initEgl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            Log.e(TAG, "eglGetDisplay failed")
            return
        }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            Log.e(TAG, "eglInitialize failed")
            return
        }

        // ThorVG GL engine requires OpenGL ES 3.0+
        val EGL_OPENGL_ES3_BIT = 0x0040
        val configAttribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)
        if (numConfigs[0] == 0 || configs[0] == null) {
            Log.e(TAG, "eglChooseConfig failed")
            return
        }

        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(
            eglDisplay, configs[0]!!, EGL14.EGL_NO_CONTEXT, contextAttribs, 0
        )
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            Log.e(TAG, "eglCreateContext failed: 0x${Integer.toHexString(EGL14.eglGetError())}")
            return
        }

        val pbufferAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, configs[0]!!, pbufferAttribs, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            Log.e(TAG, "eglCreatePbufferSurface failed: 0x${Integer.toHexString(EGL14.eglGetError())}")
            return
        }

        makeCurrent()
    }

    private fun makeCurrent(): Boolean {
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            Log.e(TAG, "makeCurrent: no display")
            return false
        }
        val ok = EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        if (!ok) {
            Log.e(TAG, "makeCurrent failed: EGL error 0x${Integer.toHexString(EGL14.eglGetError())}")
        }
        return ok
    }

    private fun destroyEgl() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
            )
            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
            }
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
            }
            EGL14.eglTerminate(eglDisplay)
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }

    // ==================== Private: HardwareBuffer Management ====================

    private fun createBuffers() {
        if (bufferWidth <= 0 || bufferHeight <= 0) return
        buffersValid = false

        for (i in 0..1) {
            val hwBuffer = HardwareBuffer.create(
                bufferWidth, bufferHeight,
                HardwareBuffer.RGBA_8888, 1,
                HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or
                        HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE
            )
            hwBuffers[i] = hwBuffer

            val result = DotLottieJNI.nativeCreateFboFromHardwareBuffer(hwBuffer)
            if (result != null && result.size == 4) {
                fboIds[i] = result[0]
                texIds[i] = result[1]
                eglImagePtrs[i] = (result[2].toLong() shl 32) or
                        (result[3].toLong() and 0xFFFFFFFFL)
            } else {
                Log.e(TAG, "Failed to create FBO from HardwareBuffer[$i], result=$result")
                destroyBuffers()
                return
            }
        }
        backIndex = 0
        buffersValid = true
    }

    private fun destroyBuffers() {
        buffersValid = false
        for (i in 0..1) {
            if (fboIds[i] != 0 || texIds[i] != 0 || eglImagePtrs[i] != 0L) {
                DotLottieJNI.nativeDestroyFboResources(fboIds[i], texIds[i], eglImagePtrs[i])
            }
            fboIds[i] = 0
            texIds[i] = 0
            eglImagePtrs[i] = 0L

            hwBuffers[i]?.close()
            hwBuffers[i] = null
        }
    }

    // ==================== Private: Frame Rendering ====================

    private var frameCount = 0

    private fun renderFrame() {
        val player = dlPlayer ?: return
        if (bufferWidth <= 0 || bufferHeight <= 0) return
        if (!buffersValid) return
        if (!makeCurrent()) return

        // Bind back buffer FBO and set GL target
        val fbo = fboIds[backIndex]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glViewport(0, 0, bufferWidth, bufferHeight)
        player.setGlTarget(fbo, bufferWidth.toUInt(), bufferHeight.toUInt())

        // Tick the player
        val ticked = if (stateMachineIsActive) {
            player.stateMachineTick()
        } else {
            player.tick()
        }

        // Poll and dispatch events
        pollEvents(player)

        if (ticked) {
            // GPU sync — ensure all rendering to the FBO is complete
            DotLottieJNI.nativeGlFinish()
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

            // Swap front/back
            val frontIndex = backIndex
            backIndex = 1 - backIndex

            // Wrap front buffer as hardware Bitmap
            val frontBuffer = hwBuffers[frontIndex]
            if (frontBuffer == null) {
                Log.e(TAG, "renderFrame: frontBuffer[$frontIndex] is null")
                return
            }
            val hwBitmap = Bitmap.wrapHardwareBuffer(
                frontBuffer,
                ColorSpace.get(ColorSpace.Named.SRGB)
            )
            if (hwBitmap == null) {
                Log.e(TAG, "renderFrame: wrapHardwareBuffer returned null")
                return
            }

            frameCount++

            // Deliver to main thread
            val cb = frameCallback ?: return
            mainHandler.post { cb.onFrame(hwBitmap) }
        } else {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        }

        // Only stop looping if content is loaded but nothing is animating.
        val shouldKeepRunning = player.isPlaying() || stateMachineIsActive || !player.isLoaded()
        if (!shouldKeepRunning && frameCount > 0) {
            running = false
        }
    }

    private fun ensureRunning() {
        if (!running && contentLoaded) {
            running = true
            choreographer?.postFrameCallback(choreographerCallback)
        }
    }

    private fun pollEvents(player: DotLottiePlayer) {
        val cb = frameCallback ?: return

        // Player events
        var event = player.pollEvent()
        while (event != null) {
            val e = event
            mainHandler.post { cb.onPlayerEvent(e) }
            event = player.pollEvent()
        }

        // State machine events
        var smEvent = player.stateMachinePollEvent()
        while (smEvent != null) {
            val e = smEvent
            mainHandler.post { cb.onStateMachineEvent(e) }
            smEvent = player.stateMachinePollEvent()
        }

        // Internal events
        var internalEvent = player.stateMachinePollInternalEvent()
        while (internalEvent != null) {
            val e = internalEvent
            mainHandler.post { cb.onStateMachineInternalEvent(e) }
            internalEvent = player.stateMachinePollInternalEvent()
        }
    }

    companion object {
        private const val TAG = "GlHardwareRenderer"
    }
}
