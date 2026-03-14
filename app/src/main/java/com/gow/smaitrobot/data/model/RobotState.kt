package com.gow.smaitrobot.data.model

/**
 * Represents the current robot behavior state, used to drive avatar animation
 * and inform the user of what Jackie is doing.
 *
 * - [IDLE]      — Robot is in standby, no active interaction
 * - [LISTENING] — Robot is listening to the user's speech
 * - [THINKING]  — Robot is processing the user's request (LLM inference in progress)
 * - [SPEAKING]  — Robot is playing TTS audio response
 */
enum class RobotState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}
