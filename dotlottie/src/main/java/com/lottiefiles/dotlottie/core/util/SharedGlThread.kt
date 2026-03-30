package com.lottiefiles.dotlottie.core.util

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Choreographer

/**
 * Singleton shared GL thread that owns a single EGL context.
 * All DotLottieGLAnimation instances render sequentially on this thread,
 * avoiding ThorVG's thread-safety issues without needing locks or glFinish().
 */
internal class SharedGlThread private constructor() {

    private val handlerThread = HandlerThread("DotLottieSharedGL").apply { start() }
    val handler: Handler = Handler(handlerThread.looper)

    var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private set
    var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private set
    private var eglConfig: EGLConfig? = null
    private var eglPbuffer: EGLSurface = EGL14.EGL_NO_SURFACE

    private val clients = mutableListOf<RenderClient>()
    private var choreographerRunning = false
    private var choreographer: Choreographer? = null

    /** Tracks which client last rendered, so clients can skip redundant setGlTarget calls. */
    var lastRenderedClient: RenderClient? = null
        private set

    interface RenderClient {
        /** Called on the shared GL thread during each choreographer frame. */
        fun onRenderFrame()
        /** Whether this client needs rendering. Checked each frame. */
        fun shouldRender(): Boolean
    }

    init {
        handler.post {
            initEgl()
            choreographer = Choreographer.getInstance()
        }
    }

    fun createWindowSurface(surfaceTexture: SurfaceTexture): EGLSurface {
        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        val surface = EGL14.eglCreateWindowSurface(
            eglDisplay, eglConfig, surfaceTexture, surfaceAttribs, 0
        )
        if (surface == EGL14.EGL_NO_SURFACE) {
            Log.e(TAG, "eglCreateWindowSurface failed: 0x${Integer.toHexString(EGL14.eglGetError())}")
        }
        return surface
    }

    fun destroyWindowSurface(surface: EGLSurface) {
        if (surface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(eglDisplay, surface)
        }
    }

    fun makeCurrent(surface: EGLSurface): Boolean {
        return EGL14.eglMakeCurrent(eglDisplay, surface, surface, eglContext)
    }

    /** Make the internal PBuffer surface current. For clients that render to FBOs only. */
    fun makeDefaultCurrent(): Boolean {
        return makeCurrent(eglPbuffer)
    }

    /** Native handle of the internal PBuffer surface, for passing to setGlTarget. */
    val eglPbufferNativeHandle: Long
        get() = eglPbuffer.nativeHandle

    fun swapBuffers(surface: EGLSurface): Boolean {
        return EGL14.eglSwapBuffers(eglDisplay, surface)
    }

    fun register(client: RenderClient) {
        handler.post {
            if (!clients.contains(client)) {
                clients.add(client)
            }
            startChoreographerIfNeeded()
        }
    }

    /** Remove client from the render list. Must be called on the GL thread. */
    fun unregisterOnGlThread(client: RenderClient) {
        clients.remove(client)
        if (clients.isEmpty()) {
            stopChoreographer()
            makeCurrent(eglPbuffer)
        }
    }

    /** Kick the choreographer if there are active clients. */
    fun requestRender() {
        handler.post { startChoreographerIfNeeded() }
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!choreographerRunning) return

            var anyActive = false
            for (client in clients) {
                if (client.shouldRender()) {
                    anyActive = true
                    client.onRenderFrame()
                    lastRenderedClient = client
                }
            }

            if (anyActive || clients.any { it.shouldRender() }) {
                choreographer?.postFrameCallback(this)
            } else {
                choreographerRunning = false
            }
        }
    }

    private fun startChoreographerIfNeeded() {
        if (!choreographerRunning && clients.any { it.shouldRender() }) {
            choreographerRunning = true
            choreographer?.postFrameCallback(frameCallback)
        }
    }

    private fun stopChoreographer() {
        choreographerRunning = false
        choreographer?.removeFrameCallback(frameCallback)
    }

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

        val EGL_OPENGL_ES3_BIT = 0x0040
        val configAttribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)
        if (numConfigs[0] == 0 || configs[0] == null) {
            Log.e(TAG, "eglChooseConfig failed")
            return
        }
        eglConfig = configs[0]

        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(
            eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0
        )
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            Log.e(TAG, "eglCreateContext failed: 0x${Integer.toHexString(EGL14.eglGetError())}")
            return
        }

        val pbufferAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
        eglPbuffer = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, pbufferAttribs, 0)
        if (eglPbuffer == EGL14.EGL_NO_SURFACE) {
            Log.e(TAG, "eglCreatePbufferSurface failed: 0x${Integer.toHexString(EGL14.eglGetError())}")
            return
        }
        makeCurrent(eglPbuffer)
    }

    companion object {
        private const val TAG = "SharedGlThread"
        val instance: SharedGlThread by lazy { SharedGlThread() }
    }
}
