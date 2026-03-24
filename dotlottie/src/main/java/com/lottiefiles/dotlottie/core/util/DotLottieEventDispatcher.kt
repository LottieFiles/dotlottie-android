package com.lottiefiles.dotlottie.core.util

import com.dotlottie.dlplayer.DotLottiePlayer
import com.dotlottie.dlplayer.DotLottiePlayerEvent
import com.dotlottie.dlplayer.StateMachinePlayerEvent

/**
 * Dispatches a [DotLottiePlayerEvent] to event listeners.
 *
 * @param onStateChange optional callback for state transitions (used by Compose controller)
 */
fun dispatchPlayerEvent(
    event: DotLottiePlayerEvent,
    eventListeners: List<DotLottieEventListener>,
    onStateChange: ((DotLottiePlayerEvent) -> Unit)? = null,
) {
    onStateChange?.invoke(event)
    when (event) {
        is DotLottiePlayerEvent.Load ->
            eventListeners.forEach(DotLottieEventListener::onLoad)
        is DotLottiePlayerEvent.LoadError ->
            eventListeners.forEach { listener ->
                listener.onLoadError()
                listener.onLoadError(Throwable("Load error occurred"))
            }
        is DotLottiePlayerEvent.Play ->
            eventListeners.forEach(DotLottieEventListener::onPlay)
        is DotLottiePlayerEvent.Pause ->
            eventListeners.forEach(DotLottieEventListener::onPause)
        is DotLottiePlayerEvent.Stop ->
            eventListeners.forEach(DotLottieEventListener::onStop)
        is DotLottiePlayerEvent.Frame ->
            eventListeners.forEach { it.onFrame(event.frameNo) }
        is DotLottiePlayerEvent.Render ->
            eventListeners.forEach { it.onRender(event.frameNo) }
        is DotLottiePlayerEvent.Loop ->
            eventListeners.forEach { it.onLoop(event.loopCount.toInt()) }
        is DotLottiePlayerEvent.Complete ->
            eventListeners.forEach(DotLottieEventListener::onComplete)
    }
}

/**
 * Dispatches a [StateMachinePlayerEvent] to state machine listeners.
 */
fun dispatchStateMachineEvent(
    event: StateMachinePlayerEvent,
    listeners: List<StateMachineEventListener>,
) {
    when (event) {
        is StateMachinePlayerEvent.Start ->
            listeners.forEach { it.onStart() }
        is StateMachinePlayerEvent.Stop ->
            listeners.forEach { it.onStop() }
        is StateMachinePlayerEvent.Transition ->
            listeners.forEach { it.onTransition(event.previousState, event.newState) }
        is StateMachinePlayerEvent.StateEntered ->
            listeners.forEach { it.onStateEntered(event.state) }
        is StateMachinePlayerEvent.StateExit ->
            listeners.forEach { it.onStateExit(event.state) }
        is StateMachinePlayerEvent.CustomEvent ->
            listeners.forEach { it.onCustomEvent(event.message) }
        is StateMachinePlayerEvent.Error ->
            listeners.forEach { it.onError(event.message) }
        is StateMachinePlayerEvent.StringInputChange ->
            listeners.forEach { it.onStringInputValueChange(event.name, event.oldValue, event.newValue) }
        is StateMachinePlayerEvent.NumericInputChange ->
            listeners.forEach { it.onNumericInputValueChange(event.name, event.oldValue, event.newValue) }
        is StateMachinePlayerEvent.BooleanInputChange ->
            listeners.forEach { it.onBooleanInputValueChange(event.name, event.oldValue, event.newValue) }
        is StateMachinePlayerEvent.InputFired ->
            listeners.forEach { it.onInputFired(event.name) }
    }
}

/**
 * Parses and handles internal state machine events (e.g. OpenUrl).
 */
fun handleInternalEvent(message: String, onOpenUrl: ((String) -> Unit)?) {
    if (message.startsWith("OpenUrl: ")) {
        val payload = message.substringAfter("OpenUrl: ")
        val url = if (payload.contains(" | Target: ")) {
            payload.substringBefore(" | Target: ")
        } else {
            payload
        }
        onOpenUrl?.invoke(url)
    }
}

/**
 * Polls all pending events from the player and dispatches them.
 */
fun pollAndDispatchAllEvents(
    player: DotLottiePlayer,
    eventListeners: List<DotLottieEventListener>,
    stateMachineListeners: List<StateMachineEventListener>,
    onStateChange: ((DotLottiePlayerEvent) -> Unit)? = null,
    onOpenUrl: ((String) -> Unit)? = null,
) {
    var event = player.pollEvent()
    while (event != null) {
        dispatchPlayerEvent(event, eventListeners, onStateChange)
        event = player.pollEvent()
    }

    var smEvent = player.stateMachinePollEvent()
    while (smEvent != null) {
        dispatchStateMachineEvent(smEvent, stateMachineListeners)
        smEvent = player.stateMachinePollEvent()
    }

    var internalEvent = player.stateMachinePollInternalEvent()
    while (internalEvent != null) {
        handleInternalEvent(internalEvent, onOpenUrl)
        internalEvent = player.stateMachinePollInternalEvent()
    }
}
