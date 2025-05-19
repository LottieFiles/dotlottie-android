package com.lottiefiles.sample.performance

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import java.text.DecimalFormat

/**
 * A custom view that displays performance metrics as an overlay on the screen.
 * Shows FPS, memory usage, CPU usage, and jank percentage.
 */
class PerformanceOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), PerformanceMonitor.PerformanceListener {

    private val bgPaint = Paint().apply {
        color = Color.parseColor("#80000000") // Semi-transparent black
        style = Paint.Style.FILL
    }
    
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        isAntiAlias = true
        // Add shadow for better visibility
        setShadowLayer(1.5f, 1f, 1f, Color.BLACK)
    }
    
    private val fpsRect = Rect()
    private val memoryRect = Rect()
    private val jankRect = Rect()
    private val cpuRect = Rect()
    
    private var fps: Float = 0f
    private var memoryUsageMb: Float = 0f
    private var jankPercentage: Float = 0f
    private var cpuUsage: Float = 0f
    
    private val decimalFormat = DecimalFormat("#0.0")
    
    private var padding = 16
    private var lineHeight = 42
    
    init {
        // Make sure we're drawn on top
        setZOrderOnTop(true)
        
        // Required for overlay
        setWillNotDraw(false)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Background for better readability
        canvas.drawRect(0f, 0f, width.toFloat(), (padding * 2 + lineHeight * 4).toFloat(), bgPaint)
        
        // Draw performance metrics
        val fpsColor = when {
            fps >= 55 -> Color.GREEN
            fps >= 30 -> Color.YELLOW
            else -> Color.RED
        }
        
        val fpsText = "FPS: ${decimalFormat.format(fps)}"
        val memoryText = "Memory: ${decimalFormat.format(memoryUsageMb)} MB"
        val jankText = "Jank: ${decimalFormat.format(jankPercentage)}%"
        val cpuText = "CPU: ${decimalFormat.format(cpuUsage)}%"
        
        // Draw FPS
        textPaint.color = fpsColor
        textPaint.getTextBounds(fpsText, 0, fpsText.length, fpsRect)
        canvas.drawText(fpsText, padding.toFloat(), (padding + fpsRect.height()).toFloat(), textPaint)
        
        // Draw Memory Usage
        textPaint.color = Color.WHITE
        textPaint.getTextBounds(memoryText, 0, memoryText.length, memoryRect)
        canvas.drawText(
            memoryText, 
            padding.toFloat(), 
            (padding + fpsRect.height() + lineHeight).toFloat(),
            textPaint
        )
        
        // Draw Jank Percentage
        val jankColor = when {
            jankPercentage <= 5 -> Color.GREEN
            jankPercentage <= 15 -> Color.YELLOW
            else -> Color.RED
        }
        
        textPaint.color = jankColor
        textPaint.getTextBounds(jankText, 0, jankText.length, jankRect)
        canvas.drawText(
            jankText,
            padding.toFloat(),
            (padding + fpsRect.height() + lineHeight * 2).toFloat(),
            textPaint
        )
        
        // Draw CPU Usage
        val cpuColor = when {
            cpuUsage <= 20 -> Color.GREEN
            cpuUsage <= 50 -> Color.YELLOW
            else -> Color.RED
        }
        
        textPaint.color = cpuColor
        textPaint.getTextBounds(cpuText, 0, cpuText.length, cpuRect)
        canvas.drawText(
            cpuText,
            padding.toFloat(),
            (padding + fpsRect.height() + lineHeight * 3).toFloat(),
            textPaint
        )
    }
    
    /**
     * Update the metrics displayed in the overlay
     */
    fun updateMetrics(metrics: PerformanceMonitor.PerformanceMetrics) {
        fps = metrics.fps
        memoryUsageMb = metrics.memoryUsageMb
        jankPercentage = metrics.jankPercentage
        cpuUsage = metrics.cpuUsage
        invalidate()
    }
    
    override fun onMetricsUpdated(metrics: PerformanceMonitor.PerformanceMetrics) {
        updateMetrics(metrics)
    }
    
    /**
     * Set Z-order on top for proper overlay display
     */
    private fun setZOrderOnTop(onTop: Boolean) {
        // This is a no-op for regular views, but needed for compatibility with SurfaceView overlays
    }
} 