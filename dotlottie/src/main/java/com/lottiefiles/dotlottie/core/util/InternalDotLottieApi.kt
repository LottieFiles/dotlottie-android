package com.lottiefiles.dotlottie.core.util

@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This API is internal and not intended for public use."
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY
)
annotation class InternalDotLottieApi
