package com.lottiefiles.dotlottie.core.util

import DotLottieLoader
import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.LifecycleOwner
import com.lottiefiles.dotlottie.core.loader.DotLottieResult
import okio.BufferedSource
import java.io.File
import java.io.FileOutputStream
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

fun String.isUrl(): Boolean {
    return startsWith("http") || startsWith("https")
}

val ZIP_MAGIC: ByteArray = byteArrayOf(0x50, 0x4b, 0x03, 0x04)

fun BufferedSource.isZipCompressed(): Boolean {
    return matchesMagicBytes(this, ZIP_MAGIC)
}

fun Context.lifecycleOwner(): LifecycleOwner? {
    var curContext = this
    var maxDepth = 20
    while (maxDepth-- > 0 && curContext !is LifecycleOwner) {
        curContext = (curContext as ContextWrapper).baseContext
    }
    return if (curContext is LifecycleOwner) {
        curContext as LifecycleOwner
    } else {
        null
    }
}

fun matchesMagicBytes(inputSource: BufferedSource, magic: ByteArray): Boolean {
    try {
        val peek = inputSource.peek()
        for (b in magic) {
            if (peek.readByte() != b) {
                return false
            }
        }
        peek.close()
        return true
    } catch (e: NoSuchMethodError) {
        // This happens in the Android Studio layout preview.
        return false
    } catch (e: java.lang.Exception) {
        return false
    }
}


object DotLottieUtils {
    suspend fun getContent(context: Context, source: DotLottieSource): DotLottieContent {
        when (source) {
            is DotLottieSource.Url -> {
                return suspendCoroutine { cont ->
                    DotLottieLoader.with(context).fromUrl(source.urlString).load(object :
                        DotLottieResult {
                        override fun onSuccess(result: DotLottieContent) {
                            cont.resume(result)
                        }

                        override fun onError(throwable: Throwable) {
                            cont.resumeWithException(throwable)
                        }
                    })
                }
            }

            is DotLottieSource.Asset -> {
                return suspendCoroutine { cont ->
                    DotLottieLoader.with(context).fromAsset(source.assetPath).load(object :
                        DotLottieResult {
                        override fun onSuccess(result: DotLottieContent) {
                            cont.resume(result)
                        }

                        override fun onError(throwable: Throwable) {
                            cont.resumeWithException(throwable)
                        }
                    })
                }
            }

            is DotLottieSource.Json -> {
                return suspendCoroutine { cont ->
                    cont.resume(DotLottieContent.Json(source.jsonString))
                }
            }

            is DotLottieSource.Data -> {
                return suspendCoroutine { cont ->
                    cont.resume(DotLottieContent.Binary(source.data))
                }
            }
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