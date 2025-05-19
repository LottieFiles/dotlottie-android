package com.lottiefiles.example.performance

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.text.DecimalFormat

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

    private var frameStartTimeNs = 0L
    private var framesRendered = 0
    private var lastFpsUpdate = 0L
    private var fpsCallback: ((Float) -> Unit)? = null
    private var isMonitoring = false
    private val fpsUpdateIntervalMs = 1000L  // Update FPS every second
    private val handler = Handler(Looper.getMainLooper())
    private val df = DecimalFormat("#.##")

    // Frame time history for jank detection
    private val frameTimeHistory = mutableListOf<Long>()
    private val maxFrameTimeHistorySize = 120 // 2 seconds at 60 FPS

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (frameStartTimeNs > 0) {
                val frameDurationNs = frameTimeNanos - frameStartTimeNs
                frameTimeHistory.add(frameDurationNs)
                if (frameTimeHistory.size > maxFrameTimeHistorySize) {
                    frameTimeHistory.removeAt(0)
                }
            }

            frameStartTimeNs = frameTimeNanos
            framesRendered++

            val currentTimeMs = System.currentTimeMillis()
            if (currentTimeMs - lastFpsUpdate >= fpsUpdateIntervalMs) {
                val elapsedSeconds = (currentTimeMs - lastFpsUpdate) / 1000f
                val fps = framesRendered / elapsedSeconds

                fpsCallback?.invoke(fps)

                // Log jank metrics
                val jankMetrics = calculateJankMetrics()
                Log.d(TAG, "FPS: ${df.format(fps)}, Jank %: ${jankMetrics.jankPercentage}%, Long frames: ${jankMetrics.longFrameCount}")

                framesRendered = 0
                lastFpsUpdate = currentTimeMs
            }

            if (isMonitoring) {
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
        if (frameTimeHistory.isEmpty()) return JankMetrics(0f, 0)

        // Frames taking longer than 16.67ms (60 FPS) are considered jank
        val targetFrameTimeNs = 16_666_666L
        val longFrames = frameTimeHistory.count { it > targetFrameTimeNs }

        val jankPercentage = (longFrames.toFloat() / frameTimeHistory.size) * 100

        return JankMetrics(
            jankPercentage = jankPercentage,
            longFrameCount = longFrames
        )
    }

    /**
     * Start monitoring performance
     */
    fun startMonitoring(fpsListener: (Float) -> Unit) {
        if (!isMonitoring) {
            // Ensure this runs on the main thread since Choreographer requires a Looper
            if (Looper.myLooper() == Looper.getMainLooper()) {
                startMonitoringOnMainThread(fpsListener)
            } else {
                handler.post {
                    startMonitoringOnMainThread(fpsListener)
                }
            }
        }
    }

    /**
     * Internal method to start monitoring on the main thread
     */
    private fun startMonitoringOnMainThread(fpsListener: (Float) -> Unit) {
        isMonitoring = true
        fpsCallback = fpsListener
        framesRendered = 0
        lastFpsUpdate = System.currentTimeMillis()
        Choreographer.getInstance().postFrameCallback(frameCallback)
        Log.d(TAG, "Performance monitoring started")
    }

    /**
     * Stop monitoring performance
     */
    fun stopMonitoring() {
        if (isMonitoring) {
            // Ensure this runs on the main thread since Choreographer requires a Looper
            if (Looper.myLooper() == Looper.getMainLooper()) {
                stopMonitoringOnMainThread()
            } else {
                handler.post {
                    stopMonitoringOnMainThread()
                }
            }
        }
    }

    /**
     * Internal method to stop monitoring on the main thread
     */
    private fun stopMonitoringOnMainThread() {
        isMonitoring = false
        fpsCallback = null
        Log.d(TAG, "Performance monitoring stopped")
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

        return MemoryMetrics(
            usedMemoryMb = usedMemory,
            freeMemoryMb = freeMemory,
            totalMemoryMb = totalMemory,
            maxMemoryMb = maxMemory
        )
    }

    data class MemoryMetrics(
        val usedMemoryMb: Long,
        val freeMemoryMb: Long,
        val totalMemoryMb: Long,
        val maxMemoryMb: Long
    )
}

/**
 * Composable to monitor performance metrics
 */
@Composable
fun PerformanceMonitorEffect(
    enabled: Boolean = true,
    onMetricsUpdated: (fps: Float, memoryUsage: Long) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val performanceMonitor = remember { PerformanceMonitor() }
    var fps by remember { mutableFloatStateOf(0f) }
    var memoryUsage by remember { mutableLongStateOf(0L) }

    // Update memory usage periodically
    LaunchedEffect(key1 = enabled) {
        if (enabled) {
            val handler = Handler(Looper.getMainLooper())
            val runnable = object : Runnable {
                override fun run() {
                    val metrics = performanceMonitor.getMemoryMetrics(context)
                    memoryUsage = metrics.usedMemoryMb
                    onMetricsUpdated(fps, memoryUsage)
                    handler.postDelayed(this, 2000)
                }
            }
            handler.post(runnable)
        }
    }

    // Start/stop monitoring based on enabled flag
    DisposableEffect(key1 = enabled) {
        if (enabled) {
            performanceMonitor.startMonitoring { newFps ->
                fps = newFps
                onMetricsUpdated(newFps, memoryUsage)
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