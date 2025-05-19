package com.lottiefiles.sample.performance

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import com.lottiefiles.sample.R

/**
 * A wrapper component for Airbnb's LottieAnimationView that provides a similar API
 * to the DotLottie LottieView for performance comparison testing.
 */
class AirbnbLottieView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val lottieAnimation: LottieAnimationView
    
    private var url: String = "https://lottiefiles-mobile-templates.s3.amazonaws.com/ar-stickers/swag_sticker_piggy.lottie"
    private var autoPlay: Boolean = true
    private var looping: Boolean = true
    private var speed: Float = 1.0f
    
    init {
        LayoutInflater.from(context).inflate(R.layout.airbnb_lottie_view, this, true)
        lottieAnimation = findViewById(R.id.airbnb_lottie_animation)
        loadAnimation()
    }
    
    /**
     * Set the URL of the animation
     */
    fun setAnimationUrl(url: String) {
        this.url = url
        loadAnimation()
    }
    
    /**
     * Set autoplay state
     */
    fun setAutoPlay(autoPlay: Boolean) {
        this.autoPlay = autoPlay
        lottieAnimation.apply {
            if (autoPlay) {
                playAnimation()
            } else {
                pauseAnimation()
            }
        }
    }
    
    /**
     * Set looping state
     */
    fun setLooping(looping: Boolean) {
        this.looping = looping
        lottieAnimation.repeatCount = if (looping) -1 else 0
    }
    
    /**
     * Set playback speed
     */
    fun setPlaybackSpeed(speed: Float) {
        this.speed = speed
        lottieAnimation.speed = speed
    }
    
    /**
     * Set frame interpolation (not supported in Airbnb Lottie)
     * Added for API compatibility with DotLottie's LottieView
     */
    fun setFrameInterpolation(enabled: Boolean) {
        // Not supported in Airbnb Lottie, but keep the method for API compatibility
    }
    
    /**
     * Load the animation with current settings
     */
    private fun loadAnimation() {
        // For URLs we need to determine if it's a .json or .lottie file
        when {
            url.endsWith(".json") -> {
                lottieAnimation.setAnimationFromUrl(url)
            }
            url.endsWith(".lottie") -> {
                // For .lottie files (which are zipped), we need to download and extract
                // This is a simple implementation; in a real app you'd want to cache the results
                loadLottieFromUrl(url)
            }
            else -> {
                // Default to URL loading and let Lottie handle it
                lottieAnimation.setAnimationFromUrl(url)
            }
        }
        
        lottieAnimation.apply {
            repeatCount = if (looping) -1 else 0
            speed = this@AirbnbLottieView.speed
            if (autoPlay) {
                playAnimation()
            }
        }
    }
    
    /**
     * Load a .lottie file from a URL
     * This is a simple implementation for testing
     */
    private fun loadLottieFromUrl(url: String) {
        // In a real implementation, you would:
        // 1. Download the .lottie file
        // 2. Unzip it to extract the animation.json
        // 3. Load the JSON into Lottie
        
        // For now, we'll just attempt to load the URL directly
        // and let Airbnb Lottie try to handle it
        lottieAnimation.setAnimationFromUrl(url)
    }
    
    /**
     * Play the animation
     */
    fun play() {
        lottieAnimation.playAnimation()
    }
    
    /**
     * Pause the animation
     */
    fun pause() {
        lottieAnimation.pauseAnimation()
    }
    
    /**
     * Stop the animation
     */
    fun stop() {
        lottieAnimation.cancelAnimation()
    }
    
    /**
     * Get the underlying LottieAnimationView instance
     */
    fun getLottieAnimation(): LottieAnimationView {
        return lottieAnimation
    }
} 