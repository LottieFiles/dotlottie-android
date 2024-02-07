package com.lottiefiles.dotlottie.core.ui.components

import android.graphics.Bitmap
import android.view.Choreographer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dotlottie.dlplayer.Mode
import com.lottiefiles.dotlottie.core.model.Config
import com.lottiefiles.dotlottie.core.util.DotLottieUtils
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.dotlottie.dlplayer.DotLottiePlayer
import com.dotlottie.dlplayer.Observer
import com.dotlottie.dlplayer.Config as DLConfig
import com.lottiefiles.dotlottie.core.util.DotLottieEventListener
import com.sun.jna.Pointer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import java.nio.ByteBuffer

class DotLottieController {
    private var dlplayer: DotLottiePlayer? = null
    private var observer: Observer? = null
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    var eventListeners = mutableListOf<DotLottieEventListener>()
        private set

    val isPlaying: Boolean
        get() = dlplayer?.isPlaying() ?: false
    val isLoaded: Boolean
        get() = dlplayer?.isLoaded() ?: false
    val isComplete: Boolean
        get() = dlplayer?.isComplete() ?: false
    val isStopped: Boolean
        get() = dlplayer?.isStopped() ?: false
    val isPaused: Boolean
        get() = dlplayer?.isPaused() ?: false
    val playMode: Mode
        get() = dlplayer?.config()?.mode ?: Mode.FORWARD
    fun play() {
        dlplayer?.play()
    }
    fun pause() {
        dlplayer?.pause()
    }
    fun stop() {
        dlplayer?.stop()
    }

