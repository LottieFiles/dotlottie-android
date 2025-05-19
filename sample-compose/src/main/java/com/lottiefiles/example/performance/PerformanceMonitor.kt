package com.lottiefiles.example.performance

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.view.Choreographer
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import java.text.DecimalFormat
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A utility class to monitor performance metrics for DotLottie animations.
 */
class PerformanceMonitor {
    companion object {
        private const val TAG = "PerformanceMonitor"
        
        /**
         * Enable hardware acceleration for better performance
         */
        fun enableHardwareAcceleration(window: Window) {
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
        }
    }

    private val frameStartTimeNs = AtomicLong(0)
    private var framesRendered = 0
    private var lastFpsUpdate = 0L
    private var fpsCallback: ((Float) -> Unit)? = null
    private var metricsCallback: ((PerformanceMetrics) -> Unit)? = null
    private val isMonitoring = AtomicBoolean(false)
    private val fpsUpdateIntervalMs = 1000L  // Update FPS every second
    private val handler = Handler(Looper.getMainLooper())
    private val df = DecimalFormat("#.##")

    // Lock for accessing shared state
    private val lock = ReentrantLock()

    // Frame time history for jank detection
    private val frameTimeHistory = mutableListOf<Long>()
    private val maxFrameTimeHistorySize = 120 // 2 seconds at 60 FPS
    private var totalFrameTime = 0L
    
    // CPU usage tracking
    private var cpuExecutor: ScheduledExecutorService? = null
    private val cpuUsage = AtomicReference(0f)
    private var lastCpuUsageTotal = 0L
    private var lastCpuUsageApp = 0L
    
    // CPU usage method - we'll try different methods based on Android version and permissions
    private var cpuUsageMethod = CpuUsageMethod.ACTIVITY_MANAGER
    private var activityManager: ActivityManager? = null

    // Rate limiting for CPU updates
    private var lastCpuUpdateTime = AtomicLong(0L)
    private val MIN_CPU_UPDATE_INTERVAL_MS = 500 // Minimum 500ms between updates

