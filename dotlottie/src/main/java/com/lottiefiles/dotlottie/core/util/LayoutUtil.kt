package com.lottiefiles.dotlottie.core.util
import com.dotlottie.dlplayer.Fit
import com.dotlottie.dlplayer.Layout

object LayoutUtil {

    enum class Alignment(val alignment: Pair<Float, Float>) {
        TopLeft(Pair(0f, 0f)),
        TopCenter(Pair(0.5f, 0f)),
        TopRight(Pair(1f, 0f)),
        CenterLeft(Pair(0f, 0.5f)),
        Center(Pair(0.5f, 0.5f)),
        CenterRight(Pair(1f, 0.5f)),
        BottomLeft(Pair(0f, 1f)),
        BottomCenter(Pair(0.5f, 1f)),
        BottomRight(Pair(1f, 1f))
    }

    fun createLayout(fit: Fit, alignment: Alignment): Layout {
        return Layout(fit, listOf(alignment.alignment.first, alignment.alignment.second))
    }
    fun createLayout(fit: Fit, alignment: Pair<Float, Float>): Layout {
        return Layout(fit, listOf(alignment.first, alignment.second))
    }
}
