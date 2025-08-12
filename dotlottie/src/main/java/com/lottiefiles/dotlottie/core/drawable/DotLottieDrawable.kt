package com.lottiefiles.dotlottie.core.drawable

import android.content.Context
import android.content.Intent
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
import com.lottiefiles.dotlottie.core.util.DotLottieEventListener
import com.dotlottie.dlplayer.DotLottiePlayer
import com.dotlottie.dlplayer.Config
import com.dotlottie.dlplayer.Event
import com.dotlottie.dlplayer.Layout
import com.dotlottie.dlplayer.Manifest
import com.dotlottie.dlplayer.Marker
import com.dotlottie.dlplayer.Mode
import com.dotlottie.dlplayer.Observer
import com.dotlottie.dlplayer.OpenUrlPolicy
import com.dotlottie.dlplayer.StateMachineObserver
import com.dotlottie.dlplayer.createDefaultOpenUrlPolicy
import com.lottiefiles.dotlottie.core.util.DotLottieContent
import com.lottiefiles.dotlottie.core.util.StateMachineEventListener
import com.sun.jna.Pointer
import androidx.core.graphics.createBitmap
import com.dotlottie.dlplayer.StateMachineInternalObserver

private const val BYTES_PER_PIXEL = 4

class DotLottieDrawable(
    private val animationData: DotLottieContent,
    private var width: Int = 0,
    private var height: Int = 0,
    private val dotLottieEventListener: MutableList<DotLottieEventListener>,
    private var config: Config,
) : Drawable(), Animatable {

    private var nativeBuffer: Pointer? = null
    private var bitmapBuffer: Bitmap? = null
    private var dlPlayer: DotLottiePlayer? = null
    private var stateMachineListeners: MutableList<StateMachineEventListener> = mutableListOf()
    private var stateMachineGestureListeners: MutableList<String> = mutableListOf()

    var freeze: Boolean = false
        set(value) {
            if (value) {
                dotLottieEventListener.forEach(DotLottieEventListener::onFreeze)
                mHandler.removeCallbacks(mNextFrameRunnable)
                dlPlayer!!.pause()
            } else {
                dotLottieEventListener.forEach(DotLottieEventListener::onUnFreeze)
                dlPlayer!!.play()
                invalidateSelf()
            }
            field = value
        }

    var duration: Float = 0.0f
        get() = dlPlayer!!.duration()

    var loopCount: UInt = 0u
        get() = dlPlayer!!.loopCount()

    val segment: Pair<Float, Float>?
        get() {
            if (dlPlayer!!.config().segment.isEmpty()) return null
            return Pair(dlPlayer!!.config().segment[0], dlPlayer!!.config().segment[1])
        }

    // TODO: Implement repeatCount

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

    val playMode: Mode
        get() = dlPlayer!!.config().mode
    val totalFrame: Float
        get() = dlPlayer!!.totalFrames()


    val autoplay: Boolean
        get() = dlPlayer!!.config().autoplay

    val currentFrame: Float
        get() = dlPlayer!!.currentFrame()

    var loop: Boolean
        get() = dlPlayer!!.config().loopAnimation
        set(value) {
            config.loopAnimation = value
            dlPlayer!!.setConfig(config)
        }

    var marker: String
        get() = dlPlayer!!.config().marker
        set(value) {
            config.marker = value
            dlPlayer!!.setConfig(config)
        }

    val markers: List<Marker>
        get() = dlPlayer!!.markers()

    var layout: Layout
        get() = dlPlayer!!.config().layout
        set(value) {
            config.layout = value
            dlPlayer!!.setConfig(config)
        }

    val activeThemeId: String
        get() = dlPlayer?.activeThemeId() ?: ""

    val activeAnimationId: String
        get() = dlPlayer?.activeAnimationId() ?: ""

    @get:FloatRange(from = 0.0)
    var speed: Float
        get() = dlPlayer!!.config().speed
        set(speed) {
            config.speed = speed
            dlPlayer!!.setConfig(config)
        }

    val isLoaded: Boolean
        get() = dlPlayer!!.isLoaded()


    init {
        try {
            initialize()
        } catch (e: Throwable) {
            dotLottieEventListener.forEach { it.onLoadError(e) }
        }
    }

    private fun subscribe() {
        val observer = object : Observer {
            override fun onComplete() {
                dotLottieEventListener.forEach(DotLottieEventListener::onComplete)
            }

            override fun onFrame(frameNo: Float) {
                dotLottieEventListener.forEach { it.onFrame(frameNo) }
            }

            override fun onPause() {
                dotLottieEventListener.forEach(DotLottieEventListener::onPause)
            }

            override fun onStop() {
                dotLottieEventListener.forEach(DotLottieEventListener::onStop)
            }

            override fun onPlay() {
                dotLottieEventListener.forEach(DotLottieEventListener::onPlay)
            }

            override fun onLoad() {
                dotLottieEventListener.forEach(DotLottieEventListener::onLoad)
            }

            override fun onLoop(loopCount: UInt) {
                dotLottieEventListener.forEach { it.onLoop(loopCount.toInt()) }
            }

            override fun onRender(frameNo: Float) {
                dotLottieEventListener.forEach { it.onRender(frameNo) }
            }

            override fun onLoadError() {
                dotLottieEventListener.forEach { listener ->
                    listener.onLoadError()
                    listener.onLoadError(Throwable("Load error occurred"))
                }
            }
        }
        dlPlayer?.subscribe(observer)
    }

    private fun initialize() {
        dlPlayer = DotLottiePlayer(config)
        when (animationData) {
            is DotLottieContent.Json -> {
                dlPlayer!!.loadAnimationData(
                    animationData.jsonString,
                    width.toUInt(),
                    height.toUInt()
                )
            }

            is DotLottieContent.Binary -> {
                dlPlayer!!.loadDotlottieData(animationData.data, width.toUInt(), height.toUInt())
            }
        }
        bitmapBuffer = createBitmap(width, height)
        nativeBuffer = Pointer(dlPlayer!!.bufferPtr().toLong())
        this.subscribe()
    }

    fun release() {
        dlPlayer!!.destroy()
        dotLottieEventListener.forEach(DotLottieEventListener::onDestroy)
        if (bitmapBuffer != null) {
            bitmapBuffer?.recycle()
            bitmapBuffer = null
        }
    }

    fun resize(w: Int, h: Int) {
        width = w
        height = h

        bitmapBuffer?.recycle()
        bitmapBuffer = createBitmap(width, height)

        dlPlayer!!.resize(width.toUInt(), height.toUInt())
        nativeBuffer = Pointer(dlPlayer!!.bufferPtr().toLong())
    }

    override fun isRunning(): Boolean {
        return dlPlayer!!.isPlaying()
    }

    fun play() {
        dlPlayer!!.play()
        invalidateSelf()
    }

    override fun start() {
        play()
    }

    fun setPlayMode(playMode: Mode) {
        config.mode = playMode
        dlPlayer!!.setConfig(config)
    }

    override fun stop() {
        dlPlayer!!.stop()
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

    fun setSegment(first: Float, second: Float) {
        config.segment = listOf(first, second)
        dlPlayer!!.setConfig(config)
    }

    fun loadAnimation(
        animationId: String,
    ) {
        dlPlayer?.loadAnimation(animationId, width.toUInt(), height.toUInt())
    }

    fun setTheme(themeId: String) {
        dlPlayer?.setTheme(themeId)
    }

    fun setThemeData(themeData: String) {
        dlPlayer?.setThemeData(themeData)
    }

    fun resetTheme() {
        dlPlayer?.resetTheme()
    }

    fun setSlots(slots: String) {
        dlPlayer?.setSlots(slots)
    }

    fun manifest(): Manifest? {
        return dlPlayer?.manifest()
    }

    fun addEventListenter(listener: DotLottieEventListener) {
        if (!dotLottieEventListener.contains(listener)) {
            dotLottieEventListener.add(listener)
        }
    }

    fun removeEventListener(listener: DotLottieEventListener) {
        dotLottieEventListener.remove(listener)
    }

    fun clearEventListeners() {
        dotLottieEventListener.clear()
    }

    fun pause() {
        dlPlayer!!.pause()
        mHandler.removeCallbacks(mNextFrameRunnable)
    }

    fun stateMachineStart(openUrl: OpenUrlPolicy = createDefaultOpenUrlPolicy(), onOpenUrl: ((url: String) -> Unit)? = null): Boolean {
        val result = dlPlayer?.stateMachineStart(openUrl) ?: false

        // Start render loop
        if (result) {
            if (dlPlayer != null) {
                stateMachineGestureListeners =
                    dlPlayer!!.stateMachineFrameworkSetup().map { it.lowercase() }.toSet().toMutableList()
            }

            dlPlayer?.stateMachineSubscribe(object : StateMachineObserver {
                override fun onBooleanInputValueChange(
                    inputName: String,
                    oldValue: Boolean,
                    newValue: Boolean
                ) {
                    stateMachineListeners.forEach { it.onBooleanInputValueChange(inputName, oldValue, newValue) }
                }

                override fun onCustomEvent(message: String) {
                    stateMachineListeners.forEach { it.onCustomEvent(message) }
                }

                override fun onError(message: String) {
                    stateMachineListeners.forEach { it.onError(message) }
                }

                override fun onNumericInputValueChange(
                    inputName: String,
                    oldValue: Float,
                    newValue: Float
                ) {
                    stateMachineListeners.forEach { it.onNumericInputValueChange(inputName, oldValue, newValue) }
                }

                override fun onStart() {
                    stateMachineListeners.forEach { it.onStart() }
                }

                override fun onStateEntered(enteringState: String) {
                    stateMachineListeners.forEach { it.onStateEntered(enteringState) }
                }

                override fun onStateExit(leavingState: String) {
                    stateMachineListeners.forEach { it.onStateExit(leavingState) }
                }

                override fun onStop() {
                    stateMachineListeners.forEach { it.onStop() }
                }

                override fun onStringInputValueChange(
                    inputName: String,
                    oldValue: String,
                    newValue: String
                ) {
                    stateMachineListeners.forEach { it.onStringInputValueChange(inputName,oldValue,newValue) }
                }

                override fun onTransition(previousState: String, newState: String) {
                    stateMachineListeners.forEach { it.onTransition(previousState, newState) }
                }

                override fun onInputFired(inputName: String) {
                    stateMachineListeners.forEach { it.onInputFired(inputName) }
                }
            })

            // For internal observer
            dlPlayer?.stateMachineInternalSubscribe(object : StateMachineInternalObserver {
                override fun onMessage(message: String) {
                    if (message.startsWith("OpenUrl: ")) {
                        val url = message.substringAfter("OpenUrl: ")
                        onOpenUrl?.invoke(url)
                    }
                }
            })
        }
        return result
    }

    fun stateMachineStop(): Boolean {
        return dlPlayer?.stateMachineStop() ?: false
    }

    fun stateMachineLoad(stateMachineId: String): Boolean {
        return dlPlayer?.stateMachineLoad(stateMachineId) ?: false
    }

    fun stateMachineLoadData(data: String): Boolean {
        return dlPlayer?.stateMachineLoadData(data) ?: false
    }

    /**
     * Internal function to notify the state machine of gesture input.
     */
    fun stateMachinePostEvent(event: Event, force: Boolean = false) {
        // Extract the event name before the parenthesis
        val eventName = event.toString().split("(").firstOrNull()?.lowercase() ?: event.toString()

        if (force) {
            dlPlayer?.stateMachinePostEvent(event)
        } else if (stateMachineGestureListeners.contains(eventName)) {
            dlPlayer?.stateMachinePostEvent(event)
        }
    }

    fun stateMachineAddEventListener(listener: StateMachineEventListener) {
        stateMachineListeners.add(listener)
    }

    fun stateMachineRemoveEventListener(listener: StateMachineEventListener) {
        stateMachineListeners.remove(listener)
    }

    fun stateMachineSetNumericInput(key: String, value: Float): Boolean {
        return dlPlayer?.stateMachineSetNumericInput(key, value) ?: false
    }

    fun stateMachineSetStringInput(key: String, value: String): Boolean {
        return dlPlayer?.stateMachineSetStringInput(key, value) ?: false
    }

    fun stateMachineSetBooleanInput(key: String, value: Boolean): Boolean {
        return dlPlayer?.stateMachineSetBooleanInput(key, value) ?: false
    }

    fun stateMachineGetNumericInput(key: String): Float? {
        return dlPlayer?.stateMachineGetNumericInput(key)
    }

    fun stateMachineGetStringInput(key: String): String? {
        return dlPlayer?.stateMachineGetStringInput(key)
    }

    fun stateMachineGetBooleanInput(key: String): Boolean? {
        return dlPlayer?.stateMachineGetBooleanInput(key)
    }

    fun stateMachineCurrentState(): String? {
        return dlPlayer?.stateMachineCurrentState()
    }

    fun stateMachineFireEvent(event: String) {
        dlPlayer?.stateMachineFireEvent(event)
    }

    override fun draw(canvas: Canvas) {
        if (bitmapBuffer == null || dlPlayer == null) return

        val ticked = dlPlayer!!.tick()

        if (ticked || dlPlayer!!.render()) {
            val bufferBytes = nativeBuffer!!.getByteBuffer(0, dlPlayer!!.bufferLen().toLong() * BYTES_PER_PIXEL)
            bufferBytes.rewind()
            bitmapBuffer!!.copyPixelsFromBuffer(bufferBytes)
            bufferBytes.rewind()
            canvas.drawBitmap(bitmapBuffer!!, 0f, 0f, Paint())
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