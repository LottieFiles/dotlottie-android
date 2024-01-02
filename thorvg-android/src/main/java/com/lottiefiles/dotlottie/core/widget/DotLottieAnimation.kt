package com.lottiefiles.dotlottie.core.widget

import android.content.Context
import android.content.res.AssetManager
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.annotation.FloatRange
import com.lottiefiles.dotlottie.core.R
import com.lottiefiles.dotlottie.core.drawable.DotLottieDrawable
import com.lottiefiles.dotlottie.core.model.Config
import com.lottiefiles.dotlottie.core.model.Mode
import com.lottiefiles.dotlottie.core.util.toColor
import io.dotlottie.loader.DotLottieLoader
import io.dotlottie.loader.models.DotLottie
import io.dotlottie.loader.models.DotLottieResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


class DotLottieAnimation @JvmOverloads constructor(
    private val context: Context,
    private val attrs: AttributeSet? = null,
    private val defStyleAttr: Int = 0,
    private val defStyleRes: Int = 0
) : View(context, attrs, defStyleAttr, defStyleRes) {

    private var mConfig: Config? = null
    private var mLottieDrawable: DotLottieDrawable? = null
    private val coroutineScope = CoroutineScope(SupervisorJob())

    private val mDotLottieEventListener = mutableListOf<DotLottieEventListener>()


    @get:FloatRange(from = 0.0)
    val speed: Float
        get() = mLottieDrawable?.speed ?: error("DotLottieDrawable is null")

    val loop: Boolean
        get() = loopCount != 1

    val autoPlay: Boolean
        get() = mLottieDrawable?.autoPlay ?: error("DotLottieDrawable is null")

    val isLoaded: Boolean
        get() = mLottieDrawable?.isLoaded ?: error("DotLottieDrawable is null")

    val totalFrames: Double
        get() = mLottieDrawable?.totalFrame ?: error("DotLottieDrawable is null")

    val currentFrame: Double
        get() = mLottieDrawable?.currentFrame ?: error("DotLottieDrawable is null")

    var mode: Mode
        get() = mLottieDrawable?.mode ?: error("DotLottieDrawable is null")
        set(value) {
            mLottieDrawable?.mode = value
        }

    val segments: Pair<Double, Double>
        get() = mLottieDrawable?.segments ?: error("DotLottieDrawable is null")

    val duration: Double
        get() = mLottieDrawable?.duration ?: error("DotLottieDrawable is null")

    val loopCount: Int
        get() = mLottieDrawable?.loopCount ?: error("DotLottieDrawable is null")

    val useFrameInterpolation: Boolean
        get() = mLottieDrawable?.useFrameInterpolation ?: error("DotLottieDrawable is null")


    /***
     * Method
     */
    fun setFrame(frame: Double) {
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

    fun freeze() {
        mLottieDrawable?.freeze = true
    }

    fun unFreeze() {
        mLottieDrawable?.freeze = false
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

    fun setBackgroundColor(color: String) {
        mLottieDrawable?.setBackgroundColor(color)
    }

    fun stop() {
        mLottieDrawable?.stop()
    }

    fun setRepeatMode(repeatMode: Mode) {
        mLottieDrawable?.setRepeatMode(repeatMode)
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
                setupDotLottieDrawable()
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

    private suspend fun loadFromUrl(url: String): String {
        return suspendCoroutine { cont ->
            DotLottieLoader.with(context).fromUrl(url).load(object : DotLottieResult {
                override fun onSuccess(result: DotLottie) {
                    val anim = result.animations.entries.lastOrNull()
                    if (anim != null) {
                        val data = String(anim.value, StandardCharsets.UTF_8)
                        cont.resume(data)
                    }
                }
                override fun onError(throwable: Throwable) {
                    cont.resumeWithException(throwable)
                }
            })
        }
    }

    private suspend fun loadAsset(filePath: String): String? {
        return suspendCoroutine { cont ->
            DotLottieLoader.with(context).fromAsset(filePath).load(object : DotLottieResult {
                override fun onSuccess(result: DotLottie) {
                    val anim = result.animations.entries.lastOrNull()
                    if (anim != null) {
                        val data = String(anim.value, StandardCharsets.UTF_8)
                        cont.resume(data)
                    }
                }
                override fun onError(throwable: Throwable) {
                    cont.resumeWithException(throwable)
                }
            })
        }
    }

    private fun setupConfig() {
        val config = mConfig ?: return
        coroutineScope.launch {
            val assetFilePath = config.asset
            val contentStr = when {
                config.asset.isJsonAsset() ||
                config.asset.isDotLottieAsset() -> loadAsset(assetFilePath)
                config.srcUrl.isNotBlank() -> loadFromUrl(config.srcUrl)
                else -> loadAsset(assetFilePath)
            }
            mLottieDrawable = DotLottieDrawable(
                mRepeatMode = config.mode,
                mLoopCount = if (config.loop) INFINITE_LOOP else 1,
                mAutoPlay = config.autoPlay,
                mSpeed = config.speed,
                backgroundColor = config.backgroundColor.toColor(),
                mUseFrameInterpolator = config.useFrameInterpolator,
                contentStr = contentStr ?: error("Invalid content !"),
                mDotLottieEventListener = mDotLottieEventListener
            )
            mLottieDrawable?.callback = this@DotLottieAnimation
            withContext(Dispatchers.Main) {
                requestLayout()
                invalidate()
            }
        }
    }

    private fun getMode(mode: Int): Mode {
        return when(mode) {
            1 -> Mode.Forward
            else -> Mode.Reverse
        }
    }

    private fun TypedArray.setupDotLottieDrawable() {
        val assetFilePath = getString(R.styleable.DotLottieAnimation_src) ?: ""
        coroutineScope.launch {
            if (assetFilePath.isNotBlank()) {
                val contentStr = loadAsset(assetFilePath)
                val mode = getInt(R.styleable.DotLottieAnimation_mode, MODE_RESTART)
                mLottieDrawable = DotLottieDrawable(
                    mAutoPlay = getBoolean(R.styleable.DotLottieAnimation_autoPlay, true),
                    contentStr = contentStr ?: error("Invalid content"),
                    mLoopCount = getInt(R.styleable.DotLottieAnimation_repeatCount, INFINITE_LOOP),
                    mRepeatMode = getMode(mode),
                    mSpeed = getFloat(R.styleable.DotLottieAnimation_speed, 1f),
                    mDotLottieEventListener = mDotLottieEventListener
                )
                mLottieDrawable?.callback = this@DotLottieAnimation
            }
        }
    }

    fun resize(width: Int, height: Int) {
        mLottieDrawable?.resize(width, height)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = measuredWidth
        val height = measuredHeight
        mLottieDrawable?.let { drawable ->
            if ((width != drawable.intrinsicWidth || height != drawable.intrinsicHeight)) {
                drawable.resize(width, height)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        coroutineScope.cancel()
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
        mDotLottieEventListener.add(listener)
    }

    fun removeEventListener(listener: DotLottieEventListener) {
        mDotLottieEventListener.remove(listener)
    }

    companion object {
        private const val TAG = "LottieDrawable"

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