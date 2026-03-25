package com.lottiefiles.dotlottie.core.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dotlottie.dlplayer.Layout
import com.dotlottie.dlplayer.Mode
import com.dotlottie.dlplayer.createDefaultLayout
import com.lottiefiles.dotlottie.core.compose.runtime.DotLottieController
import com.lottiefiles.dotlottie.core.model.Config as ViewConfig
import com.lottiefiles.dotlottie.core.util.DotLottieEventListener
import com.lottiefiles.dotlottie.core.util.DotLottieSource
import com.lottiefiles.dotlottie.core.util.InternalDotLottieApi
import com.lottiefiles.dotlottie.core.widget.DotLottieGLAnimation as GLWidget

@OptIn(InternalDotLottieApi::class)
@Composable
fun DotLottieGLSurfaceAnimation(
    modifier: Modifier = Modifier,
    source: DotLottieSource,
    autoplay: Boolean = false,
    loop: Boolean = false,
    useFrameInterpolation: Boolean = true,
    themeId: String? = null,
    stateMachineId: String? = null,
    marker: String? = null,
    speed: Float = 1f,
    @Suppress("UNUSED_PARAMETER") segment: Pair<Float, Float>? = null,
    playMode: Mode = Mode.FORWARD,
    controller: DotLottieController? = null,
    layout: Layout = createDefaultLayout(),
    eventListeners: List<DotLottieEventListener> = emptyList(),
    @Suppress("UNUSED_PARAMETER") threads: UInt? = null,
    loopCount: UInt = 0u,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    val rController = remember { controller ?: DotLottieController() }
    val initialStateMachineId = remember { stateMachineId }

    // Hold a reference to the widget so we can interact with it
    val glWidgetRef = remember { arrayOfNulls<GLWidget>(1) }

    // Build a ViewConfig from the composable parameters
    fun buildViewConfig(): ViewConfig {
        return ViewConfig.Builder()
            .source(source)
            .autoplay(autoplay)
            .loop(loop)
            .speed(speed)
            .playMode(playMode)
            .useFrameInterpolation(useFrameInterpolation)
            .marker(marker ?: "")
            .loopCount(loopCount)
            .build()
    }

    // Reload content when source changes
    LaunchedEffect(source) {
        glWidgetRef[0]?.load(buildViewConfig())
    }

    // Sync reactive parameters
    LaunchedEffect(loop, speed, playMode, useFrameInterpolation, marker, layout, themeId) {
        val widget = glWidgetRef[0] ?: return@LaunchedEffect
        widget.setLoop(loop)
        widget.setSpeed(speed)
        widget.setPlayMode(playMode)
        widget.setUseFrameInterpolation(useFrameInterpolation)
        if (marker != null) widget.setMarker(marker)
        widget.setLayout(layout.fit, Pair(layout.align[0], layout.align[1]))
        if (themeId != null) widget.setTheme(themeId) else widget.resetTheme()
    }

    // Lifecycle handling
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val widget = glWidgetRef[0] ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_RESUME -> widget.onResume()
                Lifecycle.Event.ON_PAUSE -> widget.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glWidgetRef[0]?.destroy()
            glWidgetRef[0] = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            GLWidget(ctx).also { widget ->
                glWidgetRef[0] = widget

                // Add event listeners
                eventListeners.forEach { widget.addEventListener(it) }

                // Wire up controller
                widget.setOnPlayerCreated { player, config ->
                    rController.setPlayerInstance(player, config)
                    rController.init()

                    if (!initialStateMachineId.isNullOrEmpty()) {
                        rController.stateMachineLoad(initialStateMachineId)
                        rController.stateMachineStart()
                    }
                }

                // Load content
                widget.load(buildViewConfig())
            }
        },
        update = { _ ->
            // Update is called when composable recomposes — sync state
        }
    )
}
