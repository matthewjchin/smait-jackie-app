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


//        // Simplified O(n³) Hungarian algorithm
//        private fun hungarian(cost: Array<DoubleArray>, nR: Int, nC: Int): IntArray {
//            if (nR == 0 || nC == 0) return IntArray(nR) { -1 }
//            val n = max(nR, nC)
//
//            // Pad cost matrix to square
//            val c = Array(n) { i -> DoubleArray(n) { j ->
//                if (i < nR && j < nC) cost[i][j] else 0.0
//            }}
//
//            // Row and column reduction
//            for (i in 0 until n) {
//                val min = c[i].min()
//                for (j in 0 until n) c[i][j] -= min
//            }
//            for (j in 0 until n) {
//                val min = (0 until n).minOf { c[it][j] }
//                for (i in 0 until n) c[i][j] -= min
//            }
//
//            // Star zeros greedily
//            val starRow = IntArray(n) { -1 }
//            val starCol = IntArray(n) { -1 }
//            val rCover  = IntArray(n); val cCover = IntArray(n)
//
//            for (i in 0 until n) for (j in 0 until n)
//                if (c[i][j] == 0.0 && rCover[i] == 0 && cCover[j] == 0) {
//                    starRow[i] = j; starCol[j] = i
//                    rCover[i]  = 1; cCover[j]  = 1
//                }
//
//            return IntArray(nR) { i ->
//                val j = starRow[i]
//                if (j in 0 until nC) j else -1
//            }
//        }

        /**
         * Hungarian assignment using the shortest augmenting path algorithm (O(n³)).
         * Returns result[trackIdx] = detectionIdx, or -1 if unmatched.
         */
        private fun hungarian(cost: Array<DoubleArray>, nR: Int, nC: Int): IntArray {
            if (nR == 0) return IntArray(0)
            if (nC == 0) return IntArray(nR) { -1 }
            val n = maxOf(nR, nC)
            val INF = 1e9

            // Pad to square and use 1-based indexing for the algorithm
            val c = Array(n + 1) { i ->
                DoubleArray(n + 1) { j ->
                    if (i > 0 && j > 0 && i <= nR && j <= nC) cost[i - 1][j - 1] else if (i > 0 && j > 0) INF else 0.0
                }
            }

            val u = DoubleArray(n + 1)
            val v = DoubleArray(n + 1)
            val p = IntArray(n + 1)
            val way = IntArray(n + 1)

            for (i in 1..n) {
                p[0] = i
                var j0 = 0
                val minV = DoubleArray(n + 1) { INF }
                val used = BooleanArray(n + 1)
                do {
                    used[j0] = true
                    val i0 = p[j0]
                    var delta = INF
                    var j1 = 0
                    for (j in 1..n) {
                        if (!used[j]) {
                            val cur = c[i0][j] - u[i0] - v[j]
                            if (cur < minV[j]) {
                                minV[j] = cur
                                way[j] = j0
                            }
                            if (minV[j] < delta) {
                                delta = minV[j]
                                j1 = j
                            }
                        }
                    }
                    // If no valid edge found, pick first unused column to avoid infinite loop
                    if (j1 == 0) {
                        for (j in 1..n) if (!used[j]) { j1 = j; break }
                    }
                    for (j in 0..n) {
                        if (used[j]) {
                            u[p[j]] += delta
                            v[j] -= delta
                        } else {
                            minV[j] -= delta
                        }
                    }
                    j0 = j1
                } while (p[j0] != 0)
                do {
                    val j1 = way[j0]
                    p[j0] = p[j1]
                    j0 = j1
                } while (j0 != 0)
            }

            val result = IntArray(nR) { -1 }
            for (j in 1..n) {
                if (p[j] > 0 && p[j] <= nR && j <= nC) {
                    result[p[j] - 1] = j - 1
                }
            }
            return result
        }
    }
}
