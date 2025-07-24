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

    private val mDotLottieEventListener = mutableListOf<DotLottieEventListener>()

    private lateinit var attributes: DotLottieAttributes


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
        setupConfigJob?.cancel()
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
                    themeId = getString(R.styleable.DotLottieAnimation_dotLottie_themeId)
                )
            } finally {
                recycle()
            }
        }
    }

    private fun waitForLayout() {
        viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (width > 0 && height > 0) {
                    // Start animation setup only when dimensions are available
                    setupDrawableJob?.cancel()
                    setupDotLottieDrawable()
                    // Remove the listener to avoid redundant calls
                    viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }
        })
    }

    private fun String.isJsonAsset(): Boolean {
        return endsWith(".json")
    }

    private fun String.isDotLottieAsset(): Boolean {
        return endsWith(".lottie")
    }


    private fun setupConfig() {
        val config = mConfig ?: return
        setupConfigJob = coroutineScope.launch {
            try {
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
                        stateMachineId = "", // TODO: implement stateMachine
                        animationId = ""
                    )
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

    private fun getMode(mode: Int): Mode {
        return when (mode) {
            1 -> Mode.FORWARD
            else -> Mode.REVERSE
        }
    }

    private fun setupDotLottieDrawable() {
        setupDrawableJob = coroutineScope.launch {
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
                    dotLottieEventListener = mDotLottieEventListener.toMutableList(),
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
                        stateMachineId = "", // TODO: implement statMachine
                        animationId = ""
                    )
                )
                mLottieDrawable?.callback = this@DotLottieAnimation
                withContext(Dispatchers.Main) {
                    requestLayout()
                    invalidate()
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