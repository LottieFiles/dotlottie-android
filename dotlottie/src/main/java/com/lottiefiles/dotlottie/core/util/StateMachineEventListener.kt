package com.lottiefiles.dotlottie.core.util

import com.dotlottie.dlplayer.StateMachineObserver

interface StateMachineEventListener : StateMachineObserver {
    override fun onBooleanInputValueChange(
        inputName: String,
        oldValue: Boolean,
        newValue: Boolean,
    )
    {}
    override  fun onCustomEvent(message: String) {}

    override  fun onError(message: String) {}

    override  fun onInputFired(inputName: String) {}

    override  fun onNumericInputValueChange(
        inputName: String,
        oldValue: Float,
        newValue: Float,
    ) {}

    override  fun onStart() {}

    override  fun onStateEntered(enteringState: String) {}

    override  fun onStateExit(leavingState: String) {}

    override  fun onStop() {}

    override  fun onStringInputValueChange(
        inputName: String,
        oldValue: String,
        newValue: String,
    ) {}

    override  fun onTransition(
        previousState: String,
        newState: String,
    ) {}
}