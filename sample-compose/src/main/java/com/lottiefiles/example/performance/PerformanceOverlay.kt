package com.lottiefiles.example.performance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat

/**
 * A composable that displays performance metrics as an overlay
 */
@Composable
fun PerformanceOverlay(
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val df = remember { DecimalFormat("#.##") }
    var fps by remember { mutableFloatStateOf(0f) }
    var memoryUsage by remember { mutableLongStateOf(0L) }
    
    // Add the performance monitor effect
    PerformanceMonitorEffect(
        enabled = enabled,
        onMetricsUpdated = { newFps, newMemoryUsage ->
            fps = newFps
            memoryUsage = newMemoryUsage
        }
    )
    
    if (enabled) {
        Column(
            modifier = modifier
                .padding(16.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(8.dp)
                .alpha(0.9f)
        ) {
            Text(
                text = "FPS: ${df.format(fps)}",
                color = when {
                    fps >= 55 -> Color.Green
                    fps >= 45 -> Color.Yellow
                    else -> Color.Red
                },
                fontSize = 14.sp
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "Memory: $memoryUsage MB",
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
} 