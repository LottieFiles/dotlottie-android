package com.lottiefiles.sample.performance

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import java.lang.ref.WeakReference
import kotlin.math.min

/**
 * Monitors and tracks performance metrics for Lottie animations.
 * Tracks: FPS, memory usage, and frame jank percentage.
 */
class PerformanceMonitor(context: Context) {
    private val contextRef = WeakReference(context)
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val handler = Handler(Looper.getMainLooper())
    
    private var frameCallback: Choreographer.FrameCallback? = null
    private var lastFrameTimeNanos: Long = 0
    private var frameCount: Int = 0
    private var totalFrameTime: Long = 0
    private var jankyFrames: Int = 0
    
    private val targetFrameTimeNanos = (1000000000 / 60.0).toLong() // 16.7ms for 60fps
    private var fpsUpdateInterval = 1000L // Update FPS every second
    
    private var currentFps: Float = 0f
    private var currentMemoryUsageMb: Float = 0f
    private var currentJankPercentage: Float = 0f
    
    private var isMonitoring = false
    private var listeners = mutableListOf<PerformanceListener>()
    
    /**
     * Start monitoring performance metrics
     */
    fun startMonitoring() {
        if (isMonitoring) return
        
        isMonitoring = true
        lastFrameTimeNanos = System.nanoTime()
        frameCount = 0
        totalFrameTime = 0
        jankyFrames = 0
        
        frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
            val frameTimeDiff = frameTimeNanos - lastFrameTimeNanos
            if (lastFrameTimeNanos > 0 && frameTimeDiff > 0) {
                frameCount++
                totalFrameTime += frameTimeDiff
                
                // Frame is considered janky if it took more than 150% of the target frame time
                if (frameTimeDiff > (targetFrameTimeNanos * 1.5)) {
                    jankyFrames++
                }
            }
            
            lastFrameTimeNanos = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(frameCallback!!)
        }
        
        Choreographer.getInstance().postFrameCallback(frameCallback!!)
        
        // Schedule periodic updates
        handler.post(object : Runnable {
            override fun run() {
                if (!isMonitoring) return
                
                updateMetrics()
                handler.postDelayed(this, fpsUpdateInterval)
            }
        })
    }
    
    /**
     * Stop monitoring performance metrics
     */
    fun stopMonitoring() {
        if (!isMonitoring) return
        
        isMonitoring = false
        frameCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        frameCallback = null
    }
    
    /**
     * Add a listener to receive performance metric updates
     */
    fun addListener(listener: PerformanceListener) {
        listeners.add(listener)
    }
    
    /**
     * Remove a performance listener
     */
    fun removeListener(listener: PerformanceListener) {
        listeners.remove(listener)
    }
    
    private fun updateMetrics() {
        // Calculate FPS
        val elapsedSeconds = totalFrameTime / 1_000_000_000.0f
        currentFps = if (elapsedSeconds > 0) frameCount / elapsedSeconds else 0f
        currentFps = min(currentFps, 60f) // Cap at 60fps which is the Android display refresh rate
        
        // Reset counters for next interval
        frameCount = 0
        totalFrameTime = 0
        
        // Calculate jank percentage
        currentJankPercentage = if (frameCount > 0) {
            (jankyFrames.toFloat() / frameCount) * 100f
        } else {
            0f
        }
        jankyFrames = 0
        
        // Calculate memory usage
        val memInfo = ActivityManager.MemoryInfo()
        contextRef.get()?.let { context ->
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memInfo)
            val nativeHeapSize = memInfo.totalMem - memInfo.availMem
            currentMemoryUsageMb = nativeHeapSize / (1024f * 1024f)
        }
        
        // Notify listeners
        notifyListeners()
    }
    
    private fun notifyListeners() {
        val metrics = PerformanceMetrics(
            fps = currentFps,
            memoryUsageMb = currentMemoryUsageMb,
            jankPercentage = currentJankPercentage
        )
        
        listeners.forEach { it.onMetricsUpdated(metrics) }
    }
    
    /**
     * Get current performance metrics
     */
    fun getCurrentMetrics(): PerformanceMetrics {
        return PerformanceMetrics(
            fps = currentFps,
            memoryUsageMb = currentMemoryUsageMb,
            jankPercentage = currentJankPercentage
        )
    }
    
    /**
     * Performance metrics data class
     */
    data class PerformanceMetrics(
        val fps: Float,
        val memoryUsageMb: Float,
        val jankPercentage: Float
    )
    
    /**
     * Interface for performance metric updates
     */
    interface PerformanceListener {
        fun onMetricsUpdated(metrics: PerformanceMetrics)
    }
} 