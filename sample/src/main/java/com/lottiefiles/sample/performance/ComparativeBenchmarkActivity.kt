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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
        private val FRAME_INTERPOLATION = listOf(true, false)
        
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
    
    private val dotLottieViews = mutableListOf<LottieView>()
    private val airbnbLottieViews = mutableListOf<AirbnbLottieView>()
    
    private var currentTestIndex = 0
    private var currentLibraryIndex = 0 // 0 = DotLottie, 1 = Airbnb Lottie
    private val testResults = mutableListOf<BenchmarkResult>()
    
    private var isRunning = false
    private var startTime = 0L
    private var testStartupTime = 0L
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityComparativeBenchmarkBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        setupPerformanceMonitoring()
    }
    
    override fun onResume() {
        super.onResume()
        performanceMonitor.startMonitoring()
    }
    
    override fun onPause() {
        super.onPause()
        if (isRunning) {
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
        binding.btnStartBenchmark.isEnabled = false
        binding.btnStopBenchmark.isEnabled = true
        binding.btnShareResults.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.text = "Benchmark running..."
        
        isRunning = true
        currentTestIndex = 0
        currentLibraryIndex = 0
        testResults.clear()
        
        // Clear previous results
        binding.tvResults.text = ""
        
        runNextTest()
    }
    
    private fun stopBenchmark() {
        if (!isRunning) return
        
        isRunning = false
        binding.btnStartBenchmark.isEnabled = true
        binding.btnStopBenchmark.isEnabled = false
        binding.btnShareResults.isEnabled = true
        binding.progressBar.visibility = View.INVISIBLE
        binding.tvStatus.text = "Benchmark stopped"
        
        // Remove all animations
        clearAnimations()
    }
    
    private fun runNextTest() {
        if (!isRunning) return
        
        // Check if we've completed all tests
        if (currentLibraryIndex > 1) {
            currentLibraryIndex = 0
            currentTestIndex++
        }
        
        // Check if we've completed all test combinations
        val totalTestConfigs = ANIMATION_COUNTS.size * ANIMATION_SIZES.size * FRAME_INTERPOLATION.size
        if (currentTestIndex >= totalTestConfigs) {
            finalizeBenchmark()
            return
        }
        
        // Calculate current test configuration
        val countIndex = currentTestIndex / (ANIMATION_SIZES.size * FRAME_INTERPOLATION.size)
        val sizeIndex = (currentTestIndex % (ANIMATION_SIZES.size * FRAME_INTERPOLATION.size)) / FRAME_INTERPOLATION.size
        val interpolationIndex = currentTestIndex % FRAME_INTERPOLATION.size
        
        val animationCount = ANIMATION_COUNTS[countIndex]
        val animationSize = ANIMATION_SIZES[sizeIndex]
        val useInterpolation = FRAME_INTERPOLATION[interpolationIndex]
        
        // Determine current library
        val libraryName = if (currentLibraryIndex == 0) "DotLottie" else "Airbnb Lottie"
        
        // Update UI
        val progress = (currentTestIndex * 100f) / totalTestConfigs
        binding.progressBar.progress = progress.toInt()
        binding.tvStatus.text = "Testing $libraryName"
        binding.tvSubStatus.text = "$animationCount animations, ${animationSize}dp, Interpolation: $useInterpolation"
        
        // Clear previous animations
        clearAnimations()
        
        // Create new animations based on the current test configuration
        createAnimations(animationCount, animationSize, useInterpolation)
        
        // Start the test with a warmup period
        testStartupTime = System.currentTimeMillis()
        
        Handler(Looper.getMainLooper()).postDelayed({
            // Warmup complete, start recording metrics
            startTime = System.currentTimeMillis()
            
            // End test after duration
            Handler(Looper.getMainLooper()).postDelayed({
                // Record metrics
                val metrics = performanceMonitor.getCurrentMetrics()
                val startupTime = testStartupTime - System.currentTimeMillis()
                
                // Create result object
                val result = BenchmarkResult(
                    library = libraryName,
                    animationCount = animationCount,
                    animationSize = animationSize,
                    useFrameInterpolation = useInterpolation,
                    fps = metrics.fps,
                    memoryUsageMb = metrics.memoryUsageMb,
                    jankPercentage = metrics.jankPercentage,
                    startupTimeMs = startupTime
                )
                
                testResults.add(result)
                
                // Update results display
                updateResults(result)
                
                // Move to next test
                currentLibraryIndex++
                runNextTest()
                
            }, TEST_DURATION_MS)
            
        }, WARMUP_DURATION_MS)
    }
    
    private fun createAnimations(count: Int, size: Int, useInterpolation: Boolean) {
        val container = binding.animationContainer
        
        if (currentLibraryIndex == 0) {
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
                view.setFrameInterpolation(useInterpolation)
                
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
        
        val views = if (currentLibraryIndex == 0) dotLottieViews else airbnbLottieViews
        
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
        val resultText = "${result.library}: ${result.animationCount} animations, " +
                "${result.animationSize}dp, " +
                "FPS: ${String.format("%.1f", result.fps)}, " +
                "Jank: ${String.format("%.1f", result.jankPercentage)}%, " +
                "Memory: ${String.format("%.1f", result.memoryUsageMb)} MB\n"
                
        binding.tvResults.append(resultText)
    }
    
    private fun finalizeBenchmark() {
        isRunning = false
        binding.btnStartBenchmark.isEnabled = true
        binding.btnStopBenchmark.isEnabled = false
        binding.btnShareResults.isEnabled = true
        binding.progressBar.visibility = View.INVISIBLE
        binding.tvStatus.text = "Benchmark completed"
        
        // Generate comparison report
        val reportFile = generateReport()
        reportFile?.let {
            binding.tvSubStatus.text = "Report saved: ${it.name}"
        }
    }
    
    private fun generateReport(): File? {
        if (testResults.isEmpty()) return null
        
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "lottie_comparison_$timestamp.csv"
            
            val file = File(getExternalFilesDir(null), fileName)
            FileOutputStream(file).use { fos ->
                // Write header
                val header = "# DotLottie vs Airbnb Lottie Performance Comparison\n"
                fos.write(header.toByteArray())
                
                // Write column names
                val columns = "Library,Animation Count,Size (dp),Frame Interpolation,FPS,Memory (MB),Jank %,Startup Time (ms)\n"
                fos.write(columns.toByteArray())
                
                // Write data rows
                for (result in testResults) {
                    val row = "${result.library},${result.animationCount},${result.animationSize},${result.useFrameInterpolation}," +
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
    
    private fun shareResults() {
        val filesDir = getExternalFilesDir(null) ?: return
        val files = filesDir.listFiles { file -> file.name.startsWith("lottie_comparison_") && file.extension == "csv" }
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
        val useFrameInterpolation: Boolean,
        val fps: Float,
        val memoryUsageMb: Float,
        val jankPercentage: Float,
        val startupTimeMs: Long
    )
} 