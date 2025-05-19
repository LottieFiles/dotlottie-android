package com.lottiefiles.example.performance

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dotlottie.dlplayer.Mode
import com.lottiefiles.dotlottie.core.compose.ui.DotLottieAnimation
import com.lottiefiles.dotlottie.core.util.DotLottieSource
import com.lottiefiles.example.homesample.presentation.LottieView
import java.text.DecimalFormat

/**
 * Screen for running automated benchmarks comparing DotLottie and Airbnb Lottie libraries
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    
    val df = remember { DecimalFormat("#.##") }
    
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
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Lottie Library Benchmark",
                    style = MaterialTheme.typography.titleLarge
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "This benchmark will compare the performance of DotLottie and Airbnb Lottie libraries across multiple configurations:",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                BenchmarkInfoItem(title = "Animation Counts", value = "4, 9, 16, 25")
                BenchmarkInfoItem(
                    title = "Frame Interpolation", 
                    value = "Tests both modes for DotLottie\nNot available in Airbnb Lottie"
                )
                BenchmarkInfoItem(title = "Metrics", value = "FPS, CPU Usage, Memory, Jank %")
                BenchmarkInfoItem(title = "Duration", value = "Approximately 5-10 minutes")
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "The benchmark will run automatically and generate a detailed report on completion.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onStartBenchmark,
            modifier = Modifier.fillMaxWidth()
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
    val animationSize = 80
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Test status information
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Running Benchmark",
                    style = MaterialTheme.typography.titleLarge
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Test ${state.currentTestIndex + 1} of ${state.totalTests}",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(text = "Library: ${config.library}")
                Text(text = "Animations: ${config.animationCount}")
                Text(text = "Frame Interpolation: ${if (config.useInterpolation) "ON" else "OFF"}")
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LinearProgressIndicator(
                    progress = state.progress,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Animations being tested - render different containers based on library type
        if (config.library == BenchmarkRunner.LibraryType.DOT_LOTTIE) {
            DotLottieContainer(
                count = config.animationCount,
                useInterpolation = config.useInterpolation,
                size = animationSize,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        } else {
            AirbnbLottieContainer(
                count = config.animationCount,
                useInterpolation = config.useInterpolation,
                size = animationSize,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Stop button
        Button(
            onClick = onStopBenchmark,
            modifier = Modifier.fillMaxWidth()
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
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Benchmark Results",
            style = MaterialTheme.typography.titleLarge
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Filter and group results by library for clearer comparison
        val dotLottieResults = results.filter { it.config.library == BenchmarkRunner.LibraryType.DOT_LOTTIE }
        val airbnbLottieResults = results.filter { it.config.library == BenchmarkRunner.LibraryType.AIRBNB_LOTTIE }
        
        // First show DotLottie results grouped by animation count
        if (dotLottieResults.isNotEmpty()) {
            Text(
                text = "DotLottie Library Results",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val dotLottieGrouped = dotLottieResults.groupBy { 
                "${it.config.animationCount} animations" 
            }
            
            dotLottieGrouped.forEach { (group, groupResults) ->
                ResultGroup(
                    title = group,
                    results = groupResults
                )
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        
        // Then show Airbnb Lottie results
        if (airbnbLottieResults.isNotEmpty()) {
            Text(
                text = "Airbnb Lottie Library Results",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Note: Frame interpolation is not available in Airbnb Lottie",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val airbnbLottieGrouped = airbnbLottieResults.groupBy { 
                "${it.config.animationCount} animations" 
            }
            
            airbnbLottieGrouped.forEach { (group, groupResults) ->
                ResultGroup(
                    title = group,
                    results = groupResults
                )
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        
        Button(
            onClick = onRestart,
            modifier = Modifier.fillMaxWidth()
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
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            results.forEach { result ->
                ResultItem(result = result)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ResultItem(result: BenchmarkRunner.BenchmarkResult) {
    val interpolationText = if (result.config.library == BenchmarkRunner.LibraryType.DOT_LOTTIE) {
        if (result.config.useInterpolation) "With Interpolation" else "No Interpolation"
    } else {
        "Interpolation N/A" // Airbnb Lottie doesn't support interpolation
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = interpolationText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = "FPS: ${String.format("%.1f", result.averageFps)} (min: ${String.format("%.1f", result.minFps)}, max: ${String.format("%.1f", result.maxFps)})",
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    result.averageFps >= 55 -> Color.Green
                    result.averageFps >= 45 -> Color(0xFFFFAA00) // Orange
                    else -> Color.Red
                }
            )
            
            Text(
                text = "Memory: ${result.averageMemoryMb} MB (peak: ${result.peakMemoryMb} MB)",
                style = MaterialTheme.typography.bodySmall
            )
            
            Text(
                text = "CPU: ${String.format("%.1f", result.averageCpuUsage)}% (peak: ${String.format("%.1f", result.peakCpuUsage)}%)",
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    result.averageCpuUsage <= 30 -> Color.Green
                    result.averageCpuUsage <= 60 -> Color(0xFFFFAA00) // Orange
                    else -> Color.Red
                }
            )
        }
    }
}

@Composable
private fun BenchmarkInfoItem(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
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
private fun DotLottieContainer(
    count: Int,
    useInterpolation: Boolean,
    size: Int,
    modifier: Modifier = Modifier
) {
    // Use the swag_sticker_piggy.lottie URL for testing
    val lottieUrl = "https://lottiefiles-mobile-templates.s3.amazonaws.com/ar-stickers/swag_sticker_piggy.lottie"
    
    LazyVerticalGrid(
        columns = GridCells.Adaptive(size.dp),
        modifier = modifier
    ) {
        items(
            count = count,
            // Add a key that includes the interpolation setting to force recomposition
            key = { index -> "$index-$useInterpolation" }
        ) { index ->
            LottieView(
                url = lottieUrl,
                useFrameInterpolation = useInterpolation,
                modifier = Modifier
                    .padding(4.dp)
                    .size(size.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.LightGray.copy(alpha = 0.2f))
            )
        }
    }
}

@Composable
private fun AirbnbLottieContainer(
    count: Int,
    useInterpolation: Boolean, // This parameter is ignored for Airbnb Lottie
    size: Int,
    modifier: Modifier = Modifier
) {
    // Use JSON URL for Airbnb Lottie
    val lottieUrl = "https://lottie.host/f8e7eccf-72da-40da-9dd1-0fdbdc93b9ea/yAX2Nay9jD.json"
    
    LazyVerticalGrid(
        columns = GridCells.Adaptive(size.dp),
        modifier = modifier
    ) {
        items(
            count = count,
            // Add a key to force recomposition - note that useInterpolation has no effect
            key = { index -> "$index" }
        ) { index ->
            AirbnbLottieView(
                url = lottieUrl,
                // Interpolation is not supported in Airbnb Lottie, but kept for API consistency
                useFrameInterpolation = useInterpolation, 
                modifier = Modifier
                    .padding(4.dp)
                    .size(size.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.LightGray.copy(alpha = 0.2f))
            )
        }
    }
} 