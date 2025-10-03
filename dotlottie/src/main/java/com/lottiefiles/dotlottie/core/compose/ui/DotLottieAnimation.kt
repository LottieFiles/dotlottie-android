package com.lottiefiles.dotlottie.core.compose.ui

import android.graphics.Bitmap
import android.view.Choreographer
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.input.pointer.pointerInput
import com.dotlottie.dlplayer.DotLottiePlayer
import com.dotlottie.dlplayer.Event
import com.dotlottie.dlplayer.Layout
import com.dotlottie.dlplayer.createDefaultLayout
import com.lottiefiles.dotlottie.core.compose.runtime.DotLottieController
import com.lottiefiles.dotlottie.core.util.DotLottieContent
import com.dotlottie.dlplayer.Config as DLConfig
import com.lottiefiles.dotlottie.core.util.DotLottieEventListener
import com.lottiefiles.dotlottie.core.util.DotLottieSource
import com.sun.jna.Pointer
import java.nio.ByteBuffer
import androidx.core.graphics.createBitmap
import com.lottiefiles.dotlottie.core.util.InternalDotLottieApi
import kotlin.math.pow

private const val BYTES_PER_PIXEL = 4

@OptIn(InternalDotLottieApi::class)
@Composable
fun DotLottieAnimation(
    modifier: Modifier = Modifier,
    source: DotLottieSource,
    autoplay: Boolean = false,
    loop: Boolean = false,
    useFrameInterpolation: Boolean = true,
    themeId: String? = null,
    stateMachineId: String? = null,
    marker: String? = null,
    speed: Float = 1f,
    segment: Pair<Float, Float>? = null,
    playMode: Mode = Mode.FORWARD,
    controller: DotLottieController? = null,
    layout: Layout = createDefaultLayout(),
    eventListeners: List<DotLottieEventListener> = emptyList(),
    threads: UInt? = null,
    loopCount: UInt = 0u,
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
            segment = if (segment != null) listOf(
                segment.first,
                segment.second
            ) else emptyList(),
            backgroundColor = 0u,
            marker = marker ?: "",
            layout = layout,
            themeId = themeId ?: "",
            stateMachineId = "",
            animationId = "",
            loopCount = loopCount
        )
    }

    val initialStateMachineId = remember { stateMachineId }
    val dlPlayer = remember(threads) {
        if (threads != null) {
            DotLottiePlayer.withThreads(dlConfig, threads)
        } else {
            DotLottiePlayer(dlConfig)
        }
    }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var nativeBuffer by remember { mutableStateOf<Pointer?>(null) }
    var bufferBytes by remember { mutableStateOf<ByteBuffer?>(null) }
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val choreographer = remember { Choreographer.getInstance() }
    val currentState by rController.currentState.collectAsState()
    val _width by rController.height.collectAsState()
    val _height by rController.width.collectAsState()
    var layoutSize by remember { mutableStateOf<Size?>(null) }
    var animationData by remember { mutableStateOf<DotLottieContent?>(null) }
    var forceUpdateBitmap by remember { mutableStateOf(false) }

    val frameCallback = remember {
        object : Choreographer.FrameCallback {
            var isActive = true

            override fun doFrame(frameTimeNanos: Long) {
                if (bufferBytes == null || bitmap == null || !isActive) return

                // Check if buffer needs updating
                if (rController.bufferNeedsUpdate.value) {
                    nativeBuffer = Pointer(dlPlayer.bufferPtr().toLong())
                    bufferBytes = nativeBuffer!!.getByteBuffer(
                        0,
                        dlPlayer.bufferLen().toLong() * BYTES_PER_PIXEL
                    )
                    rController.markBufferUpdated()
                }

                val ticked = dlPlayer.tick()

                if (ticked || forceUpdateBitmap) {
                    bufferBytes?.let { bytes ->
                        bitmap?.let { bmp ->
                            bytes.rewind()
                            bmp.copyPixelsFromBuffer(bytes)
                            imageBitmap = bmp.asImageBitmap()
                        }
                    }
                }

                // Bitmap update is done! Let's reset the flag
                if (!ticked && forceUpdateBitmap) {
                    forceUpdateBitmap = false
                }

                if (dlPlayer.isPlaying() || rController.stateMachineIsActive) {
                    choreographer.postFrameCallback(this)
                }
            }
        }
    }

    LaunchedEffect(source) {
        animationData = DotLottieUtils.getContent(context, source)
    }

    fun init(animationData: DotLottieContent, layoutSize: Size) {
        runCatching {
            val height = layoutSize.height.toUInt()
            val width = layoutSize.width.toUInt()
            val isLoaded = dlPlayer.isLoaded()
            // Pass the size to the controller
            rController.resize(height, width)

            when (animationData) {
                is DotLottieContent.Json -> {
                    dlPlayer.loadAnimationData(animationData.jsonString, width, height)
                }

                is DotLottieContent.Binary -> {
                    dlPlayer.loadDotlottieData(animationData.data, width, height)
                }
            }

            // Set local and native buffer
            nativeBuffer = Pointer(dlPlayer.bufferPtr().toLong())
            bufferBytes =
                nativeBuffer!!.getByteBuffer(0, dlPlayer.bufferLen().toLong() * BYTES_PER_PIXEL)
            bitmap = createBitmap(width.toInt(), height.toInt())
            imageBitmap = bitmap!!.asImageBitmap()

            if (!isLoaded) {
                rController.init()
            }
            choreographer.postFrameCallback(frameCallback)
        }.onFailure { e ->
            rController.eventListeners.forEach {
                it.onLoadError(e)
            }
        }
    }

    LaunchedEffect(animationData, layoutSize) {
        if (animationData != null && layoutSize != null) {
            init(animationData!!, layoutSize!!)
        }
        if (initialStateMachineId != null) {
            if (initialStateMachineId.isNotEmpty()) {
                rController.stateMachineLoad(initialStateMachineId)
                rController.stateMachineStart()
            }
        }
    }

    LaunchedEffect(dlPlayer.isPlaying(), currentState) {
        choreographer.postFrameCallback(frameCallback)
    }

    LaunchedEffect(
        loop,
        autoplay,
        playMode,
        useFrameInterpolation,
        speed,
        segment,
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
        conf.themeId = themeId ?: ""

        if (segment != null) {
            conf.segment = listOf(segment.first, segment.second)
        } else {
            conf.segment = emptyList()
        }

        dlPlayer.setConfig(conf)

        // Start playing if player isCompleted
        if (autoplay && loop && dlPlayer.isComplete()) {
            dlPlayer.play()
        }

        choreographer.postFrameCallback(frameCallback)
    }

    LaunchedEffect(_width, _height) {
        if (dlPlayer.isLoaded() && (_height != 0u || _width != 0u)) {
            bitmap?.recycle()
            bitmap = createBitmap(_width.toInt(), _height.toInt())
            dlPlayer.resize(_width, _height)
            nativeBuffer = Pointer(dlPlayer.bufferPtr().toLong())
            bufferBytes =
                nativeBuffer!!.getByteBuffer(0, dlPlayer.bufferLen().toLong() * BYTES_PER_PIXEL)
            imageBitmap = bitmap!!.asImageBitmap()
            if (!dlPlayer.isPlaying()) {
                forceUpdateBitmap = true
                choreographer.postFrameCallback(frameCallback)
            }
        }
    }

    DisposableEffect(UInt) {
        rController.setPlayerInstance(dlPlayer, dlConfig)
        eventListeners.forEach { rController.addEventListener(it) }

        onDispose {
            frameCallback.isActive = false
            choreographer.removeFrameCallback(frameCallback)
            dlPlayer.destroy()
            bitmap?.recycle()
        }
    }


    Box(
        modifier = modifier
            .defaultMinSize(200.dp, 200.dp)
            .onGloballyPositioned { layoutCoordinates ->
                val newSize = layoutCoordinates.size.toSize()
                if (layoutSize?.width != newSize.width || layoutSize?.height != newSize.height) {
                    layoutSize = newSize
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    // First touch (Down)
                    val down = awaitFirstDown()
                    //      down.consume() // Consume the down event

                    val downPosition = down.position
                    val scaledX = downPosition.x
                    val scaledY = downPosition.y

                    rController.stateMachinePostEvent(Event.PointerDown(scaledX, scaledY))

                    // Variables to track movement distance
                    var movedTooMuch = false
                    val touchSlop = 20f // Define a reasonable threshold for tap movement

                    // Handle move and up events
                    do {
                        val event = awaitPointerEvent()
                        val position = event.changes.first()

                        // Handle move
                        if (!position.pressed) {
                            // Touch up detected
                            val upPosition = position.position
                            val upScaledX = upPosition.x
                            val upScaledY = upPosition.y

                            rController.stateMachinePostEvent(Event.PointerUp(upScaledX, upScaledY))

                            // Check for pointer movement distance
                            val distance = kotlin.math.sqrt(
                                (upPosition.x - downPosition.x).pow(2) +
                                        (upPosition.y - downPosition.y).pow(2)
                            )

                            // If the pointer down has moved too much
                            if (distance < touchSlop && !movedTooMuch) {
                                rController.stateMachinePostEvent(Event.Click(upScaledX, upScaledY))
                            }

                            break
                        } else {
                            // Move detected
                            val movePosition = position.position
                            val moveX = movePosition.x
                            val moveY = movePosition.y

                            // Check if we've moved beyond the threshold to be considered a tap
                            val moveDistance = kotlin.math.sqrt(
                                (movePosition.x - downPosition.x).pow(2) +
                                        (movePosition.y - downPosition.y).pow(2)
                            )
                            if (moveDistance > touchSlop) {
                                movedTooMuch = true
                            }

                            rController.stateMachinePostEvent(Event.PointerMove(moveX, moveY))
                        }
                    } while (position.pressed)
                }
            }
    ) {
        imageBitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}
