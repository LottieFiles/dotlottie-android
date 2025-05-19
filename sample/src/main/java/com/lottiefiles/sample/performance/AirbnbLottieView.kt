package com.lottiefiles.sample.performance

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.widget.FrameLayout
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieTask
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A wrapper for the Airbnb Lottie animation view.
 * NOTE: Frame interpolation is not supported in Airbnb Lottie.
 */
class AirbnbLottieView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    
    private val animationView = LottieAnimationView(context).apply {
        repeatCount = LottieDrawable.INFINITE
        enableMergePathsForKitKatAndAbove(true)
    }
    
    private val TAG = "AirbnbLottieView"
    private val isAnimationLoaded = AtomicBoolean(false)
    private var animationUrl: String? = null
    
    init {
        addView(animationView)
        animationView.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
    }
    
    /**
     * Set the animation from a URL.
     * Supports loading JSON from a remote URL.
     */
    fun setAnimationUrl(url: String) {
        animationUrl = url
        isAnimationLoaded.set(false)
        
        // Load animation in a background thread
        Thread {
            try {
                // Download JSON content
                val jsonContent = URL(url).readText()
                
                // Parse Lottie composition on main thread
                post {
                    val task: LottieTask<LottieComposition> = LottieCompositionFactory
                        .fromJsonString(jsonContent, null)
                    
                    task.addListener { composition ->
                        animationView.setComposition(composition)
                        animationView.playAnimation()
                        isAnimationLoaded.set(true)
                    }
                    
                    task.addFailureListener { exception ->
                        Log.e(TAG, "Failed to load animation: ${exception.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading animation from URL: ${e.message}")
            }
        }.start()
    }
    
    /**
     * Set animation speed.
     */
    fun setSpeed(speed: Float) {
        animationView.speed = speed
    }
    
    /**
     * Set frame interpolation (Not supported in Airbnb Lottie).
     * This is included only for API compatibility with DotLottie.
     */
    fun setFrameInterpolation(enabled: Boolean) {
        if (enabled) {
            Log.w(TAG, "Frame interpolation is not supported in Airbnb Lottie")
        }
    }
    
    /**
     * Start playing the animation.
     */
    fun play() {
        if (isAnimationLoaded.get()) {
            animationView.playAnimation()
        } else {
            // Wait for animation to load
            post(object : Runnable {
                override fun run() {
                    if (isAnimationLoaded.get()) {
                        animationView.playAnimation()
                    } else if (animationView.isAttachedToWindow) {
                        postDelayed(this, 100)
                    }
                }
            })
        }
    }
    
    /**
     * Stop playing the animation.
     */
    fun stop() {
        animationView.cancelAnimation()
    }
    
    /**
     * Pause the animation at the current frame.
     */
    fun pause() {
        animationView.pauseAnimation()
    }
    
    /**
     * Reset the animation to the beginning.
     */
    fun reset() {
        animationView.frame = 0
    }
    
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animationView.isAnimating && isAnimationLoaded.get()) {
            animationView.playAnimation()
        }
    }
    
    override fun onDetachedFromWindow() {
        animationView.cancelAnimation()
        super.onDetachedFromWindow()
    }
} 