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
import com.lottiefiles.dotlottie.core.util.DotLottieUtils
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.dotlottie.dlplayer.DotLottiePlayer
import com.dotlottie.dlplayer.Layout
import com.dotlottie.dlplayer.createDefaultLayout
import com.lottiefiles.dotlottie.core.compose.runtime.DotLottieController
import com.lottiefiles.dotlottie.core.util.DotLottieContent
import com.dotlottie.dlplayer.Config as DLConfig
import com.lottiefiles.dotlottie.core.util.DotLottieEventListener
import com.lottiefiles.dotlottie.core.util.DotLottieSource
import com.sun.jna.Pointer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.nio.ByteBuffer


@Composable
fun DotLottieAnimation(
    modifier: Modifier = Modifier,
    width: UInt,
    height: UInt,
    source: DotLottieSource,
    autoplay: Boolean = false,
    loop: Boolean = false,
    useFrameInterpolation: Boolean = true,
    themeId: String? = null,
    marker: String? = null,
    speed: Float = 1f,
    segments: Pair<Float, Float>? = null,
    playMode: Mode = Mode.FORWARD,
    controller: DotLottieController? = null,
    layout: Layout = createDefaultLayout(),
    eventListeners: List<DotLottieEventListener> = emptyList(),
) {
    val context = LocalContext.current

    val rController = remember { controller ?: DotLottieController() }

    val dlConfig = remember {
        DLConfig(
            autoplay = autoplay,
            loopAnimation = loop,
            mode = playMode,
            speed = speed,
            useFrameInterpolation = useFrameInterpolation,
            segments = if (segments != null) listOf(
                segments.first,
                segments.second
            ) else emptyList(),
            backgroundColor = 0u,
            marker = marker ?: "",
            layout = layout,
        )
    }

    val dlPlayer = remember { DotLottiePlayer(dlConfig) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var nativeBuffer by remember { mutableStateOf<Pointer?>(null) }
    var bufferBytes by remember { mutableStateOf<ByteBuffer?>(null) }
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val choreographer = remember { Choreographer.getInstance() }
    val isRunning by rController.isRunning.collectAsState()
    val _width by rController.height.collectAsState()
    val _height by rController.width.collectAsState()

    val frameCallback = remember {
        object : Choreographer.FrameCallback {
            var isActive = true
            override fun doFrame(frameTimeNanos: Long) {
                if (bufferBytes == null || bitmap == null || !isActive) return

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

                if (isRunning) {
                    choreographer.postFrameCallback(this)
                }
            }
        }
    }

    LaunchedEffect(dlConfig, source) {
        try {
            when (val animationData = DotLottieUtils.getContent(context, source)) {
                is DotLottieContent.Json -> {
                    dlPlayer.loadAnimationData(animationData.jsonString, width, height)
                }

                is DotLottieContent.Binary -> {
                    dlPlayer.loadDotlottieData(animationData.data, width, height)
                }
            }
            // Register initial height/width to controller
            if (!dlPlayer.isLoaded()) {
                rController.resize(width, height)
            }

            // Set local and native buffer
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
        } catch (e: Exception) {
            rController.eventListeners.forEach {
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

    LaunchedEffect(
        loop,
        autoplay,
        playMode,
        useFrameInterpolation,
        speed,
        segments,
        themeId,
        marker,
        layout,
    ) {
        val conf = dlPlayer.config()
        conf.loopAnimation = loop
        conf.autoplay = autoplay
        conf.mode = playMode
        conf.useFrameInterpolation = useFrameInterpolation
        conf.speed = speed
        conf.marker = marker ?: ""
        conf.layout = layout
        if (segments != null) {
            conf.segments = listOf(segments.first, segments.second)
        } else {
            conf.segments = emptyList()
        }

        dlPlayer.loadTheme(themeId ?: "")
        dlPlayer.setConfig(conf)

        // Start playing if player isCompleted
        if (autoplay && loop && dlPlayer.isComplete()) {
            dlPlayer.play()
        }

        choreographer.postFrameCallback(frameCallback)
    }

    LaunchedEffect(width, height) {
        rController.resize(width, height)
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
        rController.setPlayerInstance(dlPlayer)
        eventListeners.forEach { rController.addEventListener(it) }

        onDispose {
            frameCallback.isActive = false
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
