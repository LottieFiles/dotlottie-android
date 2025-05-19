package com.lottiefiles.example.homesample.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dotlottie.dlplayer.Fit
import com.dotlottie.dlplayer.Mode
import com.lottiefiles.dotlottie.core.compose.runtime.DotLottieController
import com.lottiefiles.dotlottie.core.compose.ui.DotLottieAnimation
import com.lottiefiles.dotlottie.core.util.DotLottieSource
import com.lottiefiles.dotlottie.core.util.LayoutUtil

/**
 * A wrapper component for DotLottieAnimation to handle Lottie animations with consistent settings.
 */
@Composable
fun LottieView(
    url: String,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(200.dp)
        .background(Color.LightGray.copy(alpha = 0.2f))
) {
    DotLottieAnimation(
        source = DotLottieSource.Url(url),
        autoplay = true,
        loop = true,
        useFrameInterpolation = true,
        playMode = Mode.FORWARD,
        layout = LayoutUtil.createLayout(Fit.CONTAIN, LayoutUtil.Alignment.Center.alignment),
        modifier = modifier
    )
}

@Composable
fun LottieView(
    url: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White,
    autoPlay: Boolean = true,
    loop: Boolean = true,
    speed: Float = 1f,
    useFrameInterpolation: Boolean = false,
    playMode: Mode = Mode.FORWARD,
    fit: Fit = Fit.FIT_WIDTH,
    alignment: LayoutUtil.Alignment = LayoutUtil.Alignment.Center,
    controller: DotLottieController = DotLottieController()
) {
    // Set up controller parameters and handle interpolation changes
    LaunchedEffect(url, loop, speed, autoPlay, playMode, useFrameInterpolation) {
        // Apply all settings
        controller.setLoop(loop)
        controller.setSpeed(speed)
        controller.setPlayMode(playMode)
        controller.setUseFrameInterpolation(useFrameInterpolation)
        
        // Start playback if autoPlay is enabled
        if (autoPlay && !controller.isPlaying) {
            controller.play()
        }
    }
    
    Box(modifier = modifier.background(backgroundColor)) {
        // Recreate the animation with the updated settings
        DotLottieAnimation(
            modifier = Modifier.aspectRatio(1f),
            source = DotLottieSource.Url(url),
            autoplay = autoPlay,
            loop = loop,
            speed = speed,
            useFrameInterpolation = useFrameInterpolation,
            playMode = playMode,
            controller = controller,
            layout = LayoutUtil.createLayout(fit = fit, alignment)
        )
    }
}

