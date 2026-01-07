package com.lottiefiles.dotlottie.core.drawable

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val BYTES_PER_PIXEL = 4

class DotLottieDrawable(
    private val animationData: DotLottieContent,
    private var width: Int = 0,
    private var height: Int = 0,
    private val dotLottieEventListener: MutableList<DotLottieEventListener>,
    private var config: Config,
    private val threads: UInt? = null
) : Drawable(), Animatable {

    private var nativeBuffer: Pointer? = null
    private var bitmapBuffer: Bitmap? = null
    private var dlPlayer: DotLottiePlayer? = null
    private var stateMachineListeners: MutableList<StateMachineEventListener> = mutableListOf()
    private var stateMachineGestureListeners: MutableList<String> = mutableListOf()

    private val renderScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val renderMutex = Mutex()

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
                mHandler.postDelayed({
                    dotLottieEventListener.forEach(DotLottieEventListener::onComplete)
                }, 0)
            }

            override fun onFrame(frameNo: Float) {
                mHandler.postDelayed({
                    dotLottieEventListener.forEach { it.onFrame(frameNo) }
                }, 0)
            }

            override fun onPause() {
                mHandler.postDelayed({
                    dotLottieEventListener.forEach(DotLottieEventListener::onPause)
                }, 0)
            }

            override fun onStop() {
                mHandler.postDelayed({
                    dotLottieEventListener.forEach(DotLottieEventListener::onStop)
                }, 0)
            }

            override fun onPlay() {
                mHandler.postDelayed({
                    dotLottieEventListener.forEach(DotLottieEventListener::onPlay)
                }, 0)
            }

            override fun onLoad() {
                // Update buffer pointer when animation loads
                mHandler.postDelayed({
                    dotLottieEventListener.forEach(DotLottieEventListener::onLoad)
                }, 0)
            }

            override fun onLoop(loopCount: UInt) {
                mHandler.postDelayed({
                    dotLottieEventListener.forEach { it.onLoop(loopCount.toInt()) }
                }, 0)
            }

            override fun onRender(frameNo: Float) {
                mHandler.postDelayed({
                    dotLottieEventListener.forEach { it.onRender(frameNo) }
                }, 0)
            }

            override fun onLoadError() {
                mHandler.postDelayed({
                    dotLottieEventListener.forEach { listener ->
                        listener.onLoadError()
                        listener.onLoadError(Throwable("Load error occurred"))
                    }
                }, 0)
            }
        }
        dlPlayer?.subscribe(observer)
    }

    private fun initialize() {
        dlPlayer = if (threads != null) {
            DotLottiePlayer.withThreads(config, threads)
        } else {
            DotLottiePlayer(config)
        }
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
        // Initial Load event
        if (dlPlayer!!.isLoaded()) {
            mHandler.postDelayed({
                dotLottieEventListener.forEach(DotLottieEventListener::onLoad)
            }, 0)
        }
    }

    fun release() {
        renderScope.cancel()
        dlPlayer!!.destroy()
        dotLottieEventListener.forEach(DotLottieEventListener::onDestroy)
        // Only recycle on pre-Oreo (API < 26) where bitmap memory is in Java heap.
        // On API 26+ bitmap memory is in native heap and GC handles it safely.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            bitmapBuffer?.recycle()
        }
        bitmapBuffer = null
    }

    fun resize(w: Int, h: Int) {
        width = w
        height = h

        // Wait for any pending render to complete
        // Using runBlocking here because resize needs to be synchronous
        runBlocking {
            renderMutex.withLock {
                val oldBitmap = bitmapBuffer
                bitmapBuffer = createBitmap(width, height)
                dlPlayer!!.resize(width.toUInt(), height.toUInt())
                nativeBuffer = Pointer(dlPlayer!!.bufferPtr().toLong())
                // Only recycle on pre-Oreo (API < 26) where bitmap memory is in Java heap.
                // On API 26+ bitmap memory is in native heap and GC handles it safely.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    oldBitmap?.recycle()
                }
            }
        }
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
        invalidateSelf()
    }

    fun isPaused(): Boolean {
        return dlPlayer!!.isPaused()
    }

    fun isStopped(): Boolean {
        return dlPlayer!!.isStopped()
    }

    fun setCurrentFrame(frame: Float) {
        dlPlayer!!.setFrame(frame)
        invalidateSelf()
    }

    fun setSegment(first: Float, second: Float) {
        config.segment = listOf(first, second)
        dlPlayer!!.setConfig(config)
        invalidateSelf()
    }

    fun loadAnimation(
        animationId: String,
    ) {
        val result = dlPlayer?.loadAnimation(animationId, width.toUInt(), height.toUInt())
        if (result == true) {
            invalidateSelf()
        }
    }

    fun setTheme(themeId: String) {
        dlPlayer?.setTheme(themeId)
        invalidateSelf()
    }

    fun setThemeData(themeData: String) {
        dlPlayer?.setThemeData(themeData)
    }

    fun resetTheme() {
        dlPlayer?.resetTheme()
        invalidateSelf()
    }

    fun setSlots(slots: String) {
        dlPlayer?.setSlots(slots)
        invalidateSelf()
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

    fun stateMachineStart(
        openUrl: OpenUrlPolicy = createDefaultOpenUrlPolicy(),
        onOpenUrl: ((url: String) -> Unit)? = null
    ): Boolean {
        val result = dlPlayer?.stateMachineStart(openUrl) ?: false

        // Start render loop
        if (result) {
            if (dlPlayer != null) {
                stateMachineGestureListeners =
                    dlPlayer!!.stateMachineFrameworkSetup().map { it.lowercase() }.toSet()
                        .toMutableList()
            }

            dlPlayer?.stateMachineSubscribe(object : StateMachineObserver {
                override fun onBooleanInputValueChange(
                    inputName: String,
                    oldValue: Boolean,
                    newValue: Boolean
                ) {
                    stateMachineListeners.forEach {
                        it.onBooleanInputValueChange(
                            inputName,
                            oldValue,
                            newValue
                        )
                    }
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
                    stateMachineListeners.forEach {
                        it.onNumericInputValueChange(
                            inputName,
                            oldValue,
                            newValue
                        )
                    }
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
                    stateMachineListeners.forEach {
                        it.onStringInputValueChange(
                            inputName,
                            oldValue,
                            newValue
                        )
                    }
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
            invalidateSelf()
        }
        return result
    }

    fun stateMachineStop(): Boolean {
        val result = dlPlayer?.stateMachineStop() ?: false
        invalidateSelf()
        return result;
    }

    fun stateMachineLoad(stateMachineId: String): Boolean {
        val result = dlPlayer?.stateMachineLoad(stateMachineId) ?: false
        if (result) {
            invalidateSelf()
        }
        return result
    }

    fun stateMachineLoadData(data: String): Boolean {
        val result = dlPlayer?.stateMachineLoadData(data) ?: false
        if (result) {
            invalidateSelf()
        }
        return result
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

    fun stateMachineGetInputs(): List<String>? {
        return dlPlayer?.stateMachineGetInputs()
    }

    fun stateMachineCurrentState(): String? {
        return dlPlayer?.stateMachineCurrentState()
    }

    fun stateMachineFireEvent(event: String) {
        dlPlayer?.stateMachineFireEvent(event)
    }

    override fun draw(canvas: Canvas) {
        if (bitmapBuffer == null || dlPlayer == null) return

        // Skip if previous render is still in progress (non-blocking check)
        if (!renderMutex.tryLock()) {
            // Still draw the current bitmap
            bitmapBuffer?.let { bmp ->
                if (!bmp.isRecycled) {
                    canvas.drawBitmap(bmp, 0f, 0f, Paint())
                }
            }
            if (dlPlayer!!.isPlaying()) {
                mHandler.postDelayed(mNextFrameRunnable, 0)
            }
            return
        }
        renderMutex.unlock()

        val ticked = dlPlayer!!.tick()

        if (ticked) {
            val bufferBytes =
                nativeBuffer!!.getByteBuffer(0, dlPlayer!!.bufferLen().toLong() * BYTES_PER_PIXEL)
            val targetBitmap = bitmapBuffer

            renderScope.launch {
                renderMutex.withLock {
                    try {
                        ensureActive()

                        targetBitmap?.let { bmp ->
                            if (!bmp.isRecycled) {
                                withContext(Dispatchers.Default) {
                                    bufferBytes.rewind()
                                    bmp.copyPixelsFromBuffer(bufferBytes)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during background render", e)
                    } finally {
                        // Invalidate on main thread
                        withContext(Dispatchers.Main) {
                            invalidateSelf()
                        }
                    }
                }
            }
        }

        bitmapBuffer?.let { bmp ->
            if (!bmp.isRecycled) {
                canvas.drawBitmap(bmp, 0f, 0f, Paint())
            }
        }

        if (dlPlayer!!.isPlaying()) {
            mHandler.postDelayed(mNextFrameRunnable, 0)
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