    // Add these properties to track rapid executions
    // Task burst detection
    private var taskExecutionCount = AtomicLong(0)
    private var lastTaskCountResetTime = AtomicLong(System.currentTimeMillis())
    private val TASK_BURST_THRESHOLD = 5 // Consider 5+ tasks in 1 second as a burst
    private val TASK_COUNT_RESET_INTERVAL_MS = 1000 // Reset task count every second

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isMonitoring.get()) return
            
            val currentStartTimeNs = frameStartTimeNs.get()
            if (currentStartTimeNs > 0) {
                val frameDurationNs = frameTimeNanos - currentStartTimeNs
                lock.withLock {
                    frameTimeHistory.add(frameDurationNs)
                    if (frameTimeHistory.size > maxFrameTimeHistorySize) {
                        frameTimeHistory.removeAt(0)
                    }
                }
            }

            frameStartTimeNs.set(frameTimeNanos)
            
            lock.withLock {
                framesRendered++
            }

            val currentTimeMs = System.currentTimeMillis()
            if (currentTimeMs - lastFpsUpdate >= fpsUpdateIntervalMs) {
                val elapsedSeconds = (currentTimeMs - lastFpsUpdate) / 1000f
                val localFramesRendered: Int
                
                lock.withLock {
                    localFramesRendered = framesRendered
                    framesRendered = 0
                    totalFrameTime = 0
                    lastFpsUpdate = currentTimeMs
                }
                
                val fps = localFramesRendered / elapsedSeconds

                fpsCallback?.invoke(fps)

                // Log jank metrics
                val jankMetrics = calculateJankMetrics()
                Log.d(TAG, "FPS: ${df.format(fps)}, Jank %: ${jankMetrics.jankPercentage}%, Long frames: ${jankMetrics.longFrameCount}")
            }

            if (isMonitoring.get()) {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    data class JankMetrics(
        val jankPercentage: Float,
        val longFrameCount: Int
    )

    /**
     * Calculate jank metrics based on frame time history
     */
    private fun calculateJankMetrics(): JankMetrics {
        return lock.withLock {
            if (frameTimeHistory.isEmpty()) return JankMetrics(0f, 0)

            // Frames taking longer than 16.67ms (60 FPS) are considered jank
            val targetFrameTimeNs = 16_666_666L
            val longFrames = frameTimeHistory.count { it > targetFrameTimeNs }

            val jankPercentage = (longFrames.toFloat() / frameTimeHistory.size) * 100

            JankMetrics(
                jankPercentage = jankPercentage,
                longFrameCount = longFrames
            )
        }
    }

    /**
     * Start monitoring performance with FPS callback
     */
    fun startMonitoringWithFpsCallback(fpsListener: (Float) -> Unit) {
        this.fpsCallback = fpsListener
        startMonitoring()
    }
    
    /**
     * Set a callback for FPS updates
     */
    fun setFpsCallback(callback: (Float) -> Unit) {
        this.fpsCallback = callback
    }

    /**
     * Start monitoring performance with full metrics callback
     */
    fun startMonitoringWithMetricsCallback(metricsListener: (PerformanceMetrics) -> Unit) {
        this.metricsCallback = metricsListener
        startMonitoring()
    }

    /**
     * Initialize the performance monitor with a context
     * This will allow us to use ActivityManager for CPU usage
     */
    fun initialize(context: Context) {
        // Get the ActivityManager service for CPU monitoring
        activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    }

    /**
     * Start monitoring performance
     */
    fun startMonitoring() {
        // Use compareAndSet to safely handle concurrent calls
        if (isMonitoring.compareAndSet(false, true)) {
            // Ensure this runs on the main thread since Choreographer requires a Looper
            if (Looper.myLooper() == Looper.getMainLooper()) {
                startMonitoringOnMainThread()
            } else {
                handler.post {
                    startMonitoringOnMainThread()
                }
            }
            
            // Start CPU usage monitoring on a background thread
            startCpuMonitoring()
        }
    }

    /**
     * Internal method to start monitoring on the main thread
     */
    private fun startMonitoringOnMainThread() {
        lock.withLock {
            framesRendered = 0
            lastFpsUpdate = System.currentTimeMillis()
            frameTimeHistory.clear()
        }
        
        Choreographer.getInstance().postFrameCallback(frameCallback)
        Log.d(TAG, "Performance monitoring started")
    }
    
    /**
     * Start monitoring CPU usage
     */
    private fun startCpuMonitoring() {
        try {
            // If the executor exists and is not shutdown, use it
            if (cpuExecutor == null || cpuExecutor?.isShutdown == true || cpuExecutor?.isTerminated == true) {
                cpuExecutor = Executors.newSingleThreadScheduledExecutor()
            }
            
            // Initialize CPU usage monitoring
            updateCpuUsage()
            
            // Use scheduleWithFixedDelay instead of scheduleAtFixedRate to prevent task bursts 
            // when the app moves from cached to foreground state
            cpuExecutor?.scheduleWithFixedDelay(
                { updateCpuUsage() },
                0,
                1,
                TimeUnit.SECONDS
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error starting CPU monitoring", e)
            // Fallback to running without CPU monitoring
            cpuUsage.set(0f)
        }
    }
    
    /**
     * The different methods we can use to get CPU usage
     */
    private enum class CpuUsageMethod {
        PROC_FILES, // Direct access to /proc files - works on older Android or rooted devices
        ACTIVITY_MANAGER, // Using ActivityManager - safer but less precise
        DEBUG_API // Using Debug API - another alternative
    }
    
    /**
     * Update CPU usage statistics
     */
    private fun updateCpuUsage() {
        // Detect and handle task bursts
        val currentTime = System.currentTimeMillis()
        val taskCount = taskExecutionCount.incrementAndGet()
        val lastReset = lastTaskCountResetTime.get()
        
        // Reset the counter periodically
        if (currentTime - lastReset > TASK_COUNT_RESET_INTERVAL_MS) {
            taskExecutionCount.set(0)
            lastTaskCountResetTime.set(currentTime)
        } 
        // If we detect a task burst, reset the CPU monitoring
        else if (taskCount > TASK_BURST_THRESHOLD) {
            Log.w(TAG, "Task burst detected! Resetting CPU monitoring")
            resetCpuMonitoring()
            return
        }
        
        // Rate limiting to prevent excessive calls
        val lastUpdate = lastCpuUpdateTime.get()
        if (currentTime - lastUpdate < MIN_CPU_UPDATE_INTERVAL_MS) {
            return // Skip this update if it's too soon
        }
        lastCpuUpdateTime.set(currentTime)
        
        try {
            when (cpuUsageMethod) {
                CpuUsageMethod.PROC_FILES -> updateCpuUsageViaProcFiles()
                CpuUsageMethod.ACTIVITY_MANAGER -> updateCpuUsageViaActivityManager()
                CpuUsageMethod.DEBUG_API -> updateCpuUsageViaDebugApi()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update CPU usage with method $cpuUsageMethod: ${e.message}")
            
            // Try a different method if the current one fails
            when (cpuUsageMethod) {
                CpuUsageMethod.PROC_FILES -> {
                    cpuUsageMethod = CpuUsageMethod.ACTIVITY_MANAGER
                    updateCpuUsage() // Try again with the new method
                }
                CpuUsageMethod.ACTIVITY_MANAGER -> {
                    cpuUsageMethod = CpuUsageMethod.DEBUG_API
                    updateCpuUsage() // Try again with the new method
                }
                CpuUsageMethod.DEBUG_API -> {
                    // If all methods fail, just set a default value
                    cpuUsage.set(0f)
                }
            }
        }
    }
    
    /**
     * Update CPU usage using /proc files
     * Note: This requires special permissions on newer Android versions
     */
    private fun updateCpuUsageViaProcFiles() {
        // Get app CPU time
        val pid = Process.myPid()
        var appCpuTime = 0L
        
        BufferedReader(FileReader("/proc/$pid/stat")).use { reader ->
            val stats = reader.readLine().split(" ".toRegex())
            
            // User and system time are at positions 13 and 14
            val utime = stats[13].toLong()
            val stime = stats[14].toLong()
            appCpuTime = utime + stime
        }
        
        // Get total CPU time across all cores
        var cpuTimeTotal = 0L
        
        BufferedReader(FileReader("/proc/stat")).use { reader ->
            val stats = reader.readLine().split("\\s+".toRegex())
            
            // Sum all CPU time components (user, nice, system, idle, iowait, irq, softirq)
            for (i in 1 until minOf(stats.size, 8)) {
                cpuTimeTotal += stats[i].toLong()
            }
        }
        
        // Calculate CPU usage percentage if we have previous measurements
        if (lastCpuUsageTotal > 0 && lastCpuUsageApp > 0) {
            val totalDelta = cpuTimeTotal - lastCpuUsageTotal
            val appDelta = appCpuTime - lastCpuUsageApp
            
            if (totalDelta > 0) {
                val usage = (appDelta.toFloat() / totalDelta) * 100f
                
                // CPU usage may be multiplied by core count, cap at 100%
                cpuUsage.set(usage.coerceAtMost(100f))
            }
        }
        
        // Update previous values
        lastCpuUsageTotal = cpuTimeTotal
        lastCpuUsageApp = appCpuTime
    }
    
    /**
     * Update CPU usage using ActivityManager
     * This is a safer alternative that works on all Android versions
     */
    private fun updateCpuUsageViaActivityManager() {
        val am = activityManager ?: return
        
        // Get process memory info
        val pid = Process.myPid()
        val pids = intArrayOf(pid)
        val procMemInfo = am.getProcessMemoryInfo(pids)
        
        if (procMemInfo.isNotEmpty()) {
            // For CPU approximation, we can use memory info indirectly
            // Higher memory pressure often correlates with higher CPU usage
            // This is not perfect but gives us a relative indication
            val memInfo = procMemInfo[0]
            val totalPss = memInfo.totalPss.toFloat()
            val totalPrivateDirty = memInfo.totalPrivateDirty.toFloat()
            
            // Get total available memory
            val memoryInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memoryInfo)
            val totalMem = memoryInfo.totalMem / 1024f
            
            // Calculate an approximate CPU usage based on memory pressure
            // This is not accurate, but gives us a relative indication
            val approxCpuUsage = if (totalPss > 0 && totalMem > 0) {
                (totalPss / totalMem) * 50f + if (totalPrivateDirty > 0) (totalPrivateDirty / totalPss) * 10f else 0f
            } else {
                0f
            }
            
            // Set to a reasonable range
            cpuUsage.set(approxCpuUsage.coerceIn(0f, 100f))
        }
    }
    
    /**
     * Update CPU usage using Debug API
     * Another alternative method
     */
    private fun updateCpuUsageViaDebugApi() {
        // Get thread CPU time
        val threadCpuTimeNs = Debug.threadCpuTimeNanos()
        val processTime = Process.getElapsedCpuTime()
        val totalCpuTimeNs = threadCpuTimeNs + processTime
        
        // If we have previous values, calculate the change
        if (lastCpuUsageTotal > 0 && lastCpuUsageApp > 0) {
            val appDelta = threadCpuTimeNs - lastCpuUsageApp
            val totalDelta = totalCpuTimeNs - lastCpuUsageTotal
            
            if (totalDelta > 0) {
                val usage = (appDelta.toFloat() / totalDelta.toFloat()) * 100f
                cpuUsage.set(usage.coerceIn(0f, 100f))
            }
        }
        
        // Update previous values
        lastCpuUsageTotal = totalCpuTimeNs
        lastCpuUsageApp = threadCpuTimeNs
    }

    /**
     * Stop monitoring performance metrics
     */
    fun stopMonitoring() {
        // Use compareAndSet to safely handle concurrent calls
        if (isMonitoring.compareAndSet(true, false)) {
            // Ensure we run Choreographer operations on the main thread
            if (Looper.myLooper() == Looper.getMainLooper()) {
                stopMonitoringOnMainThread()
            } else {
                handler.post {
                    stopMonitoringOnMainThread() 
                }
            }
            
            // Stop CPU monitoring - this can run on any thread
            try {
                cpuExecutor?.let { executor ->
                    if (!executor.isShutdown && !executor.isTerminated) {
                        executor.shutdownNow()
                    }
                }
                cpuExecutor = null
            } catch (e: Exception) {
                Log.e(TAG, "Error shutting down CPU monitoring", e)
            }
            
            Log.d(TAG, "Performance monitoring stopped")
        }
    }

    /**
     * Internal method to stop monitoring on the main thread
     */
    private fun stopMonitoringOnMainThread() {
        // No need to check isMonitoring here since we've already checked in stopMonitoring
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    /**
     * Get all performance metrics (memory, CPU, etc.)
     */
    fun getPerformanceMetrics(context: Context): PerformanceMetrics {
        // Ensure we have an ActivityManager instance
        if (activityManager == null) {
            initialize(context)
        }
        
        val memoryMetrics = getMemoryMetrics(context)
        val jankMetrics = calculateJankMetrics()
        
        // Calculate FPS based on current data
        val currentTimeMs = System.currentTimeMillis()
        val fps: Float
        
        lock.withLock {
            val elapsedMs = currentTimeMs - lastFpsUpdate
            
            // If we've been running for a while, calculate the current rate
            fps = if (elapsedMs > 0) {
                (framesRendered.toFloat() / elapsedMs) * 1000
            } else {
                0f
            }
        }
        
        return PerformanceMetrics(
            fps = fps,
            memoryUsageMb = memoryMetrics.usedMemoryMb,
            jankPercentage = jankMetrics.jankPercentage,
            cpuUsagePercent = cpuUsage.get()
        )
    }

    /**
     * Get memory metrics
     */
    fun getMemoryMetrics(context: Context): MemoryMetrics {
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val totalMemory = runtime.totalMemory() / (1024 * 1024)
        val freeMemory = runtime.freeMemory() / (1024 * 1024)
        val maxMemory = runtime.maxMemory() / (1024 * 1024)

        Log.d(TAG, "Memory - Used: $usedMemory MB, Free: $freeMemory MB, Total: $totalMemory MB, Max: $maxMemory MB")
        Log.d(TAG, "CPU Usage: ${cpuUsage.get()}%")

        return MemoryMetrics(
            usedMemoryMb = usedMemory,
            freeMemoryMb = freeMemory,
            totalMemoryMb = totalMemory,
            maxMemoryMb = maxMemory,
            cpuUsagePercent = cpuUsage.get()
        )
    }

    data class MemoryMetrics(
        val usedMemoryMb: Long,
        val freeMemoryMb: Long,
        val totalMemoryMb: Long,
        val maxMemoryMb: Long,
        val cpuUsagePercent: Float = 0f
    )
    
    /**
     * Performance metrics data class (combined metrics)
     */
    data class PerformanceMetrics(
        val fps: Float,
        val memoryUsageMb: Long,
        val jankPercentage: Float,
        val cpuUsagePercent: Float
    )

    /**
     * Reset CPU monitoring if we detect a task burst
     */
    private fun resetCpuMonitoring() {
        try {
            // Shutdown the current executor
            cpuExecutor?.let { executor ->
                if (!executor.isShutdown && !executor.isTerminated) {
                    executor.shutdownNow()
                }
            }
            
            // Create a new executor
            cpuExecutor = Executors.newSingleThreadScheduledExecutor()
            
            // Reset counters
            taskExecutionCount.set(0)
            lastTaskCountResetTime.set(System.currentTimeMillis())
            
            // Schedule task with a delay to avoid immediate execution
            cpuExecutor?.schedule(
                { 
                    // Only restart if monitoring is still active
                    if (isMonitoring.get()) {
                        startCpuMonitoring() 
                    }
                },
                1,
                TimeUnit.SECONDS
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting CPU monitoring", e)
        }
    }
}

/**
 * Composable to monitor performance metrics
 */
@Composable
fun PerformanceMonitorEffect(
    enabled: Boolean = true,
    onMetricsUpdated: (fps: Float, memoryUsage: Long, cpuUsage: Float) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val performanceMonitor = remember { PerformanceMonitor() }
    var fps by remember { mutableFloatStateOf(0f) }
    var memoryUsage by remember { mutableLongStateOf(0L) }
    var cpuUsage by remember { mutableFloatStateOf(0f) }

    // Initialize performance monitor with context
    LaunchedEffect(key1 = Unit) {
        performanceMonitor.initialize(context)
    }

    // Update memory and CPU usage periodically
    LaunchedEffect(key1 = enabled) {
        if (enabled) {
            val handler = Handler(Looper.getMainLooper())
            val runnable = object : Runnable {
                override fun run() {
                    if (!enabled) return
                    
                    val metrics = performanceMonitor.getMemoryMetrics(context)
                    memoryUsage = metrics.usedMemoryMb
                    cpuUsage = metrics.cpuUsagePercent
                    onMetricsUpdated(fps, memoryUsage, cpuUsage)
                    handler.postDelayed(this, 1000)
                }
            }
            handler.post(runnable)
        }
    }

    // Start/stop monitoring based on enabled flag
    DisposableEffect(key1 = enabled) {
        if (enabled) {
            // Use no-argument startMonitoring to avoid ambiguity
            performanceMonitor.startMonitoring()
            
            // Add a manual FPS callback
            performanceMonitor.setFpsCallback { newFps ->
                fps = newFps
                onMetricsUpdated(fps, memoryUsage, cpuUsage)
            }
        }

        onDispose {
            performanceMonitor.stopMonitoring()
        }
    }
}

/**
 * Extension function to enable hardware acceleration for an Activity
 */
fun Activity.enableHardwareAcceleration() {
    PerformanceMonitor.enableHardwareAcceleration(window)
} 