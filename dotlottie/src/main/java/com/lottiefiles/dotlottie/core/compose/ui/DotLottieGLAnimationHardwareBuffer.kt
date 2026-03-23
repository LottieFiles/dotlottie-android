package com.lottiefiles.dotlottie.core.compose.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.dotlottie.dlplayer.DotLottiePlayerEvent
import com.dotlottie.dlplayer.Event
import com.dotlottie.dlplayer.Layout
import com.dotlottie.dlplayer.Mode
import com.dotlottie.dlplayer.StateMachinePlayerEvent
import com.dotlottie.dlplayer.createDefaultLayout
import com.lottiefiles.dotlottie.core.compose.runtime.DotLottieController
import com.lottiefiles.dotlottie.core.compose.runtime.DotLottiePlayerState
import com.lottiefiles.dotlottie.core.util.DotLottieContent
import com.lottiefiles.dotlottie.core.util.DotLottieEventListener
import com.lottiefiles.dotlottie.core.util.DotLottieSource
import com.lottiefiles.dotlottie.core.util.DotLottieUtils
import com.lottiefiles.dotlottie.core.util.GlHardwareRenderer
import com.lottiefiles.dotlottie.core.util.InternalDotLottieApi
import kotlin.math.pow
import com.dotlottie.dlplayer.Config as DLConfig

/**
 * API 31+ Compose-native GL composable using AHardwareBuffer for near-zero-copy GPU rendering.
 *
 * Renders into AHardwareBuffer-backed FBOs via OpenGL on a dedicated thread,
 * then wraps the result as a hardware-backed Bitmap for direct Skia drawing in Compose.
 */
@androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
@OptIn(InternalDotLottieApi::class)
@Composable
internal fun DotLottieGLAnimationHardwareBuffer(
    modifier: Modifier,
    source: DotLottieSource,
    autoplay: Boolean,
    loop: Boolean,
    useFrameInterpolation: Boolean,
    themeId: String?,
    stateMachineId: String?,
    marker: String?,
    speed: Float,
    segment: Pair<Float, Float>?,
    playMode: Mode,
    controller: DotLottieController?,
    layout: Layout,
    eventListeners: List<DotLottieEventListener>,
    threads: UInt?,
    loopCount: UInt,
) {
    val context = LocalContext.current
    val rController = remember { controller ?: DotLottieController() }
    val initialStateMachineId = remember { stateMachineId }

    val dlConfig = remember {
        DLConfig(
            autoplay = autoplay,
            loopAnimation = loop,
            mode = playMode,
            speed = speed,
            useFrameInterpolation = useFrameInterpolation,
            segment = if (segment != null) listOf(segment.first, segment.second) else emptyList(),
            backgroundColor = 0u,
            marker = marker ?: "",
            layout = layout,
            themeId = themeId ?: "",
            stateMachineId = "",
            animationId = "",
            loopCount = loopCount
        )
    }

    val renderer = remember { GlHardwareRenderer() }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var drawVersion by remember { mutableIntStateOf(0) }
    val drawDstRect = remember { android.graphics.RectF() }
    var layoutSize by remember { mutableStateOf<Size?>(null) }
    var animationData by remember { mutableStateOf<Result<DotLottieContent>?>(null) }
    val currentState by rController.currentState.collectAsState()
    val frameUpdateRequested by rController.frameUpdateRequested.collectAsState()

    // Set up frame callback to receive hardware bitmaps from the renderer
    remember {
        renderer.setFrameCallback(object : GlHardwareRenderer.FrameCallback {
            override fun onFrame(newBitmap: Bitmap) {
                bitmap = newBitmap
                drawVersion++
            }

            override fun onPlayerEvent(event: DotLottiePlayerEvent) {
                when (event) {
                    is DotLottiePlayerEvent.Load -> {
                        rController.updateState(DotLottiePlayerState.LOADED)
                        rController.eventListeners.forEach(DotLottieEventListener::onLoad)
                    }
                    is DotLottiePlayerEvent.LoadError -> {
                        rController.updateState(DotLottiePlayerState.ERROR)
                        rController.eventListeners.forEach { listener ->
                            listener.onLoadError()
                            listener.onLoadError(Throwable("Load error occurred"))
                        }
                    }
                    is DotLottiePlayerEvent.Play -> {
                        rController.updateState(DotLottiePlayerState.PLAYING)
                        rController.eventListeners.forEach(DotLottieEventListener::onPlay)
                    }
                    is DotLottiePlayerEvent.Pause -> {
                        rController.updateState(DotLottiePlayerState.PAUSED)
                        rController.eventListeners.forEach(DotLottieEventListener::onPause)
                    }
                    is DotLottiePlayerEvent.Stop -> {
                        rController.updateState(DotLottiePlayerState.STOPPED)
                        rController.eventListeners.forEach(DotLottieEventListener::onStop)
                    }
                    is DotLottiePlayerEvent.Frame -> {
                        rController.eventListeners.forEach { it.onFrame(event.frameNo) }
                    }
                    is DotLottiePlayerEvent.Render -> {
                        rController.eventListeners.forEach { it.onRender(event.frameNo) }
                    }
                    is DotLottiePlayerEvent.Loop -> {
                        rController.eventListeners.forEach { it.onLoop(event.loopCount.toInt()) }
                    }
                    is DotLottiePlayerEvent.Complete -> {
                        rController.updateState(DotLottiePlayerState.COMPLETED)
                        rController.eventListeners.forEach(DotLottieEventListener::onComplete)
                    }
                }
            }

            override fun onStateMachineEvent(event: StateMachinePlayerEvent) {
                when (event) {
                    is StateMachinePlayerEvent.Start ->
                        rController.stateMachineListeners.forEach { it.onStart() }
                    is StateMachinePlayerEvent.Stop ->
                        rController.stateMachineListeners.forEach { it.onStop() }
                    is StateMachinePlayerEvent.Transition ->
                        rController.stateMachineListeners.forEach {
                            it.onTransition(event.previousState, event.newState)
                        }
                    is StateMachinePlayerEvent.StateEntered ->
                        rController.stateMachineListeners.forEach { it.onStateEntered(event.state) }
                    is StateMachinePlayerEvent.StateExit ->
                        rController.stateMachineListeners.forEach { it.onStateExit(event.state) }
                    is StateMachinePlayerEvent.CustomEvent ->
                        rController.stateMachineListeners.forEach { it.onCustomEvent(event.message) }
                    is StateMachinePlayerEvent.Error ->
                        rController.stateMachineListeners.forEach { it.onError(event.message) }
                    is StateMachinePlayerEvent.StringInputChange ->
                        rController.stateMachineListeners.forEach {
                            it.onStringInputValueChange(event.name, event.oldValue, event.newValue)
                        }
                    is StateMachinePlayerEvent.NumericInputChange ->
                        rController.stateMachineListeners.forEach {
                            it.onNumericInputValueChange(event.name, event.oldValue, event.newValue)
                        }
                    is StateMachinePlayerEvent.BooleanInputChange ->
                        rController.stateMachineListeners.forEach {
                            it.onBooleanInputValueChange(event.name, event.oldValue, event.newValue)
                        }
                    is StateMachinePlayerEvent.InputFired ->
                        rController.stateMachineListeners.forEach { it.onInputFired(event.name) }
                }
            }

            override fun onStateMachineInternalEvent(message: String) {
                if (message.startsWith("OpenUrl: ")) {
                    val payload = message.substringAfter("OpenUrl: ")
                    val url = if (payload.contains(" | Target: ")) {
                        payload.substringBefore(" | Target: ")
                    } else {
                        payload
                    }
                    rController.onOpenUrlCallback?.invoke(url)
                }
            }
        })
        true // return non-null for remember
    }

    // Start renderer and wire up controller when player is ready
    LaunchedEffect(Unit) {
        renderer.setOnPlayerReady { player, config ->
            rController.setPlayerInstance(player, config)
            rController.init()
            // State machine loading is deferred to after content is loaded
        }
        renderer.start(dlConfig, threads)
    }

    // Fetch content when source changes
    LaunchedEffect(source) {
        animationData = kotlin.runCatching {
            DotLottieUtils.getContent(context, source)
        }
    }

    // Load content + resize when data or layout changes
    LaunchedEffect(animationData, layoutSize) {
        val data = animationData?.getOrNull() ?: return@LaunchedEffect
        val size = layoutSize ?: return@LaunchedEffect

        renderer.resize(size.width.toInt(), size.height.toInt())
        renderer.loadContent(data)

        // Load state machine AFTER content — both post to the GL handler in order,
        // so stateMachineLoad runs after loadContent completes on the GL thread.
        if (!initialStateMachineId.isNullOrEmpty()) {
            renderer.stateMachineLoad(initialStateMachineId)
            renderer.stateMachineStart()
        }

        drawDstRect.set(0f, 0f, size.width, size.height)
    }

    // Handle resize from layout changes
    LaunchedEffect(layoutSize) {
        val size = layoutSize ?: return@LaunchedEffect
        renderer.resize(size.width.toInt(), size.height.toInt())
        drawDstRect.set(0f, 0f, size.width, size.height)
    }

    // Sync reactive config parameters
    LaunchedEffect(loop, autoplay, playMode, useFrameInterpolation, speed, segment, themeId, marker, layout) {
        val conf = DLConfig(
            autoplay = autoplay,
            loopAnimation = loop,
            mode = playMode,
            speed = speed,
            useFrameInterpolation = useFrameInterpolation,
            segment = if (segment != null) listOf(segment.first, segment.second) else emptyList(),
            backgroundColor = 0u,
            marker = marker ?: "",
            layout = layout,
            themeId = themeId ?: "",
            stateMachineId = "",
            animationId = "",
            loopCount = loopCount
        )
        renderer.setConfig(conf)
    }

    // Handle controller-driven frame updates
    LaunchedEffect(frameUpdateRequested) {
        if (frameUpdateRequested > 0) {
            renderer.requestRender()
        }
    }

    // Re-kick rendering on state changes
    LaunchedEffect(currentState) {
        renderer.requestRender()
    }

    // Register event listeners + cleanup
    DisposableEffect(Unit) {
        eventListeners.forEach { rController.addEventListener(it) }

        onDispose {
            renderer.release()
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
            .graphicsLayer { scaleY = -1f } // Flip: GL origin is bottom-left, Canvas is top-left
            .drawBehind {
                @Suppress("UNUSED_EXPRESSION")
                drawVersion
                val bmp = bitmap
                if (bmp != null && !bmp.isRecycled) {
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawBitmap(bmp, null, drawDstRect, null)
                    }
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val downPosition = down.position
                    val scaledX = downPosition.x
                    val scaledY = downPosition.y

                    rController.stateMachinePostEvent(Event.PointerDown(scaledX, scaledY))

                    var movedTooMuch = false
                    val touchSlop = 20f

                    do {
                        val event = awaitPointerEvent()
                        val position = event.changes.first()

                        if (!position.pressed) {
                            val upPosition = position.position
                            val upScaledX = upPosition.x
                            val upScaledY = upPosition.y

                            rController.stateMachinePostEvent(Event.PointerUp(upScaledX, upScaledY))

                            val distance = kotlin.math.sqrt(
                                (upPosition.x - downPosition.x).pow(2) +
                                        (upPosition.y - downPosition.y).pow(2)
                            )

                            if (distance < touchSlop && !movedTooMuch) {
                                rController.stateMachinePostEvent(Event.Click(upScaledX, upScaledY))
                            }
                            break
                        } else {
                            val movePosition = position.position
                            val moveX = movePosition.x
                            val moveY = movePosition.y

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
        @Suppress("UNUSED_EXPRESSION")
        drawVersion
    }
}
