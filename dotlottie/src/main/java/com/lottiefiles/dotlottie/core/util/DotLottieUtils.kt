package com.lottiefiles.dotlottie.core.util

import android.content.Context
import com.lottiefiles.dotlottie.core.model.Config
import io.dotlottie.loader.DotLottieLoader
import io.dotlottie.loader.models.DotLottie
import io.dotlottie.loader.models.DotLottieResult
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


fun String.isJsonAsset(): Boolean {
    return endsWith(".json")
}

fun String.isDotLottieAsset(): Boolean {
    return endsWith(".lottie")
}

object DotLottieUtils {
    suspend fun getContent(context: Context, config: Config): String? {

        val assetFilePath = config.asset
        val contentStr = when {
            config.asset.isJsonAsset() ||
                    config.asset.isDotLottieAsset() -> loadAsset(context, assetFilePath)
            config.srcUrl.isNotBlank() -> loadFromUrl(context, config.srcUrl)
            config.data is String -> config.data
            config.data is ByteArray -> loadFromByteArray(context, config.data)
            else -> error("Asset not found")
        }

        return contentStr
    }
    suspend fun loadAsset(context: Context, filePath: String): String? {
        return suspendCoroutine { cont ->
            DotLottieLoader.with(context).fromAsset(filePath).load(object : DotLottieResult {
                override fun onSuccess(result: DotLottie) {
                    val anim = result.animations.entries.lastOrNull()
                    if (anim != null) {
                        val data = String(anim.value, StandardCharsets.UTF_8)
                        cont.resume(data)
                    }
                }
                override fun onError(throwable: Throwable) {
                    cont.resumeWithException(throwable)
                }
            })
        }
    }
    suspend fun loadFromUrl(context: Context, url: String): String {
        return suspendCoroutine { cont ->
            DotLottieLoader.with(context).fromUrl(url).load(object : DotLottieResult {
                override fun onSuccess(result: DotLottie) {
                    val anim = result.animations.entries.lastOrNull()
                    if (anim != null) {
                        val data = String(anim.value, StandardCharsets.UTF_8)
                        cont.resume(data)
                    }
                }
                override fun onError(throwable: Throwable) {
                    cont.resumeWithException(throwable)
                }
            })
        }
    }
    suspend fun loadFromByteArray(context: Context, data: ByteArray): String? {
        return suspendCoroutine { cont ->
            val file = byteArrayToFile(context, data) ?: return@suspendCoroutine
            DotLottieLoader.with(context).fromAsset(file.path).load(object : DotLottieResult {
                override fun onSuccess(result: DotLottie) {
                    val anim = result.animations.entries.lastOrNull()
                    if (anim != null) {
                        val content = String(anim.value, StandardCharsets.UTF_8)
                        cont.resume(content)
                    }
                }
                override fun onError(throwable: Throwable) {
                    cont.resumeWithException(throwable)
                }
            })
        }
    }
    private fun byteArrayToFile(context: Context, data: ByteArray): File? {
        return try {
            val filePath = File(context.cacheDir, "${UUID.randomUUID()}.lottie").apply {
                createNewFile()
            }
            val fileOutputStream = FileOutputStream(filePath)
            fileOutputStream.write(data)
            fileOutputStream.close()
            filePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}