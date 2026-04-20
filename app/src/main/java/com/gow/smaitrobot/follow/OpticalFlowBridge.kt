package com.gow.smaitrobot.follow

import org.opencv.core.*
import org.opencv.video.Video
import org.opencv.imgproc.Imgproc

class OpticalFlowBridge {

    private var prevGray: Mat? = null
    private var trackedPoints: MatOfPoint2f? = null
    private var lastKnownRect: android.graphics.Rect? = null

    /**
     * Call every frame with the raw bitmap.
     * @param mediaPipeRect  Face rect from MediaPipe this frame, or null if no detection.
     * @return               Best available face rect (MediaPipe or flow-predicted).
     */
    fun update(bitmap: android.graphics.Bitmap, mediaPipeRect: android.graphics.Rect?): android.graphics.Rect? {

        // Convert bitmap to grayscale Mat
        val rgba = Mat()
        val gray = Mat()
        org.opencv.android.Utils.bitmapToMat(bitmap, rgba)
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)

        val prev = prevGray
        val pts  = trackedPoints

        var result: android.graphics.Rect? = null

        if (mediaPipeRect != null) {
            // MediaPipe has a detection — reinitialise flow points from face corners
            trackedPoints = cornersFromRect(mediaPipeRect)
            lastKnownRect = mediaPipeRect
            result = mediaPipeRect

        } else if (prev != null && pts != null && pts.rows() > 0) {
            // No MediaPipe detection — use optical flow to predict where face moved
            val nextPts  = MatOfPoint2f()
            val status   = MatOfByte()
            val err      = MatOfFloat()

            Video.calcOpticalFlowPyrLK(prev, gray, pts, nextPts, status, err)

            val goodPts = filterGoodPoints(nextPts, status)
            if (goodPts.isNotEmpty()) {
                result = rectFromPoints(goodPts)
                trackedPoints = MatOfPoint2f(*goodPts.toTypedArray())
                lastKnownRect = result
            } else {
                // Flow lost too many points — clear so FSM can trigger scan
                trackedPoints = null
                lastKnownRect = null
            }
        }

        // Roll gray frame forward
        prevGray = gray
        return result
    }

    fun reset() {
        prevGray = null
        trackedPoints = null
        lastKnownRect = null
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun cornersFromRect(r: android.graphics.Rect): MatOfPoint2f {
        // Use a grid of points across the face region for stable flow
        val pts = mutableListOf<Point>()
        for (x in listOf(r.left + r.width()/4, r.centerX(), r.right - r.width()/4)) {
            for (y in listOf(r.top + r.height()/4, r.centerY(), r.bottom - r.height()/4)) {
                pts.add(Point(x.toDouble(), y.toDouble()))
            }
        }
        return MatOfPoint2f(*pts.toTypedArray())
    }

    private fun filterGoodPoints(pts: MatOfPoint2f, status: MatOfByte): List<Point> {
        val ptArray     = pts.toArray()
        val statusArray = status.toArray()
        return ptArray.filterIndexed { i, _ -> statusArray[i].toInt() == 1 }
    }

    private fun rectFromPoints(pts: List<Point>): android.graphics.Rect {
        val minX = pts.minOf { it.x }.toInt()
        val maxX = pts.maxOf { it.x }.toInt()
        val minY = pts.minOf { it.y }.toInt()
        val maxY = pts.maxOf { it.y }.toInt()
        return android.graphics.Rect(minX, minY, maxX, maxY)
    }
}
