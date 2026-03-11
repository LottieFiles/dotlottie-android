package com.dotlottie.dlplayer

/**
 * Animation playback mode
 */
enum class Mode(val value: Int) {
    FORWARD(0),
    REVERSE(1),
    BOUNCE(2),
    REVERSE_BOUNCE(3);

    companion object {
        fun fromValue(value: Int): Mode = entries.find { it.value == value } ?: FORWARD
    }
}

/**
 * Layout fit mode for animation rendering
 */
enum class Fit(val value: Int) {
    CONTAIN(0),
    FILL(1),
    COVER(2),
    FIT_WIDTH(3),
    FIT_HEIGHT(4),
    NONE(5);

    companion object {
        fun fromValue(value: Int): Fit = entries.find { it.value == value } ?: CONTAIN
    }
}

/**
 * Layout configuration for animation rendering
 */
data class Layout(
    val fit: Fit,
    val align: List<Float>
) {
    init {
        require(align.size == 2) { "Align must have exactly 2 values (x, y)" }
    }
}

/**
 * Animation marker
 */
data class Marker(
    val name: String,
    val time: Float,
    val duration: Float
)

/**
 * DotLottie manifest
 */
data class Manifest(
    val activeAnimationId: String?,
    val animations: List<Animation>?,
    val author: String?,
    val description: String?,
    val generator: String?,
    val keywords: String?,
    val revision: UInt?,
    val themes: List<Theme>?,
    val stateMachines: List<StateMachine>?,
    val version: String?,
    val customData: Map<String, String>?
) {
    data class Animation(
        val id: String,
        val name: String?,
        val initialTheme: String?,
        val background: String?
    )

    data class Theme(
        val id: String,
        val name: String?
    )

    data class StateMachine(
        val id: String,
        val name: String?
    )
}

/**
 * Configuration for DotLottiePlayer
 */
data class Config(
    var mode: Mode = Mode.FORWARD,
    var loopAnimation: Boolean = false,
    var loopCount: UInt = 0u,
    var speed: Float = 1f,
    var useFrameInterpolation: Boolean = false,
    var autoplay: Boolean = false,
    var segment: List<Float> = emptyList(),
    var backgroundColor: UInt = 0u,
    var layout: Layout = createDefaultLayout(),
    var marker: String = "",
    var themeId: String = "",
    var stateMachineId: String = "",
    var animationId: String = ""
)

/**
 * Helper function to create default layout (Contain with center alignment)
 */
fun createDefaultLayout(): Layout = Layout(Fit.CONTAIN, listOf(0.5f, 0.5f))

/**
 * Helper function to create default config
 */
fun createDefaultConfig(): Config = Config()

/**
 * Event types for state machine interaction (input events)
 */
sealed class Event(val tag: Int) {
    data class PointerDown(val x: Float, val y: Float) : Event(0)
    data class PointerUp(val x: Float, val y: Float) : Event(1)
    data class PointerMove(val x: Float, val y: Float) : Event(2)
    data class PointerEnter(val x: Float, val y: Float) : Event(3)
    data class PointerExit(val x: Float, val y: Float) : Event(4)
    data class Click(val x: Float, val y: Float) : Event(5)
    object OnComplete : Event(6)
    object OnLoopComplete : Event(7)
}

/**
 * Policy for handling URLs in interactive animations
 */
data class OpenUrlPolicy(
    val requireUserInteraction: Boolean = false,
    val whitelist: List<String> = emptyList()
)

/**
 * Helper function to create default open URL policy
 */
fun createDefaultOpenUrlPolicy(): OpenUrlPolicy = OpenUrlPolicy()

/**
 * Layer bounding box
 */
data class LayerBoundingBox(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val x3: Float,
    val y3: Float,
    val x4: Float,
    val y4: Float
)


typealias ManifestAnimation = Manifest.Animation
typealias ManifestTheme = Manifest.Theme
typealias ManifestStateMachine = Manifest.StateMachine

data class ManifestInitial(
    val animation: String?,
    val stateMachine: String?
)
