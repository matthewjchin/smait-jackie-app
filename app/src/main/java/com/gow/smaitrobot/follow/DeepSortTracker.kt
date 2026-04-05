package com.gow.smaitrobot.follow

import android.graphics.Rect

/**
 * DeepSORT multi-face tracker using Kalman-filtered tracks with IOU-based assignment.
 *
 * Manages KalmanTrack lifecycle: predict → match (Hungarian) → update/spawn/prune.
 * Returns only confirmed tracks (3+ consecutive hits).
 *
 * Ported from Jason's RobotController.java DeepSortTracker.
 */
class DeepSortTracker {

    private val tracks = mutableListOf<KalmanTrack>()

    fun update(detections: List<Rect>): List<KalmanTrack> {
        // 1. Predict all existing tracks
        for (t in tracks) t.predict()

        // 2. Build IOU cost matrix
        val nT = tracks.size
        val nD = detections.size
        val cost = Array(nT) { ti ->
            DoubleArray(nD) { di ->
                1.0 - iou(tracks[ti].predictedRect(), detections[di])
            }
        }

        // 3. Hungarian assignment
        val assignment = hungarian(cost, nT, nD)

        // 4. Update matched, spawn new, prune stale
        val detUsed = BooleanArray(nD)
        for (ti in 0 until nT) {
            val di = assignment[ti]
            if (di >= 0 && cost[ti][di] < (1.0 - IOU_THRESHOLD)) {
                tracks[ti].update(detections[di])
                detUsed[di] = true
            }
        }
        for (di in 0 until nD) {
            if (!detUsed[di]) tracks.add(KalmanTrack(detections[di]))
        }

        tracks.removeAll { it.misses > MAX_MISSES }

        // 5. Return confirmed tracks only
        return tracks.filter { it.confirmed }
    }

    fun clear() {
        tracks.clear()
    }

    companion object {
        private const val IOU_THRESHOLD = 0.30f
        private const val MAX_MISSES = 5

        private fun iou(a: Rect, b: Rect): Float {
            val iL = maxOf(a.left, b.left)
            val iT = maxOf(a.top, b.top)
            val iR = minOf(a.right, b.right)
            val iB = minOf(a.bottom, b.bottom)
            val inter = maxOf(0, iR - iL) * maxOf(0, iB - iT).toFloat()
            val union = a.width() * a.height() + b.width() * b.height() - inter
            return if (union <= 0) 0f else inter / union
        }

        /**
         * Full Munkres / Hungarian assignment.
         * Returns result[trackIdx] = detectionIdx, or -1 if unmatched.
         *
         * Fixes vs. original:
         *  - Pads non-square matrices with INF (not 0) so phantom cells are never preferred
         *  - Uses shortest-augmenting-path to guarantee globally optimal assignment
         *  - Gates output so tracks assigned to phantom columns return -1
         */
        private fun hungarian(cost: Array<DoubleArray>, nR: Int, nC: Int): IntArray {
            if (nR == 0 || nC == 0) return IntArray(nR) { -1 }
            val n = maxOf(nR, nC)
            val INF = 1e18

            // Pad to square with INF so phantom cells are never preferred
            val c = Array(n) { i ->
                DoubleArray(n) { j ->
                    if (i < nR && j < nC) cost[i][j] else INF
                }
            }

            // Dual variables for rows (u) and columns (v) — 1-indexed internally
            val u   = DoubleArray(n + 1)
            val v   = DoubleArray(n + 1)
            val p   = IntArray(n + 1)      // p[j] = row currently assigned to column j
            val way = IntArray(n + 1)      // augmenting path back-pointer

            for (i in 1..n) {
                p[0] = i
                var j0 = 0
                val minDist = DoubleArray(n + 1) { INF }
                val used    = BooleanArray(n + 1)

                // Dijkstra-like shortest augmenting path
                do {
                    used[j0] = true
                    val i0 = p[j0]
                    var delta = INF
                    var j1 = -1
                    for (j in 1..n) {
                        if (!used[j]) {
                            val cur = c[i0 - 1][j - 1] - u[i0] - v[j]
                            if (cur < minDist[j]) {
                                minDist[j] = cur
                                way[j] = j0
                            }
                            if (minDist[j] < delta) {
                                delta = minDist[j]
                                j1 = j
                            }
                        }
                    }
                    // Update potentials
                    for (j in 0..n) {
                        if (used[j]) { u[p[j]] += delta; v[j] -= delta }
                        else minDist[j] -= delta
                    }
                    j0 = j1
                } while (p[j0] != 0)

                // Flip augmenting path
                do {
                    val j1 = way[j0]
                    p[j0] = p[j1]
                    j0 = j1
                } while (j0 != 0)
            }

            // Read results — gate out phantom column assignments
            val result = IntArray(nR) { -1 }
            for (j in 1..n) {
                val row = p[j] - 1
                val col = j - 1
                if (row in 0 until nR && col in 0 until nC) {
                    result[row] = col
                }
            }
            return result
        }
    }
}
