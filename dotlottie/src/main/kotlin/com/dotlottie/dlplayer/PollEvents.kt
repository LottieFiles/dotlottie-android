package com.dotlottie.dlplayer

/**
 * SDL-style player events for DotLottie player.
 * These events are polled from the native player event queue.
 */
sealed class DotLottiePlayerEvent {
    // Player lifecycle events
    object Load : DotLottiePlayerEvent()
    object LoadError : DotLottiePlayerEvent()

    // Playback control events
    object Play : DotLottiePlayerEvent()
    object Pause : DotLottiePlayerEvent()
    object Stop : DotLottiePlayerEvent()

    // Frame events
    data class Frame(val frameNo: Float) : DotLottiePlayerEvent()
    data class Render(val frameNo: Float) : DotLottiePlayerEvent()

    // Loop/complete events
    data class Loop(val loopCount: UInt) : DotLottiePlayerEvent()
    object Complete : DotLottiePlayerEvent()

    companion object {
        // Event type constants matching C API
        const val TYPE_LOAD = 0
        const val TYPE_LOAD_ERROR = 1
        const val TYPE_PLAY = 2
        const val TYPE_PAUSE = 3
        const val TYPE_STOP = 4
        const val TYPE_FRAME = 5
        const val TYPE_RENDER = 6
        const val TYPE_LOOP = 7
        const val TYPE_COMPLETE = 8

        /**
         * Parse event from JNI array [eventType, data1, data2]
         */
        fun fromJniArray(arr: IntArray?): DotLottiePlayerEvent? {
            if (arr == null || arr.size < 3) return null

            return when (arr[0]) {
                TYPE_LOAD -> Load
                TYPE_LOAD_ERROR -> LoadError
                TYPE_PLAY -> Play
                TYPE_PAUSE -> Pause
                TYPE_STOP -> Stop
                TYPE_FRAME -> {
                    // Convert int bits back to float
                    val frameNo = Float.fromBits(arr[1])
                    Frame(frameNo)
                }
                TYPE_RENDER -> {
                    val frameNo = Float.fromBits(arr[1])
                    Render(frameNo)
                }
                TYPE_LOOP -> Loop(arr[1].toUInt())
                TYPE_COMPLETE -> Complete
                else -> null
            }
        }
    }
}

/**
 * State machine events for DotLottie state machine.
 * These events are polled from the state machine event queue.
 */
sealed class StateMachinePlayerEvent {
    // Lifecycle
    object Start : StateMachinePlayerEvent()
    object Stop : StateMachinePlayerEvent()

    // State transitions
    data class Transition(val previousState: String, val newState: String) : StateMachinePlayerEvent()
    data class StateEntered(val state: String) : StateMachinePlayerEvent()
    data class StateExit(val state: String) : StateMachinePlayerEvent()

    // Custom events and errors
    data class CustomEvent(val message: String) : StateMachinePlayerEvent()
    data class Error(val message: String) : StateMachinePlayerEvent()

    // Input value changes
    data class StringInputChange(val name: String, val oldValue: String, val newValue: String) : StateMachinePlayerEvent()
    data class NumericInputChange(val name: String, val oldValue: Float, val newValue: Float) : StateMachinePlayerEvent()
    data class BooleanInputChange(val name: String, val oldValue: Boolean, val newValue: Boolean) : StateMachinePlayerEvent()

    // Event input fired
    data class InputFired(val name: String) : StateMachinePlayerEvent()

    companion object {
        // Event type constants matching C API
        const val TYPE_START = 0
        const val TYPE_STOP = 1
        const val TYPE_TRANSITION = 2
        const val TYPE_STATE_ENTERED = 3
        const val TYPE_STATE_EXIT = 4
        const val TYPE_CUSTOM_EVENT = 5
        const val TYPE_ERROR = 6
        const val TYPE_STRING_INPUT_CHANGE = 7
        const val TYPE_NUMERIC_INPUT_CHANGE = 8
        const val TYPE_BOOLEAN_INPUT_CHANGE = 9
        const val TYPE_INPUT_FIRED = 10

        /**
         * Parse event from JNI string array
         * [eventType, str1, str2, str3, numOld, numNew, boolOld, boolNew]
         */
        fun fromJniArray(arr: Array<String?>?): StateMachinePlayerEvent? {
            if (arr == null || arr.isEmpty()) return null

            val eventType = arr[0]?.toIntOrNull() ?: return null

            return when (eventType) {
                TYPE_START -> Start
                TYPE_STOP -> Stop
                TYPE_TRANSITION -> Transition(arr[1] ?: "", arr[2] ?: "")
                TYPE_STATE_ENTERED -> StateEntered(arr[1] ?: "")
                TYPE_STATE_EXIT -> StateExit(arr[1] ?: "")
                TYPE_CUSTOM_EVENT -> CustomEvent(arr[1] ?: "")
                TYPE_ERROR -> Error(arr[1] ?: "")
                TYPE_STRING_INPUT_CHANGE -> StringInputChange(arr[1] ?: "", arr[2] ?: "", arr[3] ?: "")
                TYPE_NUMERIC_INPUT_CHANGE -> NumericInputChange(
                    arr[1] ?: "",
                    arr[4]?.toFloatOrNull() ?: 0f,
                    arr[5]?.toFloatOrNull() ?: 0f
                )
                TYPE_BOOLEAN_INPUT_CHANGE -> BooleanInputChange(
                    arr[1] ?: "",
                    arr[6] == "true",
                    arr[7] == "true"
                )
                TYPE_INPUT_FIRED -> InputFired(arr[1] ?: "")
                else -> null
            }
        }
    }
}