    private fun subscribe() {
        observer = object : Observer {
            override fun onComplete() {
                _isRunning.value = false
                eventListeners.forEach(DotLottieEventListener::onComplete)
            }
            override fun onFrame(frameNo: Float) { eventListeners.forEach{ it.onFrame(frameNo) } }
            override fun onPause() {
                _isRunning.value = false
                eventListeners.forEach(DotLottieEventListener::onPause)
            }
            override fun onStop() {
                _isRunning.value = false
                eventListeners.forEach(DotLottieEventListener::onStop)
            }
            override fun onPlay() {
                _isRunning.value = true
                eventListeners.forEach(DotLottieEventListener::onPlay)
            }
            override fun onLoad() {
                _isRunning.value = dlplayer?.isPlaying() ?: false
                eventListeners.forEach(DotLottieEventListener::onLoad)
            }
            override fun onLoop(loopCount: UInt) { eventListeners.forEach{ it.onLoop(loopCount) } }
            override fun onRender(frameNo: Float) { eventListeners.forEach{ it.onRender(frameNo) } }
        }
        dlplayer?.subscribe(observer!!)
    }
    fun setPlayerInstance(player: DotLottiePlayer) {
        dlplayer?.destroy()
        dlplayer = player
        subscribe()
    }
    // TODO: Resize
    fun setFrame(frame: Float) {
        dlplayer?.setFrame(frame)
    }
    fun setUseFrameInterpolation(enable: Boolean) {
        dlplayer?.let {
            val config = it.config()
            config.useFrameInterpolation = enable;
            it.setConfig(config)
        }

    }
    fun setSegments(firstFrame: Float, lastFrame: Float) {
        dlplayer?.let {
            val config = it.config()
            config.segments = listOf(firstFrame, lastFrame);
            it.setConfig(config)
        }
    }
    fun setLoop(loop: Boolean) {
        dlplayer?.let {
            val config = it.config()
            config.loopAnimation = loop
            it.setConfig(config)
        }
    }
    fun freeze() {
        dlplayer?.pause()
        eventListeners.forEach(DotLottieEventListener::onFreeze)
    }
    fun unFreeze() {
        dlplayer?.play()
        eventListeners.forEach(DotLottieEventListener::onUnFreeze)
    }
    fun setSpeed(speed: Float) {
        dlplayer?.let {
            val config = it.config()
            config.speed = speed
            it.setConfig(config)
        }
    }
    fun setPlayMode(mode: Mode) {
        dlplayer?.let {
            val config = it.config()
            config.mode = mode
            it.setConfig(config)
        }
    }
    fun addEventListener(listener: DotLottieEventListener) {
        eventListeners.add(listener)
    }
    fun removeEventListener(listener: DotLottieEventListener) {
        eventListeners.remove(listener)
    }
}
@Composable
fun DotLottieAnimation(
    modifier: Modifier = Modifier,
    width: UInt,
    height: UInt,
    src: String = "",
    asset: String = "",
    data: Any? = null,
    autoplay: Boolean = false,
    loop: Boolean = false,
    useFrameInterpolator: Boolean = true,
    speed: Float = 1f,
    playMode: Mode = Mode.FORWARD,
    controller: DotLottieController = DotLottieController(),
    eventListeners: List<DotLottieEventListener> = listOf(),
) {
    require(src.isNotBlank() || asset.isNotBlank() || data != null) {
        "You must provide at least one of src, asset, or data parameters."
    }

    val context = LocalContext.current

    val config = remember {
        val conf  = Config.Builder()
            .autoplay(autoplay)
            .speed(speed)
            .loop(loop)
            .playMode(playMode)
            .useFrameInterpolation(useFrameInterpolator)

        if (src.isNotEmpty()) { conf.src(src) }
        if (asset.isNotEmpty()) { conf.fileName(asset) }
        if (data != null) { conf.data(data) }

        conf.build()
    }
    val dlConfig = remember {
        DLConfig(
            autoplay = config.autoplay,
            loopAnimation = config.loop,
            mode = config.playMode,
            speed = config.speed,
            useFrameInterpolation = config.useFrameInterpolator,
            segments = listOf(),
            backgroundColor = 0u,
        )
    }

    val dlPlayer = remember { DotLottiePlayer(dlConfig) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var nativeBuffer by remember { mutableStateOf<Pointer?>(null) }
    var bufferBytes by remember { mutableStateOf<ByteBuffer?>(null) }
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val choreographer = remember { Choreographer.getInstance()  }
    val isRunning by controller.isRunning.collectAsState()

    val frameCallback = remember {
        object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (bufferBytes == null || bitmap == null) return

                val nextFrame = dlPlayer.requestFrame()
                dlPlayer.setFrame(nextFrame)
                dlPlayer.render()

                bufferBytes?.let { bytes ->
                    bitmap?.let { bmp ->
                        bytes.rewind()
                        bmp.copyPixelsFromBuffer(bytes)
                        imageBitmap = bmp.asImageBitmap()
                    }
                }
                choreographer.postFrameCallback(this)
            }
        }
    }

    LaunchedEffect(config, dlConfig) {
        try {
            val animationData = DotLottieUtils.getContent(context, config)
            if (animationData != null) {
                dlPlayer.loadAnimationData(animationData, width, height)
                nativeBuffer = Pointer(dlPlayer.bufferPtr().toLong())
                bufferBytes = nativeBuffer!!.getByteBuffer(0, dlPlayer.bufferLen().toLong())
                bitmap = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
                imageBitmap = bitmap!!.asImageBitmap()
                choreographer.postFrameCallback(frameCallback)
                // Renders initial frame if not autoplaying
                val startTime = System.currentTimeMillis()
                val timeout = 500L // 500 milliseconds
                while (isActive && System.currentTimeMillis() - startTime < timeout) {
                    if (System.currentTimeMillis() - startTime > 100L && !isRunning) {
                        choreographer.removeFrameCallback(frameCallback)
                        break
                    }
                    delay(16L)
                }
            }
        } catch (e: Exception) {
            controller.eventListeners.forEach {
                it.onLoadError(e)
            }
        }
    }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            choreographer.postFrameCallback(frameCallback)
        } else {
            choreographer.removeFrameCallback(frameCallback)
        }
    }

    DisposableEffect(UInt) {
        controller.setPlayerInstance(dlPlayer)
        eventListeners.forEach{ controller.addEventListener(it) }

        onDispose {
            choreographer.removeFrameCallback(frameCallback)
            dlPlayer.destroy()
            bitmap?.recycle()
        }
    }

    Box() {
        imageBitmap?.let {
            Image(bitmap = it, contentDescription = null, modifier = modifier)
        }
    }
}
