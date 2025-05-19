package com.lottiefiles.sample.performance

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages automated benchmark tests for Lottie animations.
 * Records performance metrics and generates reports.
 */
class BenchmarkRunner(private val context: Context) {
    companion object {
        private const val TAG = "BenchmarkRunner"
        private const val TEST_DURATION_MS = 10000L // 10 seconds per test
        private const val WARMUP_DURATION_MS = 3000L // 3 seconds warmup before recording
    }
    
    private val performanceMonitor = PerformanceMonitor(context)
    private val handler = Handler(Looper.getMainLooper())
    
    private var currentTestIndex = 0
    private val testResults = mutableListOf<BenchmarkResult>()
    private var testConfig = BenchmarkConfig()
    private var onTestCompletedListener: OnTestCompletedListener? = null
    private var startupTimestamp: Long = 0
    
    private var benchmarkTitle: String = "Lottie Performance Benchmark"
    private var isRunning = false
    private var lastMetrics: PerformanceMonitor.PerformanceMetrics? = null
    private val metricsListener = object : PerformanceMonitor.PerformanceListener {
        override fun onMetricsUpdated(metrics: PerformanceMonitor.PerformanceMetrics) {
            lastMetrics = metrics
        }
    }
    
    /**
     * Set the configuration for the benchmark tests
     */
    fun setConfig(config: BenchmarkConfig) {
        testConfig = config
    }
    
    /**
     * Set the title for the benchmark report
     */
    fun setBenchmarkTitle(title: String) {
        benchmarkTitle = title
    }
    
    /**
     * Set a listener to receive test completion events
     */
    fun setOnTestCompletedListener(listener: OnTestCompletedListener) {
        onTestCompletedListener = listener
    }
    
    /**
     * Start running the benchmark tests
     */
    fun startBenchmark() {
        if (isRunning) {
            Log.w(TAG, "Benchmark is already running")
            return
        }
        
        isRunning = true
        currentTestIndex = 0
        testResults.clear()
        
        // Start monitoring
        performanceMonitor.addListener(metricsListener)
        performanceMonitor.startMonitoring()
        
        // Start the first test
        startupTimestamp = System.currentTimeMillis()
        runNextTest()
    }
    
    /**
     * Stop the benchmark prematurely
     */
    fun stopBenchmark() {
        if (!isRunning) return
        
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        performanceMonitor.stopMonitoring()
        performanceMonitor.removeListener(metricsListener)
        
        onTestCompletedListener?.onAllTestsCompleted(testResults)
    }
    
    /**
     * Run the next test in the sequence
     */
    private fun runNextTest() {
        if (!isRunning || currentTestIndex >= testConfig.animationCounts.size) {
            // All tests are complete
            isRunning = false
            performanceMonitor.stopMonitoring()
            performanceMonitor.removeListener(metricsListener)
            
            // Generate report
            val reportFile = generateReport()
            Log.d(TAG, "Benchmark completed, report saved to: ${reportFile?.absolutePath}")
            
            onTestCompletedListener?.onAllTestsCompleted(testResults)
            return
        }
        
        val animationCount = testConfig.animationCounts[currentTestIndex]
        val size = testConfig.animationSizes[Math.min(currentTestIndex, testConfig.animationSizes.size - 1)]
        val useInterpolation = testConfig.useFrameInterpolation
        
        Log.d(TAG, "Starting test #${currentTestIndex + 1}: $animationCount animations, size: $size, interpolation: $useInterpolation")
        
        // Notify listener of test start
        onTestCompletedListener?.onTestStarted(currentTestIndex, animationCount, size, useInterpolation)
        
        // First, do warmup
        handler.postDelayed({
            // After warmup, start recording metrics
            Log.d(TAG, "Warmup completed, recording metrics...")
            
            // Reset metrics for this test
            lastMetrics = null
            
            // End test and record metrics after duration
            handler.postDelayed({
                // Record results
                val metrics = lastMetrics ?: performanceMonitor.getCurrentMetrics()
                
                val testResult = BenchmarkResult(
                    animationCount = animationCount,
                    animationSize = size,
                    useFrameInterpolation = useInterpolation,
                    fps = metrics.fps,
                    memoryUsageMb = metrics.memoryUsageMb,
                    jankPercentage = metrics.jankPercentage,
                    startupTimeMs = System.currentTimeMillis() - startupTimestamp
                )
                
                testResults.add(testResult)
                
                // Notify listener
                onTestCompletedListener?.onTestCompleted(currentTestIndex, testResult)
                
                // Move to next test
                currentTestIndex++
                runNextTest()
                
            }, TEST_DURATION_MS)
            
        }, WARMUP_DURATION_MS)
    }
    
    /**
     * Generate a CSV report of the benchmark results
     */
    private fun generateReport(): File? {
        if (testResults.isEmpty()) return null
        
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "lottie_benchmark_$timestamp.csv"
            
            val file = File(context.getExternalFilesDir(null), fileName)
            FileOutputStream(file).use { fos ->
                // Write header
                val header = "# $benchmarkTitle\n"
                fos.write(header.toByteArray())
                
                // Write column names
                val columns = "Animation Count,Size (dp),Frame Interpolation,FPS,Memory (MB),Jank %,Startup Time (ms)\n"
                fos.write(columns.toByteArray())
                
                // Write data rows
                for (result in testResults) {
                    val row = "${result.animationCount},${result.animationSize},${result.useFrameInterpolation}," +
                            "${String.format("%.1f", result.fps)},${String.format("%.1f", result.memoryUsageMb)}," +
                            "${String.format("%.1f", result.jankPercentage)},${result.startupTimeMs}\n"
                    fos.write(row.toByteArray())
                }
            }
            
            return file
        } catch (e: Exception) {
            Log.e(TAG, "Error generating report", e)
            return null
        }
    }
    
    /**
     * Benchmark configuration data class
     */
    data class BenchmarkConfig(
        val animationCounts: List<Int> = listOf(1, 5, 10, 20, 30, 50),
        val animationSizes: List<Int> = listOf(100, 200),
        val useFrameInterpolation: Boolean = true
    )
    
    /**
     * Benchmark result data class
     */
    data class BenchmarkResult(
        val animationCount: Int,
        val animationSize: Int,
        val useFrameInterpolation: Boolean,
        val fps: Float,
        val memoryUsageMb: Float,
        val jankPercentage: Float,
        val startupTimeMs: Long
    )
    
    /**
     * Listener interface for benchmark events
     */
    interface OnTestCompletedListener {
        fun onTestStarted(testIndex: Int, animationCount: Int, size: Int, useInterpolation: Boolean)
        fun onTestCompleted(testIndex: Int, result: BenchmarkResult)
        fun onAllTestsCompleted(results: List<BenchmarkResult>)
    }
} 