package com.lottiefiles.dotlottie.core.util

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.annotation.RequiresApi
import java.util.concurrent.CountDownLatch

/**
 * Singleton managing a single shared GL thread and EGL 3.0 context for all
 * HardwareBuffer-based GL renderers. Reference-counted: the thread and context
 * are created on the first acquire() and destroyed when the last release() fires.
 *
 * By funnelling every renderer onto one thread/context we eliminate the
 * ThorVG GlProgram::mCurrentProgram static-cache race that causes flickering
 * when multiple renderers run on separate threads with separate EGL contexts.
 */
@RequiresApi(Build.VERSION_CODES.S)
internal object SharedGlThread {

    private const val TAG = "SharedGlThread"

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var refCount = 0

    /**
     * Acquire a reference to the shared GL thread.
     * If this is the first reference, the thread and EGL context are created
     * synchronously (blocks until EGL init completes on the GL thread).
     *
     * @return the shared [Handler] for posting work to the GL thread.
     */
    @Synchronized
    fun acquire(): Handler {
        if (refCount == 0) {
            val t = HandlerThread("DotLottieSharedGL").apply { start() }
            thread = t
            val h = Handler(t.looper)
            handler = h

            val latch = CountDownLatch(1)
            h.post {
                initEgl()
                latch.countDown()
            }
            latch.await()
        }
        refCount++
        return handler!!
    }

    /**
     * Release a reference. When the last reference is released the EGL context
     * is torn down and the thread is stopped.
     */
    @Synchronized
    fun release() {
        refCount--
        if (refCount <= 0) {
            refCount = 0
            val h = handler
            val t = thread
            val display = eglDisplay
            val context = eglContext
            val surface = eglSurface

            // Clear shared fields immediately so a subsequent acquire() starts fresh
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            eglSurface = EGL14.EGL_NO_SURFACE
            handler = null
            thread = null

            // Post EGL teardown to the GL thread using captured references
            h?.post {
                if (display != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(
                        display,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_CONTEXT
                    )
                    if (surface != EGL14.EGL_NO_SURFACE) {
                        EGL14.eglDestroySurface(display, surface)
                    }
                    if (context != EGL14.EGL_NO_CONTEXT) {
                        EGL14.eglDestroyContext(display, context)
                    }
                    EGL14.eglTerminate(display)
                }
                t?.quitSafely()
            }
        }
    }

    /**
     * Make the shared EGL context current on the calling thread.
     * Must be called from the shared GL thread.
     */
    fun makeCurrent(): Boolean {
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
        Log.d(TAG, "EGL initialized: ES 3.0 context ready")
    }
}
