package com.lottiefiles.example.performance

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A utility class to run automated performance benchmarks
 */
class BenchmarkRunner(private val context: Context) {
    companion object {
        private const val TAG = "BenchmarkRunner"
    }

    // Performance monitor for metrics
    private val performanceMonitor = PerformanceMonitor()
    
    // Benchmark state
    private val _benchmarkState = MutableStateFlow<BenchmarkState>(BenchmarkState.Idle)
    val benchmarkState: StateFlow<BenchmarkState> = _benchmarkState
    
    // Test configurations with library type
    val testConfigurations = listOf(
        // DotLottie tests
        TestConfiguration(animationCount = 4, useInterpolation = true, durationSeconds = 10, library = LibraryType.DOT_LOTTIE),
        TestConfiguration(animationCount = 4, useInterpolation = false, durationSeconds = 10, library = LibraryType.DOT_LOTTIE),
        TestConfiguration(animationCount = 9, useInterpolation = true, durationSeconds = 10, library = LibraryType.DOT_LOTTIE),
        TestConfiguration(animationCount = 9, useInterpolation = false, durationSeconds = 10, library = LibraryType.DOT_LOTTIE),
        TestConfiguration(animationCount = 16, useInterpolation = true, durationSeconds = 10, library = LibraryType.DOT_LOTTIE),
        TestConfiguration(animationCount = 16, useInterpolation = false, durationSeconds = 10, library = LibraryType.DOT_LOTTIE),
        TestConfiguration(animationCount = 25, useInterpolation = true, durationSeconds = 10, library = LibraryType.DOT_LOTTIE),
        TestConfiguration(animationCount = 25, useInterpolation = false, durationSeconds = 10, library = LibraryType.DOT_LOTTIE),
        
        // Airbnb Lottie tests
        TestConfiguration(animationCount = 4, useInterpolation = true, durationSeconds = 10, library = LibraryType.AIRBNB_LOTTIE),
        TestConfiguration(animationCount = 4, useInterpolation = false, durationSeconds = 10, library = LibraryType.AIRBNB_LOTTIE),
        TestConfiguration(animationCount = 9, useInterpolation = true, durationSeconds = 10, library = LibraryType.AIRBNB_LOTTIE),
        TestConfiguration(animationCount = 9, useInterpolation = false, durationSeconds = 10, library = LibraryType.AIRBNB_LOTTIE),
        TestConfiguration(animationCount = 16, useInterpolation = true, durationSeconds = 10, library = LibraryType.AIRBNB_LOTTIE),
        TestConfiguration(animationCount = 16, useInterpolation = false, durationSeconds = 10, library = LibraryType.AIRBNB_LOTTIE),
        TestConfiguration(animationCount = 25, useInterpolation = true, durationSeconds = 10, library = LibraryType.AIRBNB_LOTTIE),
        TestConfiguration(animationCount = 25, useInterpolation = false, durationSeconds = 10, library = LibraryType.AIRBNB_LOTTIE)
    )
    
    private var currentTestIndex = 0
    private val results = mutableListOf<BenchmarkResult>()
    private var benchmarkJob: Job? = null
    private var fpsValues = mutableListOf<Float>()
    private var memoryValues = mutableListOf<Long>()
    
    // Handler for main thread operations
    private val handler = Handler(Looper.getMainLooper())
    
    // Coroutine scope
    private val scope = CoroutineScope(Dispatchers.Default)
    
    /**
     * Start the benchmark
     */
    fun startBenchmark() {
        if (benchmarkState.value != BenchmarkState.Idle && benchmarkState.value !is BenchmarkState.Completed) {
            Log.w(TAG, "Benchmark already running")
            return
        }
        
        results.clear()
        currentTestIndex = 0
        
        runNextTest()
    }
    
    /**
     * Stop the benchmark
     */
    fun stopBenchmark() {
        benchmarkJob?.cancel()
        _benchmarkState.value = BenchmarkState.Idle
        Log.d(TAG, "Benchmark stopped")
    }
    
