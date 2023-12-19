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
import com.lottiefiles.dotlottie.core.widget.DotLottieAnimation.Companion.INFINITE
import com.lottiefiles.dotlottie.core.widget.DotLottieAnimation.Companion.RESTART
import com.lottiefiles.dotlottie.core.widget.DotLottieAnimation.Companion.REVERSE

// https://android.googlesource.com/platform/frameworks/support/+/0402748/graphics/drawable/static/src/android/support/graphics/drawable/VectorDrawableCompat.java
// https://android.googlesource.com/platform/frameworks/support/+/f185f10/compat/java/android/support/v4/graphics/drawable/DrawableCompat.java
// https://android.googlesource.com/platform/frameworks/base/+/53a3ed7c46c12c2e578d1b1df8b039c6db690eaa/core/java/android/view/LayoutInflater.java
// https://android.googlesource.com/platform/frameworks/base/+/HEAD/core/java/android/animation/ValueAnimator.java
class DotLottieDrawable(
    /**
     * Internal variables
     */
    private val mContext: Context,
    mDuration: Long,
    private var mRepeatCount: Int = 0,
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

    private var mFrameInterval: Long = 0

    // The number of times the animation will repeat. The default is 0, which means the animation
    // will play only once
    private var mRemainingRepeatCount = 0

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
        if (mRepeatCount == INFINITE || mRemainingRepeatCount > -1) {
            invalidateSelf()
        }
    }

    /**
     * Public constants
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    @IntDef(*[RESTART, REVERSE])
    @Retention(
        AnnotationRetention.SOURCE
    )
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

    var repeatCount: Int
        /**
         * Defines how many times the animation should repeat. The default value
         * is 0.
         *
         * @return the number of times the animation should repeat, or [.INFINITE]
         */
        get() = mRepeatCount
        /**
         * Sets how many times the animation should be repeated. If the repeat
         * count is 0, the animation is never repeated. If the repeat count is
         * greater than 0 or [.INFINITE], the repeat mode will be taken
         * into account. The repeat count is 0 by default.
         *
         * @param value the number of times the animation should be repeated
         */
        set(value) {
            mRepeatCount = value
            mRemainingRepeatCount = value
        }

    @get:RepeatMode
    var repeatMode: Int
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
            mFramesPerUpdate = if (mRepeatMode == RESTART) 1 else -1
        }

    val totalFrame: Int
        get() = lastFrame - firstFrame

    @get:FloatRange(from = 0.0)
    var speed: Float
        get() = mSpeed
        set(speed) {
            mSpeed = speed
            updateFrameInterval()
        }

    private fun updateFrameInterval() {
        mFrameInterval = (duration / totalFrame / mSpeed).toLong()
    }

    fun release() {
        nDestroyLottie(mNativePtr)
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
        mFrame = firstFrame
        mRemainingRepeatCount = mRepeatCount
        invalidateSelf()
    }

    override fun stop() {
        mRunning = false
        mHandler.removeCallbacks(mNextFrameRunnable)
    }

    fun pause() {
        mPaused = true
        mHandler.removeCallbacks(mNextFrameRunnable)
    }

    fun resume() {
        mPaused = false
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        if (mNativePtr == 0L) {
            return
        }
        if (mAutoPlay || mRunning) {
            val startTime = System.nanoTime()
            nDrawLottieFrame(mNativePtr, mBuffer, mFrame)
            canvas.drawBitmap(mBuffer!!, 0f, 0f, Paint())

            // Increase frame count.
            mFrame += mFramesPerUpdate
            if (mFrame > lastFrame) {
                mFrame = firstFrame
                mRemainingRepeatCount--
            } else if (mFrame < firstFrame) {
                mFrame = lastFrame
                mRemainingRepeatCount--
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