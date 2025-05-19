package com.lottiefiles.sample.performance

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.dotlottie.dlplayer.Mode
import com.lottiefiles.dotlottie.core.model.Config
import com.lottiefiles.dotlottie.core.util.DotLottieSource
import com.lottiefiles.dotlottie.core.widget.DotLottieAnimation
import com.lottiefiles.sample.R

/**
 * A wrapper component for DotLottieAnimation that simplifies creating multiple instances
 * for performance testing.
 */
class LottieView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val lottieAnimation: DotLottieAnimation
    
    private var url: String = "https://lottiefiles-mobile-templates.s3.amazonaws.com/ar-stickers/swag_sticker_piggy.lottie"
    private var autoPlay: Boolean = true
    private var looping: Boolean = true
    private var playMode: Mode = Mode.FORWARD
    private var speed: Float = 1.0f
    private var useFrameInterpolation: Boolean = true
    
    init {
        LayoutInflater.from(context).inflate(R.layout.lottie_view, this, true)
        lottieAnimation = findViewById(R.id.lottie_animation)
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
        loadAnimation()
    }
    
    /**
     * Set looping state
     */
    fun setLooping(looping: Boolean) {
        this.looping = looping
        loadAnimation()
    }
    
    /**
     * Set playback mode
     */
    fun setPlayMode(mode: Mode) {
        this.playMode = mode
        loadAnimation()
    }
    
    /**
     * Set playback speed
     */
    fun setPlaybackSpeed(speed: Float) {
        this.speed = speed
        lottieAnimation.setSpeed(speed)
    }
    
    /**
     * Set frame interpolation
     */
    fun setFrameInterpolation(enabled: Boolean) {
        this.useFrameInterpolation = enabled
        lottieAnimation.setUseFrameInterpolation(enabled)
    }
    
    /**
     * Load the animation with current settings
     */
    private fun loadAnimation() {
        val config = Config.Builder()
            .autoplay(autoPlay)
            .loop(looping)
            .source(DotLottieSource.Url(url))
            .playMode(playMode)
            .speed(speed)
            .useFrameInterpolation(useFrameInterpolation)
            .build()
            
        lottieAnimation.load(config)
    }
    
    /**
     * Play the animation
     */
    fun play() {
        lottieAnimation.play()
    }
    
    /**
     * Pause the animation
     */
    fun pause() {
        lottieAnimation.pause()
    }
    
    /**
     * Stop the animation
     */
    fun stop() {
        lottieAnimation.stop()
    }
    
    /**
     * Get the underlying DotLottieAnimation instance
     */
    fun getLottieAnimation(): DotLottieAnimation {
        return lottieAnimation
    }
} 