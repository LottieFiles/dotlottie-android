package com.lottiefiles.example.performancetest

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.lottiefiles.example.util.PerformanceMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A utility class to run automated performance benchmarks
 */
class BenchmarkRunner(private val context: Context) {
    companion object {
        private const val TAG = "BenchmarkRunner"
    }

    // Performance monitor for metrics
    private val performanceMonitor = PerformanceMonitor().apply {
        initialize(context)
    }

    // Benchmark state
    private val _benchmarkState = MutableStateFlow<BenchmarkState>(BenchmarkState.Idle)
    val benchmarkState: StateFlow<BenchmarkState> = _benchmarkState

    // Test configurations with library type
    private val testConfigurations = listOf(
        // DotLottie tests with .json format
        TestConfiguration(
            animationCount = 4,
            useInterpolation = true,
            durationSeconds = 10,
            library = LibraryType.DOT_LOTTIE,
            fileFormat = FileFormat.JSON
        ),
        TestConfiguration(
            animationCount = 4,
            useInterpolation = false,
            durationSeconds = 10,
            library = LibraryType.DOT_LOTTIE,
            fileFormat = FileFormat.JSON
        ),
        TestConfiguration(
            animationCount = 9,
            useInterpolation = true,
            durationSeconds = 10,
            library = LibraryType.DOT_LOTTIE,
            fileFormat = FileFormat.JSON
        ),
        TestConfiguration(
            animationCount = 9,
            useInterpolation = false,
            durationSeconds = 10,
            library = LibraryType.DOT_LOTTIE,
            fileFormat = FileFormat.JSON
        ),
        TestConfiguration(
            animationCount = 16,
            useInterpolation = true,
            durationSeconds = 10,
            library = LibraryType.DOT_LOTTIE,
            fileFormat = FileFormat.JSON
        ),
        TestConfiguration(
            animationCount = 16,
            useInterpolation = false,
            durationSeconds = 10,
            library = LibraryType.DOT_LOTTIE,
            fileFormat = FileFormat.JSON
        ),
        TestConfiguration(
            animationCount = 25,
            useInterpolation = true,
            durationSeconds = 10,
            library = LibraryType.DOT_LOTTIE,
            fileFormat = FileFormat.JSON
        ),
        TestConfiguration(
            animationCount = 25,
            useInterpolation = false,
            durationSeconds = 10,
            library = LibraryType.DOT_LOTTIE,
            fileFormat = FileFormat.JSON
        ),

        // DotLottie tests with .lottie format
        TestConfiguration(
            animationCount = 4,
            useInterpolation = true,
            durationSeconds = 10,
            library = LibraryType.DOT_LOTTIE,
            fileFormat = FileFormat.LOTTIE
        ),
        TestConfiguration(
            animationCount = 4,
            useInterpolation = false,
            durationSeconds = 10,
            library = LibraryType.DOT_LOTTIE,
            fileFormat = FileFormat.LOTTIE
        ),
        TestConfiguration(
            animationCount = 9,
            useInterpolation = true,
            durationSeconds = 10,
            library = LibraryType.DOT_LOTTIE,
            fileFormat = FileFormat.LOTTIE
        ),
        TestConfiguration(
            animationCount = 9,
            useInterpolation = false,
            durationSeconds = 10,
            library = LibraryType.DOT_LOTTIE,
            fileFormat = FileFormat.LOTTIE
        ),
        TestConfiguration(
            animationCount = 16,
            useInterpolation = true,
            durationSeconds = 10,
            library = LibraryType.DOT_LOTTIE,
            fileFormat = FileFormat.LOTTIE
        ),
        TestConfiguration(
            animationCount = 16,
            useInterpolation = false,
            durationSeconds = 10,
            library = LibraryType.DOT_LOTTIE,
            fileFormat = FileFormat.LOTTIE
        ),
        TestConfiguration(
            animationCount = 25,
            useInterpolation = true,
            durationSeconds = 10,
            library = LibraryType.DOT_LOTTIE,
            fileFormat = FileFormat.LOTTIE
        ),
        TestConfiguration(
            animationCount = 25,
            useInterpolation = false,
            durationSeconds = 10,
            library = LibraryType.DOT_LOTTIE,
            fileFormat = FileFormat.LOTTIE
        ),

        // Airbnb Lottie tests - interpolation parameter is irrelevant as this library doesn't support it
        TestConfiguration(
            animationCount = 4,
            useInterpolation = false,
            durationSeconds = 10,
            library = LibraryType.AIRBNB_LOTTIE,
            fileFormat = FileFormat.JSON
        ),
        TestConfiguration(
            animationCount = 9,
            useInterpolation = false,
            durationSeconds = 10,
            library = LibraryType.AIRBNB_LOTTIE,
            fileFormat = FileFormat.JSON
        ),
        TestConfiguration(
            animationCount = 16,
            useInterpolation = false,
            durationSeconds = 10,
            library = LibraryType.AIRBNB_LOTTIE,
            fileFormat = FileFormat.JSON
        ),
        TestConfiguration(
            animationCount = 25,
            useInterpolation = false,
            durationSeconds = 10,
            library = LibraryType.AIRBNB_LOTTIE,
            fileFormat = FileFormat.JSON
        )
    )

