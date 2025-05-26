package com.lottiefiles.example.features.performance.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dotlottie.dlplayer.Mode
import com.lottiefiles.example.features.home.presentation.DotLottieView
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceTestScreen(onBackClick: (() -> Unit)? = null) {
    // Wrap with permission screen
    PermissionRequiredScreen {
        PerformanceTestScreenContent(onBackClick)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PerformanceTestScreenContent(onBackClick: (() -> Unit)? = null) {
    var animationCount by remember { mutableIntStateOf(4) }
    var showControls by remember { mutableStateOf(true) }
    var useInterpolation by remember { mutableStateOf(true) }
    var animationSize by remember { mutableIntStateOf(120) }

    // Use the swag_sticker_piggy.lottie URL for better performance testing
    val lottieUrl =
        "https://lottiefiles-mobile-templates.s3.amazonaws.com/ar-stickers/swag_sticker_piggy.lottie"

    // Generate animation items once
    val animationItems = remember(animationCount, useInterpolation) {
        List(animationCount) { index ->
            AnimationItem(id = index, useInterpolation = useInterpolation)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Performance Test") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                if (showControls) {
                    ControlPanel(
                        animationCount = animationCount,
                        onAnimationCountChanged = { animationCount = it },
                        useInterpolation = useInterpolation,
                        onUseInterpolationChanged = { useInterpolation = it },
                        animationSize = animationSize,
                        onAnimationSizeChanged = { animationSize = it }
                    )
                }

                // Grid of animations using LottieView
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(animationSize.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    items(
                        items = animationItems,
                        key = { item -> "${item.id}-${item.useInterpolation}" }
                    ) { item ->
                        DotLottieView(
                            url = lottieUrl,
                            autoPlay = true,
                            loop = true,
                            useFrameInterpolation = item.useInterpolation,
                            playMode = Mode.FORWARD,
                            backgroundColor = Color.LightGray.copy(alpha = 0.3f),
                            modifier = Modifier
                                .padding(4.dp)
                                .size(animationSize.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
            }

            // Toggle button for controls
            Button(
                onClick = { showControls = !showControls },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Text(text = if (showControls) "Hide Controls" else "Show Controls")
            }

            // Overlay with performance metrics
            PerformanceOverlay(
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
    }
}

/**
 * Data class to hold animation items with stable identity for LazyVerticalGrid
 */
data class AnimationItem(
    val id: Int,
    val useInterpolation: Boolean
)

@Composable
private fun ControlPanel(
    animationCount: Int,
    onAnimationCountChanged: (Int) -> Unit,
    useInterpolation: Boolean,
    onUseInterpolationChanged: (Boolean) -> Unit,
    animationSize: Int,
    onAnimationSizeChanged: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                RoundedCornerShape(8.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = "Performance Test Controls",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Number of animations
        Text(text = "Number of animations: $animationCount")
        Slider(
            value = animationCount.toFloat(),
            onValueChange = { onAnimationCountChanged(it.roundToInt()) },
            valueRange = 1f..50f,
            steps = 48
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Animation size
        Text(text = "Animation size: ${animationSize}dp")
        Slider(
            value = animationSize.toFloat(),
            onValueChange = { onAnimationSizeChanged(it.roundToInt()) },
            valueRange = 50f..200f,
            steps = 14
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Frame interpolation
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Use frame interpolation: ")
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { onUseInterpolationChanged(!useInterpolation) }
            ) {
                Text(text = if (useInterpolation) "ON" else "OFF")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Test buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = { onAnimationCountChanged(4) }) {
                Text(text = "4 animations")
            }

            Button(onClick = { onAnimationCountChanged(9) }) {
                Text(text = "9 animations")
            }

            Button(onClick = { onAnimationCountChanged(16) }) {
                Text(text = "16 animations")
            }
        }
    }
} 