package com.lottiefiles.example.core.util

import android.util.Log
import com.lottiefiles.dotlottie.core.util.DotLottieEventListener

/**
 * Performance-focused DotLottie event listener following best practices from main branch
 * Provides comprehensive event monitoring for benchmark and performance testing
 */
class PerformanceDotLottieEventListener(
    private val tag: String = "PerformanceDotLottie",
    private val onFrameCallback: ((Float) -> Unit)? = null,
    private val onLoadCallback: (() -> Unit)? = null,
    private val onErrorCallback: ((String) -> Unit)? = null
) : DotLottieEventListener {
    
    private var loadStartTime: Long = 0
    private var frameCount: Int = 0
    private var lastFrameTime: Long = 0
    
    override fun onLoad() {
        val loadTime = System.currentTimeMillis() - loadStartTime
        Log.i(tag, "Animation loaded successfully in ${loadTime}ms")
        onLoadCallback?.invoke()
    }

    override fun onPause() {
        Log.d(tag, "Animation paused at frame $frameCount")
    }

    override fun onPlay() {
        Log.d(tag, "Animation started playing")
        lastFrameTime = System.currentTimeMillis()
        frameCount = 0
    }

    override fun onStop() {
        Log.d(tag, "Animation stopped after $frameCount frames")
    }

    override fun onComplete() {
        Log.d(tag, "Animation completed after $frameCount frames")
    }

    override fun onFreeze() {
        Log.d(tag, "Animation frozen for performance optimization")
    }

    override fun onUnFreeze() {
        Log.d(tag, "Animation unfrozen and resumed")
    }

    override fun onFrame(frame: Float) {
        frameCount++
        val currentTime = System.currentTimeMillis()
        
        // Log frame performance every 60 frames to avoid spam
        if (frameCount % 60 == 0) {
            val timeDiff = currentTime - lastFrameTime
            val fps = if (timeDiff > 0) 60000.0 / timeDiff else 0.0
            Log.v(tag, "Frame $frame - FPS: %.1f (${frameCount} total frames)".format(fps))
            lastFrameTime = currentTime
        }
        
        onFrameCallback?.invoke(frame)
    }

    override fun onLoadError() {
        val errorMsg = "Failed to load animation"
        Log.e(tag, errorMsg)
        onErrorCallback?.invoke(errorMsg)
    }

    override fun onLoadError(error: Throwable) {
        Log.e(tag, "Load error: ${error.message}", error)
        onErrorCallback?.invoke("Load error: ${error.message}")
    }

    override fun onError(error: Throwable) {
        Log.e(tag, "General error: ${error.message}", error)
        onErrorCallback?.invoke("Error: ${error.message}")
    }

    override fun onDestroy() {
        Log.d(tag, "Animation destroyed - cleanup completed")
    }

    override fun onRender(frameNo: Float) {
        // Called during rendering - can be used for render performance tracking
        // Log.v(tag, "Render frame: $frameNo")
    }

    override fun onLoop(loopCount: Int) {
        Log.d(tag, "Animation loop completed - Loop count: $loopCount")
    }

    /**
     * Call this when starting to load an animation to track load time
     */
    fun startLoadTracking() {
        loadStartTime = System.currentTimeMillis()
        Log.d(tag, "Started loading animation...")
    }

    /**
     * Get performance summary
     */
    fun getPerformanceSummary(): String {
        return "Animation Performance - Total frames: $frameCount"
    }
}
