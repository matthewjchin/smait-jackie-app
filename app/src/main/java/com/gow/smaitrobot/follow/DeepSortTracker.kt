package com.gow.smaitrobot.follow

import android.graphics.Rect

/**
 * DeepSORT multi-face tracker using Kalman-filtered tracks with IOU-based assignment.
 *
 * Manages KalmanTrack lifecycle: predict → match (Hungarian) → update/spawn/prune.
 * Returns only confirmed tracks (3+ consecutive hits).
 *
 * Ported from the original Android RobotController DeepSortTracker.
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
         * Simplified O(n^3) Hungarian algorithm for assignment.
         * Returns array where result[trackIdx] = detectionIdx (-1 if unmatched).
         */
        private fun hungarian(cost: Array<DoubleArray>, nR: Int, nC: Int): IntArray {
            if (nR == 0 || nC == 0) return IntArray(nR) { -1 }
            val n = maxOf(nR, nC)
            val c = Array(n) { i ->
                DoubleArray(n) { j ->
                    if (i < nR && j < nC) cost[i][j] else 0.0
                }
            }

            // Row reduction
            for (i in 0 until n) {
                val min = c[i].min()
                for (j in 0 until n) c[i][j] -= min
            }
            // Column reduction
            for (j in 0 until n) {
                var min = Double.MAX_VALUE
                for (i in 0 until n) if (c[i][j] < min) min = c[i][j]
                for (i in 0 until n) c[i][j] -= min
            }

            val starRow = IntArray(n) { -1 }
            val starCol = IntArray(n) { -1 }
            val rCover = IntArray(n)
            val cCover = IntArray(n)

            for (i in 0 until n) for (j in 0 until n) {
                if (c[i][j] == 0.0 && rCover[i] == 0 && cCover[j] == 0) {
                    starRow[i] = j; starCol[j] = i
                    rCover[i] = 1; cCover[j] = 1
                }
            }

            val result = IntArray(nR) { -1 }
            for (i in 0 until nR) {
                if (starRow[i] in 0 until nC) result[i] = starRow[i]
            }
            return result
        }
    }
}
