package com.gow.smaitrobot.follow

/**
 * Simple PID controller for smooth chassis motion control.
 *
 * Ported from the original Android RobotController PidController.
 */
class PidController(
    private val kp: Double,
    private val ki: Double,
    private val kd: Double
) {
    private var integral: Double = 0.0
    private var prevError: Double = 0.0

    fun compute(error: Double, dt: Double): Double {
        integral += error * dt
        val deriv = (error - prevError) / dt
        prevError = error
        return kp * error + ki * integral + kd * deriv
    }

    fun reset() {
        integral = 0.0
        prevError = 0.0
    }
}
