package com.lottiefiles.dotlottie.core.widget

import android.content.Context
import android.content.res.AssetManager
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.annotation.FloatRange
import com.lottiefiles.dotlottie.core.LottieNative
import com.lottiefiles.dotlottie.core.R
import com.lottiefiles.dotlottie.core.drawable.DotLottieDrawable
import java.io.IOException
import java.nio.charset.StandardCharsets

class DotLottieAnimation @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : View(context, attrs, defStyleAttr, defStyleRes) {

    private var mLottieDrawable: DotLottieDrawable? = null
    private val mAssetManager: AssetManager = context.assets

    @get:FloatRange(from = 0.0)
    val speed: Float
        get() = mLottieDrawable?.speed ?: error("DotLottieDrawable is null")

    val totalFrame: Int
        get() = mLottieDrawable?.totalFrame ?: error("DotLottieDrawable is null")

    var repeatMode: Int
        get() = mLottieDrawable?.repeatMode ?: error("DotLottieDrawable is null")
        set(value) {
            mLottieDrawable?.repeatMode = value
        }

    fun setSpeed(speed: Float) {
        mLottieDrawable?.speed = speed
    }

    fun isPlaying(): Boolean {
        return mLottieDrawable?.isRunning ?: false
    }

    fun play() {
        mLottieDrawable?.start()
    }

    fun stop() {
        mLottieDrawable?.stop()
    }

    fun duration(): Long {
        return mLottieDrawable?.duration ?: error("DotLottieDrawable is null")
    }

    fun pause() {
        mLottieDrawable?.pause()
    }

    fun resume() {
        mLottieDrawable?.resume()
    }

    init {
        context.theme?.obtainStyledAttributes(attrs, R.styleable.DotLottieAnimation, 0, 0)?.apply {
            try {
                setupDotLottieDrawable(context)
            } finally {
                recycle()
            }
        }
    }

    private fun TypedArray.setupDotLottieDrawable(context: Context) {
        val assetFilePath = getString(R.styleable.DotLottieAnimation_assetFilePath)
        val contentStr = loadJsonFromAsset(assetFilePath)
        val outValues = IntArray(LOTTIE_INFO_COUNT)
        mLottieDrawable = DotLottieDrawable(
            mContext = context,
            mNativePtr = LottieNative.nCreateLottie(contentStr, contentStr!!.length, outValues),
            mRepeatMode = getInt(R.styleable.DotLottieAnimation_repeatMode, RESTART),
            mRepeatCount = getInt(R.styleable.DotLottieAnimation_repeatCount, INFINITE),
            mAutoPlay = getBoolean(R.styleable.DotLottieAnimation_autoPlay, true),
            mSpeed = getFloat(R.styleable.DotLottieAnimation_speed, 1f),
            mFirstFrame = 0,
            mLastFrame = outValues[LOTTIE_INFO_FRAME_COUNT],
            mDuration = outValues[LOTTIE_INFO_DURATION] * 1000L
        )
        mLottieDrawable?.callback = this@DotLottieAnimation
    }

    private fun loadJsonFromAsset(fileName: String?): String? {
        var json: String? = null
        try {
            val inputStream = mAssetManager.open(fileName!!)
            val size = inputStream.available()
            val buffer = ByteArray(size)

            // Read JSON data from InputStream.
            inputStream.read(buffer)
            inputStream.close()

            // Convert a byte array to a string.
            json = String(buffer, StandardCharsets.UTF_8)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return json
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = measuredWidth
        val height = measuredHeight
        mLottieDrawable?.let { drawable ->
            if ((width != drawable.intrinsicWidth || height != drawable.intrinsicHeight)) {
                drawable.setSize(width, height)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mLottieDrawable?.let { drawable ->
            drawable.release()
            mLottieDrawable = null
        }
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        val lottieDrawable = mLottieDrawable ?: return
        lottieDrawable.draw(canvas)
    }

    override fun invalidateDrawable(drawable: Drawable) {
        super.invalidateDrawable(drawable)
        invalidate()
    }


    companion object {
        private const val TAG = "LottieDrawable"

        /**
         * Internal constants
         */
        private const val LOTTIE_INFO_FRAME_COUNT = 0
        private const val LOTTIE_INFO_DURATION = 1
        private const val LOTTIE_INFO_COUNT = 2

        /**
         * When the animation reaches the end and `repeatCount` is INFINITE
         * or a positive value, the animation restarts from the beginning.
         */
        const val RESTART = 1

        /**
         * When the animation reaches the end and `repeatCount` is INFINITE
         * or a positive value, the animation reverses direction on every iteration.
         */
        const val REVERSE = 2

        /**
         * This value used used with the [.setRepeatCount] property to repeat
         * the animation indefinitely.
         */
        const val INFINITE = -1
    }
}