package com.lottiefiles.sample.performance

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.lottiefiles.sample.R
import com.lottiefiles.sample.databinding.ActivityBenchmarkBinding
import java.io.File
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Activity for running automated benchmark tests on Lottie animations.
 * Displays performance metrics and generates reports.
 */
class BenchmarkActivity : AppCompatActivity(), BenchmarkRunner.OnTestCompletedListener {
    companion object {
        private const val DEFAULT_ANIMATION_URL = "https://lottiefiles-mobile-templates.s3.amazonaws.com/ar-stickers/swag_sticker_piggy.lottie"
        private const val ANIMATION_CONTAINER_SIZE = 800 // dp
        
        fun start(context: Context) {
            val intent = Intent(context, BenchmarkActivity::class.java)
            context.startActivity(intent)
        }
    }
    
    private lateinit var binding: ActivityBenchmarkBinding
    private lateinit var benchmarkRunner: BenchmarkRunner
    private lateinit var performanceMonitor: PerformanceMonitor
    
    private val animationViews = mutableListOf<LottieView>()
    private var animationSize = 100
    private var useFrameInterpolation = true
    private var lastReportFile: File? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityBenchmarkBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        setupPerformanceMonitoring()
        initializeBenchmarkRunner()
    }
    
    override fun onResume() {
        super.onResume()
        performanceMonitor.startMonitoring()
    }
    
    override fun onPause() {
        super.onPause()
        performanceMonitor.stopMonitoring()
    }
    
    private fun setupUI() {
        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Lottie Benchmark"
        
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
    
    private fun initializeBenchmarkRunner() {
        benchmarkRunner = BenchmarkRunner(this)
        benchmarkRunner.setOnTestCompletedListener(this)
        
        // Set default configuration
        val config = BenchmarkRunner.BenchmarkConfig(
            animationCounts = listOf(1, 5, 10, 20, 30, 50),
            animationSizes = listOf(100, 200),
            useFrameInterpolation = true
        )
        
        benchmarkRunner.setConfig(config)
        benchmarkRunner.setBenchmarkTitle("DotLottie-Android Performance Benchmark")
    }
    
    private fun startBenchmark() {
        binding.btnStartBenchmark.isEnabled = false
        binding.btnStopBenchmark.isEnabled = true
        binding.btnShareResults.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.text = "Benchmark running..."
        
        benchmarkRunner.startBenchmark()
    }
    
    private fun stopBenchmark() {
        benchmarkRunner.stopBenchmark()
    }
    
    private fun shareResults() {
        lastReportFile?.let { file ->
            try {
                val uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )
                
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Lottie Performance Benchmark Results")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                startActivity(Intent.createChooser(intent, "Share Benchmark Results"))
            } catch (e: Exception) {
                Toast.makeText(this, "Error sharing report: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(this, "No benchmark results available", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateAnimationCount(count: Int) {
        // Remove all existing views
        for (view in animationViews) {
            binding.animationContainer.removeView(view)
        }
        animationViews.clear()
        
        // Add new views
        for (i in 0 until count) {
            addAnimationView()
        }
        
        updateAnimationLayout()
    }
    
    private fun addAnimationView() {
        val lottieView = LottieView(this)
        lottieView.setAnimationUrl(DEFAULT_ANIMATION_URL)
        lottieView.setFrameInterpolation(useFrameInterpolation)
        
        val params = FrameLayout.LayoutParams(
            animationSize,
            animationSize
        )
        
        binding.animationContainer.addView(lottieView, params)
        animationViews.add(lottieView)
    }
    
    private fun updateAnimationSize(size: Int) {
        animationSize = size
        
        for (view in animationViews) {
            val params = view.layoutParams
            params.width = size
            params.height = size
            view.layoutParams = params
        }
        
        updateAnimationLayout()
    }
    
    private fun updateFrameInterpolation(enabled: Boolean) {
        useFrameInterpolation = enabled
        
        for (view in animationViews) {
            view.setFrameInterpolation(enabled)
        }
    }
    
    private fun updateAnimationLayout() {
        // Calculate grid dimensions based on animation count
        val count = animationViews.size
        if (count == 0) return
        
        val columns = max(1, sqrt(count.toFloat()).toInt())
        val rows = (count + columns - 1) / columns
        
        val containerWidth = binding.animationContainer.width
        val containerHeight = binding.animationContainer.height
        
        if (containerWidth <= 0 || containerHeight <= 0) return
        
        val cellWidth = containerWidth / columns
        val cellHeight = containerHeight / rows
        
        for (i in 0 until count) {
            val row = i / columns
            val col = i % columns
            
            val view = animationViews[i]
            val params = view.layoutParams as FrameLayout.LayoutParams
            
            // Center in the cell
            params.leftMargin = col * cellWidth + (cellWidth - animationSize) / 2
            params.topMargin = row * cellHeight + (cellHeight - animationSize) / 2
            
            view.layoutParams = params
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
    
    // BenchmarkRunner.OnTestCompletedListener implementation
    override fun onTestStarted(testIndex: Int, animationCount: Int, size: Int, useInterpolation: Boolean) {
        runOnUiThread {
            binding.tvStatus.text = "Test ${testIndex + 1}: Running with $animationCount animations"
            binding.tvSubStatus.text = "Size: ${size}dp, Interpolation: $useInterpolation"
            binding.progressBar.progress = ((testIndex * 100f) / 6).toInt()
            
            updateAnimationCount(animationCount)
            updateAnimationSize(size)
            updateFrameInterpolation(useInterpolation)
        }
    }
    
    override fun onTestCompleted(testIndex: Int, result: BenchmarkRunner.BenchmarkResult) {
        runOnUiThread {
            binding.tvResults.append(
                "Test ${testIndex + 1}: ${result.animationCount} animations, " +
                        "${result.animationSize}dp, " +
                        "FPS: ${String.format("%.1f", result.fps)}, " +
                        "Jank: ${String.format("%.1f", result.jankPercentage)}%\n"
            )
        }
    }
    
    override fun onAllTestsCompleted(results: List<BenchmarkRunner.BenchmarkResult>) {
        runOnUiThread {
            binding.btnStartBenchmark.isEnabled = true
            binding.btnStopBenchmark.isEnabled = false
            binding.btnShareResults.isEnabled = true
            binding.progressBar.visibility = View.INVISIBLE
            binding.tvStatus.text = "Benchmark completed"
            
            // Find and display the report file
            lastReportFile = getReportFile()
            lastReportFile?.let { file ->
                binding.tvSubStatus.text = "Report saved: ${file.name}"
            }
        }
    }
    
    private fun getReportFile(): File? {
        val filesDir = getExternalFilesDir(null) ?: return null
        val files = filesDir.listFiles { file -> file.name.startsWith("lottie_benchmark_") && file.extension == "csv" }
        return files?.maxByOrNull { it.lastModified() }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
} 