    /**
     * Run the next test in the sequence
     */
    private fun runNextTest() {
        if (currentTestIndex >= testConfigurations.size) {
            // All tests completed
            _benchmarkState.value = BenchmarkState.Completed(results)
            Log.d(TAG, "All benchmarks completed")
            generateReport()
            return
        }
        
        val config = testConfigurations[currentTestIndex]
        _benchmarkState.value = BenchmarkState.Running(
            config = config,
            progress = 0f,
            currentTestIndex = currentTestIndex,
            totalTests = testConfigurations.size
        )
        
        fpsValues.clear()
        memoryValues.clear()
        
        benchmarkJob = scope.launch {
            Log.d(TAG, "Starting benchmark test ${currentTestIndex + 1}/${testConfigurations.size} - ${config.library}")
            
            // Wait for animations to fully load
            delay(2000)
            
            // Start collecting metrics
            performanceMonitor.startMonitoring { fps ->
                fpsValues.add(fps)
            }
            
            // Run test for specified duration
            val startTime = System.currentTimeMillis()
            val endTime = startTime + (config.durationSeconds * 1000)
            
            while (System.currentTimeMillis() < endTime) {
                val elapsedTime = System.currentTimeMillis() - startTime
                val progress = elapsedTime.toFloat() / (config.durationSeconds * 1000)
                
                _benchmarkState.value = BenchmarkState.Running(
                    config = config,
                    progress = progress,
                    currentTestIndex = currentTestIndex,
                    totalTests = testConfigurations.size
                )
                
                // Collect memory metrics
                val memoryMetrics = performanceMonitor.getMemoryMetrics(context)
                memoryValues.add(memoryMetrics.usedMemoryMb)
                
                delay(500) // Update every 500ms
            }
            
            // Stop monitoring and collect results
            performanceMonitor.stopMonitoring()
            
            // Calculate results
            val avgFps = fpsValues.average().toFloat()
            val minFps = fpsValues.minOrNull() ?: 0f
            val maxFps = fpsValues.maxOrNull() ?: 0f
            
            val avgMemory = memoryValues.average().toLong()
            val peakMemory = memoryValues.maxOrNull() ?: 0L
            
            // Save results
            val result = BenchmarkResult(
                config = config,
                averageFps = avgFps,
                minFps = minFps,
                maxFps = maxFps,
                averageMemoryMb = avgMemory,
                peakMemoryMb = peakMemory
            )
            
            results.add(result)
            Log.d(TAG, "Test completed: $result")
            
            // Move to next test
            currentTestIndex++
            delay(1000) // Delay before next test
            runNextTest()
        }
    }
    
    /**
     * Generate a CSV report with the benchmark results
     */
    private fun generateReport() {
        scope.launch {
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
                val timestamp = dateFormat.format(Date())
                val fileName = "lottie_comparison_benchmark_$timestamp.csv"
                
                val file = File(context.getExternalFilesDir(null), fileName)
                val fos = FileOutputStream(file)
                
                // Write header
                fos.write("Test,Library,Animations,Interpolation,Avg FPS,Min FPS,Max FPS,Avg Memory (MB),Peak Memory (MB)\n".toByteArray())
                
                // Write results
                results.forEachIndexed { index, result ->
                    val line = "${index + 1},${result.config.library},${result.config.animationCount},${result.config.useInterpolation}," +
                            "%.2f,%.2f,%.2f,%d,%d\n".format(
                                result.averageFps,
                                result.minFps,
                                result.maxFps,
                                result.averageMemoryMb,
                                result.peakMemoryMb
                            )
                    fos.write(line.toByteArray())
                }
                
                fos.close()
                Log.d(TAG, "Benchmark report saved to ${file.absolutePath}")
                
                // Also log to console for convenience
                Log.i(TAG, "Benchmark Results Summary:")
                results.forEachIndexed { index, result ->
                    val formattedResult = String.format(
                        "Test %d: %s - %d animations, interpolation=%s, Avg FPS: %.2f, Memory: %d MB",
                        index + 1,
                        result.config.library,
                        result.config.animationCount,
                        result.config.useInterpolation,
                        result.averageFps,
                        result.averageMemoryMb
                    )
                    Log.i(TAG, formattedResult)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate benchmark report", e)
            }
        }
    }
    
    /**
     * Enum representing the library type being tested
     */
    enum class LibraryType {
        DOT_LOTTIE,
        AIRBNB_LOTTIE
    }
    
    /**
     * Data class representing a test configuration
     */
    data class TestConfiguration(
        val animationCount: Int,
        val useInterpolation: Boolean,
        val durationSeconds: Int,
        val library: LibraryType
    )
    
    /**
     * Data class representing benchmark results
     */
    data class BenchmarkResult(
        val config: TestConfiguration,
        val averageFps: Float,
        val minFps: Float,
        val maxFps: Float,
        val averageMemoryMb: Long,
        val peakMemoryMb: Long
    )
    
    /**
     * Sealed class representing benchmark state
     */
    sealed class BenchmarkState {
        object Idle : BenchmarkState()
        
        data class Running(
            val config: TestConfiguration,
            val progress: Float,
            val currentTestIndex: Int,
            val totalTests: Int
        ) : BenchmarkState()
        
        data class Completed(val results: List<BenchmarkResult>) : BenchmarkState()
    }
} 