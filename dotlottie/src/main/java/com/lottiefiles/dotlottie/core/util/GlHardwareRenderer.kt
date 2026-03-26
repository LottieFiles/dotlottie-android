package com.lottiefiles.dotlottie.core.util

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.hardware.HardwareBuffer
import android.opengl.GLES20
import android.opengl.GLES30
import android.os.Build
import android.os.Handler
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
 * Manages GL rendering on a shared GL thread (via [SharedGlThread]) using
 * AHardwareBuffer-backed FBOs. Produces hardware-backed Bitmaps for direct
 * Compose drawing via Skia (near-zero-copy GPU path).
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

    // Shared GL thread
    private var glHandler: Handler? = null
    private var choreographer: Choreographer? = null

    // Double-buffered HardwareBuffers
    private var bufferWidth = 0
    private var bufferHeight = 0
    private val hwBuffers = arrayOfNulls<HardwareBuffer>(2)
    private val fboIds = IntArray(2)
    private val texIds = IntArray(2)
    private val eglImagePtrs = LongArray(2)
    private var backIndex = 0
    private var buffersValid = false

    // Intermediate render FBO (texture-backed, ThorVG renders into this)
    private var renderFboId = 0
    private var renderTexId = 0

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
        if (glHandler != null) return

        dlConfig = config
        val handler = SharedGlThread.acquire()
        glHandler = handler

        handler.post {
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
            if (!SharedGlThread.makeCurrent()) {
                Log.e(TAG, "loadContent: makeCurrent failed")
                return@post
            }

            // Create intermediate render FBO
            destroyRenderFbo()
            createRenderFbo()
            if (renderFboId == 0) {
                Log.e(TAG, "loadContent: createRenderFbo failed")
                return@post
            }

            // Set GL target to render FBO once (not per-frame)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFboId)
            GLES20.glViewport(0, 0, bufferWidth, bufferHeight)

            val glTargetOk = player.setGlTarget(renderFboId, bufferWidth.toUInt(), bufferHeight.toUInt())
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
            if (!SharedGlThread.makeCurrent()) {
                Log.e(TAG, "resize: makeCurrent failed")
                return@post
            }
            destroyRenderFbo()
            destroyBuffers()
            bufferWidth = width
            bufferHeight = height
            createBuffers()
            createRenderFbo()

            val player = dlPlayer ?: return@post
            player.resize(width.toUInt(), height.toUInt())
            if (buffersValid && renderFboId != 0) {
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFboId)
                GLES20.glViewport(0, 0, width, height)
                player.setGlTarget(renderFboId, width.toUInt(), height.toUInt())
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
        glHandler = null
        handler.post {
            choreographer?.removeFrameCallback(choreographerCallback)
            stateMachineIsActive = false

            dlPlayer?.destroy()
            dlPlayer = null

            destroyRenderFbo()
            destroyBuffers()
            choreographer = null
        }
        SharedGlThread.release()
    }

    // ==================== Private: Intermediate Render FBO ====================

    private fun createRenderFbo() {
        if (bufferWidth <= 0 || bufferHeight <= 0) return

        val texArr = IntArray(1)
        GLES20.glGenTextures(1, texArr, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texArr[0])
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            bufferWidth, bufferHeight, 0,
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
            Log.e(TAG, "createRenderFbo: incomplete, status=0x${Integer.toHexString(status)}")
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
        if (renderFboId == 0) return
        if (!SharedGlThread.makeCurrent()) return

        // Tick the player — ThorVG renders into renderFbo (set once via setGlTarget)
        val ticked = if (stateMachineIsActive) {
            player.stateMachineTick()
        } else {
            player.tick()
        }

        // Poll and dispatch events
        pollEvents(player)

        if (ticked) {
            // Blit from intermediate renderFbo to the back HardwareBuffer FBO
            GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, renderFboId)
            GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, fboIds[backIndex])
            GLES30.glBlitFramebuffer(
                0, 0, bufferWidth, bufferHeight,
                0, 0, bufferWidth, bufferHeight,
                GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_NEAREST
            )
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

            // GPU sync — ensure blit to HardwareBuffer is complete
            DotLottieJNI.nativeGlFinish()

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
