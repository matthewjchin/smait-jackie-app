package com.gow.smaitrobot.follow

/**
 * Simple PID controller for smooth chassis motion control.
 *
 * Ported from the original Android RobotController PidController.
 */
class PidController(
    private val kp: Double,
    private val ki: Double,
    private val kd: Double,
    private val integralLimit: Double = 50.0   // ← new: clamp integral
) {
    private var integral: Double = 0.0
    private var prevError: Double = 0.0

    fun compute(error: Double, dt: Double): Double {
        integral = (integral + error * dt).coerceIn(-integralLimit, integralLimit) // ← clamped
        val deriv = (error - prevError) / dt
        prevError = error
        return kp * error + ki * integral + kd * deriv
    }

    fun reset() {
        integral = 0.0
        prevError = 0.0
    }
}