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
                        IconButton(onClick = { shareBenchmarkResults(context) }) {
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
                BenchmarkInfoItem(title = "With/Without Frame Interpolation", value = "Tests both modes")
                BenchmarkInfoItem(title = "Metrics", value = "FPS, Memory Usage, Jank %")
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
        
        // Group results by library and animation count
        val groupedResults = results.groupBy { 
            "${it.config.library} - ${it.config.animationCount} animations" 
        }
        
        groupedResults.forEach { (group, groupResults) ->
            ResultGroup(
                title = group,
                results = groupResults
            )
            
            Spacer(modifier = Modifier.height(16.dp))
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
    val interpolationText = if (result.config.useInterpolation) "With Interpolation" else "No Interpolation"
    
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
    useInterpolation: Boolean,
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
            // Add a key that includes the interpolation setting to force recomposition
            key = { index -> "$index-$useInterpolation" }
        ) { index ->
            AirbnbLottieView(
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

/**
 * Share benchmark results via an Intent
 */
private fun shareBenchmarkResults(context: Context) {
    // Find the most recent benchmark report file and share it
    val filesDir = context.getExternalFilesDir(null)
    filesDir?.listFiles { file -> 
        file.name.startsWith("lottie_comparison_benchmark_") && file.extension == "csv" 
    }?.maxByOrNull { it.lastModified() }?.let { reportFile ->
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            reportFile
        )
        
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Lottie Performance Benchmark Results")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(android.content.Intent.createChooser(intent, "Share Benchmark Results"))
    }
} 