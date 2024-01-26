package com.lottiefiles.dotlottie.core.model

import com.dotlottie.dlplayer.Mode

class Config private constructor(
    val autoPlay: Boolean,
    val useFrameInterpolator: Boolean,
    val speed: Float,
    val asset: String,
    val mode: Mode,
    val backgroundColor: Int,
    val loop: Boolean,
    val srcUrl: String,
    val data: Any?
){

    class Builder {

        private var autoPlay: Boolean = false
        private var loop: Boolean = false
        private var useFrameInterpolator: Boolean = false
        private var speed: Float = 1f
        private var asset: String = ""
        private var srcUrl: String = ""
        private var backgroundColor: Int = 0x0000000
        private var mode: Mode = Mode.FORWARD
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

        fun src(url: String) = apply {
            this.srcUrl = url
        }

        fun backgroundColor(color: Int) = apply {
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
                srcUrl = this.srcUrl,
                backgroundColor = this.backgroundColor
            )
        }
    }
}