    private var currentTestIndex = 0
    private val results = mutableListOf<BenchmarkResult>()
    private var benchmarkJob: Job? = null
    private var fpsValues = mutableListOf<Float>()
    private var memoryValues = mutableListOf<Long>()
    private var cpuValues = mutableListOf<Float>()
    private val isRunning = AtomicBoolean(false)  // Track if benchmark is running

    // Handler for main thread operations
    private val handler = Handler(Looper.getMainLooper())

    // Coroutine scope
    private val scope = CoroutineScope(Dispatchers.Default)

    // Last generated report file
    private var lastReportFile: File? = null

    /**
     * Start the benchmark
     */
    fun startBenchmark() {
        if (isRunning.getAndSet(true)) {
            Log.w(TAG, "Benchmark already running")
            return
        }

        synchronized(results) {
            results.clear()
        }

        currentTestIndex = 0

        // Reset to idle state initially
        _benchmarkState.value = BenchmarkState.Idle

        runNextTest()
    }

    /**
     * Stop the benchmark prematurely
     */
    fun stopBenchmark() {
        if (!isRunning.getAndSet(false)) return

        benchmarkJob?.cancel()
        benchmarkJob = null

        // Make sure performance monitor is stopped on the main thread
        handler.post {
            try {
                performanceMonitor.stopMonitoring()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping performance monitor", e)
            }
        }

        Log.d(TAG, "Benchmark stopped")
    }

