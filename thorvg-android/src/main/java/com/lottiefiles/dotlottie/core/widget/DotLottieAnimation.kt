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
import com.lottiefiles.dotlottie.core.model.Config
import java.io.IOException
import java.nio.charset.StandardCharsets

class DotLottieAnimation @JvmOverloads constructor(
    private val context: Context,
    private val attrs: AttributeSet? = null,
    private val defStyleAttr: Int = 0,
    private val defStyleRes: Int = 0
) : View(context, attrs, defStyleAttr, defStyleRes) {

    private var mWidth: Int? = 0
    private var mHeight: Int? = 0

    private var mConfig: Config? = null
    private var mLottieDrawable: DotLottieDrawable? = null
    private val mAssetManager: AssetManager = context.assets

    @get:FloatRange(from = 0.0)
    val speed: Float
        get() = mLottieDrawable?.speed ?: error("DotLottieDrawable is null")

    val autoPlay: Boolean
        get() = mLottieDrawable?.autoPlay ?: error("DotLottieDrawable is null")

    val totalFrames: Int
        get() = mLottieDrawable?.totalFrame ?: error("DotLottieDrawable is null")

    val currentFrame: Int
        get() = mLottieDrawable?.currentFrame ?: error("DotLottieDrawable is null")

    var mode: Mode
        get() = mLottieDrawable?.mode ?: error("DotLottieDrawable is null")
        set(value) {
            mLottieDrawable?.mode = value
        }

    val segments: Pair<Int, Int>
        get() = mLottieDrawable?.segments ?: error("DotLottieDrawable is null")

    val duration: Long
        get() = mLottieDrawable?.duration ?: error("DotLottieDrawable is null")

    val loopCount: Int
        get() = mLottieDrawable?.loopCount ?: error("DotLottieDrawable is null")

    val useFrameInterpolation: Boolean
        get() = mLottieDrawable?.useFrameInterpolation ?: error("DotLottieDrawable is null")


    /***
     * Method
     */
    fun setFrame(frame: Int) {
        mLottieDrawable?.setCurrentFrame(frame)
        invalidate()
    }

    fun setFrameInterpolation(enable: Boolean) {
        mLottieDrawable?.setFrameInterpolation(enable)
    }

    fun setSegments(firstFrame: Double, lastFrame: Double) {
        mLottieDrawable?.setSegments(firstFrame, lastFrame)
    }

    fun setLoop(loop: Boolean) {
        mLottieDrawable?.loopCount = if (loop) INFINITE_LOOP else 1
    }

    fun setSpeed(speed: Float) {
        mLottieDrawable?.speed = speed
    }

    fun isPlaying(): Boolean {
        return mLottieDrawable?.isRunning ?: false
    }

    fun isPaused(): Boolean {
        return mLottieDrawable?.isPaused() ?: false
    }

    fun isStopped(): Boolean {
        return mLottieDrawable?.isStopped() ?: false
    }

    fun play() {
        mLottieDrawable?.start()
    }

    fun stop() {
        mLottieDrawable?.stop()
    }

    fun pause() {
        mLottieDrawable?.pause()
    }

    fun load(config: Config) {
        mConfig = config
        setupConfig()
    }

    init {
        setupConfigFromXml()
    }

    private fun setupConfigFromXml() {
        context.theme?.obtainStyledAttributes(attrs, R.styleable.DotLottieAnimation, 0, 0)?.apply {
            try {
                setupDotLottieDrawable(context)
            } finally {
                recycle()
            }
        }
    }

    private fun String.isJsonAsset(): Boolean {
        return endsWith(".json")
    }

    private fun String.isDotLottieAsset(): Boolean {
        return endsWith(".lottie")
    }

    private fun setupConfig() {
        val config = mConfig ?: return
        val assetFilePath = config.asset
        val contentStr = if (config.asset.isJsonAsset()) {
            loadJsonFromAsset(assetFilePath)
        } else {
            loadDotLottieAsset(assetFilePath)
        }
        val outValues = IntArray(LOTTIE_INFO_COUNT)
        mLottieDrawable = DotLottieDrawable(
            mRepeatMode = config.mode,
            mLoopCount = if (config.loop) INFINITE_LOOP else 1,
            mAutoPlay = config.autoPlay,
            mSpeed = config.speed,
            mFirstFrame = 0,
            mLastFrame = outValues[LOTTIE_INFO_FRAME_COUNT],
            mDuration = outValues[LOTTIE_INFO_DURATION] * 1000L
        )
        mLottieDrawable?.callback = this@DotLottieAnimation
    }

    private fun getMode(mode: Int): Mode {
        return when(mode) {
            1 -> Mode.Forward
            else -> Mode.Reverse
        }
    }

    private fun TypedArray.setupDotLottieDrawable() {
        val assetFilePath = getString(R.styleable.DotLottieAnimation_src)
        val contentStr = loadJsonFromAsset(assetFilePath)
        val mode = getInt(R.styleable.DotLottieAnimation_mode, MODE_RESTART)
        mLottieDrawable = DotLottieDrawable(
            mRepeatMode = getMode(mode),
            mLoopCount = getInt(R.styleable.DotLottieAnimation_repeatCount, INFINITE_LOOP),
            mAutoPlay = getBoolean(R.styleable.DotLottieAnimation_autoPlay, true),
            mSpeed = getFloat(R.styleable.DotLottieAnimation_speed, 1f),
            contentStr = contentStr ?: error("Invalid content")
        )
        mLottieDrawable?.callback = this@DotLottieAnimation
    }

    private fun loadDotLottieAsset(fileName: String?): String? {
        TODO()
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

    fun resize(width: Int, height: Int) {
        mWidth = width
        mHeight = height
        mLottieDrawable?.resize(width, height)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = if (mWidth == null) measuredWidth else mWidth!!
        val height = if (mHeight == null) measuredHeight else mHeight!!
        mLottieDrawable?.let { drawable ->
            if ((width != drawable.intrinsicWidth || height != drawable.intrinsicHeight)) {
                drawable.resize(width, height)
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

    fun addEventListener(listener: DotLottieEventListener) {
        mLottieDrawable?.addEventListener(listener)
    }

    fun removeEventListener(listener: DotLottieEventListener) {
        mLottieDrawable?.removeEventListener(listener)
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
        const val MODE_RESTART = 1

        /**
         * This value used used with the [.setRepeatCount] property to repeat
         * the animation indefinitely.
         */
        const val INFINITE_LOOP = -1
    }
}