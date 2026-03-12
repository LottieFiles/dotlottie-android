package com.lottiefiles.dotlottie.core.compose.ui

import com.lottiefiles.dotlottie.core.BuildConfig
import android.graphics.Bitmap
import android.os.Build
import android.view.Choreographer
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.core.graphics.createBitmap
import com.dotlottie.dlplayer.DotLottiePlayer
import com.dotlottie.dlplayer.Event
import com.lottiefiles.dotlottie.core.jni.DotLottiePlayer as JNI
import com.dotlottie.dlplayer.Layout
import com.dotlottie.dlplayer.Mode
import com.dotlottie.dlplayer.createDefaultLayout
import com.lottiefiles.dotlottie.core.compose.runtime.DotLottieController
import com.lottiefiles.dotlottie.core.compose.runtime.DotLottiePlayerState
import com.lottiefiles.dotlottie.core.util.DotLottieContent
import com.lottiefiles.dotlottie.core.util.DotLottieEventListener
import com.lottiefiles.dotlottie.core.util.DotLottieSource
import com.lottiefiles.dotlottie.core.util.DotLottieUtils
import com.lottiefiles.dotlottie.core.util.InternalDotLottieApi
import com.dotlottie.dlplayer.Pointer
import com.dotlottie.dlplayer.DotLottiePlayerEvent
import com.dotlottie.dlplayer.StateMachinePlayerEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer
import kotlin.math.pow
import com.dotlottie.dlplayer.Config as DLConfig

private const val BYTES_PER_PIXEL = 4

@InternalDotLottieApi
private fun pollPlayerEvents(dlPlayer: DotLottiePlayer, controller: DotLottieController) {
    var event = dlPlayer.pollEvent()
    while (event != null) {
        val e = event
        when (e) {
            is DotLottiePlayerEvent.Load -> {
                controller.updateState(DotLottiePlayerState.LOADED)
                controller.eventListeners.forEach(DotLottieEventListener::onLoad)
            }

            is DotLottiePlayerEvent.LoadError -> {
                controller.updateState(DotLottiePlayerState.ERROR)
                controller.eventListeners.forEach { listener ->
                    listener.onLoadError()
                    listener.onLoadError(Throwable("Load error occurred"))
                }
            }

            is DotLottiePlayerEvent.Play -> {
                controller.updateState(DotLottiePlayerState.PLAYING)
                controller.eventListeners.forEach(DotLottieEventListener::onPlay)
            }

            is DotLottiePlayerEvent.Pause -> {
                controller.updateState(DotLottiePlayerState.PAUSED)
                controller.eventListeners.forEach(DotLottieEventListener::onPause)
            }

            is DotLottiePlayerEvent.Stop -> {
                controller.updateState(DotLottiePlayerState.STOPPED)
                controller.eventListeners.forEach(DotLottieEventListener::onStop)
            }

            is DotLottiePlayerEvent.Frame -> {
                controller.eventListeners.forEach { it.onFrame(e.frameNo) }
            }

            is DotLottiePlayerEvent.Render -> {
                controller.eventListeners.forEach { it.onRender(e.frameNo) }
            }

            is DotLottiePlayerEvent.Loop -> {
                controller.eventListeners.forEach { it.onLoop(e.loopCount.toInt()) }
            }

            is DotLottiePlayerEvent.Complete -> {
                controller.updateState(DotLottiePlayerState.COMPLETED)
                controller.eventListeners.forEach(DotLottieEventListener::onComplete)
            }
        }
        event = dlPlayer.pollEvent()
    }
}

