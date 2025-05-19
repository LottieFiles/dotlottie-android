package com.lottiefiles.sample.performance

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.lottiefiles.sample.R
import com.lottiefiles.sample.databinding.ActivityComparativeBenchmarkBinding
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Activity for running automated benchmark tests comparing DotLottie and Airbnb Lottie.
 * Displays performance metrics and generates comparison reports.
 */
class ComparativeBenchmarkActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "ComparativeBenchmark"
        private const val ANIMATION_URL_JSON = "https://lottie.host/f8e7eccf-72da-40da-9dd1-0fdbdc93b9ea/yAX2Nay9jD.json"
        private const val ANIMATION_URL_LOTTIE = "https://lottiefiles-mobile-templates.s3.amazonaws.com/ar-stickers/swag_sticker_piggy.lottie"
        
        // Test configurations
        private val ANIMATION_COUNTS = listOf(1, 5, 10, 20, 30)
        private val ANIMATION_SIZES = listOf(100, 200)
        
        // DotLottie supports interpolation, Airbnb Lottie doesn't
        private val INTERPOLATION_DOT_LOTTIE = listOf(true, false)
        private val INTERPOLATION_AIRBNB_LOTTIE = listOf(false)
        
        // Test durations
        private const val WARMUP_DURATION_MS = 3000L
        private const val TEST_DURATION_MS = 10000L
        
        fun start(context: Context) {
            val intent = Intent(context, ComparativeBenchmarkActivity::class.java)
            context.startActivity(intent)
        }
    }
    
    private lateinit var binding: ActivityComparativeBenchmarkBinding
    private lateinit var performanceMonitor: PerformanceMonitor
    private lateinit var permissionsHelper: PermissionsHelper
    
    private val dotLottieViews = mutableListOf<LottieView>()
    private val airbnbLottieViews = mutableListOf<AirbnbLottieView>()
    
    // Thread-safe state variables
    private val isRunning = AtomicBoolean(false)
    private val currentTestIndex = AtomicInteger(0)
    private val currentLibraryIndex = AtomicInteger(0) // 0 = DotLottie, 1 = Airbnb Lottie
    
    private val testResults = mutableListOf<BenchmarkResult>()
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var startTime = 0L
    private var testStartupTime = 0L
    
    // Save Activity reference for error handling
    private val activityReference = WeakReference<ComparativeBenchmarkActivity>(this)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityComparativeBenchmarkBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        permissionsHelper = PermissionsHelper(this)
        setupUI()
        setupPerformanceMonitoring()
    }
    
    override fun onResume() {
        super.onResume()
        performanceMonitor.startMonitoring()
    }
    
    override fun onPause() {
        super.onPause()
        if (isRunning.get()) {
            stopBenchmark()
        }
        performanceMonitor.stopMonitoring()
    }
    
    private fun setupUI() {
        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Comparative Benchmark"
        
        // Setup benchmark controls
        binding.btnStartBenchmark.setOnClickListener {
            if (isRunning.get()) {
                Toast.makeText(this, "Benchmark already running", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startBenchmark()
        }
        
        binding.btnStopBenchmark.setOnClickListener {
            stopBenchmark()
        }
        
        binding.btnShareResults.setOnClickListener {
            shareResults()
        }
        
        // Apply edge-to-edge immersive mode
        applyEdgeToEdge()
    }
    
    private fun setupPerformanceMonitoring() {
        performanceMonitor = PerformanceMonitor(this)
        
        // Add overlay to the root view
        binding.overlayContainer.bringToFront()
        
        // Connect performance monitor to overlay
        performanceMonitor.addListener(binding.performanceOverlay)
    }
    
    private fun startBenchmark() {
        if (!permissionsHelper.getBenchmarkStorageDirectory()?.canWrite() == true) {
            permissionsHelper.requestStoragePermission(this)
            Toast.makeText(this, "Storage permission needed to save benchmark results", Toast.LENGTH_LONG).show()
            return
        }
        
        binding.btnStartBenchmark.isEnabled = false
        binding.btnStopBenchmark.isEnabled = true
        binding.btnShareResults.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.text = "Benchmark running..."
        
        isRunning.set(true)
        currentTestIndex.set(0)
        currentLibraryIndex.set(0)
        
        synchronized(testResults) {
            testResults.clear()
        }
        
        // Clear previous results
        binding.tvResults.text = ""
        
        runNextTest()
    }
    
    private fun stopBenchmark() {
        if (!isRunning.getAndSet(false)) return
        
        binding.btnStartBenchmark.isEnabled = true
        binding.btnStopBenchmark.isEnabled = false
        binding.btnShareResults.isEnabled = true
        binding.progressBar.visibility = View.INVISIBLE
        binding.tvStatus.text = "Benchmark stopped"
        
        // Cancel any pending operations
        mainHandler.removeCallbacksAndMessages(null)
        
        // Remove all animations
        clearAnimations()
    }
    
    private fun runNextTest() {
        if (!isRunning.get()) return
        
        // Check if we've completed all tests for the current library
        val libraryIndex = currentLibraryIndex.get()
        if (libraryIndex > 1) {
            currentLibraryIndex.set(0)
            currentTestIndex.incrementAndGet()
        }
        
        // Calculate total test counts
        val dotLottieTestCount = ANIMATION_COUNTS.size * ANIMATION_SIZES.size * INTERPOLATION_DOT_LOTTIE.size
        val airbnbLottieTestCount = ANIMATION_COUNTS.size * ANIMATION_SIZES.size * INTERPOLATION_AIRBNB_LOTTIE.size
        val totalTestCount = dotLottieTestCount + airbnbLottieTestCount
        
        // Check if we've completed all test combinations
        val testIndex = currentTestIndex.get()
        if (calculateTestsCompleted() >= totalTestCount) {
            finalizeBenchmark()
            return
        }
        
        // Calculate current test configuration indices
        val countConfigs = ANIMATION_COUNTS.size
        val sizeConfigs = ANIMATION_SIZES.size
        
        val countIndex = testIndex / (sizeConfigs * 2) // 2 libraries
        val remainingIndex = testIndex % (sizeConfigs * 2)
        val sizeIndex = remainingIndex / 2
        
        // Make sure indices are in bounds
        if (countIndex >= ANIMATION_COUNTS.size || sizeIndex >= ANIMATION_SIZES.size) {
            finalizeBenchmark()
            return
        }
        
        val animationCount = ANIMATION_COUNTS[countIndex]
        val animationSize = ANIMATION_SIZES[sizeIndex]
        
        // Handle interpolation based on library - Airbnb Lottie doesn't support it
        val useInterpolation = if (libraryIndex == 0) {
            // For DotLottie, use the appropriate interpolation setting
            val interpolationIndex = testIndex % INTERPOLATION_DOT_LOTTIE.size
            INTERPOLATION_DOT_LOTTIE[interpolationIndex]
        } else {
            // Airbnb Lottie doesn't support interpolation
            false
        }
        
        // Determine current library
        val libraryName = if (libraryIndex == 0) "DotLottie" else "Airbnb Lottie"
        
        // Update UI 
        val progress = (calculateTestsCompleted() * 100f) / totalTestCount
        binding.progressBar.progress = progress.toInt()
        binding.tvStatus.text = "Testing $libraryName"
        
        val interpolationText = if (libraryIndex == 1 && useInterpolation) 
            "N/A (not supported)" 
        else 
            useInterpolation.toString()
            
        binding.tvSubStatus.text = "$animationCount animations, ${animationSize}dp, Interpolation: $interpolationText"
        
        // Clear previous animations
        clearAnimations()
        
        try {
            // Create new animations based on the current test configuration
            createAnimations(animationCount, animationSize, useInterpolation)
            
            // Start the test with a warmup period
            testStartupTime = System.currentTimeMillis()
            
            mainHandler.postDelayed({
                if (!isRunning.get()) return@postDelayed
                
                // Warmup complete, start recording metrics
                startTime = System.currentTimeMillis()
                
                // End test after duration
                mainHandler.postDelayed({
                    if (!isRunning.get()) return@postDelayed
                    
                    // Record metrics
                    val metrics = performanceMonitor.getCurrentMetrics()
                    val startupTime = System.currentTimeMillis() - testStartupTime
                    
                    // Create result object
                    val result = BenchmarkResult(
                        library = libraryName,
                        animationCount = animationCount,
                        animationSize = animationSize,
                        useFrameInterpolation = if (libraryIndex == 1) "N/A" else useInterpolation.toString(),
                        fps = metrics.fps,
                        memoryUsageMb = metrics.memoryUsageMb,
                        jankPercentage = metrics.jankPercentage,
                        cpuUsage = metrics.cpuUsage,
                        startupTimeMs = startupTime
                    )
                    
                    // Add to results list
                    synchronized(testResults) {
                        testResults.add(result)
                    }
                    
                    // Update results display
                    updateResults(result)
                    
                    // Move to next test
                    currentLibraryIndex.incrementAndGet()
                    runNextTest()
                    
                }, TEST_DURATION_MS)
                
            }, WARMUP_DURATION_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Error during test execution: ${e.message}", e)
            
            // Try to recover by moving to the next test
            currentLibraryIndex.incrementAndGet()
            mainHandler.postDelayed({ runNextTest() }, 1000)
        }
    }
    
    /**
     * Calculate how many tests have been completed so far
     */
    private fun calculateTestsCompleted(): Int {
        val testIndex = currentTestIndex.get()
        val libraryIndex = currentLibraryIndex.get()
        return testIndex * 2 + libraryIndex  // 2 libraries per test configuration
    }
    
    private fun createAnimations(count: Int, size: Int, useInterpolation: Boolean) {
        val container = binding.animationContainer
        
        if (currentLibraryIndex.get() == 0) {
            // Create DotLottie animations
            for (i in 0 until count) {
                val view = LottieView(this)
                view.setAnimationUrl(ANIMATION_URL_LOTTIE)
                view.setFrameInterpolation(useInterpolation)
                
                val params = FrameLayout.LayoutParams(size, size)
                container.addView(view, params)
                dotLottieViews.add(view)
            }
        } else {
            // Create Airbnb Lottie animations
            for (i in 0 until count) {
                val view = AirbnbLottieView(this)
                view.setAnimationUrl(ANIMATION_URL_JSON)
                // Airbnb Lottie doesn't support interpolation, ignore the parameter
                
                val params = FrameLayout.LayoutParams(size, size)
                container.addView(view, params)
                airbnbLottieViews.add(view)
            }
        }
        
        // Arrange views in a grid
        layoutAnimations()
    }
    
    private fun clearAnimations() {
        binding.animationContainer.removeAllViews()
        
        // Clear lists
        for (view in dotLottieViews) {
            view.stop()
        }
        dotLottieViews.clear()
        
        for (view in airbnbLottieViews) {
            view.stop()
        }
        airbnbLottieViews.clear()
    }
    
    private fun layoutAnimations() {
        val count = dotLottieViews.size + airbnbLottieViews.size
        if (count == 0) return
        
        val views = if (currentLibraryIndex.get() == 0) dotLottieViews else airbnbLottieViews
        
        val columns = max(1, sqrt(count.toFloat()).toInt())
        val rows = (count + columns - 1) / columns
        
        val containerWidth = binding.animationContainer.width
        val containerHeight = binding.animationContainer.height
        
        if (containerWidth <= 0 || containerHeight <= 0) return
        
        val cellWidth = containerWidth / columns
        val cellHeight = containerHeight / rows
        
        for (i in 0 until views.size) {
            val row = i / columns
            val col = i % columns
            
            val view = views[i]
            val params = view.layoutParams as FrameLayout.LayoutParams
            
            // Get current animation size
            val size = params.width
            
            // Center in the cell
            params.leftMargin = col * cellWidth + (cellWidth - size) / 2
            params.topMargin = row * cellHeight + (cellHeight - size) / 2
            
            view.layoutParams = params
        }
    }
    
    private fun updateResults(result: BenchmarkResult) {
        val interpolationText = if (result.library == "Airbnb Lottie") "N/A" else result.useFrameInterpolation
        
        val resultText = "${result.library}: ${result.animationCount} animations, " +
                "${result.animationSize}dp, " +
                "Interpolation: $interpolationText, " +
                "FPS: ${String.format("%.1f", result.fps)}, " +
                "Jank: ${String.format("%.1f", result.jankPercentage)}%, " +
                "CPU: ${String.format("%.1f", result.cpuUsage)}%, " +
                "Memory: ${String.format("%.1f", result.memoryUsageMb)} MB\n"
                
        binding.tvResults.append(resultText)
    }
    
    private fun finalizeBenchmark() {
        isRunning.set(false)
        binding.btnStartBenchmark.isEnabled = true
        binding.btnStopBenchmark.isEnabled = false
        binding.btnShareResults.isEnabled = true
        binding.progressBar.visibility = View.INVISIBLE
        binding.tvStatus.text = "Benchmark completed"
        
        // Make a copy to avoid concurrent modification
        val resultsCopy: List<BenchmarkResult>
        synchronized(testResults) {
            resultsCopy = testResults.toList()
        }
        
        // Generate comparison report in background
        Thread {
            val reportFile = generateReport(resultsCopy)
            
            // Update UI on main thread
            mainHandler.post {
                if (reportFile != null) {
                    binding.tvSubStatus.text = "Report saved: ${reportFile.name}"
                    Toast.makeText(
                        this@ComparativeBenchmarkActivity,
                        "Benchmark report saved",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    binding.tvSubStatus.text = "Failed to save report"
                    Toast.makeText(
                        this@ComparativeBenchmarkActivity,
                        "Failed to save benchmark report",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.start()
    }
    
    private fun generateReport(results: List<BenchmarkResult>): File? {
        if (results.isEmpty()) return null
        
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "lottie_comparison_$timestamp.csv"
            
            // Use app-specific directory that doesn't require special permissions
            val storageDir = permissionsHelper.getBenchmarkStorageDirectory()
                ?: getExternalFilesDir(null)
                ?: throw IOException("Could not access external storage")
            
            val file = File(storageDir, fileName)
            
            try {
                FileOutputStream(file).use { fos ->
                    // Write header
                    val header = "# DotLottie vs Airbnb Lottie Performance Comparison\n"
                    fos.write(header.toByteArray())
                    
                    // Add a note about interpolation
                    val note = "# Note: Frame interpolation is only supported in DotLottie, not in Airbnb Lottie\n"
                    fos.write(note.toByteArray())
                    
                    // Write column names
                    val columns = "Library,Animation Count,Size (dp),Frame Interpolation,FPS,Memory (MB),Jank %,CPU %,Startup Time (ms)\n"
                    fos.write(columns.toByteArray())
                    
                    // Write data rows
                    for (result in results) {
                        val row = "${result.library},${result.animationCount},${result.animationSize},${result.useFrameInterpolation}," +
                                "${String.format("%.1f", result.fps)},${String.format("%.1f", result.memoryUsageMb)}," +
                                "${String.format("%.1f", result.jankPercentage)},${String.format("%.1f", result.cpuUsage)}," +
                                "${result.startupTimeMs}\n"
                        fos.write(row.toByteArray())
                    }
                }
                
                return file
            } catch (e: IOException) {
                Log.e(TAG, "Error writing report file", e)
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating report", e)
            return null
        }
    }
    
    private fun shareResults() {
        val storageDir = permissionsHelper.getBenchmarkStorageDirectory() ?: getExternalFilesDir(null)
        val files = storageDir?.listFiles { file -> 
            file.name.startsWith("lottie_comparison_") && file.extension == "csv" 
        }
        
        val reportFile = files?.maxByOrNull { it.lastModified() }
        
        reportFile?.let { file ->
            try {
                val uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )
                
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Lottie Libraries Comparison Results")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                startActivity(Intent.createChooser(intent, "Share Comparison Results"))
            } catch (e: Exception) {
                Toast.makeText(this, "Error sharing report: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(this, "No comparison results available", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun applyEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            binding.appBarLayout.setPadding(0, insets.top, 0, 0)
            binding.scrollView.setPadding(0, 0, 0, insets.bottom)
            
            WindowInsetsCompat.CONSUMED
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    /**
     * Benchmark result data class
     */
    data class BenchmarkResult(
        val library: String,
        val animationCount: Int,
        val animationSize: Int,
        val useFrameInterpolation: String,
        val fps: Float,
        val memoryUsageMb: Float,
        val jankPercentage: Float,
        val cpuUsage: Float = 0f,
        val startupTimeMs: Long
    )
} 