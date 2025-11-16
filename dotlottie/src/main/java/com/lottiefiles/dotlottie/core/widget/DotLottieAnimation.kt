package com.lottiefiles.dotlottie.core.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.FloatRange
import com.dotlottie.dlplayer.Event
import com.dotlottie.dlplayer.Fit
import com.dotlottie.dlplayer.Layout
import com.dotlottie.dlplayer.Manifest
import com.dotlottie.dlplayer.Marker
import com.dotlottie.dlplayer.Mode
import com.dotlottie.dlplayer.OpenUrlPolicy
import com.dotlottie.dlplayer.createDefaultLayout
import com.dotlottie.dlplayer.createDefaultOpenUrlPolicy
import com.lottiefiles.dotlottie.core.R
import com.lottiefiles.dotlottie.core.drawable.DotLottieDrawable
import com.lottiefiles.dotlottie.core.model.Config
import com.lottiefiles.dotlottie.core.util.DotLottieContent
import com.lottiefiles.dotlottie.core.util.DotLottieEventListener
import com.lottiefiles.dotlottie.core.util.DotLottieSource
import com.lottiefiles.dotlottie.core.util.DotLottieUtils
import com.lottiefiles.dotlottie.core.util.LayoutUtil
import com.lottiefiles.dotlottie.core.util.StateMachineEventListener
import com.lottiefiles.dotlottie.core.util.isUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.dotlottie.dlplayer.Config as DLConfig

