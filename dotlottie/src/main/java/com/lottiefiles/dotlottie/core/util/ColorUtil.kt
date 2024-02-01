package com.lottiefiles.dotlottie.core.util

import android.graphics.Color

fun String.toColor(): Int {
    val colorHex = if (startsWith("#")) this else "#$this"
    return try {
        Color.parseColor(colorHex)
    } catch (e: Exception) {
        Color.TRANSPARENT
    }
}