    /**
     * Run the next test in the sequence
     */
    private fun runNextTest() {
        if (!isRunning.get()) return

        // Check if we've completed all tests
        if (currentTestIndex >= testConfigurations.size) {
            // All tests completed
            isRunning.set(false)

            // Make sure performance monitor is stopped on the main thread
            handler.post {
                try {
                    performanceMonitor.stopMonitoring()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping performance monitor on completion", e)
                }
            }

            val resultsCopy: List<BenchmarkResult>
            synchronized(results) {
                resultsCopy = results.toList()
            }

            _benchmarkState.value = BenchmarkState.Completed(resultsCopy)
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

        synchronized(fpsValues) {
            fpsValues.clear()
        }

        synchronized(memoryValues) {
            memoryValues.clear()
        }

        synchronized(cpuValues) {
            cpuValues.clear()
        }

        benchmarkJob = scope.launch {
            Log.d(
                TAG,
                "Starting benchmark test ${currentTestIndex + 1}/${testConfigurations.size} - ${config.library}"
            )

            // Wait for animations to fully load
            delay(2000)

            // Start collecting metrics - ensure on main thread
            withContext(Dispatchers.Main) {
                try {
                    performanceMonitor.startMonitoring()
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting performance monitoring", e)
                }
            }

            // Run test for specified duration
            val startTime = System.currentTimeMillis()
            val endTime = startTime + (config.durationSeconds * 1000)

            while (System.currentTimeMillis() < endTime && isRunning.get()) {
                val elapsedTime = System.currentTimeMillis() - startTime
                val progress = elapsedTime.toFloat() / (config.durationSeconds * 1000)

                _benchmarkState.value = BenchmarkState.Running(
                    config = config,
                    progress = progress,
                    currentTestIndex = currentTestIndex,
                    totalTests = testConfigurations.size
                )

                // Collect metrics
                val metrics = withContext(Dispatchers.Main) {
                    performanceMonitor.getPerformanceMetrics(context)
                }

                synchronized(fpsValues) {
                    fpsValues.add(metrics.fps)
                }

                synchronized(memoryValues) {
                    memoryValues.add(metrics.memoryUsageMb)
                }

                synchronized(cpuValues) {
                    cpuValues.add(metrics.cpuUsagePercent)
                }

                delay(500) // Update every 500ms
            }

            // Stop monitoring and collect results - ensure on main thread
            withContext(Dispatchers.Main) {
                try {
                    performanceMonitor.stopMonitoring()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping performance monitoring", e)
                }
            }

            // Calculate results in a thread-safe way
            var avgFps: Float
            var minFps: Float
            var maxFps: Float

            synchronized(fpsValues) {
                avgFps = if (fpsValues.isNotEmpty()) fpsValues.average().toFloat() else 0f
                minFps = fpsValues.minOrNull() ?: 0f
                maxFps = fpsValues.maxOrNull() ?: 0f
            }

            var avgMemory = 0L
            var peakMemory = 0L

            synchronized(memoryValues) {
                avgMemory = if (memoryValues.isNotEmpty()) memoryValues.average().toLong() else 0L
                peakMemory = memoryValues.maxOrNull() ?: 0L
            }

            var avgCpu = 0f
            var peakCpu = 0f

            synchronized(cpuValues) {
                avgCpu = if (cpuValues.isNotEmpty()) cpuValues.average().toFloat() else 0f
                peakCpu = cpuValues.maxOrNull() ?: 0f
            }

            // Save results
            val result = BenchmarkResult(
                config = config,
                averageFps = avgFps,
                minFps = minFps,
                maxFps = maxFps,
                averageMemoryMb = avgMemory,
                peakMemoryMb = peakMemory,
                averageCpuUsage = avgCpu,
                peakCpuUsage = peakCpu
            )

            synchronized(results) {
                results.add(result)
            }

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
                // Get app-specific directory - this doesn't require special permissions
                val appFilesDir = context.getExternalFilesDir(null)
                if (appFilesDir == null) {
                    Log.e(TAG, "Failed to access external files directory")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Failed to save benchmark results: Cannot access storage",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                // Create reports directory if needed
                val reportsDir = File(appFilesDir, "benchmark_reports")
                if (!reportsDir.exists() && !reportsDir.mkdirs()) {
                    Log.e(TAG, "Failed to create benchmark_reports directory")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Failed to save benchmark results: Cannot create directory",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
                val timestamp = dateFormat.format(Date())
                val fileName = "lottie_comparison_benchmark_$timestamp.csv"

                val file = File(reportsDir, fileName)
                val fos = FileOutputStream(file)

                // Write header
                val header = "# DotLottie vs Airbnb Lottie Performance Comparison\n"
                fos.write(header.toByteArray())

                // Add a note about interpolation
                val note = "# Feature comparison:\n"
                fos.write(note.toByteArray())

                // Add notes about the differences between libraries
                val libraryNotes =
                    "# - Frame interpolation: Only available in DotLottie. Airbnb Lottie doesn't support this feature.\n"
                fos.write(libraryNotes.toByteArray())

                // Add a note about file formats
                val formatNote =
                    "# - File formats: DotLottie supports both .json and .lottie formats. Airbnb Lottie only supports .json\n"
                fos.write(formatNote.toByteArray())

                // Add a note about using the same animation
                val animationNote =
                    "# - Both libraries are rendering the same animation for fair comparison\n\n"
                fos.write(animationNote.toByteArray())

                // Write column names
                val columns =
                    "Test,Library,Animations,Interpolation,Format,Avg FPS,Min FPS,Max FPS,Avg Memory (MB),Peak Memory (MB),Avg CPU (%),Peak CPU (%)\n"
                fos.write(columns.toByteArray())

                // Write data rows in a thread-safe way
                val resultsCopy: List<BenchmarkResult>
                synchronized(results) {
                    resultsCopy = results.toList()
                }

                resultsCopy.forEachIndexed { index, result ->
                    // For Airbnb Lottie, add special note about interpolation
                    val interpolationValue =
                        if (result.config.library == LibraryType.AIRBNB_LOTTIE) {
                            "Not Supported"
                        } else {
                            result.config.useInterpolation.toString()
                        }

                    val formatValue = when {
                        result.config.library == LibraryType.AIRBNB_LOTTIE -> ".json (only)"
                        result.config.fileFormat == FileFormat.JSON -> ".json"
                        result.config.fileFormat == FileFormat.LOTTIE -> ".lottie"
                        else -> result.config.fileFormat.toString()
                    }

                    val line =
                        "${index + 1},${result.config.library},${result.config.animationCount},$interpolationValue,$formatValue," +
                                "%.2f,%.2f,%.2f,%d,%d,%.2f,%.2f\n".format(
                                    result.averageFps,
                                    result.minFps,
                                    result.maxFps,
                                    result.averageMemoryMb,
                                    result.peakMemoryMb,
                                    result.averageCpuUsage,
                                    result.peakCpuUsage
                                )
                    fos.write(line.toByteArray())
                }

                fos.close()
                lastReportFile = file

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Benchmark report saved to ${file.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                Log.d(TAG, "Benchmark report saved to ${file.absolutePath}")

                // Also log to console for convenience
                Log.i(TAG, "Benchmark Results Summary:")
                resultsCopy.forEachIndexed { index, result ->
                    val interpolationInfo =
                        if (result.config.library == LibraryType.AIRBNB_LOTTIE) {
                            "interpolation not supported"
                        } else if (result.config.useInterpolation) {
                            "with interpolation"
                        } else {
                            "without interpolation"
                        }

                    val formattedResult = String.format(
                        "Test %d: %s - %d animations, %s, Avg FPS: %.2f, Memory: %d MB",
                        index + 1,
                        result.config.library,
                        result.config.animationCount,
                        interpolationInfo,
                        result.averageFps,
                        result.averageMemoryMb
                    )
                    Log.i(TAG, formattedResult)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate benchmark report", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Failed to save benchmark results: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * Share the benchmark results CSV file
     */
    fun shareBenchmarkResults() {
        lastReportFile?.let { file ->
            try {
                // Use FileProvider to generate a content URI
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_SUBJECT, "DotLottie Benchmark Results")
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Please find attached the benchmark results comparing DotLottie and Airbnb Lottie libraries."
                    )
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(intent, "Share Benchmark Results")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            } catch (e: Exception) {
                Log.e(TAG, "Error sharing benchmark results", e)
                Toast.makeText(context, "Error sharing results: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        } ?: run {
            // No results to share
            Toast.makeText(context, "No benchmark results available to share", Toast.LENGTH_SHORT)
                .show()
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
     * Enum representing the file format being tested
     */
    enum class FileFormat {
        JSON,
        LOTTIE
    }

    /**
     * Data class representing a test configuration
     */
    data class TestConfiguration(
        val animationCount: Int,
        val useInterpolation: Boolean,
        val durationSeconds: Int,
        val library: LibraryType,
        val fileFormat: FileFormat
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
        val peakMemoryMb: Long,
        val averageCpuUsage: Float,
        val peakCpuUsage: Float
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