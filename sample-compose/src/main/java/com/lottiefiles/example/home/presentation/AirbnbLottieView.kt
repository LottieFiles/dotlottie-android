package com.lottiefiles.example.home.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

/**
 * A wrapper component for Airbnb's Lottie library with a minimal API focusing only on
 * features common to both libraries.
 */
@Composable
fun AirbnbLottieView(
    url: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White,
    autoPlay: Boolean = true,
    loop: Boolean = true,
    speed: Float = 1f
) {
    val iterations = if (loop) LottieConstants.IterateForever else 1

    // Force recomposition when key parameters change
    key(url) {
        // Load the composition from URL
        val composition by rememberLottieComposition(
            spec = LottieCompositionSpec.Url(url)
        )

        // Control animation state
        val isPlaying by remember { mutableStateOf(autoPlay) }

        val progress by animateLottieCompositionAsState(
            composition = composition,
            iterations = iterations,
            isPlaying = isPlaying,
            speed = speed
        )

        Box(modifier = modifier.background(backgroundColor)) {
            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier.aspectRatio(1f),
                contentScale = ContentScale.Fit
            )
        }
    }
} 