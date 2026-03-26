package com.lottiefiles.dotlottie.core.compose.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.dotlottie.dlplayer.Config
import com.dotlottie.dlplayer.DotLottiePlayer
import com.dotlottie.dlplayer.DotLottiePlayerEvent
import com.dotlottie.dlplayer.Event
import com.dotlottie.dlplayer.Layout
import com.dotlottie.dlplayer.Mode
import com.lottiefiles.dotlottie.core.compose.runtime.DotLottieController
import com.lottiefiles.dotlottie.core.compose.runtime.DotLottiePlayerState
import com.lottiefiles.dotlottie.core.util.InternalDotLottieApi
import com.lottiefiles.dotlottie.core.util.pollAndDispatchAllEvents
import kotlin.math.pow

/**
 * Maps [DotLottiePlayerEvent] to [DotLottiePlayerState] updates on the controller.
 */
@InternalDotLottieApi
internal fun controllerStateChange(controller: DotLottieController): (DotLottiePlayerEvent) -> Unit = { event ->
    when (event) {
        is DotLottiePlayerEvent.Load -> controller.updateState(DotLottiePlayerState.LOADED)
        is DotLottiePlayerEvent.LoadError -> controller.updateState(DotLottiePlayerState.ERROR)
        is DotLottiePlayerEvent.Play -> controller.updateState(DotLottiePlayerState.PLAYING)
        is DotLottiePlayerEvent.Pause -> controller.updateState(DotLottiePlayerState.PAUSED)
        is DotLottiePlayerEvent.Stop -> controller.updateState(DotLottiePlayerState.STOPPED)
        is DotLottiePlayerEvent.Complete -> controller.updateState(DotLottiePlayerState.COMPLETED)
        else -> {}
    }
}

/**
 * Polls all pending events from the player and dispatches them via the controller.
 */
@InternalDotLottieApi
internal fun pollAndDispatchEvents(player: DotLottiePlayer, controller: DotLottieController) {
    pollAndDispatchAllEvents(
        player = player,
        eventListeners = controller.eventListeners,
        stateMachineListeners = controller.stateMachineListeners,
        onStateChange = controllerStateChange(controller),
        onOpenUrl = controller.onOpenUrlCallback,
    )
}

/**
 * Pointer input modifier for state machine interaction (PointerDown, PointerUp, PointerMove, Click).
 */
internal fun Modifier.dotLottiePointerInput(controller: DotLottieController): Modifier =
    this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val downPosition = down.position
            val scaledX = downPosition.x
            val scaledY = downPosition.y

            controller.stateMachinePostEvent(Event.PointerDown(scaledX, scaledY))

            var movedTooMuch = false
            val touchSlop = 20f

            do {
                val event = awaitPointerEvent()
                val position = event.changes.first()

                if (!position.pressed) {
                    val upPosition = position.position
                    val upScaledX = upPosition.x
                    val upScaledY = upPosition.y

                    controller.stateMachinePostEvent(Event.PointerUp(upScaledX, upScaledY))

                    val distance = kotlin.math.sqrt(
                        (upPosition.x - downPosition.x).pow(2) +
                                (upPosition.y - downPosition.y).pow(2)
                    )

                    if (distance < touchSlop && !movedTooMuch) {
                        controller.stateMachinePostEvent(Event.Click(upScaledX, upScaledY))
                    }
                    break
                } else {
                    val movePosition = position.position
                    val moveX = movePosition.x
                    val moveY = movePosition.y

                    val moveDistance = kotlin.math.sqrt(
                        (movePosition.x - downPosition.x).pow(2) +
                                (movePosition.y - downPosition.y).pow(2)
                    )
                    if (moveDistance > touchSlop) {
                        movedTooMuch = true
                    }

                    controller.stateMachinePostEvent(Event.PointerMove(moveX, moveY))
                }
            } while (position.pressed)
        }
    }

/**
 * Builds a [Config] from composable parameters.
 */
internal fun buildDLConfig(
    autoplay: Boolean,
    loop: Boolean,
    playMode: Mode,
    speed: Float,
    useFrameInterpolation: Boolean,
    segment: Pair<Float, Float>?,
    marker: String?,
    layout: Layout,
    themeId: String?,
    loopCount: UInt,
): Config = Config(
    autoplay = autoplay,
    loopAnimation = loop,
    mode = playMode,
    speed = speed,
    useFrameInterpolation = useFrameInterpolation,
    segment = if (segment != null) listOf(segment.first, segment.second) else emptyList(),
    backgroundColor = 0u,
    marker = marker ?: "",
    layout = layout,
    themeId = themeId ?: "",
    stateMachineId = "",
    animationId = "",
    loopCount = loopCount
)
