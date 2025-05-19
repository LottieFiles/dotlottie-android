package com.lottiefiles.sample.performance

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.lottiefiles.sample.R
import com.lottiefiles.sample.databinding.ActivityPerformanceTestBinding
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Activity for testing the performance of multiple Lottie animations simultaneously.
 * Provides controls to adjust the number of animations, their size, and other parameters.
 */
class PerformanceTestActivity : AppCompatActivity() {
    companion object {
        private const val DEFAULT_ANIMATION_URL = "https://lottiefiles-mobile-templates.s3.amazonaws.com/ar-stickers/swag_sticker_piggy.lottie"
        private val ANIMATION_URLS = listOf(
            "https://lottiefiles-mobile-templates.s3.amazonaws.com/ar-stickers/swag_sticker_piggy.lottie",
            "https://lottie.host/f8e7eccf-72da-40da-9dd1-0fdbdc93b9ea/yAX2Nay9jD.json",
            "https://lottie.host/68cb18b9-7b21-43f1-af26-960e30c55134/TkjEKmaXl4.json",
            "https://lottie.host/dcdec187-bbd9-4e93-9479-979e97f5c3ac/r2h3LzKp0p.json"
        )
    }
    
    private lateinit var binding: ActivityPerformanceTestBinding
    private lateinit var performanceMonitor: PerformanceMonitor
    
    private val animationViews = mutableListOf<LottieView>()
    private var currentAnimationCount = 0
    private var animationSize = 100
    private var useFrameInterpolation = true
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityPerformanceTestBinding.inflate(layoutInflater)
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
        supportActionBar?.title = "Lottie Performance Test"
        
        // Setup animation count slider
        binding.sliderAnimationCount.addOnChangeListener { _, value, _ ->
            val count = value.toInt()
            binding.tvAnimationCount.text = "Animation Count: $count"
            updateAnimationCount(count)
        }
        
        // Setup animation size slider
        binding.sliderAnimationSize.addOnChangeListener { _, value, _ ->
            animationSize = value.toInt()
            binding.tvAnimationSize.text = "Animation Size: ${animationSize}dp"
            updateAnimationSize(animationSize)
        }
        
        // Setup frame interpolation toggle
        binding.switchInterpolation.setOnCheckedChangeListener { _, isChecked ->
            useFrameInterpolation = isChecked
            updateFrameInterpolation(isChecked)
        }
        
        // Setup animation URL spinner
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            ANIMATION_URLS.map { url -> url.substringAfterLast("/") }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerAnimation.adapter = adapter
        binding.spinnerAnimation.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val url = ANIMATION_URLS[position]
                updateAnimationUrl(url)
            }
            
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        
        // Benchmark button
        binding.btnRunBenchmark.setOnClickListener {
            startBenchmarkActivity()
        }
        
        // Reset to default settings
        binding.btnReset.setOnClickListener {
            resetToDefaults()
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
        // Initialize but don't start - will be used in the BenchmarkActivity
    }
    
    private fun updateAnimationCount(count: Int) {
        val diff = count - currentAnimationCount
        
        if (diff > 0) {
            // Add more animations
            for (i in 0 until diff) {
                addAnimationView()
            }
        } else if (diff < 0) {
            // Remove animations
            for (i in 0 until -diff) {
                removeLastAnimationView()
            }
        }
        
        currentAnimationCount = count
        updateAnimationLayout()
    }
    
    private fun addAnimationView() {
        val lottieView = LottieView(this)
        lottieView.setAnimationUrl(ANIMATION_URLS.first())
        lottieView.setFrameInterpolation(useFrameInterpolation)
        
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        
        binding.animationContainer.addView(lottieView, params)
        animationViews.add(lottieView)
    }
    
    private fun removeLastAnimationView() {
        if (animationViews.isNotEmpty()) {
            val view = animationViews.removeAt(animationViews.size - 1)
            binding.animationContainer.removeView(view)
        }
    }
    
    private fun updateAnimationSize(size: Int) {
        for (view in animationViews) {
            val params = view.layoutParams
            params.width = size
            params.height = size
            view.layoutParams = params
        }
    }
    
    private fun updateFrameInterpolation(enabled: Boolean) {
        for (view in animationViews) {
            view.setFrameInterpolation(enabled)
        }
    }
    
    private fun updateAnimationUrl(url: String) {
        for (view in animationViews) {
            view.setAnimationUrl(url)
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
    
    private fun resetToDefaults() {
        binding.sliderAnimationCount.value = 1f
        binding.sliderAnimationSize.value = 100f
        binding.switchInterpolation.isChecked = true
        binding.spinnerAnimation.setSelection(0)
    }
    
    private fun startBenchmarkActivity() {
        BenchmarkActivity.start(this)
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
} 