package com.lottiefiles.dotlottie.core.drawable

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import androidx.annotation.FloatRange
import com.lottiefiles.dotlottie.core.LottieNative
import com.lottiefiles.dotlottie.core.LottieNative.nDestroyLottie
import com.lottiefiles.dotlottie.core.LottieNative.nDrawLottieFrame
import com.lottiefiles.dotlottie.core.LottieNative.nSetLottieBufferSize
import com.lottiefiles.dotlottie.core.model.Mode
import com.lottiefiles.dotlottie.core.util.toColor
import com.lottiefiles.dotlottie.core.widget.DotLottieAnimation.Companion.INFINITE_LOOP
import com.lottiefiles.dotlottie.core.widget.DotLottieEventListener

class DotLottieDrawable(
    private val mUseFrameInterpolator: Boolean = true,
    private val mAutoPlay: Boolean = false,
    private val contentStr: String,
    private var mLoopCount: Int = 0,
    private var mRepeatMode: Mode = Mode.Forward,
    private var mSpeed: Float,
    private var backgroundColor: Int = Color.TRANSPARENT
) : Drawable(), Animatable {

    /**
     * Additional playing state to indicate whether an animator has been start()'d. There is
     * some lag between a call to start() and the first animation frame. We should still note
     * that the animation has been started, even if it's first animation frame has not yet
     * happened, and reflect that state in isRunning().
     * Note that delayed animations are different: they are not started until their first
     * animation frame, which occurs after their delay elapses.
     */
    private var mRunning = false
    private var mPaused = false
    private var mNativePtr: Long = 0
    /**
     * Gets the length of the animation. The default duration is 300 milliseconds.
     *
     * @return The length of the animation, in milliseconds.
     */
    /**
     * Backing variables
     */
    // How long the animation should last in ms
    var duration: Double = 0.0
        private set

    val segments: Pair<Double, Double>
        get() = firstFrame to lastFrame

    private var mFrameDuration: Double = 0.0

    // The number of times the animation will repeat. The default is 0, which means the animation
    // will play only once
    private var mRemainingRepeatCount = mLoopCount

    private val mDotLottieEventListener = mutableListOf<DotLottieEventListener>()

    /**
     * The type of repetition that will occur when repeatMode is nonzero. RESTART means the
     * animation will start from the beginning on every new cycle. REVERSE means the animation
     * will reverse directions on each iteration.
     */
    private var mFramesPerUpdate = mRepeatMode.getFramePerUpdateIncrement()
    private var mBuffer: Bitmap? = null
    private var mWidth = 0
    private var mHeight = 0
    private var mFrame = 0.0
    private var firstFrame = 0.0
    private var lastFrame = 0.0

    /**
     * Animation handler used to schedule updates for this animation.
     */
    private val mHandler = Handler(Looper.getMainLooper())
    private val mNextFrameRunnable = Runnable {
        if (mLoopCount == INFINITE_LOOP || mRemainingRepeatCount > -1) {
            invalidateSelf()
        }
    }


    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun getIntrinsicWidth(): Int {
        return mWidth
    }

    override fun getIntrinsicHeight(): Int {
        return mHeight
    }

    private fun Mode.getFramePerUpdateIncrement(): Double {
        return when(this) {
            Mode.Forward -> 1.0
            Mode.Reverse -> -1.0
        }
    }

    var useFrameInterpolation: Boolean = mUseFrameInterpolator
        private set

    var loopCount: Int
        /**
         * Defines how many times the animation should repeat. The default value
         * is 0.
         *
         * @return the number of times the animation should repeat, or [.INFINITE]
         */
        get() = mLoopCount
        /**
         * Sets how many times the animation should be repeated. If the repeat
         * count is 0, the animation is never repeated. If the repeat count is
         * greater than 0 or [.INFINITE], the repeat mode will be taken
         * into account. The repeat count is 0 by default.
         *
         * @param value the number of times the animation should be repeated
         */
        set(value) {
            mLoopCount = value
            mRemainingRepeatCount = value
        }

    var mode: Mode
        /**
         * Defines what this animation should do when it reaches the end.
         *
         * @return either one of [.REVERSE] or [.RESTART]
         */
        get() = mRepeatMode
        /**
         * Defines what this animation should do when it reaches the end. This
         * setting is applied only when the repeat count is either greater than
         * 0 or [.INFINITE]. Defaults to [.RESTART].
         *
         * @param value [.RESTART] or [.REVERSE]
         */
        set(value) {
            mRepeatMode = value
            mFramesPerUpdate = value.getFramePerUpdateIncrement()
        }

    private var mBeginTime: Long = 0

    val totalFrame: Double
        get() = lastFrame - firstFrame


    val autoPlay: Boolean
        get() = mAutoPlay

    val currentFrame: Double
        get() = mFrame

    @get:FloatRange(from = 0.0)
    var speed: Float
        get() = mSpeed
        set(speed) {
            mSpeed = speed
            updateFrameInterval()
        }

    private var mDotLottieError: Throwable? = null
    var isLoaded: Boolean = false
        private set


    init {
        try {
            initialize()
        } catch (e: Throwable) {
            mDotLottieError = e
        }
    }

    private fun initialize() {
        val outValues = DoubleArray(LOTTIE_INFO_COUNT)
        mNativePtr = LottieNative.nCreateLottie(contentStr, contentStr.length, outValues)
        firstFrame = 0.0
        lastFrame = outValues[LOTTIE_INFO_FRAME_COUNT]
        duration = outValues[LOTTIE_INFO_DURATION] * 1000L
        isLoaded = true
        updateFrameInterval()
    }

    private fun updateFrameInterval() {
        mFrameDuration = duration / totalFrame / mSpeed
    }

    fun release() {
        nDestroyLottie(mNativePtr)
        mDotLottieEventListener.forEach(DotLottieEventListener::onDestroy)
        if (mBuffer != null) {
            mBuffer!!.recycle()
            mBuffer = null
        }
    }

    fun resize(width: Int, height: Int) {
        mWidth = width
        mHeight = height
        mBuffer = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888)
        nSetLottieBufferSize(mNativePtr, mBuffer, mWidth.toFloat(), mHeight.toFloat())
    }

    override fun isRunning(): Boolean {
        return mRunning
    }

    fun setBackgroundColor(hexColor: String) {
        backgroundColor = hexColor.toColor()
    }

    override fun start() {
        mRunning = true
        mPaused = false
        mBeginTime = System.nanoTime()
        // Resume the frame from where we left of
        mFrame = when(mode) {
            Mode.Forward -> if (mFrame == 0.0) firstFrame else mFrame
            Mode.Reverse -> if (mFrame == lastFrame) lastFrame else mFrame
        }
        mRemainingRepeatCount = mLoopCount
        mDotLottieEventListener.forEach(DotLottieEventListener::onPlay)
        invalidateSelf()
    }

    fun setRepeatMode(repeatMode: Mode) {
        this.mode = repeatMode
    }

    override fun stop() {
        mRunning = false
        mFrame = when(mode) {
            Mode.Forward -> firstFrame
            Mode.Reverse -> lastFrame
        }
        mDotLottieEventListener.forEach(DotLottieEventListener::onStop)
        mHandler.removeCallbacks(mNextFrameRunnable)
    }

    fun isPaused(): Boolean {
        return mPaused
    }

    fun isStopped(): Boolean {
        return !isRunning
    }

    fun setCurrentFrame(frame: Double) {
        if (isPaused()) mFrame = frame
    }

    fun setFrameInterpolation(enabled: Boolean) {
        useFrameInterpolation = enabled
    }

    fun setSegments(first: Double, last: Double) {
        when(mode) {
            Mode.Forward -> {
                firstFrame = first
                lastFrame = last
            }
            Mode.Reverse -> {
                firstFrame = last
                lastFrame = first
            }
        }
    }

    fun pause() {
        mPaused = true
        mDotLottieEventListener.forEach(DotLottieEventListener::onPause)
        mHandler.removeCallbacks(mNextFrameRunnable)
    }

    fun addEventListener(eventListener: DotLottieEventListener) {
        mDotLottieEventListener.add(eventListener)
        collectOldState()
    }

    private fun collectOldState() {
        mDotLottieError?.let { error ->
            mDotLottieEventListener.forEach { it.onLoadError(error) }
        }
        if (isLoaded) {
            mDotLottieEventListener.forEach(DotLottieEventListener::onLoad)
        }
    }

    fun removeEventListener(eventListener: DotLottieEventListener) {
        mDotLottieEventListener.remove(eventListener)
    }

    override fun draw(canvas: Canvas) {
        if (mNativePtr == 0L || mBuffer == null) {
            return
        }

        if (mAutoPlay || mRunning) {
            val startTime = System.nanoTime()
            nDrawLottieFrame(mNativePtr, mBuffer, mFrame.toFloat())
            canvas.drawColor(backgroundColor)
            canvas.drawBitmap(mBuffer!!, 0f, 0f, Paint())
            mDotLottieEventListener.forEach { it.onFrame(mFrame) }

            // Increase frame count.
            if (!useFrameInterpolation) {
                mFrame += mFramesPerUpdate
            }
            if (mFrame > lastFrame) {
                mFrame = firstFrame
                mRemainingRepeatCount--
                if (loopCount == INFINITE_LOOP) {
                    mDotLottieEventListener.forEach(DotLottieEventListener::onLoop)
                }
                if (loopCount != INFINITE_LOOP && mRemainingRepeatCount == -1) {
                    mPaused = true
                    mDotLottieEventListener.forEach(DotLottieEventListener::onComplete)
                }
            } else if (mFrame < firstFrame) {
                mFrame = lastFrame
                mRemainingRepeatCount--
                if (loopCount == INFINITE_LOOP) {
                    mDotLottieEventListener.forEach(DotLottieEventListener::onLoop)
                }
                if (loopCount != INFINITE_LOOP && mRemainingRepeatCount == -1) {
                    mPaused = true
                    mDotLottieEventListener.forEach(DotLottieEventListener::onComplete)
                }
            }
            val endTime = System.nanoTime()
            if (mPaused) {
                return
            }

            // Frame progression with Interpolation
            val elapsedTime = ((endTime - startTime) / 100000) * speed
            val frameProgress = elapsedTime / mFrameDuration
            if (useFrameInterpolation) {
                if (mode == Mode.Reverse) {
                    mFrame -= frameProgress
                } else {
                    mFrame += frameProgress
                }
            }

            mHandler.postDelayed(
                mNextFrameRunnable,
                (mFrameDuration - (endTime - startTime) / 1000000).toLong()
            )
        }
    }

    companion object {

        private const val TAG = "DotLottieDrawable"
        /**
         * Internal constants
         */
        private const val LOTTIE_INFO_FRAME_COUNT = 0
        private const val LOTTIE_INFO_DURATION = 1
        private const val LOTTIE_INFO_COUNT = 2
    }
}