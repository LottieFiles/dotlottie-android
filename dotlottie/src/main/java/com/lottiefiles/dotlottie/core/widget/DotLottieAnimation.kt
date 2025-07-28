package com.lottiefiles.dotlottie.core.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.FloatRange
import com.dotlottie.dlplayer.Event
import com.dotlottie.dlplayer.Fit
import com.dotlottie.dlplayer.Layout
import com.dotlottie.dlplayer.Manifest
import com.dotlottie.dlplayer.Marker
import com.dotlottie.dlplayer.Mode
import com.dotlottie.dlplayer.createDefaultLayout
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
    val themeId: String?
)

data class DotLottieRuntimeConfig(
    val source: DotLottieSource?,
    var autoplay: Boolean,
    var loop: Boolean,
    var speed: Float,
    var useFrameInterpolation: Boolean,
    var marker: String?,
    var playMode: Mode,
    var layout: Layout,
    var themeId: String?,
    var backgroundColor: Int,
    var segment: Pair<Float, Float>?
) {
    companion object {
        fun fromXmlAttributes(attributes: DotLottieAttributes): DotLottieRuntimeConfig {
            val source = if (attributes.src.isNotBlank()) {
                if (attributes.src.isUrl()) {
                    DotLottieSource.Url(attributes.src)
                } else {
                    DotLottieSource.Asset(attributes.src)
                }
            } else null
            
            return DotLottieRuntimeConfig(
                source = source,
                autoplay = attributes.autoplay,
                loop = attributes.loop,
                speed = attributes.speed,
                useFrameInterpolation = attributes.useFrameInterpolation,
                marker = attributes.marker,
                playMode = when (attributes.playMode) {
                    1 -> Mode.FORWARD
                    else -> Mode.REVERSE
                },
                layout = createDefaultLayout(),
                themeId = attributes.themeId,
                backgroundColor = attributes.backgroundColor,
                segment = null
            )
        }
    }
}

