package com.gow.smaitrobot.follow

import android.graphics.Rect
import kotlin.collections.get

/**
 * Kalman-filtered face track for DeepSORT.
 *
 * State vector: [cx, cy, w, h, vx, vy, vw, vh]
 * Constant-velocity motion model with 8-state Kalman filter.
 *
 * Ported from the original Android RobotController KalmanTrack.
 */
class KalmanTrack(detection: Rect) {

    val id: Int = nextId++

    var hits: Int = 1
        private set
    var misses: Int = 0
        private set
    var confirmed: Boolean = false
        private set

    // State: [cx, cy, w, h, vx, vy, vw, vh]
    private val x = DoubleArray(8)
    private val p = Array(8) { DoubleArray(8) }

    init {
        x[0] = detection.centerX().toDouble()
        x[1] = detection.centerY().toDouble()
        x[2] = detection.width().toDouble()
        x[3] = detection.height().toDouble()
        for (i in 0 until 8) p[i][i] = if (i < 4) 10.0 else 1000.0
    }


    /** Estimated horizontal velocity in pixels/frame */
    fun velocityX(): Double = x[4]

    /** Estimated vertical velocity in pixels/frame */
    fun velocityY(): Double = x[5]


    fun predict() {
        val xp = matVec(F, x)
        System.arraycopy(xp, 0, x, 0, 8)
        val fp = matMul(matMul(F, p), transpose(F))
        val q = processNoise()
        val pn = matAdd(fp, q)
        for (i in 0 until 8) System.arraycopy(pn[i], 0, p[i], 0, 8)
        misses++
    }

    fun update(detection: Rect) {
        val z = doubleArrayOf(
            detection.centerX().toDouble(),
            detection.centerY().toDouble(),
            detection.width().toDouble(),
            detection.height().toDouble()
        )
        val y = vecSub(z, matVec(H, x))
        val s = matAdd(matMul(matMul(H, p), transpose(H)), measureNoise())
        val k = matMul(matMul(p, transpose(H)), inv4(s))
        val xn = vecAdd(x, matVec(k, y))
        System.arraycopy(xn, 0, x, 0, 8)
        val eye = eye(8)
        val pn = matMul(matSub(eye, matMul(k, H)), p)
        for (i in 0 until 8) System.arraycopy(pn[i], 0, p[i], 0, 8)
        misses = 0
        hits++
        if (hits >= 3) confirmed = true
    }

    fun predictedRect(): Rect {
        val cx = x[0].toInt()
        val cy = x[1].toInt()
        val w = x[2].toInt().coerceAtLeast(1)
        val h = x[3].toInt().coerceAtLeast(1)
        return Rect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
    }

    companion object {
        private var nextId = 1

        // Constant-velocity transition matrix
        private val F = arrayOf(
            doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0),
            doubleArrayOf(0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0),
            doubleArrayOf(0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0),
            doubleArrayOf(0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0),
            doubleArrayOf(0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0),
            doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0),
            doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0),
            doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0)
        )

        // Observation matrix [cx, cy, w, h]
        private val H = arrayOf(
            doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
            doubleArrayOf(0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
            doubleArrayOf(0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0),
            doubleArrayOf(0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0)
        )

        private fun processNoise(): Array<DoubleArray> {
            val q = Array(8) { DoubleArray(8) }
            val diag = doubleArrayOf(1.0, 1.0, 1.0, 1.0, 0.01, 0.01, 0.01, 0.01)
            for (i in 0 until 8) q[i][i] = diag[i]
            return q
        }

        private fun measureNoise(): Array<DoubleArray> {
            val r = Array(4) { DoubleArray(4) }
            val diag = doubleArrayOf(1.0, 1.0, 10.0, 10.0)
            for (i in 0 until 4) r[i][i] = diag[i]
            return r
        }
    }
}

// ── Matrix math utilities ──────────────────────────────────────────────────

internal fun matVec(m: Array<DoubleArray>, v: DoubleArray): DoubleArray {
    val r = DoubleArray(m.size)
    for (i in m.indices)
        for (j in v.indices) r[i] += m[i][j] * v[j]
    return r
}

internal fun matMul(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
    val m = a.size; val n = b[0].size; val k = b.size
    val c = Array(m) { DoubleArray(n) }
    for (i in 0 until m) for (j in 0 until n)
        for (p in 0 until k) c[i][j] += a[i][p] * b[p][j]
    return c
}

internal fun transpose(a: Array<DoubleArray>): Array<DoubleArray> {
    val t = Array(a[0].size) { DoubleArray(a.size) }
    for (i in a.indices) for (j in a[0].indices) t[j][i] = a[i][j]
    return t
}

internal fun matAdd(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
    val c = Array(a.size) { DoubleArray(a[0].size) }
    for (i in a.indices) for (j in a[0].indices) c[i][j] = a[i][j] + b[i][j]
    return c
}

internal fun matSub(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
    val c = Array(a.size) { DoubleArray(a[0].size) }
    for (i in a.indices) for (j in a[0].indices) c[i][j] = a[i][j] - b[i][j]
    return c
}

internal fun vecAdd(a: DoubleArray, b: DoubleArray): DoubleArray {
    return DoubleArray(a.size) { a[it] + b[it] }
}

internal fun vecSub(a: DoubleArray, b: DoubleArray): DoubleArray {
    return DoubleArray(a.size) { a[it] - b[it] }
}

internal fun eye(n: Int): Array<DoubleArray> {
    val m = Array(n) { DoubleArray(n) }
    for (i in 0 until n) m[i][i] = 1.0
    return m
}

internal fun inv4(m: Array<DoubleArray>): Array<DoubleArray> {
    val n = 4
    val a = Array(n) { DoubleArray(2 * n) }
    for (i in 0 until n) {
        for (j in 0 until n) a[i][j] = m[i][j]
        a[i][i + n] = 1.0
    }
    for (i in 0 until n) {
        var piv = a[i][i]; if (kotlin.math.abs(piv) < 1e-12) piv = 1e-12
        for (j in 0 until 2 * n) a[i][j] /= piv
        for (k in 0 until n) {
            if (k == i) continue
            val f = a[k][i]
            for (j in 0 until 2 * n) a[k][j] -= f * a[i][j]
        }
    }
    val inv = Array(n) { DoubleArray(n) }
    for (i in 0 until n) for (j in 0 until n) inv[i][j] = a[i][j + n]
    return inv
}