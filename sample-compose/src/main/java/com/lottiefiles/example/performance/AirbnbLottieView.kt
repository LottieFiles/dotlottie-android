package com.lottiefiles.example.performance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.dotlottie.dlplayer.Mode

/**
 * A wrapper component for Airbnb's Lottie library with a similar API to our DotLottieView
 * for easier comparison benchmarking.
 */
@Composable
fun AirbnbLottieView(
    url: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White,
    autoPlay: Boolean = true,
    loop: Boolean = true,
    speed: Float = 1f,
    useFrameInterpolation: Boolean = true, // This parameter has no direct equivalent in Airbnb Lottie
    playMode: Mode = Mode.FORWARD // We'll simulate this with speed
) {
    // Handle play mode via speed direction
    val effectiveSpeed = when (playMode) {
        Mode.REVERSE, Mode.REVERSE_BOUNCE -> -speed
        else -> speed
    }
    
    val iterations = if (loop) LottieConstants.IterateForever else 1
    
    // Load the composition from URL
    val composition by rememberLottieComposition(
        spec = LottieCompositionSpec.Url(url)
    )
    
    // Control animation state
    var isPlaying by remember { mutableStateOf(autoPlay) }
    
    // Animation progress state
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = iterations,
        isPlaying = isPlaying,
        speed = effectiveSpeed,
        // Airbnb Lottie doesn't have direct frame interpolation control
    )
    
    // Handle playMode for bounce effects
    LaunchedEffect(playMode, composition) {
        if (composition != null && 
            (playMode == Mode.BOUNCE || playMode == Mode.REVERSE_BOUNCE) && 
            (progress >= 0.99f || progress <= 0.01f)) {
            // Simulate bounce behavior by changing direction at endpoints
            isPlaying = false
            isPlaying = true
        }
    }
    
    Box(modifier = modifier.background(backgroundColor)) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier.aspectRatio(1f),
            contentScale = ContentScale.Fit
        )
    }
} 