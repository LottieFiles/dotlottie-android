package com.lottiefiles.dotlottie.core.model

import com.dotlottie.dlplayer.Fit
import com.dotlottie.dlplayer.Layout
import com.dotlottie.dlplayer.Mode
import com.dotlottie.dlplayer.createDefaultLayout
import com.lottiefiles.dotlottie.core.util.DotLottieSource
import com.lottiefiles.dotlottie.core.util.LayoutUtil

class Config private constructor(
    val autoplay: Boolean,
    val useFrameInterpolator: Boolean,
    val speed: Float,
    val playMode: Mode,
//    val backgroundColor: Int,
    val loop: Boolean,
    val loopCount: UInt,
    val marker: String,
    val layout: Layout,
    val source: DotLottieSource,
    val themeId: String = "",
    val stateMachineId: String = "",
    val animationId: String = "",
    val threads: UInt? = null
) {

    class Builder {

        private var autoplay: Boolean = false
        private var loop: Boolean = false
        private var useFrameInterpolator: Boolean = false
        private var speed: Float = 1f
        private var backgroundColor: Int = 0x0000000
        private var playMode: Mode = Mode.FORWARD
        private var source: DotLottieSource? = null
        private var marker: String = ""
        private var layout: Layout = createDefaultLayout()
        private var themeId: String = ""
        private var threads: UInt? = null
        private var loopCount: UInt = 0u

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

        fun marker(marker: String) = apply {
            this.marker = marker
        }

        fun layout(fit: Fit, alignment: LayoutUtil.Alignment) = apply {
            this.layout = Layout(fit, listOf(alignment.alignment.first, alignment.alignment.second))
        }

        fun layout(fit: Fit, alignment: Pair<Float, Float>) = apply {
            this.layout = Layout(fit, listOf(alignment.first, alignment.second))
        }

        fun themeId(themeId: String) = apply {
            this.themeId = themeId
        }

        fun threads(threads: UInt) = apply {
            this.threads = threads
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
                marker = this.marker,
                layout = this.layout,
                themeId = this.themeId,
                threads = this.threads,
                loopCount = this.loopCount
//                backgroundColor = this.backgroundColor
            )
        }
    }
}