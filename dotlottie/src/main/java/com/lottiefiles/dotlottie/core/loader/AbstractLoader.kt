package com.lottiefiles.dotlottie.core.loader

import android.content.Context
import androidx.lifecycle.lifecycleScope
import com.lottiefiles.dotlottie.core.util.DotLottieContent
import com.lottiefiles.dotlottie.core.util.lifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Prototype for loaders,
 * subclasses must provide implementation for [loadInternal]
 */
abstract class AbstractLoader(protected val context: Context) {
    /**
     * commit actual load, launch coroutine scope and
     * pass up the result
     */
    fun load(listener: DotLottieResult) {
        (context.lifecycleOwner()?.lifecycleScope ?: GlobalScope)
            .launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        loadInternal()
                    }
                    listener.onSuccess(result)
                } catch (e: Exception) {
                    listener.onError(e)
                }
            }
    }

    /**
     * internal loader function to be overridden
     */
    protected abstract suspend fun loadInternal(): DotLottieContent

}