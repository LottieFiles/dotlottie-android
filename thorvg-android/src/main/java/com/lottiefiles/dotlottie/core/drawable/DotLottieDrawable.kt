package com.lottiefiles.dotlottie.core.drawable

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
import com.lottiefiles.dotlottie.core.util.toColor
import com.lottiefiles.dotlottie.core.widget.DotLottieEventListener
import com.dotlottie.dlplayer.DotLottiePlayer
import com.dotlottie.dlplayer.Config
import com.dotlottie.dlplayer.Mode
import com.sun.jna.Pointer

class DotLottieDrawable(
    private val useFrameInterpolator: Boolean = true,
    private val _autoPlay: Boolean = false,
    private val animationData: String,
    var repeatCount: Int,
    private var width: Int = 0,
    private var height: Int = 0,
    private var playMode: Mode = Mode.FORWARD,
    private var _speed: Float,
    private var _backgroundColor: String = "",
    private val dotLottieEventListener: List<DotLottieEventListener>
) : Drawable(), Animatable {

    private var nativeBuffer: Pointer? = null
    private var bitmapBuffer: Bitmap? = null
    private var dlPlayer: DotLottiePlayer? = null

    private var config = Config(
        autoplay = true,
        loopAnimation = true,
        mode = Mode.FORWARD,  // Replace SOME_MODE with actual value
        speed = 1.0f,
        useFrameInterpolation = true,
        backgroundColor = 0x00000000u,
        segments = listOf()
    )

   var freeze: Boolean = false
        set(value) {
            if (value) {
                dotLottieEventListener.forEach(DotLottieEventListener::onFreeze)
            } else {
                dotLottieEventListener.forEach(DotLottieEventListener::onUnFreeze)
                start()
            }
            field = value
        }

    // TODO: Implement duration
    var duration: Double = 0.0
        private set

    // TODO: get segments from dlPlayer
    val segments: Pair<Double, Double>
        get() = 0.0 to 0.0

    // TODO: Implement repeatCount
    private var remainingRepeatCount: Int = repeatCount

    /**
     * Animation handler used to schedule updates for this animation.
     */
    private val mHandler = Handler(Looper.getMainLooper())
    private val mNextFrameRunnable = Runnable {
        invalidateSelf()
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun getIntrinsicWidth(): Int {
        return width
    }

    override fun getIntrinsicHeight(): Int {
        return height
    }

    var useFrameInterpolation: Boolean
        get() = dlPlayer!!.config().useFrameInterpolation
        set(value) {
            config.useFrameInterpolation = value
            dlPlayer!!.setConfig(config)
        }

    // TODO: implement repeat count
    var loopCount: UInt = 0u
        get() = 0u

    var mode: Mode
        get() = dlPlayer!!.config().mode
        set(value) {
            config.mode = value;
            dlPlayer!!.setConfig(config)
        }

    val totalFrame: Float
        get() = dlPlayer!!.totalFrames()


    val autoPlay: Boolean
        get() = dlPlayer!!.config().autoplay

    val currentFrame: Float
        get() = dlPlayer!!.currentFrame()

    val backgroundColor: String
        get() = _backgroundColor

    @get:FloatRange(from = 0.0)
    var speed: Float
        get() = dlPlayer!!.config().speed
        set(speed) {
            config.speed = speed
            dlPlayer!!.setConfig(config)
        }

    private var mDotLottieError: Throwable? = null
    val isLoaded: Boolean
        get() = dlPlayer!!.isLoaded()


    init {
        try {
            initialize()
        } catch (e: Throwable) {
            dotLottieEventListener.forEach { it.onLoadError(e) }
        }
    }

    private fun initialize() {
        dlPlayer = DotLottiePlayer(config)
        dlPlayer!!.loadAnimationData(animationData, width.toUInt(), height.toUInt())
        bitmapBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        nativeBuffer = Pointer(dlPlayer!!.bufferPtr().toLong())
        dotLottieEventListener.forEach(DotLottieEventListener::onLoad)
    }


    fun release() {
        dlPlayer!!.destroy()
        dotLottieEventListener.forEach(DotLottieEventListener::onDestroy)
        if (bitmapBuffer != null)  {
            bitmapBuffer?.recycle()
            bitmapBuffer = null
        }
    }
    fun resize(w: Int, h: Int) {
        width = w
        height = h

        bitmapBuffer?.recycle()
        bitmapBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        dlPlayer!!.resize(width.toUInt(), height.toUInt())
        nativeBuffer = Pointer(dlPlayer!!.bufferPtr().toLong())
    }

    override fun isRunning(): Boolean {
        return dlPlayer!!.isPlaying()
    }

    fun setBackgroundColor(hexColor: String) {
        _backgroundColor = hexColor
    }

    fun play() {
        dlPlayer!!.play()
        dotLottieEventListener.forEach(DotLottieEventListener::onPlay)
        invalidateSelf()
    }

    override fun start() {
        play()
    }

    fun setPlayMode(playMode:  Mode) {
        config.mode = playMode
        dlPlayer!!.setConfig(config)
    }

    override fun stop() {
        dlPlayer!!.stop()
        dotLottieEventListener.forEach(DotLottieEventListener::onStop)
        mHandler.removeCallbacks(mNextFrameRunnable)
    }

    fun isPaused(): Boolean {
        return dlPlayer!!.isPaused()
    }

    fun isStopped(): Boolean {
        return dlPlayer!!.isStopped()
    }

    fun setCurrentFrame(frame: Float) {
        mHandler.removeCallbacks(mNextFrameRunnable)
        dlPlayer!!.setFrame(frame)
        dlPlayer!!.render()
        invalidateSelf()
    }

    fun setFrameInterpolation(enabled: Boolean) {
        useFrameInterpolation = enabled
    }

    fun setSegments(first: Double, last: Double) {
        // TODO: OOPS: where is segments method from rust
    }

    fun pause() {
        dlPlayer!!.pause()
        dotLottieEventListener.forEach(DotLottieEventListener::onPause)
        mHandler.removeCallbacks(mNextFrameRunnable)
    }

    override fun draw(canvas: Canvas) {
        if (bitmapBuffer == null || dlPlayer == null) return

        val nextFrame = dlPlayer!!.requestFrame()
        dlPlayer!!.setFrame(nextFrame)
        dlPlayer!!.render()

        val bufferBytes = nativeBuffer!!.getByteBuffer(0, dlPlayer!!.bufferLen().toLong())
        bufferBytes.rewind()
        bitmapBuffer!!.copyPixelsFromBuffer(bufferBytes)
        bufferBytes.rewind()

        canvas.drawColor(backgroundColor.toColor())
        canvas.drawBitmap(bitmapBuffer!!, 0f, 0f, Paint())
        dotLottieEventListener.forEach {
            it.onFrame(frame = dlPlayer!!.currentFrame())
        }

        mHandler.postDelayed(
            mNextFrameRunnable,
            0
        )
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