private fun pollStateMachineEvents(dlPlayer: DotLottiePlayer, controller: DotLottieController) {
    var smEvent = dlPlayer.stateMachinePollEvent()
    while (smEvent != null) {
        val e = smEvent
        when (e) {
            is StateMachinePlayerEvent.Start -> {
                controller.stateMachineListeners.forEach { it.onStart() }
            }

            is StateMachinePlayerEvent.Stop -> {
                controller.stateMachineListeners.forEach { it.onStop() }
            }

            is StateMachinePlayerEvent.Transition -> {
                controller.stateMachineListeners.forEach {
                    it.onTransition(
                        e.previousState,
                        e.newState
                    )
                }
            }

            is StateMachinePlayerEvent.StateEntered -> {
                controller.stateMachineListeners.forEach { it.onStateEntered(e.state) }
            }

            is StateMachinePlayerEvent.StateExit -> {
                controller.stateMachineListeners.forEach { it.onStateExit(e.state) }
            }

            is StateMachinePlayerEvent.CustomEvent -> {
                controller.stateMachineListeners.forEach { it.onCustomEvent(e.message) }
            }

            is StateMachinePlayerEvent.Error -> {
                controller.stateMachineListeners.forEach { it.onError(e.message) }
            }

            is StateMachinePlayerEvent.StringInputChange -> {
                controller.stateMachineListeners.forEach {
                    it.onStringInputValueChange(
                        e.name,
                        e.oldValue,
                        e.newValue
                    )
                }
            }

            is StateMachinePlayerEvent.NumericInputChange -> {
                controller.stateMachineListeners.forEach {
                    it.onNumericInputValueChange(
                        e.name,
                        e.oldValue,
                        e.newValue
                    )
                }
            }

            is StateMachinePlayerEvent.BooleanInputChange -> {
                controller.stateMachineListeners.forEach {
                    it.onBooleanInputValueChange(
                        e.name,
                        e.oldValue,
                        e.newValue
                    )
                }
            }

            is StateMachinePlayerEvent.InputFired -> {
                controller.stateMachineListeners.forEach { it.onInputFired(e.name) }
            }
        }
        smEvent = dlPlayer.stateMachinePollEvent()
    }
    // Poll internal events
    var internalEvent = dlPlayer.stateMachinePollInternalEvent()
    while (internalEvent != null) {
        if (internalEvent.startsWith("OpenUrl: ")) {
            val payload = internalEvent.substringAfter("OpenUrl: ")
            val url = if (payload.contains(" | Target: ")) {
                payload.substringBefore(" | Target: ")
            } else {
                payload
            }
            controller.onOpenUrlCallback?.invoke(url)
        }
        internalEvent = dlPlayer.stateMachinePollInternalEvent()
    }
}

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

    // Initialize Android context for audio support (idempotent, no-op when audio disabled)
    if (BuildConfig.DOTLOTTIE_AUDIO) JNI.nativeInitAndroid(context)

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
    var nativeBufferAddress by remember { mutableStateOf(0L) }
    var bufferBytes by remember { mutableStateOf<ByteBuffer?>(null) }
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
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
                if (bufferBytes == null || bitmap == null || !isActive) return

                // Skip if previous render is still in progress (non-blocking check)
                if (!renderMutex.tryLock()) {
                    if (dlPlayer.isPlaying() || rController.stateMachineIsActive) {
                        choreographer.postFrameCallback(this)
                    }
                    return
                }

                // stateMachineTick() calls player tick() internally (player is borrowed by state machine)
                val ticked = if (rController.stateMachineIsActive) {
                    dlPlayer.stateMachineTick()
                } else {
                    dlPlayer.tick()
                }

                // Poll and dispatch player events to controller listeners
                pollPlayerEvents(dlPlayer, rController)
                // Poll and dispatch state machine events to controller listeners
                pollStateMachineEvents(dlPlayer, rController)

                var lockHandedToCoroutine = false

                if (ticked || forceUpdateBitmap) {
                    val shouldResetFlag = !ticked && forceUpdateBitmap

                    // Capture references for background thread
                    val bytes = bufferBytes
                    val bmp = bitmap

                    lockHandedToCoroutine = true
                    renderScope.launch(singleThreadDispatcher) {
                        try {
                            ensureActive()

                            bytes?.let { b ->
                                bmp?.let { targetBitmap ->
                                    if (!targetBitmap.isRecycled) {
                                        withContext(Dispatchers.Default) {
                                            b.rewind()
                                            targetBitmap.copyPixelsFromBuffer(b)
                                        }

                                        withContext(Dispatchers.Main) {
                                            if (isActive && !targetBitmap.isRecycled) {
                                                imageBitmap = targetBitmap.asImageBitmap()
                                                if (shouldResetFlag) {
                                                    forceUpdateBitmap = false
                                                }
                                            }
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

            // Coordinate with render loop to prevent use-after-free during buffer realloc
            renderMutex.withLock {
                // Allocate caller-managed buffer and register SW target
                if (nativeBufferAddress != 0L) {
                    dlPlayer.freeBuffer(nativeBufferAddress)
                }
                nativeBufferAddress = dlPlayer.allocateBuffer(width.toInt(), height.toInt())
                nativeBuffer = Pointer(nativeBufferAddress)
                dlPlayer.setSwTarget(nativeBufferAddress, width, height)

                when (animationData) {
                    is DotLottieContent.Json -> {
                        dlPlayer.loadAnimationData(animationData.jsonString, width, height)
                    }

                    is DotLottieContent.Binary -> {
                        dlPlayer.loadDotlottieData(animationData.data, width, height)
                    }
                }

                // Set local buffer
                bufferBytes =
                    nativeBuffer!!.getByteBuffer(0, dlPlayer.bufferLen().toLong() * BYTES_PER_PIXEL)
                bitmap = createBitmap(width.toInt(), height.toInt())
                imageBitmap = bitmap!!.asImageBitmap()
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
                bitmap = createBitmap(_width.toInt(), _height.toInt())

                // Free old buffer, allocate new, resize, re-register SW target
                if (nativeBufferAddress != 0L) {
                    dlPlayer.freeBuffer(nativeBufferAddress)
                }
                nativeBufferAddress = dlPlayer.allocateBuffer(_width.toInt(), _height.toInt())
                nativeBuffer = Pointer(nativeBufferAddress)
                dlPlayer.resize(_width, _height)
                dlPlayer.setSwTarget(nativeBufferAddress, _width, _height)
                bufferBytes =
                    nativeBuffer!!.getByteBuffer(0, dlPlayer.bufferLen().toLong() * BYTES_PER_PIXEL)
                imageBitmap = bitmap!!.asImageBitmap()
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    oldBitmap?.recycle()
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
            // Wait for any in-flight render to complete before freeing native resources
            runBlocking {
                renderMutex.withLock {
                    if (nativeBufferAddress != 0L) {
                        dlPlayer.freeBuffer(nativeBufferAddress)
                        nativeBufferAddress = 0
                    }
                    dlPlayer.destroy()
                }
            }
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
            .pointerInput(Unit) {
                awaitEachGesture {
                    // First touch (Down)
                    val down = awaitFirstDown()

                    val downPosition = down.position
                    val scaledX = downPosition.x
                    val scaledY = downPosition.y

                    rController.stateMachinePostEvent(Event.PointerDown(scaledX, scaledY))

                    // Variables to track movement distance
                    var movedTooMuch = false
                    val touchSlop = 20f

                    // Handle move and up events
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
        imageBitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}
