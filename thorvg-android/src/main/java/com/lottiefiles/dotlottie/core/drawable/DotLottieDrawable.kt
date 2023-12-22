package com.lottiefiles.dotlottie.core.drawable

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import androidx.annotation.FloatRange
import androidx.annotation.IntDef
import androidx.annotation.RestrictTo
import com.lottiefiles.dotlottie.core.LottieNative.nDestroyLottie
import com.lottiefiles.dotlottie.core.LottieNative.nDrawLottieFrame
import com.lottiefiles.dotlottie.core.LottieNative.nSetLottieBufferSize
import com.lottiefiles.dotlottie.core.widget.DotLottieAnimation.Companion.INFINITE_LOOP
import com.lottiefiles.dotlottie.core.widget.DotLottieAnimation.Companion.MODE_RESTART
import com.lottiefiles.dotlottie.core.widget.DotLottieAnimation.Companion.MODE_REVERSE
import com.lottiefiles.dotlottie.core.widget.DotLottieEventListener

class DotLottieDrawable(
    private val mContext: Context,
    mDuration: Long,
    private var mLoopCount: Int = 0,
    private val mNativePtr: Long,
    private var mRepeatMode: Int = 0,
    private val mFirstFrame: Int,
    private val mLastFrame: Int,
    private var mAutoPlay: Boolean = false,
    private var mSpeed: Float,
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
    /**
     * Gets the length of the animation. The default duration is 300 milliseconds.
     *
     * @return The length of the animation, in milliseconds.
     */
    /**
     * Backing variables
     */
    // How long the animation should last in ms
    var duration: Long = mDuration
        private set

    val segments: Pair<Int, Int>
        get() = firstFrame to lastFrame

    private var mFrameInterval: Long = 0

    // The number of times the animation will repeat. The default is 0, which means the animation
    // will play only once
    private var mRemainingRepeatCount = mLoopCount

    private val mDotLottieEventListener = mutableListOf<DotLottieEventListener>()

    /**
     * The type of repetition that will occur when repeatMode is nonzero. RESTART means the
     * animation will start from the beginning on every new cycle. REVERSE means the animation
     * will reverse directions on each iteration.
     */
    private var mFramesPerUpdate = 1
    private var mBuffer: Bitmap? = null
    private var mWidth = 0
    private var mHeight = 0
    private var mFrame = 0

    var firstFrame = mFirstFrame
        private set

    var lastFrame = mLastFrame
        private set

    /**
     * Animation handler used to schedule updates for this animation.
     */
    private val mHandler = Handler(Looper.getMainLooper())
    private val mNextFrameRunnable = Runnable {
        if (mLoopCount == INFINITE_LOOP || mRemainingRepeatCount > -1) {
            invalidateSelf()
        }
    }

    /**
     * Public constants
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    @IntDef(*[MODE_RESTART, MODE_REVERSE])
    @Retention(AnnotationRetention.SOURCE)
    annotation class RepeatMode

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

    @get:RepeatMode
    var mode: Int
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
            mFramesPerUpdate = if (mRepeatMode == MODE_RESTART) 1 else -1
        }

    val totalFrame: Int
        get() = lastFrame - firstFrame

    val autoPlay: Boolean
        get() = mAutoPlay

    val currentFrame: Int
        get() = mFrame

    @get:FloatRange(from = 0.0)
    var speed: Float
        get() = mSpeed
        set(speed) {
            mSpeed = speed
            updateFrameInterval()
        }

    init {
        updateFrameInterval()
    }

    private fun updateFrameInterval() {
        mFrameInterval = (duration / totalFrame / mSpeed).toLong()
    }

    fun release() {
        nDestroyLottie(mNativePtr)
        mDotLottieEventListener.forEach(DotLottieEventListener::onDestroy)
        if (mBuffer != null) {
            mBuffer!!.recycle()
            mBuffer = null
        }
    }

    fun setSize(width: Int, height: Int) {
        mWidth = width
        mHeight = height
        mBuffer = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888)
        nSetLottieBufferSize(mNativePtr, mBuffer, mWidth.toFloat(), mHeight.toFloat())
    }

    override fun isRunning(): Boolean {
        return mRunning
    }

    override fun start() {
        mRunning = true
        mPaused = false
        // Resume the frame from where we left of
        mFrame = if (mFrame == 0) firstFrame else mFrame
        mRemainingRepeatCount = mLoopCount
        mDotLottieEventListener.forEach(DotLottieEventListener::onPlay)
        invalidateSelf()
    }

    override fun stop() {
        mRunning = false
        mFrame = firstFrame
        mDotLottieEventListener.forEach(DotLottieEventListener::onStop)
        mHandler.removeCallbacks(mNextFrameRunnable)
    }

    fun isPaused(): Boolean {
        return mPaused
    }

    fun isStopped(): Boolean {
        return !isRunning
    }

    fun setCurrentFrame(frame: Int) {
        if (isPaused()) mFrame = frame
    }

    fun setSegments(first: Int, last: Int) {
        firstFrame = first
        lastFrame = last
    }

    fun pause() {
        mPaused = true
        mDotLottieEventListener.forEach(DotLottieEventListener::onPause)
        mHandler.removeCallbacks(mNextFrameRunnable)
    }

    fun addEventListener(eventListener: DotLottieEventListener) {
        mDotLottieEventListener.add(eventListener)
    }

    fun removeEventListener(eventListener: DotLottieEventListener) {
        mDotLottieEventListener.remove(eventListener)
    }

    override fun draw(canvas: Canvas) {
        if (mNativePtr == 0L) {
            return
        }

        if (mAutoPlay || mRunning) {
            val startTime = System.nanoTime()
            nDrawLottieFrame(mNativePtr, mBuffer, mFrame)
            canvas.drawBitmap(mBuffer!!, 0f, 0f, Paint())
            mDotLottieEventListener.forEach { it.onFrame(mFrame) }

            // Increase frame count.
            mFrame += mFramesPerUpdate
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
                //Log.d("MainActivity", "Remaining loop $mRemainingRepeatCount")
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
                //Log.d(TAG", "Remaining loop $mRemainingRepeatCount")
            }
            val endTime = System.nanoTime()
            if (mPaused) {
                return
            }
            mHandler.postDelayed(
                mNextFrameRunnable,
                mFrameInterval - (endTime - startTime) / 1000000
            )
        }
    }
}