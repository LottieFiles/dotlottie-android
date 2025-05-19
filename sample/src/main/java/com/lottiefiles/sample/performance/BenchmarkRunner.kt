package com.lottiefiles.sample.performance

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

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
    private val permissionsHelper = PermissionsHelper(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Thread-safe state variables
    private val isRunning = AtomicBoolean(false)
    private val currentTestIndex = AtomicInteger(0)
    
    private val testResults = mutableListOf<BenchmarkResult>()
    private var testConfig = BenchmarkConfig()
    private var onTestCompletedListener: OnTestCompletedListener? = null
    private var startupTimestamp: Long = 0
    
    private var benchmarkTitle: String = "Lottie Performance Benchmark"
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
        if (isRunning.getAndSet(true)) {
            Log.w(TAG, "Benchmark is already running")
            return
        }
        
        currentTestIndex.set(0)
        synchronized(testResults) {
            testResults.clear()
        }
        
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
        if (!isRunning.getAndSet(false)) return
        
        mainHandler.removeCallbacksAndMessages(null)
        performanceMonitor.stopMonitoring()
        performanceMonitor.removeListener(metricsListener)
        
        // Make a copy to avoid concurrent modification
        val resultsCopy: List<BenchmarkResult>
        synchronized(testResults) {
            resultsCopy = testResults.toList()
        }
        
        mainHandler.post {
            onTestCompletedListener?.onAllTestsCompleted(resultsCopy)
        }
    }
    
    /**
     * Run the next test in the sequence
     */
    private fun runNextTest() {
        if (!isRunning.get() || currentTestIndex.get() >= testConfig.animationCounts.size) {
            // All tests are complete
            finalizeBenchmark()
            return
        }
        
        val testIndex = currentTestIndex.get()
        val animationCount = testConfig.animationCounts[testIndex]
        val size = testConfig.animationSizes[Math.min(testIndex, testConfig.animationSizes.size - 1)]
        val useInterpolation = testConfig.useFrameInterpolation
        
        Log.d(TAG, "Starting test #${testIndex + 1}: $animationCount animations, size: $size, interpolation: $useInterpolation")
        
        // Notify listener of test start on main thread
        mainHandler.post {
            onTestCompletedListener?.onTestStarted(testIndex, animationCount, size, useInterpolation)
        }
        
        // First, do warmup
        mainHandler.postDelayed({
            if (!isRunning.get()) return@postDelayed
            
            // After warmup, start recording metrics
            Log.d(TAG, "Warmup completed, recording metrics...")
            
            // Reset metrics for this test
            lastMetrics = null
            
            // End test and record metrics after duration
            mainHandler.postDelayed({
                if (!isRunning.get()) return@postDelayed
                
                // Record results
                val metrics = lastMetrics ?: performanceMonitor.getCurrentMetrics()
                
                val testResult = BenchmarkResult(
                    animationCount = animationCount,
                    animationSize = size,
                    useFrameInterpolation = useInterpolation,
                    fps = metrics.fps,
                    memoryUsageMb = metrics.memoryUsageMb,
                    jankPercentage = metrics.jankPercentage,
                    cpuUsage = metrics.cpuUsage,
                    startupTimeMs = System.currentTimeMillis() - startupTimestamp
                )
                
                // Add result to list
                synchronized(testResults) {
                    testResults.add(testResult)
                }
                
                // Notify listener on main thread
                mainHandler.post {
                    onTestCompletedListener?.onTestCompleted(testIndex, testResult)
                }
                
                // Move to next test
                currentTestIndex.incrementAndGet()
                runNextTest()
                
            }, TEST_DURATION_MS)
            
        }, WARMUP_DURATION_MS)
    }
    
    /**
     * Finalize the benchmark and generate report
     */
    private fun finalizeBenchmark() {
        isRunning.set(false)
        performanceMonitor.stopMonitoring()
        performanceMonitor.removeListener(metricsListener)
        
        // Make a copy to avoid concurrent modification
        val resultsCopy: List<BenchmarkResult>
        synchronized(testResults) {
            resultsCopy = testResults.toList()
        }
        
        // Generate report in background
        Thread {
            val reportFile = generateReport(resultsCopy)
            
            // Notify on main thread
            mainHandler.post {
                if (reportFile != null) {
                    Log.d(TAG, "Benchmark completed, report saved to: ${reportFile.absolutePath}")
                    Toast.makeText(context, "Benchmark report saved", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e(TAG, "Failed to save benchmark report")
                    Toast.makeText(context, "Failed to save benchmark report", Toast.LENGTH_SHORT).show()
                }
                
                onTestCompletedListener?.onAllTestsCompleted(resultsCopy)
            }
        }.start()
    }
    
    /**
     * Generate a CSV report of the benchmark results
     */
    private fun generateReport(results: List<BenchmarkResult>): File? {
        if (results.isEmpty()) return null
        
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "lottie_benchmark_$timestamp.csv"
            
            // Use app-specific directory that doesn't require special permissions
            val storageDir = permissionsHelper.getBenchmarkStorageDirectory()
                ?: context.getExternalFilesDir(null)
                ?: throw IOException("Could not access external storage")
            
            val file = File(storageDir, fileName)
            
            try {
                FileOutputStream(file).use { fos ->
                    // Write header
                    val header = "# $benchmarkTitle\n"
                    fos.write(header.toByteArray())
                    
                    // Write column names
                    val columns = "Animation Count,Size (dp),Frame Interpolation,FPS,Memory (MB),Jank %,CPU %,Startup Time (ms)\n"
                    fos.write(columns.toByteArray())
                    
                    // Write data rows
                    for (result in results) {
                        val row = "${result.animationCount},${result.animationSize},${result.useFrameInterpolation}," +
                                "${String.format("%.1f", result.fps)},${String.format("%.1f", result.memoryUsageMb)}," +
                                "${String.format("%.1f", result.jankPercentage)},${String.format("%.1f", result.cpuUsage)}," +
                                "${result.startupTimeMs}\n"
                        fos.write(row.toByteArray())
                    }
                }
                
                return file
            } catch (e: FileNotFoundException) {
                Log.e(TAG, "FileNotFoundException while generating report", e)
                return null
            } catch (e: IOException) {
                Log.e(TAG, "IOException while generating report", e)
                return null
            }
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
        val cpuUsage: Float = 0f,
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