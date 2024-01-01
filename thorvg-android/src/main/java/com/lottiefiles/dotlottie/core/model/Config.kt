package com.lottiefiles.dotlottie.core.model

class Config private constructor(
    val autoPlay: Boolean,
    val useFrameInterpolator: Boolean,
    val speed: Float,
    val asset: String,
    val mode: Mode,
    val backgroundColor: String,
    val loop: Boolean,
    val data: Any?
){

    class Builder {

        private var autoPlay: Boolean = false
        private var loop: Boolean = false
        private var useFrameInterpolator: Boolean = false
        private var speed: Float = 1f
        private var asset: String = ""
        private var backgroundColor: String = "#FFFFFF"
        private var mode: Mode = Mode.Forward
        private var data: Any? = null

        fun autoPlay(autoPlay: Boolean) = apply {
            this.autoPlay = autoPlay
        }

        fun loop(loop: Boolean) = apply {
            this.loop = loop
        }

        fun speed(speed: Float) = apply {
            this.speed = speed
        }

        fun fileName(asset: String) = apply {
            this.asset = asset
        }

        fun backgroundColor(color: String) = apply {
            this.backgroundColor = color
        }

        fun useFrameInterpolation(useFrameInterpolator: Boolean) = apply {
            this.useFrameInterpolator = useFrameInterpolator
        }

        fun data(data: Any) = apply {
            this.data = data
        }

        fun mode(mode: Mode) = apply {
            this.mode = mode
        }

        fun build(): Config {
            return Config(
                autoPlay = this.autoPlay,
                speed = this.speed,
                mode = this.mode,
                data = this.data,
                asset = this.asset,
                useFrameInterpolator = this.useFrameInterpolator,
                loop = this.loop,
                backgroundColor = this.backgroundColor
            )
        }
    }
}