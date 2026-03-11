package com.lottiefiles.example

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lottiefiles.dotlottie.core.compose.ui.DotLottieAnimation
import com.lottiefiles.dotlottie.core.util.DotLottieSource
import com.lottiefiles.example.ui.theme.ExampleTheme

/**
 * Stress test activity that reproduces ANR when multiple DotLottieAnimation
 * instances are created simultaneously during composition.
 *
 * This simulates a home screen with horizontal carousels of animation thumbnails.
 *
 * To run: Change the launcher activity in AndroidManifest.xml to this activity,
 * or add a button in MainActivity to navigate here.
 */
class MultiInstanceStressTest : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val launchTime = SystemClock.elapsedRealtime()
        Log.w("PERF_STRESS", "=== Activity.onCreate START ===")

        setContent {
            ExampleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreenSimulation()
                }
            }
        }

        val elapsed = SystemClock.elapsedRealtime() - launchTime
        Log.w("PERF_STRESS", "=== Activity.onCreate END === took ${elapsed}ms")
    }
}

/**
 * Simulates a home screen with multiple carousels, each containing
 * several DotLottieAnimation thumbnails visible at once.
 *
 * With default settings this creates ~20 instances to stress test.
 * Adjust CAROUSEL_COUNT and ITEMS_PER_CAROUSEL to control severity.
 */
@Composable
fun HomeScreenSimulation() {
    // --- Tunables ---
    val carouselCount = 50        // Number of horizontal carousels
    val itemsPerCarousel = 20     // Items per carousel (all visible at once)

    // Use local assets to eliminate network latency from the test
    val animationSources = listOf(
        DotLottieSource.Asset("swinging.json"),
        DotLottieSource.Asset("star.json"),
        DotLottieSource.Asset("check.json"),
        DotLottieSource.Asset("contact.json"),
    )

    val totalInstances = carouselCount * itemsPerCarousel

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        // Header
        Text(
            text = "Multi-Instance Stress Test",
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "$carouselCount carousels x $itemsPerCarousel items = $totalInstances DotLottieAnimation instances",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Carousels
        repeat(carouselCount) { carouselIndex ->
            Text(
                text = "Carousel ${carouselIndex + 1}",
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 4.dp, top = 8.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                items(itemsPerCarousel) { itemIndex ->
                    val source = animationSources[(carouselIndex * itemsPerCarousel + itemIndex) % animationSources.size]
                    AnimationThumbnail(
                        source = source,
                        label = "C${carouselIndex + 1}-${itemIndex + 1}"
                    )
                }
            }
        }
    }
}

@Composable
fun AnimationThumbnail(
    source: DotLottieSource,
    label: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(Color(0xFFF5F5F5))
                .border(1.dp, Color.LightGray)
        ) {
            DotLottieAnimation(
                source = source,
                autoplay = true,
                loop = true,
                speed = 1f,
                useFrameInterpolation = false, // Disabled for thumbnails as colleague suggested
                modifier = Modifier.fillMaxSize()
            )
        }
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
