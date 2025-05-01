package com.lottiefiles.dotlottie.core.loader

import android.content.Context
import com.lottiefiles.dotlottie.core.util.DotLottieContent
import com.lottiefiles.dotlottie.core.util.isZipCompressed
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

class NetworkLoader(context: Context, private val url: String) : AbstractLoader(context) {
    private val httpClient = OkHttpClient()

    private fun cacheJson(data: String) {
        try {
            val cacheFile = File(context.cacheDir, "${url.hashCode()}.json")
            cacheFile.writeText(data)
        } catch (e: IOException) {
            throw e
        }
    }

    private fun cacheBytes(data: ByteArray) {
        try {
            val cacheFile = File(context.cacheDir, "${url.hashCode()}.bytes")
            cacheFile.writeBytes(data)
        } catch (e: IOException) {
            throw e
        }
    }

    private fun fromCache(): DotLottieContent? {
        return if (url.endsWith(".json")) {
            val cacheFile = File(context.cacheDir, "${url.hashCode()}.json")
            val cached = if (cacheFile.exists()) cacheFile.readText() else null
            if (cached != null) {
                DotLottieContent.Json(cached)
            } else {
                null
            }
        } else {
            val cacheFile = File(context.cacheDir, "${url.hashCode()}.bytes")
            val cached = if (cacheFile.exists()) cacheFile.readBytes() else null
            if (cached != null) {
                DotLottieContent.Binary(cached)
            } else {
                null
            }
        }
    }

    override suspend fun loadInternal(): DotLottieContent {
        try {
            // Trying to load from cache
            when (val cached = fromCache()) {
                is DotLottieContent.Json -> {
                    return cached
                }

                is DotLottieContent.Binary -> {
                    return cached
                }

                else -> {}
            }

            val request = Request
                .Builder()
                .url(url)
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                throw IOException("[NetworkLoader]: Failed to download file: $url")
            }

            val isDotLottie = response.body?.source()
                ?.isZipCompressed()
                ?:false

            val content = if (!isDotLottie) {
                val text = response.body?.string()
                    ?: throw IOException("Response body is null: $url")
                cacheJson(text)
                DotLottieContent.Json(text)
            } else {
                val bytes = response.body?.bytes()
                    ?: throw IOException("Response body is null: $url")
                cacheBytes(bytes)
                DotLottieContent.Binary(bytes)
            }

            return content
        } catch (e: IOException) {
            throw e
        }
    }
}