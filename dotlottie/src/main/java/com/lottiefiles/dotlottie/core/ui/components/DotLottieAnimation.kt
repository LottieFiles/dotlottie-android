package com.lottiefiles.dotlottie.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.dotlottie.dlplayer.Mode
import com.lottiefiles.dotlottie.core.model.Config
import com.lottiefiles.dotlottie.core.widget.DotLottieAnimation

@Composable
fun DotLottieAnimation(
    modifier: Modifier = Modifier,
    config: Config
) {
    Box() {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                DotLottieAnimation(context).apply {
                    load(config)
                }
            }
        )
    }
}


@Preview(showBackground = true)
@Composable
fun DotLottieAnimationPreview() {
    val config = Config.Builder()
        .autoplay(true)
        .speed(1f)
        .loop(true)
        .fileName("swinging.json") // file name of json/.lottie
//            .src("https://lottie.host/5525262b-4e57-4f0a-8103-cfdaa7c8969e/VCYIkooYX8.json")
//            .src("https://lottiefiles-mobile-templates.s3.amazonaws.com/ar-stickers/swag_sticker_piggy.lottie")
        .playMode(Mode.FORWARD)
        .useFrameInterpolation(true)
        .build()
    DotLottieAnimation(config = config)
}