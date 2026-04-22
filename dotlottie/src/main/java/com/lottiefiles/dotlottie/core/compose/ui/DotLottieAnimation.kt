package com.lottiefiles.dotlottie.core.compose.ui

import android.graphics.Bitmap
import android.os.Build
import android.view.Choreographer
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.core.graphics.createBitmap
import com.dotlottie.dlplayer.DotLottiePlayer
import com.dotlottie.dlplayer.Layout
import com.dotlottie.dlplayer.Mode
import com.dotlottie.dlplayer.createDefaultLayout
import com.lottiefiles.dotlottie.core.compose.runtime.DotLottieController
import com.lottiefiles.dotlottie.core.util.DotLottieContent
import com.lottiefiles.dotlottie.core.util.DotLottieEventListener
import com.lottiefiles.dotlottie.core.util.DotLottieSource
import com.lottiefiles.dotlottie.core.util.DotLottieUtils
import com.lottiefiles.dotlottie.core.util.InternalDotLottieApi
import com.lottiefiles.dotlottie.core.jni.DotLottiePlayer as DotLottieJNI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

private val cleanupScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

@OptIn(InternalDotLottieApi::class, ExperimentalCoroutinesApi::class)
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
        buildDLConfig(autoplay, loop, playMode, speed, useFrameInterpolation, segment, marker, layout, themeId, loopCount)
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
    var drawVersion by remember { mutableIntStateOf(0) }
    val drawDstRect = remember { android.graphics.RectF() }
    val choreographer = remember { Choreographer.getInstance() }
    val currentState by rController.currentState.collectAsState()
    val _width by rController.height.collectAsState()
    val _height by rController.width.collectAsState()
    val frameUpdateRequested by rController.frameUpdateRequested.collectAsState()
    var layoutSize by remember { mutableStateOf<Size?>(null) }
    var animationData by remember { mutableStateOf<Result<DotLottieContent>?>(null) }
    var forceUpdateBitmap by remember { mutableStateOf(false) }

    val renderScope = rememberCoroutineScope()
    val renderMutex = remember { Mutex() }
    val singleThreadDispatcher = remember { Dispatchers.Default.limitedParallelism(1) }

    val frameCallback = remember {
        object : Choreographer.FrameCallback {
            var isActive = true

            override fun doFrame(frameTimeNanos: Long) {
                if (bitmap == null || !isActive) return

                // Skip if previous render is still in progress (non-blocking check)
                if (!renderMutex.tryLock()) {
                    if (dlPlayer.isPlaying() || rController.stateMachineIsActive) {
                        choreographer.postFrameCallback(this)
                    }
                    return
                }

                val ticked = if (rController.stateMachineIsActive) {
                    dlPlayer.stateMachineTick()
                } else {
                    dlPlayer.tick()
                }

                // Poll and dispatch events
                pollAndDispatchEvents(dlPlayer, rController)

                var lockHandedToCoroutine = false

                if (ticked || forceUpdateBitmap) {
                    val shouldResetFlag = !ticked && forceUpdateBitmap
                    val bmp = bitmap

                    lockHandedToCoroutine = true
                    renderScope.launch(singleThreadDispatcher) {
                        try {
                            ensureActive()

                            if (bmp != null && !bmp.isRecycled) {
                                DotLottieJNI.nativeFlushBitmapPixels(bmp)

                                withContext(Dispatchers.Main) {
                                    if (isActive && !bmp.isRecycled) {
                                        drawVersion++
                                        if (shouldResetFlag) {
                                            forceUpdateBitmap = false
                                        }
                                    }
                                }
                            }
                        } finally {
                            renderMutex.unlock()
                        }
                    }
                }

                if (dlPlayer.isPlaying() || rController.stateMachineIsActive) {
                    choreographer.postFrameCallback(this)
                }

                if (!lockHandedToCoroutine) {
                    renderMutex.unlock()
                }
            }
        }
    }

    LaunchedEffect(source) {
        animationData = kotlin.runCatching {
            DotLottieUtils.getContent(context, source)
        }
    }

    suspend fun init(animationData: DotLottieContent, layoutSize: Size) {
        runCatching {
            val height = layoutSize.height.toUInt()
            val width = layoutSize.width.toUInt()
            val isLoaded = dlPlayer.isLoaded()
            rController.resize(height, width)

            // Move expensive native load off the main thread.
            // The frame callback uses renderMutex.tryLock() and skips frames while held.
            withContext(Dispatchers.Default) {
                renderMutex.withLock {
                    bitmap?.let { DotLottieJNI.nativeUnlockBitmapPixels(it) }

                    // Create bitmap and lock its pixels as the render target
                    val newBitmap = createBitmap(width.toInt(), height.toInt())
                    val pixelPtr = DotLottieJNI.nativeLockBitmapPixels(newBitmap)
                    if (pixelPtr == 0L) return@withLock
                    dlPlayer.setSwTarget(pixelPtr, width, height)

                    when (animationData) {
                        is DotLottieContent.Json -> {
                            dlPlayer.loadAnimationData(animationData.jsonString, width, height)
                        }

                        is DotLottieContent.Binary -> {
                            dlPlayer.loadDotlottieData(animationData.data, width, height)
                        }
                    }

                    bitmap = newBitmap
                    drawDstRect.set(0f, 0f, layoutSize.width, layoutSize.height)
                    drawVersion++
                }
            }

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
        animationData?.let { result ->
            result.onSuccess { data ->
                if (layoutSize != null) {
                    init(data, layoutSize!!)
                }
                if (initialStateMachineId != null) {
                    if (initialStateMachineId.isNotEmpty()) {
                        rController.stateMachineLoad(initialStateMachineId)
                        rController.stateMachineStart()
                    }
                }
            }.onFailure { t ->
                rController.eventListeners.forEach {
                    it.onLoadError(t)
                }
            }
        }
    }

    LaunchedEffect(dlPlayer.isPlaying(), currentState) {
        choreographer.postFrameCallback(frameCallback)
    }

    LaunchedEffect(frameUpdateRequested) {
        if (frameUpdateRequested > 0 && !dlPlayer.isPlaying()) {
            forceUpdateBitmap = true
            choreographer.postFrameCallback(frameCallback)
        }
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
            // Skip if already at the correct size (avoids destroying the buffer init() just populated)
            val currentBmp = bitmap
            if (currentBmp != null && !currentBmp.isRecycled
                && currentBmp.width == _width.toInt() && currentBmp.height == _height.toInt()
            ) {
                return@LaunchedEffect
            }
            // Wait for any pending render to complete
            renderMutex.withLock {
                val oldBitmap = bitmap
                val newBitmap = createBitmap(_width.toInt(), _height.toInt())
                val pixelPtr = DotLottieJNI.nativeLockBitmapPixels(newBitmap)
                if (pixelPtr == 0L) return@withLock
                dlPlayer.resize(_width, _height)
                dlPlayer.setSwTarget(pixelPtr, _width, _height)
                bitmap = newBitmap
                drawVersion++
                oldBitmap?.let {
                    DotLottieJNI.nativeUnlockBitmapPixels(it)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        it.recycle()
                    }
                }
            }
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
            renderScope.cancel()

            bitmap?.let { DotLottieJNI.nativeUnlockBitmapPixels(it) }
            // Detach the controller from the native player before destroying it so any
            // pending callbacks or recompositions that touch the controller see a
            // safe-default state instead of calling into an already-destroyed player.
            rController.destroy()
            runCatching { dlPlayer.destroy() }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                bitmap?.recycle()
            }
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
