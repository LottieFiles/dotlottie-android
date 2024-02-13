package com.lottiefiles.dotlottie.core.model

import com.dotlottie.dlplayer.Mode
import com.lottiefiles.dotlottie.core.compose.ui.DotLottieSource

class Config private constructor(
    val autoplay: Boolean,
    val useFrameInterpolator: Boolean,
    val speed: Float,
    val playMode: Mode,
//    val backgroundColor: Int,
    val loop: Boolean,
    val source: DotLottieSource,
){

    class Builder {

        private var autoplay: Boolean = false
        private var loop: Boolean = false
        private var useFrameInterpolator: Boolean = false
        private var speed: Float = 1f
        private var backgroundColor: Int = 0x0000000
        private var playMode: Mode = Mode.FORWARD
        private var source: DotLottieSource? = null

        fun autoplay(autoplay: Boolean) = apply {
            this.autoplay = autoplay
        }

        fun loop(loop: Boolean) = apply {
            this.loop = loop
        }

        fun speed(speed: Float) = apply {
            this.speed = speed
        }

        fun source(source: DotLottieSource) = apply {
            this.source = source
        }

//        fun backgroundColor(color: Int) = apply {
//            this.backgroundColor = color
//        }

        fun useFrameInterpolation(useFrameInterpolator: Boolean) = apply {
            this.useFrameInterpolator = useFrameInterpolator
        }


        fun playMode(mode: Mode) = apply {
            this.playMode = mode
        }

        fun build(): Config {
            require(source != null) { "`source` must be provided" }

            return Config(
                autoplay = this.autoplay,
                speed = this.speed,
                playMode = this.playMode,
                useFrameInterpolator = this.useFrameInterpolator,
                loop = this.loop,
                source = source!!,
//                backgroundColor = this.backgroundColor
            )
        }
    }
}