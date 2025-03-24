package com.lottiefiles.dotlottie.core.loader

import com.lottiefiles.dotlottie.core.util.DotLottieContent

interface DotLottieResult {
    fun onSuccess(result: DotLottieContent)
    fun onError(throwable: Throwable)
}