class DotLottieAnimation @JvmOverloads constructor(
    private val context: Context,
    private val attrs: AttributeSet? = null,
    private val defStyleAttr: Int = 0,
    private val defStyleRes: Int = 0
) : View(context, attrs, defStyleAttr, defStyleRes) {

    private var width: Int = 0
    private var height: Int = 0
    private var mLottieDrawable: DotLottieDrawable? = null
    private val coroutineScope = CoroutineScope(SupervisorJob())
    private var setupJob: Job? = null
    private var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    private val mDotLottieEventListener = mutableListOf<DotLottieEventListener>()

    private lateinit var attributes: DotLottieAttributes
    private var runtimeConfig: DotLottieRuntimeConfig? = null


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

    // TODO: Implement repeat count
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
        ensureRuntimeConfig().useFrameInterpolation = enable
    }

    fun setSegment(firstFrame: Float, lastFrame: Float) {
        mLottieDrawable?.setSegment(firstFrame, lastFrame)
        ensureRuntimeConfig().segment = Pair(firstFrame, lastFrame)
    }

    fun setLoop(loop: Boolean) {
        mLottieDrawable?.loop = loop
        ensureRuntimeConfig().loop = loop
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
        ensureRuntimeConfig().speed = speed
    }

    fun setMarker(marker: String) {
        mLottieDrawable?.marker = marker
        ensureRuntimeConfig().marker = marker
    }

    fun setLayout(fit: Fit, alignment: LayoutUtil.Alignment) {
        val layout = Layout(fit, listOf(alignment.alignment.first, alignment.alignment.second))
        mLottieDrawable?.layout = layout
        ensureRuntimeConfig().layout = layout
    }

    fun setLayout(fit: Fit, alignment: Pair<Float, Float>) {
        val layout = Layout(fit, listOf(alignment.first, alignment.second))
        mLottieDrawable?.layout = layout
        ensureRuntimeConfig().layout = layout
    }

    fun setTheme(themeId: String) {
        mLottieDrawable?.setTheme(themeId)
        ensureRuntimeConfig().themeId = themeId
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
        ensureRuntimeConfig().playMode = repeatMode
    }

    fun pause() {
        mLottieDrawable?.pause()
    }

    fun destroy() {
        mLottieDrawable?.release()
    }

    fun load(
        source: DotLottieSource,
        autoplay: Boolean = attributes.autoplay,
        loop: Boolean = attributes.loop,
        useFrameInterpolation: Boolean = attributes.useFrameInterpolation,
        themeId: String? = attributes.themeId,
        marker: String? = attributes.marker,
        speed: Float = attributes.speed,
        segment: Pair<Float, Float>? = null,
        playMode: Mode = getMode(attributes.playMode),
        layout: Layout = createDefaultLayout(),
        backgroundColor: Int = attributes.backgroundColor
    ) {
        // Update runtime config with programmatic values
        runtimeConfig = DotLottieRuntimeConfig(
            source = source,
            autoplay = autoplay,
            loop = loop,
            useFrameInterpolation = useFrameInterpolation,
            themeId = themeId,
            marker = marker,
            speed = speed,
            segment = segment,
            playMode = playMode,
            layout = layout,
            backgroundColor = backgroundColor
        )
        
        mLottieDrawable?.release()
        mLottieDrawable = null
        setupFromConfig()
    }

    @Deprecated(
        "Use load(source, autoplay, loop, ...) instead for better consistency with Compose API",
        ReplaceWith("load(source = config.source, autoplay = config.autoplay, loop = config.loop, useFrameInterpolation = config.useFrameInterpolator, themeId = config.themeId, marker = config.marker, speed = config.speed, playMode = config.playMode, layout = config.layout)")
    )
    fun load(config: Config) {
        load(
            source = config.source,
            autoplay = config.autoplay,
            loop = config.loop,
            useFrameInterpolation = config.useFrameInterpolator,
            themeId = config.themeId,
            marker = config.marker,
            speed = config.speed,
            playMode = config.playMode,
            layout = config.layout
        )
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
                    themeId = getString(R.styleable.DotLottieAnimation_dotLottie_themeId)
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
                    setupFromConfig()
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


    private fun getMode(mode: Int): Mode {
        return when (mode) {
            1 -> Mode.FORWARD
            else -> Mode.REVERSE
        }
    }

    private fun getCurrentConfig(): DotLottieRuntimeConfig {
        return runtimeConfig ?: DotLottieRuntimeConfig.fromXmlAttributes(attributes)
    }

    private fun ensureRuntimeConfig(): DotLottieRuntimeConfig {
        return runtimeConfig ?: DotLottieRuntimeConfig.fromXmlAttributes(attributes).also {
            runtimeConfig = it
        }
    }

    private fun setupFromConfig() {
        val config = getCurrentConfig()
        config.source?.let { source ->
            setupJob?.cancel()
            setupJob = coroutineScope.launch {
                runCatching {
                    val content = DotLottieUtils.getContent(context, source)
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
                            useFrameInterpolation = config.useFrameInterpolation,
                            backgroundColor = config.backgroundColor.toUInt(),
                            segment = config.segment?.let { listOf(it.first, it.second) } ?: listOf(),
                            marker = config.marker ?: "",
                            layout = config.layout,
                            themeId = config.themeId ?: "",
                            stateMachineId = "", // TODO: implement stateMachine
                            animationId = ""
                        )
                    )

                    mLottieDrawable?.callback = this@DotLottieAnimation
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
        setupJob?.cancel()
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
        if (!mDotLottieEventListener.contains(listener)) {
            mDotLottieEventListener.add(listener)
            mLottieDrawable?.addEventListenter(listener)
        }
    }

    fun removeEventListener(listener: DotLottieEventListener) {
        mDotLottieEventListener.remove(listener)
        mLottieDrawable?.removeEventListener(listener)
    }

    fun clearEventListeners() {
        mDotLottieEventListener.clear()
        mLottieDrawable?.clearEventListeners()
    }


    // TODO: Add stateMachine features
//    fun startStateMachine(): Boolean {
//        return mLottieDrawable?.startStateMachine() ?: false
//    }
//
//    fun stopStateMachine(): Boolean {
//        return mLottieDrawable?.stopStateMachine() ?: false
//    }
//
//    fun loadStateMachine(stateMachineId: String): Boolean {
//        return mLottieDrawable?.loadStateMachine(stateMachineId) ?: false
//    }
//
//    fun postEvent(event: Event): Int {
//        return mLottieDrawable?.postEvent(event) ?: 0
//    }
//
//    fun addStateMachineEventListener(listener: StateMachineEventListener) {
//        mLottieDrawable?.addStateMachineEventListener(listener)
//    }
//
//    fun removeStateMachineEventListener(listener: StateMachineEventListener) {
//        mLottieDrawable?.removeStateMachineEventListener(listener)
//    }
//
//    fun setStateMachineNumericContext(key: String, value: Float): Boolean {
//        return mLottieDrawable?.setStateMachineNumericContext(key, value) ?: false
//    }
//
//    fun setStateMachineStringContext(key: String, value: String): Boolean {
//        return mLottieDrawable?.setStateMachineStringContext(key, value) ?: false
//    }
//
//    fun setStateMachineBooleanContext(key: String, value: Boolean): Boolean {
//        return mLottieDrawable?.setStateMachineBooleanContext(key, value) ?: false
//    }

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