package com.lottiefiles.dotlottie.core.compose.ui

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dotlottie.dlplayer.Layout
import com.dotlottie.dlplayer.Mode
import com.dotlottie.dlplayer.createDefaultLayout
import com.lottiefiles.dotlottie.core.compose.runtime.DotLottieController
import com.lottiefiles.dotlottie.core.util.DotLottieEventListener
import com.lottiefiles.dotlottie.core.util.DotLottieSource
import com.lottiefiles.dotlottie.core.util.InternalDotLottieApi

/**
 * Compose-native GL composable using AHardwareBuffer for near-zero-copy GPU rendering.
 *
 * On API 31+, renders into AHardwareBuffer-backed FBOs via OpenGL on a dedicated thread,
 * then wraps the result as a hardware-backed Bitmap for direct Skia drawing in Compose.
 *
 * On API < 31, falls back to [DotLottieGLSurfaceAnimation] (AndroidView + GLSurfaceView wrapper).
 */
@OptIn(InternalDotLottieApi::class)
@Composable
fun DotLottieGLAnimation(
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        DotLottieGLAnimationHardwareBuffer(
            modifier = modifier,
            source = source,
            autoplay = autoplay,
            loop = loop,
            useFrameInterpolation = useFrameInterpolation,
            themeId = themeId,
            stateMachineId = stateMachineId,
            marker = marker,
            speed = speed,
            segment = segment,
            playMode = playMode,
            controller = controller,
            layout = layout,
            eventListeners = eventListeners,
            threads = threads,
            loopCount = loopCount,
        )
    } else {
        // Fallback to existing AndroidView-based GL composable
        DotLottieGLSurfaceAnimation(
            modifier = modifier,
            source = source,
            autoplay = autoplay,
            loop = loop,
            useFrameInterpolation = useFrameInterpolation,
            themeId = themeId,
            stateMachineId = stateMachineId,
            marker = marker,
            speed = speed,
            segment = segment,
            playMode = playMode,
            controller = controller,
            layout = layout,
            eventListeners = eventListeners,
            threads = threads,
            loopCount = loopCount,
        )
    }
}
