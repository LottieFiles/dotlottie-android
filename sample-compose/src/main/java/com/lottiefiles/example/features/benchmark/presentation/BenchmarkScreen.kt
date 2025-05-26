package com.lottiefiles.example.features.benchmark.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lottiefiles.example.features.home.presentation.AirbnbLottieView
import com.lottiefiles.example.features.home.presentation.DotLottieView
import com.lottiefiles.example.features.performance.presentation.BenchmarkRunner
import com.lottiefiles.example.features.performance.presentation.PerformanceOverlay
import com.lottiefiles.example.features.performance.presentation.PermissionRequiredScreen

private object BenchmarkConstants {
    const val JSON_ANIMATION_URL =
        "https://lottie.host/e55c67db-398a-4b32-9c21-d5ccc374658c/C6ZURJ4vJP.json"
    const val LOTTIE_ANIMATION_URL =
        "https://lottie.host/d205a0a0-3e1c-4501-a036-7719e9668616/5sOh6gibkX.lottie"

    const val DEFAULT_ANIMATION_SIZE = 80
    const val DEFAULT_SPACING = 16
    const val SMALL_SPACING = 8
    const val GRID_ITEM_SPACING = 4

    object PerformanceColors {
        val Good = Color(0xFF4CAF50) // Material Green
        val Warning = Color(0xFFFFAA00) // Material Orange
        val Poor = Color(0xFFE57373) // Material Red Light
    }
}

private fun formatFloat(value: Float): String {
    return String.format(java.util.Locale.US, "%.1f", value)
}

/**
 * Screen for running automated benchmarks comparing DotLottie and Airbnb Lottie libraries
 */
