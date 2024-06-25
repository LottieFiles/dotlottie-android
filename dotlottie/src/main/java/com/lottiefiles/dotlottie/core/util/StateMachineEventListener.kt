package com.lottiefiles.dotlottie.core.util

import com.dotlottie.dlplayer.StateMachineObserver

interface StateMachineEventListener : StateMachineObserver {
    override fun onStateEntered(enteringState: String) {}

    override fun onStateExit(leavingState: String) {}

    override fun onTransition(previousState: String, newState: String) {}
}