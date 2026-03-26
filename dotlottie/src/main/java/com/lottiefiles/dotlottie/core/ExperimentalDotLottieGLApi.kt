package com.lottiefiles.dotlottie.core

@RequiresOptIn(
    message = "This GL rendering API is experimental and may change in future releases.",
    level = RequiresOptIn.Level.WARNING
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class ExperimentalDotLottieGLApi
