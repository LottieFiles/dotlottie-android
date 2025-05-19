package com.lottiefiles.sample.performance

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

/**
 * Monitors and tracks performance metrics for Lottie animations.
 * Tracks: FPS, memory usage, CPU usage, and frame jank percentage.
 */
class PerformanceMonitor(context: Context) {
    private val contextRef = WeakReference(context)
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cpuMonitor = CpuMonitor(context)
    
    // Thread safety
    private val isMonitoring = AtomicBoolean(false)
    private val frameCount = AtomicInteger(0)
    private val jankyFrames = AtomicInteger(0)
    
    // Monitoring thread management
    private var metricsExecutor: ScheduledExecutorService? = null
    private var metricsUpdateTask: ScheduledFuture<*>? = null
    
    // Frame timing
    private var frameCallback: Choreographer.FrameCallback? = null
    private var lastFrameTimeNanos: Long = 0
    private var totalFrameTime: Long = 0
    
    // Target frame rate
    private val targetFrameTimeNanos = (1000000000 / 60.0).toLong() // 16.7ms for 60fps
    private var fpsUpdateInterval = 1000L // Update FPS every second
    
    // Current metrics
    private var currentFps: Float = 0f
    private var currentMemoryUsageMb: Float = 0f
    private var currentJankPercentage: Float = 0f
    private var currentCpuUsage: Float = 0f
    
    private val listeners = mutableListOf<PerformanceListener>()
    
    /**
     * Start monitoring performance metrics
     */
    fun startMonitoring() {
        if (isMonitoring.getAndSet(true)) return
        
        // Reset counters
        frameCount.set(0)
        jankyFrames.set(0)
        lastFrameTimeNanos = System.nanoTime()
        totalFrameTime = 0
        
        // Start CPU monitoring
        cpuMonitor.startMonitoring()
        
        // Create a new executor if needed
        if (metricsExecutor == null || metricsExecutor?.isShutdown == true) {
            metricsExecutor = Executors.newSingleThreadScheduledExecutor()
        }
        
        // Start frame monitoring
        setupFrameCallback()
        
        // Schedule periodic updates on a background thread
        metricsUpdateTask = metricsExecutor?.scheduleWithFixedDelay(
            { updateMetrics() },
            0,
            fpsUpdateInterval,
            TimeUnit.MILLISECONDS
        )
    }
    
    /**
     * Stop monitoring performance metrics
     */
    fun stopMonitoring() {
        if (!isMonitoring.getAndSet(false)) return
        
        // Remove frame callback
        frameCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        frameCallback = null
        
        // Stop CPU monitoring
        cpuMonitor.stopMonitoring()
        
        // Cancel and shutdown the executor safely
        metricsUpdateTask?.cancel(false)
        metricsExecutor?.apply {
            shutdown()
            try {
                if (!awaitTermination(1, TimeUnit.SECONDS)) {
                    shutdownNow()
                }
            } catch (e: InterruptedException) {
                shutdownNow()
            }
        }
        metricsExecutor = null
    }
    
    /**
     * Setup the Choreographer frame callback
     */
    private fun setupFrameCallback() {
        frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
            val frameTimeDiff = frameTimeNanos - lastFrameTimeNanos
            if (lastFrameTimeNanos > 0 && frameTimeDiff > 0) {
                frameCount.incrementAndGet()
                totalFrameTime += frameTimeDiff
                
                // Frame is considered janky if it took more than 150% of the target frame time
                if (frameTimeDiff > (targetFrameTimeNanos * 1.5)) {
                    jankyFrames.incrementAndGet()
                }
            }
            
            lastFrameTimeNanos = frameTimeNanos
            
            // Only post next frame callback if still monitoring
            if (isMonitoring.get()) {
                try {
                    Choreographer.getInstance().postFrameCallback(frameCallback!!)
                } catch (e: Exception) {
                    // Handle potential choreographer issues
                }
            }
        }
        
        // Post initial frame callback on main thread
        mainHandler.post {
            try {
                Choreographer.getInstance().postFrameCallback(frameCallback!!)
            } catch (e: Exception) {
                // Handle potential choreographer issues
            }
        }
    }
    
    /**
     * Add a listener to receive performance metric updates
     */
    fun addListener(listener: PerformanceListener) {
        synchronized(listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
            }
        }
    }
    
    /**
     * Remove a performance listener
     */
    fun removeListener(listener: PerformanceListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }
    
    /**
     * Update metrics and notify listeners
     */
    private fun updateMetrics() {
        if (!isMonitoring.get()) return
        
        try {
            // Calculate FPS
            val currentFrameCount = frameCount.getAndSet(0)
            val elapsedSeconds = totalFrameTime / 1_000_000_000.0f
            currentFps = if (elapsedSeconds > 0) currentFrameCount / elapsedSeconds else 0f
            currentFps = min(currentFps, 60f) // Cap at 60fps
            
            // Reset frame time counter
            totalFrameTime = 0
            
            // Calculate jank percentage
            val currentJankyCount = jankyFrames.getAndSet(0)
            currentJankPercentage = if (currentFrameCount > 0) {
                (currentJankyCount.toFloat() / currentFrameCount) * 100f
            } else {
                0f
            }
            
            // Get CPU usage
            currentCpuUsage = cpuMonitor.getCpuUsage()
            
            // Calculate memory usage
            val memInfo = ActivityManager.MemoryInfo()
            contextRef.get()?.let { context ->
                (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memInfo)
                val nativeHeapSize = memInfo.totalMem - memInfo.availMem
                currentMemoryUsageMb = nativeHeapSize / (1024f * 1024f)
            }
            
            // Notify listeners on main thread
            mainHandler.post { notifyListeners() }
        } catch (e: Exception) {
            // Handle any errors during metrics update
        }
    }
    
    /**
     * Notify all listeners of updated metrics
     */
    private fun notifyListeners() {
        val metrics = PerformanceMetrics(
            fps = currentFps,
            memoryUsageMb = currentMemoryUsageMb,
            jankPercentage = currentJankPercentage,
            cpuUsage = currentCpuUsage
        )
        
        synchronized(listeners) {
            listeners.forEach { it.onMetricsUpdated(metrics) }
        }
    }
    
    /**
     * Get current performance metrics
     */
    fun getCurrentMetrics(): PerformanceMetrics {
        return PerformanceMetrics(
            fps = currentFps,
            memoryUsageMb = currentMemoryUsageMb,
            jankPercentage = currentJankPercentage,
            cpuUsage = currentCpuUsage
        )
    }
    
    /**
     * Performance metrics data class
     */
    data class PerformanceMetrics(
        val fps: Float,
        val memoryUsageMb: Float,
        val jankPercentage: Float,
        val cpuUsage: Float = 0f
    )
    
    /**
     * Interface for performance metric updates
     */
    interface PerformanceListener {
        fun onMetricsUpdated(metrics: PerformanceMetrics)
    }
} 