data class DotLottieAttributes(
    val src: String,
    val loop: Boolean,
    val marker: String?,
    val playMode: Int,
    val speed: Float,
    val autoplay: Boolean,
    val backgroundColor: Int,
    val useFrameInterpolation: Boolean,
    val themeId: String?,
    val stateMachineId: String?,
    val animationId: String?,
    val threads: UInt?,
    val loopCount: UInt
)

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
    private var setupConfigJob: Job? = null
    private var setupDrawableJob: Job? = null
    private var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    private val mDotLottieEventListener = mutableListOf<DotLottieEventListener>()

    private lateinit var attributes: DotLottieAttributes

    // Touch tracking for state machine interactions
    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f
    private var movedTooMuch: Boolean = false
    private val touchSlop: Float = 20f // Movement threshold to distinguish tap from drag

    @get:FloatRange(from = 0.0)
    val speed: Float
        get() = mLottieDrawable?.speed ?: error("DotLottieDrawable is null")

    val loop: Boolean
        get() = mLottieDrawable?.loop ?: false

    val autoplay: Boolean
        get() = mLottieDrawable?.autoplay ?: error("DotLottieDrawable is null")

    val isPlaying: Boolean
        get() = mLottieDrawable?.isRunning ?: false

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

    val playMode: Mode
        get() = mLottieDrawable?.playMode ?: error("DotLottieDrawable is null")

    val segment: Pair<Float, Float>
        get() = mLottieDrawable?.segment ?: error("DotLottieDrawable is null")

    val duration: Float
        get() = mLottieDrawable?.duration ?: error("DotLottieDrawable is null")

    val loopCount: UInt
        get() = mLottieDrawable?.loopCount ?: error("DotLottieDrawable is null")

    val useFrameInterpolation: Boolean
        get() = mLottieDrawable?.useFrameInterpolation ?: error("DotLottieDrawable is null")

    val marker: String
        get() = mLottieDrawable?.marker ?: error("DotLottieDrawable is null")

    val markers: List<Marker>
        get() = mLottieDrawable?.markers ?: error("DotLottieDrawable is null")

    val activeThemeId: String
        get() = mLottieDrawable?.activeThemeId ?: ""

    val activeAnimationId: String
        get() = mLottieDrawable?.activeAnimationId ?: ""

    /***
     * Method
     */
    fun setFrame(frame: Float) {
        mLottieDrawable?.setCurrentFrame(frame)
        invalidate()
    }

    fun setUseFrameInterpolation(enable: Boolean) {
        mLottieDrawable?.useFrameInterpolation = enable
    }

    fun setSegment(firstFrame: Float, lastFrame: Float) {
        mLottieDrawable?.setSegment(firstFrame, lastFrame)
    }

    fun setLoop(loop: Boolean) {
        mLottieDrawable?.loop = loop
    }

    fun loadAnimation(
        animationId: String,
    ) {
        mLottieDrawable?.loadAnimation(animationId)
    }

    fun manifest(): Manifest? {
        return mLottieDrawable?.manifest()
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

    fun setLoopCount(loopCount: UInt) {
        mLottieDrawable?.loopCount = loopCount
    }

    fun setMarker(marker: String) {
        mLottieDrawable?.marker = marker
    }

    fun setLayout(fit: Fit, alignment: LayoutUtil.Alignment) {
        mLottieDrawable?.let {
            it.layout = Layout(fit, listOf(alignment.alignment.first, alignment.alignment.second))
        }
    }

    fun setLayout(fit: Fit, alignment: Pair<Float, Float>) {
        mLottieDrawable?.let {
            it.layout = Layout(fit, listOf(alignment.first, alignment.second))
        }
    }

    fun setTheme(themeId: String) {
        mLottieDrawable?.setTheme(themeId)
    }

    fun setThemeData(themeData: String) {
        mLottieDrawable?.setThemeData(themeData)
    }

    fun resetTheme() {
        mLottieDrawable?.resetTheme()
    }

    fun setSlots(slots: String) {
        mLottieDrawable?.setSlots(slots)
    }

    fun play() {
        mLottieDrawable?.play()
    }

    fun stop() {
        mLottieDrawable?.stop()
    }

    fun setPlayMode(repeatMode: Mode) {
        mLottieDrawable?.setPlayMode(repeatMode)
    }

    fun pause() {
        mLottieDrawable?.pause()
    }

    fun destroy() {
        mLottieDrawable?.release()
    }

    fun load(config: Config) {
        mLottieDrawable?.release()
        mLottieDrawable = null
        mConfig = config
        setupConfig()
    }

    init {
        retrieveAttributes()
        waitForLayout()
    }

    private fun retrieveAttributes() {
        context.theme.obtainStyledAttributes(attrs, R.styleable.DotLottieAnimation, 0, 0).apply {
            try {
                attributes = DotLottieAttributes(
                    src = getString(R.styleable.DotLottieAnimation_dotLottie_src) ?: "",
                    loop = getBoolean(R.styleable.DotLottieAnimation_dotLottie_loop, false),
                    marker = getString(R.styleable.DotLottieAnimation_dotLottie_marker),
                    playMode = getInt(
                        R.styleable.DotLottieAnimation_dotLottie_playMode,
                        MODE_FORWARD
                    ),
                    speed = getFloat(R.styleable.DotLottieAnimation_dotLottie_speed, 1f),
                    autoplay = getBoolean(R.styleable.DotLottieAnimation_dotLottie_autoplay, false),
                    backgroundColor = getColor(
                        R.styleable.DotLottieAnimation_dotLottie_backgroundColor,
                        Color.TRANSPARENT
                    ),
                    useFrameInterpolation = getBoolean(
                        R.styleable.DotLottieAnimation_dotLottie_useFrameInterpolation,
                        false
                    ),
                    themeId = getString(R.styleable.DotLottieAnimation_dotLottie_themeId),
                    stateMachineId = getString(R.styleable.DotLottieAnimation_dotLottie_stateMachineId),
                    animationId = getString(R.styleable.DotLottieAnimation_dotLottie_animationId),
                    threads = if (hasValue(R.styleable.DotLottieAnimation_dotLottie_threads)) {
                        getInt(R.styleable.DotLottieAnimation_dotLottie_threads, 0).toUInt()
                    } else null,
                    loopCount = if (hasValue(R.styleable.DotLottieAnimation_dotLottie_loop_count)) {
                        getInt(R.styleable.DotLottieAnimation_dotLottie_loop_count, 0).toUInt()
                    } else 0u,
                )
            } finally {
                recycle()
            }
        }
    }

    private fun waitForLayout() {
        layoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (width > 0 && height > 0) {
                    // Start animation setup only when dimensions are available
                    setupDotLottieDrawable()
                    // Remove the listener to avoid redundant calls
                    removeLayoutListener()
                }
            }
        }
        viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
    }

    private fun removeLayoutListener() {
        layoutListener?.let { listener ->
            if (viewTreeObserver.isAlive()) {
                viewTreeObserver.removeOnGlobalLayoutListener(listener)
            }
            layoutListener = null
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
        setupConfigJob?.cancel()
        setupConfigJob = coroutineScope.launch {
            runCatching {
                val content = DotLottieUtils.getContent(context, config.source)
                mLottieDrawable = DotLottieDrawable(
                    height = height,
                    width = width,
                    animationData = content,
                    dotLottieEventListener = mDotLottieEventListener.toMutableList(),
                    config = DLConfig(
                        autoplay = config.autoplay,
                        loopAnimation = config.loop,
                        mode = config.playMode,
                        speed = config.speed,
                        useFrameInterpolation = config.useFrameInterpolator,
                        backgroundColor = Color.TRANSPARENT.toUInt(),
                        segment = listOf(),
                        marker = config.marker,
                        layout = config.layout,
                        themeId = config.themeId,
                        stateMachineId = config.stateMachineId,
                        animationId = config.animationId,
                        loopCount = config.loopCount
                    ),
                    threads = config.threads
                )

                mLottieDrawable?.callback = this@DotLottieAnimation

                if (config.stateMachineId.isNotEmpty()) {
                    mLottieDrawable?.stateMachineLoad(config.stateMachineId)
                    mLottieDrawable?.stateMachineStart()
                }

                withContext(Dispatchers.Main) {
                    requestLayout()
                    invalidate()
                }
            }.onFailure { e ->
                mDotLottieEventListener.forEach {
                    it.onLoadError(e)
                }
            }
        }
    }

    private fun getMode(mode: Int): Mode {
        return when (mode) {
            1 -> Mode.FORWARD
            else -> Mode.REVERSE
        }
    }

    private fun setupDotLottieDrawable() {
        setupDrawableJob?.cancel()
        setupDrawableJob = coroutineScope.launch {
            runCatching {
                if (attributes.src.isNotBlank()) {
                    val content: DotLottieContent = if (attributes.src.isUrl()) {
                        DotLottieUtils.getContent(context, DotLottieSource.Url(attributes.src))
                    } else {
                        DotLottieUtils.getContent(context, DotLottieSource.Asset(attributes.src))
                    }

                    mLottieDrawable = DotLottieDrawable(
                        animationData = content,
                        width = width,
                        height = height,
                        dotLottieEventListener = mDotLottieEventListener,
                        config = DLConfig(
                            autoplay = attributes.autoplay,
                            loopAnimation = attributes.loop,
                            mode = getMode(attributes.playMode),
                            speed = attributes.speed,
                            useFrameInterpolation = attributes.useFrameInterpolation,
                            backgroundColor = attributes.backgroundColor.toUInt(),
                            segment = listOf(),
                            marker = attributes.marker ?: "",
                            layout = createDefaultLayout(),
                            themeId = attributes.themeId ?: "",
                            stateMachineId = attributes.stateMachineId ?: "",
                            animationId = attributes.animationId ?: "",
                            loopCount = attributes.loopCount
                        ),
                        threads = attributes.threads
                    )
                    mLottieDrawable?.callback = this@DotLottieAnimation
                    withContext(Dispatchers.Main) {
                        requestLayout()
                        invalidate()
                    }
                }
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    mDotLottieEventListener.forEach {
                        it.onLoadError(e)
                    }
                }
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

        mLottieDrawable?.let { drawable ->
            if ((width != drawable.intrinsicWidth || height != drawable.intrinsicHeight)) {
                drawable.resize(width, height)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeLayoutListener()
        setupConfigJob?.cancel()
        setupDrawableJob?.cancel()
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val drawable = mLottieDrawable ?: return super.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                movedTooMuch = false

                drawable.stateMachinePostEvent(Event.PointerDown(event.x, event.y))
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                // Calculate movement distance from initial touch
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)

                // Check if movement exceeds threshold
                if (distance > touchSlop) {
                    movedTooMuch = true
                }

                // Post pointer move event to state machine
                drawable.stateMachinePostEvent(Event.PointerMove(event.x, event.y))
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                // Post pointer up event to state machine
                drawable.stateMachinePostEvent(Event.PointerUp(event.x, event.y))

                // If movement was minimal, trigger click via performClick for accessibility
                if (!movedTooMuch) {
                    performClick()
                }

                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                // Treat cancel as pointer up
                drawable.stateMachinePostEvent(Event.PointerUp(event.x, event.y))
                invalidate()
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    // For accessibility support
    override fun performClick(): Boolean {
        super.performClick()
        // Post click event to state machine for accessibility support
        mLottieDrawable?.stateMachinePostEvent(Event.Click(lastTouchX, lastTouchY))
        invalidate()
        return true
    }

    fun addEventListener(listener: DotLottieEventListener) {
        if (!mDotLottieEventListener.contains(listener)) {
            mDotLottieEventListener.add(listener)
            mLottieDrawable?.addEventListenter(listener)
        }
    }

    fun removeEventListener(listener: DotLottieEventListener) {
        mDotLottieEventListener.remove(listener)
        mLottieDrawable?.removeEventListener(listener)
    }


    fun stateMachineStart(
        urlConfig: OpenUrlPolicy = createDefaultOpenUrlPolicy(),
        onOpenUrl: ((url: String) -> Unit)? = null
    ): Boolean {
        return mLottieDrawable?.stateMachineStart(urlConfig, onOpenUrl) ?: false
    }

    fun stateMachineStop(): Boolean {
        val result = mLottieDrawable?.stateMachineStop() ?: false
        invalidate()
        return result
    }

    fun stateMachineLoad(stateMachineId: String): Boolean {
        return mLottieDrawable?.stateMachineLoad(stateMachineId) ?: false
    }

    fun stateMachineLoadData(data: String): Boolean {
        return mLottieDrawable?.stateMachineLoadData(data) ?: false
    }

    fun clearEventListeners() {
        mDotLottieEventListener.clear()
        mLottieDrawable?.clearEventListeners()
    }

    fun stateMachinePostEvent(event: Event) {
        mLottieDrawable?.stateMachinePostEvent(event)
    }

    fun addStateMachineEventListener(listener: StateMachineEventListener) {
        mLottieDrawable?.stateMachineAddEventListener(listener)
    }

    fun removeStateMachineEventListener(listener: StateMachineEventListener) {
        mLottieDrawable?.stateMachineRemoveEventListener(listener)
    }

    fun stateMachineSetNumericInput(key: String, value: Float): Boolean {
        return mLottieDrawable?.stateMachineSetNumericInput(key, value) ?: false
    }

    fun stateMachineSetStringInput(key: String, value: String): Boolean {
        return mLottieDrawable?.stateMachineSetStringInput(key, value) ?: false
    }

    fun stateMachineSetBooleanInput(key: String, value: Boolean): Boolean {
        return mLottieDrawable?.stateMachineSetBooleanInput(key, value) ?: false
    }

    fun stateMachineGetNumericInput(key: String): Float? {
        return mLottieDrawable?.stateMachineGetNumericInput(key)
    }

    fun stateMachineGetStringInput(key: String): String? {
        return mLottieDrawable?.stateMachineGetStringInput(key)
    }

    fun stateMachineGetBooleanInput(key: String): Boolean? {
        return mLottieDrawable?.stateMachineGetBooleanInput(key)
    }

    fun stateMachineGetInputs(): List<String>? {
        return mLottieDrawable?.stateMachineGetInputs()
    }

    fun stateMachineFireEvent(event: String) {
        mLottieDrawable?.stateMachineFireEvent(event)
    }

    fun stateMachineCurrentState(): String? {
        return mLottieDrawable?.stateMachineCurrentState()
    }

    companion object {
        private const val TAG = "DotLottieAnimation"

        /**
         * When the animation reaches the end and `repeatCount` is INFINITE
         * or a positive value, the animation restarts from the beginning.
         */
        const val MODE_FORWARD = 1

        /**
         * This value used used with the [.setRepeatCount] property to repeat
         * the animation indefinitely.
         */
        const val INFINITE_LOOP = Int.MAX_VALUE
    }
}