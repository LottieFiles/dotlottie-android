package com.dotlottie.dlplayer

/**
 * Observer interface for state machine events.
 * All methods are optional - implement only what you need.
 */
interface StateMachineObserver {
    fun onTransition(previousState: String, newState: String) {}
    fun onStateEntered(enteringState: String) {}
    fun onStateExit(leavingState: String) {}
    fun onCustomEvent(message: String) {}
    fun onError(message: String) {}
    fun onStart() {}
    fun onStop() {}
    fun onStringInputValueChange(inputName: String, oldValue: String, newValue: String) {}
    fun onNumericInputValueChange(inputName: String, oldValue: Float, newValue: Float) {}
    fun onBooleanInputValueChange(inputName: String, oldValue: Boolean, newValue: Boolean) {}
    fun onInputFired(inputName: String) {}
}
