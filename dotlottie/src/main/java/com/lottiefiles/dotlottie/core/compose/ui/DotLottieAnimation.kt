package com.lottiefiles.dotlottie.core.compose.ui

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
import com.lottiefiles.dotlottie.core.compose.runtime.DotLottieController
import com.dotlottie.dlplayer.Config as DLConfig
import com.lottiefiles.dotlottie.core.util.DotLottieEventListener
import com.sun.jna.Pointer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.nio.ByteBuffer

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
    useFrameInterpolation: Boolean = true,
    speed: Float = 1f,
    playMode: Mode = Mode.FORWARD,
    controller: DotLottieController = DotLottieController(),
    eventListeners: List<DotLottieEventListener> = emptyList(),
) {
    require(src.isNotBlank() || asset.isNotBlank() || data != null) {
        "You must provide at least one of src, asset, or data parameters."
    }

    val context = LocalContext.current

    val config = remember {
        val conf = Config.Builder()
            .autoplay(autoplay)
            .speed(speed)
            .loop(loop)
            .playMode(playMode)
            .useFrameInterpolation(useFrameInterpolation)

        if (src.isNotEmpty()) {
            conf.src(src)
        }
        if (asset.isNotEmpty()) {
            conf.fileName(asset)
        }
        if (data != null) {
            conf.data(data)
        }

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
    val choreographer = remember { Choreographer.getInstance() }
    val isRunning by controller.isRunning.collectAsState()
    val _width by controller.height.collectAsState()
    val _height by controller.width.collectAsState()

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

    LaunchedEffect(_width, _height) {
        if (dlPlayer.isLoaded() && (_height != 0u || _width != 0u)) {
            bitmap?.recycle()
            bitmap = Bitmap.createBitmap(_width.toInt(), _height.toInt(), Bitmap.Config.ARGB_8888)
            dlPlayer.resize(_width, _height)
            nativeBuffer = Pointer(dlPlayer.bufferPtr().toLong())
            bufferBytes = nativeBuffer!!.getByteBuffer(0, dlPlayer.bufferLen().toLong())
            imageBitmap = bitmap!!.asImageBitmap()
        }
    }

    DisposableEffect(UInt) {
        controller.setPlayerInstance(dlPlayer)
        eventListeners.forEach { controller.addEventListener(it) }

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