@Composable
fun BenchmarkScreen(onBackClick: (() -> Unit)? = null) {
    // Wrap main content in permission screen
    PermissionRequiredScreen {
        BenchmarkScreenContent(onBackClick)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkScreenContent(onBackClick: (() -> Unit)? = null) {
    val context = LocalContext.current
    val benchmarkRunner = remember { BenchmarkRunner(context) }
    val benchmarkState by benchmarkRunner.benchmarkState.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Benchmark Test") },
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
                },
                actions = {
                    if (benchmarkState is BenchmarkRunner.BenchmarkState.Completed) {
                        IconButton(onClick = { benchmarkRunner.shareBenchmarkResults() }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Results"
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
            // Main content based on benchmark state
            when (val state = benchmarkState) {
                is BenchmarkRunner.BenchmarkState.Idle -> {
                    IdleStateContent(
                        onStartBenchmark = { benchmarkRunner.startBenchmark() }
                    )
                }

                is BenchmarkRunner.BenchmarkState.Running -> {
                    RunningStateContent(
                        state = state,
                        onStopBenchmark = { benchmarkRunner.stopBenchmark() }
                    )
                }

                is BenchmarkRunner.BenchmarkState.Completed -> {
                    CompletedStateContent(
                        results = state.results,
                        onRestart = { benchmarkRunner.startBenchmark() }
                    )
                }
            }

            // Always show performance overlay
            PerformanceOverlay(
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
    }

    // Ensure benchmark is stopped when leaving the screen
    DisposableEffect(key1 = benchmarkRunner) {
        onDispose {
            if (benchmarkState is BenchmarkRunner.BenchmarkState.Running) {
                benchmarkRunner.stopBenchmark()
            }
        }
    }
}

@Composable
private fun IdleStateContent(onStartBenchmark: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(BenchmarkConstants.DEFAULT_SPACING.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BenchmarkConstants.DEFAULT_SPACING.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(BenchmarkConstants.DEFAULT_SPACING.dp),
                verticalArrangement = Arrangement.spacedBy(BenchmarkConstants.SMALL_SPACING.dp)
            ) {
                Text(
                    text = "Lottie Library Benchmark",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = "This benchmark will compare the performance of DotLottie and Airbnb Lottie libraries across multiple configurations:",
                    style = MaterialTheme.typography.bodyMedium
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(BenchmarkConstants.SMALL_SPACING.dp)
                ) {
                    BenchmarkInfoItem(
                        title = "Animation Counts",
                        value = "4, 9, 16, 25",
                    )
                    BenchmarkInfoItem(
                        title = "Frame Interpolation",
                        value = "Tests both modes for DotLottie\nNot available in Airbnb Lottie",
                    )
                    BenchmarkInfoItem(
                        title = "Metrics",
                        value = "FPS, CPU Usage, Memory, Jank %",
                    )
                    BenchmarkInfoItem(
                        title = "Duration",
                        value = "Approximately 5-10 minutes",
                    )
                }

                Text(
                    text = "The benchmark will run automatically and generate a detailed report on completion.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Button(
            onClick = onStartBenchmark,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Start Benchmark")
        }
    }
}

@Composable
private fun RunningStateContent(
    state: BenchmarkRunner.BenchmarkState.Running,
    onStopBenchmark: () -> Unit
) {
    val config = state.config

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(BenchmarkConstants.DEFAULT_SPACING.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BenchmarkConstants.DEFAULT_SPACING.dp)
    ) {
        // Test status information
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(BenchmarkConstants.DEFAULT_SPACING.dp),
                verticalArrangement = Arrangement.spacedBy(BenchmarkConstants.SMALL_SPACING.dp)
            ) {
                Text(
                    text = "Running Benchmark",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = "Test ${state.currentTestIndex + 1} of ${state.totalTests}",
                    style = MaterialTheme.typography.titleMedium
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(BenchmarkConstants.SMALL_SPACING.dp)
                ) {
                    Text(
                        text = "Library: ${config.library}",
                    )
                    Text(
                        text = "Animations: ${config.animationCount}",
                    )
                    Text(
                        text = "Frame Interpolation: ${if (config.useInterpolation) "ON" else "OFF"}",
                    )
                    Text(
                        text = "File Format: ${config.fileFormat}",
                    )
                }

                LinearProgressIndicator(
                    progress = state.progress,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Animations being tested - render different containers based on library type and file format
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                config.library == BenchmarkRunner.LibraryType.DOT_LOTTIE &&
                        config.fileFormat == BenchmarkRunner.FileFormat.JSON -> {
                    DotLottieJsonContainer(
                        count = config.animationCount,
                        useInterpolation = config.useInterpolation,
                        size = BenchmarkConstants.DEFAULT_ANIMATION_SIZE,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                config.library == BenchmarkRunner.LibraryType.DOT_LOTTIE -> {
                    DotLottieContainer(
                        count = config.animationCount,
                        useInterpolation = config.useInterpolation,
                        size = BenchmarkConstants.DEFAULT_ANIMATION_SIZE,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                else -> {
                    AirbnbLottieContainer(
                        count = config.animationCount,
                        size = BenchmarkConstants.DEFAULT_ANIMATION_SIZE,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Button(
            onClick = onStopBenchmark,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Stop Benchmark")
        }
    }
}

@Composable
private fun CompletedStateContent(
    results: List<BenchmarkRunner.BenchmarkResult>,
    onRestart: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(BenchmarkConstants.DEFAULT_SPACING.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BenchmarkConstants.DEFAULT_SPACING.dp)
    ) {
        Text(
            text = "Benchmark Results",
            style = MaterialTheme.typography.titleLarge
        )

        // Filter and group results by library and format
        val dotLottieJsonResults = results.filter {
            it.config.library == BenchmarkRunner.LibraryType.DOT_LOTTIE &&
                    it.config.fileFormat == BenchmarkRunner.FileFormat.JSON
        }

        val dotLottieLottieResults = results.filter {
            it.config.library == BenchmarkRunner.LibraryType.DOT_LOTTIE &&
                    it.config.fileFormat == BenchmarkRunner.FileFormat.LOTTIE
        }

        val airbnbLottieResults = results.filter {
            it.config.library == BenchmarkRunner.LibraryType.AIRBNB_LOTTIE
        }

        // Show DotLottie JSON results
        if (dotLottieJsonResults.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(BenchmarkConstants.SMALL_SPACING.dp)
            ) {
                Text(
                    text = "DotLottie Library (.json format)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )

                val dotLottieJsonGrouped = dotLottieJsonResults.groupBy {
                    "${it.config.animationCount} animations"
                }

                dotLottieJsonGrouped.forEach { (group, groupResults) ->
                    ResultGroup(
                        title = group,
                        results = groupResults
                    )
                }
            }
        }

        // Show DotLottie LOTTIE results
        if (dotLottieLottieResults.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(BenchmarkConstants.SMALL_SPACING.dp)
            ) {
                Text(
                    text = "DotLottie Library (.lottie format)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )

                val dotLottieLottieGrouped = dotLottieLottieResults.groupBy {
                    "${it.config.animationCount} animations"
                }

                dotLottieLottieGrouped.forEach { (group, groupResults) ->
                    ResultGroup(
                        title = group,
                        results = groupResults
                    )
                }
            }
        }

        // Show Airbnb Lottie results
        if (airbnbLottieResults.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(BenchmarkConstants.SMALL_SPACING.dp)
            ) {
                Text(
                    text = "Airbnb Lottie Library",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Note: Frame interpolation is not available in Airbnb Lottie",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )

                val airbnbLottieGrouped = airbnbLottieResults.groupBy {
                    "${it.config.animationCount} animations"
                }

                airbnbLottieGrouped.forEach { (group, groupResults) ->
                    ResultGroup(
                        title = group,
                        results = groupResults
                    )
                }
            }
        }

        Button(
            onClick = onRestart,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Run Benchmark Again")
        }
    }
}

@Composable
private fun ResultGroup(
    title: String,
    results: List<BenchmarkRunner.BenchmarkResult>
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(BenchmarkConstants.DEFAULT_SPACING.dp),
            verticalArrangement = Arrangement.spacedBy(BenchmarkConstants.SMALL_SPACING.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            results.forEach { result ->
                ResultItem(result = result)
            }
        }
    }
}

@Composable
private fun ResultItem(result: BenchmarkRunner.BenchmarkResult) {
    val interpolationText = if (result.config.library == BenchmarkRunner.LibraryType.DOT_LOTTIE) {
        if (result.config.useInterpolation) "With Interpolation" else "No Interpolation"
    } else {
        "Interpolation Not Supported" // Airbnb Lottie doesn't support interpolation
    }

    val formatText = when (result.config.library) {
        BenchmarkRunner.LibraryType.DOT_LOTTIE -> result.config.fileFormat.toString()
        BenchmarkRunner.LibraryType.AIRBNB_LOTTIE -> "JSON (only)" // Airbnb Lottie only supports JSON
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$interpolationText - $formatText",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "FPS: ${formatFloat(result.averageFps)} (min: ${formatFloat(result.minFps)}, max: ${
                    formatFloat(
                        result.maxFps
                    )
                })",
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    result.averageFps >= 55 -> BenchmarkConstants.PerformanceColors.Good
                    result.averageFps >= 45 -> BenchmarkConstants.PerformanceColors.Warning
                    else -> BenchmarkConstants.PerformanceColors.Poor
                }
            )

            Text(
                text = "Memory: ${result.averageMemoryMb} MB (peak: ${result.peakMemoryMb} MB)",
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = "CPU: ${formatFloat(result.averageCpuUsage)}% (peak: ${formatFloat(result.peakCpuUsage)}%)",
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    result.averageCpuUsage <= 30 -> BenchmarkConstants.PerformanceColors.Good
                    result.averageCpuUsage <= 60 -> BenchmarkConstants.PerformanceColors.Warning
                    else -> BenchmarkConstants.PerformanceColors.Poor
                }
            )
        }
    }
}

@Composable
private fun BenchmarkInfoItem(
    title: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = BenchmarkConstants.GRID_ITEM_SPACING.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.4f)
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.6f),
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DotLottieJsonContainer(
    count: Int,
    useInterpolation: Boolean,
    size: Int,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(size.dp),
        modifier = modifier
    ) {
        items(
            count = count,
            key = { index -> "$index-json-$useInterpolation" }
        ) { index ->
            DotLottieView(
                url = BenchmarkConstants.JSON_ANIMATION_URL,
                useFrameInterpolation = useInterpolation,
                modifier = Modifier
                    .padding(4.dp)
                    .size(size.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            )
        }
    }
}

@Composable
private fun DotLottieContainer(
    count: Int,
    useInterpolation: Boolean,
    size: Int,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(size.dp),
        modifier = modifier
    ) {
        items(
            count = count,
            key = { index -> "$index-lottie-$useInterpolation" }
        ) { index ->
            DotLottieView(
                url = BenchmarkConstants.LOTTIE_ANIMATION_URL,
                useFrameInterpolation = useInterpolation,
                modifier = Modifier
                    .padding(4.dp)
                    .size(size.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            )
        }
    }
}

@Composable
private fun AirbnbLottieContainer(
    count: Int,
    size: Int,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(size.dp),
        modifier = modifier
    ) {
        items(
            count = count,
            key = { index -> "$index" }
        ) { index ->
            AirbnbLottieView(
                url = BenchmarkConstants.JSON_ANIMATION_URL,
                autoPlay = true,
                loop = true,
                speed = 1f,
                modifier = Modifier
                    .padding(4.dp)
                    .size(size.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            )
        }
    }
} 