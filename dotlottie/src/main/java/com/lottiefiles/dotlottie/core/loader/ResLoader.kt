package com.lottiefiles.dotlottie.core.loader

import android.content.Context
import com.lottiefiles.dotlottie.core.util.DotLottieContent
import com.lottiefiles.dotlottie.core.util.isZipCompressed
import java.io.IOException

class ResLoader(context: Context, private val resourceId: Int) : AbstractLoader(context) {
    override suspend fun loadInternal(): DotLottieContent {
        try {
            context.resources.openRawResource(resourceId).use { inputStream ->
                val bytes = inputStream.readBytes()
                val isDotLottie = bytes.isZipCompressed()
                
                val content = if (!isDotLottie) {
                    val text = bytes.toString(Charsets.UTF_8)
                    DotLottieContent.Json(text)
                } else {
                    DotLottieContent.Binary(bytes)
                }
                return content
            }
        } catch (e: IOException) {
            throw e
        }
    }
}