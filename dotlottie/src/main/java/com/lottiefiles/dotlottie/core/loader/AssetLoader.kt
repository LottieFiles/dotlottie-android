package com.lottiefiles.dotlottie.core.loader

import android.content.Context
import com.lottiefiles.dotlottie.core.util.DotLottieContent
import com.lottiefiles.dotlottie.core.util.isJsonAsset
import java.io.IOException

class AssetLoader(context: Context, private val assetPath: String) : AbstractLoader(context) {
    override suspend fun loadInternal(): DotLottieContent {
        try {
            context.assets.open(assetPath).use { inputStream ->
                val content = if(assetPath.isJsonAsset()) {
                    DotLottieContent.Json(inputStream.reader().readText())
                } else {
                    DotLottieContent.Binary(inputStream.readBytes())
                }
                return content
            }
        } catch (e: IOException) {
            throw e
        }
    }

}