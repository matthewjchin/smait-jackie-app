package com.gow.smaitrobot.follow

import org.opencv.core.*
import org.opencv.video.BackgroundSubtractorMOG2
import org.opencv.video.Video
import org.opencv.imgproc.Imgproc

class MotionDetector {

    private val mog2: BackgroundSubtractorMOG2 = Video.createBackgroundSubtractorMOG2(
        500,    // history frames
        16.0,   // variance threshold
        false   // no shadow detection needed
    )

    /**
     * Returns the horizontal centre-of-mass of motion (0.0 = left, 1.0 = right),
     * or null if no significant motion detected.
     */
    fun detectMotionDirection(bitmap: android.graphics.Bitmap): Double? {
        val rgba = Mat()
        val gray = Mat()
        val fgMask = Mat()
        org.opencv.android.Utils.bitmapToMat(bitmap, rgba)
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)

        mog2.apply(gray, fgMask)

        // Count foreground pixels in left vs right half
        val width = fgMask.cols()
        val leftRoi  = fgMask.submat(0, fgMask.rows(), 0, width / 2)
        val rightRoi = fgMask.submat(0, fgMask.rows(), width / 2, width)

        val leftCount  = Core.countNonZero(leftRoi).toDouble()
        val rightCount = Core.countNonZero(rightRoi).toDouble()
        val total = leftCount + rightCount

        if (total < MIN_MOTION_PIXELS) return null   // not enough motion

        return rightCount / total   // > 0.5 means motion is to the right
    }

    fun reset() = mog2.apply(Mat(), Mat())  // flush history

    companion object {
        private const val MIN_MOTION_PIXELS = 500
    }
}