package com.lottiefiles.dotlottie.core.util

/**
 * Lowercased names of the state-machine framework events that originate from
 * pointer input. Used by both the Compose modifier and the view-based widgets
 * to decide whether the animation should claim touch events for the active
 * state machine.
 */
internal val POINTER_EVENT_NAMES: Set<String> = setOf(
    "pointerup",
    "pointerdown",
    "pointerenter",
    "pointerexit",
    "pointermove",
    "click",
)
