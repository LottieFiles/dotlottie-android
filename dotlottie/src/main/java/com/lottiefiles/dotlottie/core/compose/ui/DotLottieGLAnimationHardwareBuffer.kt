package com.lottiefiles.dotlottie.core.compose.ui

import android.graphics.Bitmap
import android.os.Build
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.dotlottie.dlplayer.Layout
import com.dotlottie.dlplayer.Mode
import com.lottiefiles.dotlottie.core.compose.runtime.DotLottieController
import com.lottiefiles.dotlottie.core.util.DotLottieContent
import com.lottiefiles.dotlottie.core.util.DotLottieEventListener
import com.lottiefiles.dotlottie.core.util.DotLottieSource
import com.lottiefiles.dotlottie.core.util.DotLottieUtils
import com.lottiefiles.dotlottie.core.util.GlHardwareRenderer
import com.lottiefiles.dotlottie.core.util.InternalDotLottieApi

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
        buildDLConfig(autoplay, loop, playMode, speed, useFrameInterpolation, segment, marker, layout, themeId, loopCount)
    }

    val renderer = remember { GlHardwareRenderer() }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var drawVersion by remember { mutableIntStateOf(0) }
    val drawDstRect = remember { android.graphics.RectF() }
    var layoutSize by remember { mutableStateOf<Size?>(null) }
    var animationData by remember { mutableStateOf<Result<DotLottieContent>?>(null) }
    val currentState by rController.currentState.collectAsState()
    val frameUpdateRequested by rController.frameUpdateRequested.collectAsState()
    val controllerWidth by rController.width.collectAsState()
    val controllerHeight by rController.height.collectAsState()

    // Set up frame callback to receive hardware bitmaps from the renderer
    remember {
        renderer.setFrameCallback(object : GlHardwareRenderer.FrameCallback {
            override fun onFrame(newBitmap: Bitmap) {
                bitmap = newBitmap
                drawVersion++
            }

            override fun onPlayerEvent(event: com.dotlottie.dlplayer.DotLottiePlayerEvent) {
                com.lottiefiles.dotlottie.core.util.dispatchPlayerEvent(
                    event, rController.eventListeners, controllerStateChange(rController)
                )
            }

            override fun onStateMachineEvent(event: com.dotlottie.dlplayer.StateMachinePlayerEvent) {
                com.lottiefiles.dotlottie.core.util.dispatchStateMachineEvent(
                    event, rController.stateMachineListeners
                )
            }

            override fun onStateMachineInternalEvent(message: String) {
                com.lottiefiles.dotlottie.core.util.handleInternalEvent(
                    message, rController.onOpenUrlCallback
                )
            }
        })
        true // return non-null for remember
    }

    // Start renderer and wire up controller when player is ready
    LaunchedEffect(Unit) {
        renderer.setOnPlayerReady { player, config ->
            rController.setPlayerInstance(player, config)
            rController.init()
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
        renderer.setConfig(
            buildDLConfig(autoplay, loop, playMode, speed, useFrameInterpolation, segment, marker, layout, themeId, loopCount)
        )
    }

    // Handle controller-driven resize
    LaunchedEffect(controllerWidth, controllerHeight) {
        if (controllerWidth != 0u && controllerHeight != 0u) {
            renderer.resize(controllerWidth.toInt(), controllerHeight.toInt())
        }
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
            .dotLottiePointerInput(rController)
    ) {
        @Suppress("UNUSED_EXPRESSION")
        drawVersion
    }
}
