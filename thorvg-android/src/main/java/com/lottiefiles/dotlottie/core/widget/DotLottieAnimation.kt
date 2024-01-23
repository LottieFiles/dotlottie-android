package com.lottiefiles.dotlottie.core.widget

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.annotation.FloatRange
import com.dotlottie.dlplayer.Mode
import com.lottiefiles.dotlottie.core.R
import com.lottiefiles.dotlottie.core.drawable.DotLottieDrawable
import com.lottiefiles.dotlottie.core.model.Config
import com.dotlottie.dlplayer.Mode as RustMode
import io.dotlottie.loader.DotLottieLoader
import io.dotlottie.loader.models.DotLottie
import io.dotlottie.loader.models.DotLottieResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


class DotLottieAnimation @JvmOverloads constructor(
    private val context: Context,
    private val attrs: AttributeSet? = null,
    private val defStyleAttr: Int = 0,
    private val defStyleRes: Int = 0
) : View(context, attrs, defStyleAttr, defStyleRes) {

    private var width: Int = 0
    private var height: Int = 0
    private var mConfig: Config? = null
    private var mLottieDrawable: DotLottieDrawable? = null
    private val coroutineScope = CoroutineScope(SupervisorJob())

    private val mDotLottieEventListener = mutableListOf<DotLottieEventListener>()


    @get:FloatRange(from = 0.0)
    val speed: Float
        get() = mLottieDrawable?.speed ?: error("DotLottieDrawable is null")

    val loop: Boolean
        get() = loopCount != 1u

    val direction: Int
        get() = when(mLottieDrawable?.mode ?: RustMode.FORWARD) {
            RustMode.FORWARD, RustMode.BOUNCE -> 1
            RustMode.REVERSE, RustMode.REVERSE_BOUNCE -> -1
        }

    val autoPlay: Boolean
        get() = mLottieDrawable?.autoPlay ?: error("DotLottieDrawable is null")

    val isPlaying: Boolean
        get() =  mLottieDrawable?.isRunning ?: false


    val isPaused: Boolean
        get() = mLottieDrawable?.isPaused() ?: false


    val isStopped: Boolean
        get() = mLottieDrawable?.isStopped() ?: false

    val isLoaded: Boolean
        get() = mLottieDrawable?.isLoaded ?: error("DotLottieDrawable is null")

    val totalFrames: Float
        get() = mLottieDrawable?.totalFrame ?: error("DotLottieDrawable is null")

    val currentFrame: Float
        get() = mLottieDrawable?.currentFrame ?: error("DotLottieDrawable is null")

    var mode: RustMode
        get() = mLottieDrawable?.mode ?: error("DotLottieDrawable is null")
        set(value) {
            mLottieDrawable?.mode = value
        }

    val backgroundColor: String
        get() = mLottieDrawable?.backgroundColor ?: ""

    val segments: Pair<Double, Double>
        get() = mLottieDrawable?.segments ?: error("DotLottieDrawable is null")

    val duration: Double
        get() = mLottieDrawable?.duration ?: error("DotLottieDrawable is null")

    // TODO: Implement repeat count
    val loopCount: UInt
        get() = 0u ?: error("DotLottieDrawable is null")

    val useFrameInterpolation: Boolean
        get() = mLottieDrawable?.useFrameInterpolation ?: error("DotLottieDrawable is null")


    /***
     * Method
     */
    fun setFrame(frame: Float) {
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
        mLottieDrawable?.repeatCount = if (loop) INFINITE_LOOP else 1
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

    fun play() {
        Log.i("DRAW:", "play function")
        mLottieDrawable?.play()
    }

    fun setBackgroundColor(color: String) {
        mLottieDrawable?.setBackgroundColor(color)
    }

    fun stop() {
        mLottieDrawable?.stop()
    }

    fun setRepeatMode(repeatMode: RustMode) {
        mLottieDrawable?.setRepeatMode(repeatMode)
    }

    fun pause() {
        mLottieDrawable?.pause()
    }

    fun destroy() {
        mLottieDrawable?.release()
    }

    fun load(config: Config) {
        mConfig = config
        setupConfig()
    }

    init {
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
            try {
                val assetFilePath = config.asset
                val contentStr = when {
                    config.asset.isJsonAsset() ||
                            config.asset.isDotLottieAsset() -> loadAsset(assetFilePath)
                    config.srcUrl.isNotBlank() -> loadFromUrl(config.srcUrl)
                    config.data is String -> config.data
                    config.data is ByteArray -> loadFromByteArray(config.data)
                    else -> error("Asset not found")
                }
                // TODO: MOde from config
                mLottieDrawable = DotLottieDrawable(
                    playMode = RustMode.FORWARD,
                    repeatCount = if (config.loop) INFINITE_LOOP else 1,
                    _autoPlay = config.autoPlay,
                    _speed = config.speed,
                    height = height,
                    width = width,
                    _backgroundColor = config.backgroundColor,
                    useFrameInterpolator = config.useFrameInterpolator,
                    animationData = contentStr ?: error("Invalid content !"),
                    dotLottieEventListener = mDotLottieEventListener
                )
                mLottieDrawable?.callback = this@DotLottieAnimation
                withContext(Dispatchers.Main) {
                    requestLayout()
                    invalidate()
                }
            } catch (e: Exception) {
                mDotLottieEventListener.forEach {
                    it.onLoadError(e)
                }
            }
        }
    }

    private fun byteArrayToFile(data: ByteArray): File? {
        return try {
            val filePath = File(context.cacheDir, "${UUID.randomUUID()}.lottie").apply {
                createNewFile()
            }
            val fileOutputStream = FileOutputStream(filePath)
            fileOutputStream.write(data)
            fileOutputStream.close()
            filePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun loadFromByteArray(data: ByteArray): String? {
        return suspendCoroutine { cont ->
            val file = byteArrayToFile(data) ?: return@suspendCoroutine
            DotLottieLoader.with(context).fromAsset(file.path).load(object : DotLottieResult {
                override fun onSuccess(result: DotLottie) {
                    val anim = result.animations.entries.lastOrNull()
                    if (anim != null) {
                        val content = String(anim.value, StandardCharsets.UTF_8)
                        cont.resume(content)
                    }
                }
                override fun onError(throwable: Throwable) {
                    cont.resumeWithException(throwable)
                }
            })
        }
    }

    private fun getMode(mode: Int): Mode {
        return when(mode) {
            1 -> Mode.FORWARD
            else -> Mode.REVERSE
        }
    }

    private fun TypedArray.setupDotLottieDrawable() {
        val assetFilePath = getString(R.styleable.DotLottieAnimation_src) ?: ""
        coroutineScope.launch {
            if (assetFilePath.isNotBlank()) {
                val contentStr = loadAsset(assetFilePath)
                val mode = getInt(R.styleable.DotLottieAnimation_mode, MODE_RESTART)
                mLottieDrawable = DotLottieDrawable(
                    _autoPlay = getBoolean(R.styleable.DotLottieAnimation_autoPlay, true),
                    animationData = contentStr ?: error("Invalid content"),
                    repeatCount = getInt(R.styleable.DotLottieAnimation_repeatCount, INFINITE_LOOP),
                    width = width,
                    height = height,
//                   TODO: getMode(mode)
                    playMode = RustMode.FORWARD,
                    _speed = getFloat(R.styleable.DotLottieAnimation_speed, 1f),
                    dotLottieEventListener = mDotLottieEventListener
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

        width = measuredWidth
        height = measuredHeight

        setupConfigFromXml()
        mLottieDrawable?.let { drawable ->
            if ((width != drawable.intrinsicWidth || height != drawable.intrinsicHeight)) {
                Log.i("DRAW: ", "Measured ${width} - ${height}")
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
        const val INFINITE_LOOP = Int.MAX_VALUE
    }
}