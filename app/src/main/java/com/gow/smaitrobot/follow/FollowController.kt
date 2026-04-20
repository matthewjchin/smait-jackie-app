package com.gow.smaitrobot.follow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import org.json.JSONObject
import java.util.concurrent.Executors
import org.opencv.core.*
import org.opencv.imgproc.Imgproc


/**
 * Person-following robot controller using MediaPipe face detection + DeepSORT tracking.
 *
 * Runs entirely on-device (no server needed). Uses the existing ChassisProxy
 * to send cmd_vel commands to Jackie's chassis via rosbridge.
 *
 * Behaviours (FSM):
 * - FOLLOW:    Face detected within range -> track with PID
 * - SCAN:      No face / out of range -> rotate 45 deg CCW, retry
 * - OBSTACLE:  Obstacle ahead -> rotate 90 deg CW, resume
 * - COLLISION: Object < 0.1m -> stop 3s, rotate 45 deg CW
 *
 * Ported from the original Android RobotController, integrated with Jackie app architecture.
 *
 * @param chassisSender Callback to send rosbridge JSON through ChassisProxy.
 *                      Expects a cmd_vel Twist JSON string.
 */
class FollowController(
    private val chassisSender: (String) -> Unit
) {
    companion object {
        private const val TAG = "FollowController"

        // Thresholds (metres)
        private const val FOLLOW_DISTANCE_M = 5.0
        private const val COLLISION_DISTANCE_M = 0.33
        private const val TARGET_FOLLOW_DISTANCE_M = 0.8
        private const val PAN_FF_GAIN = 0.002

        private var lastTargetVelX: Double = 0.0

        // Camera geometry
        private const val FRAME_WIDTH_PX = 640
        private const val FOCAL_LENGTH_PX = 600.0
        private const val FACE_WIDTH_M = 0.165

        // Rotation speeds (rad/s)
        private const val TURN_SPEED_RAD_S = 0.6f
        private val DEG_45_RAD = Math.PI / 4.0
        private val DEG_90_RAD = Math.PI / 2.0
        private var scanDirection = 1f

        private const val COLLISION_PAUSE_MS = 3_000L
    }

    // Sub-systems
    private var mediaPipe: FaceLandmarker? = null
    private val deepSort = DeepSortTracker()
    private val motionDetector = MotionDetector()
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    // Tracking state
    private var targetTrackId = -1
    private var running = false
    private var cameraProvider: ProcessCameraProvider? = null

    // FSM
    private var fsmState = FsmState.SCAN_ROTATE
    private var manoeuvreEndMs = 0L
    private val panPid = PidController(0.003, 0.0001, 0.001)
    private val distPid = PidController(0.5, 0.0, 0.1)

    var currentState: FsmState = FsmState.SCAN_ROTATE
        private set
    var currentDistance: Double = Double.MAX_VALUE
        private set
    var onStateChanged: ((FsmState, Double) -> Unit)? = null

    private var lastFrameMs = 0L

    enum class FsmState {
        FOLLOWING,
        SCAN_ROTATE,
        OBSTACLE_TURN,
        COLLISION_STOP,
        COLLISION_TURN,
        CLEAR_CHECK
    }

    fun start(context: Context, lifecycleOwner: LifecycleOwner) {
        if (running) return
        running = true
        initMediaPipe(context)
        startCamera(context, lifecycleOwner)
        enterState(FsmState.SCAN_ROTATE)
        Log.i(TAG, "Follow mode started")
    }

    fun stop() {
        running = false
        sendVelocity(0f, 0f)
        cameraProvider?.unbindAll()
        cameraProvider = null
        mediaPipe?.close()
        mediaPipe = null
        deepSort.clear()
        targetTrackId = -1
        panPid.reset()
        distPid.reset()
        Log.i(TAG, "Follow mode stopped")
    }

    private fun initMediaPipe(context: Context) {
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(4)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener(this::onMediaPipeResult)
            .setErrorListener { e -> Log.e(TAG, "MediaPipe error: $e") }
            .build()
        mediaPipe = FaceLandmarker.createFromOptions(context, options)
    }

    private fun startCamera(context: Context, lifecycleOwner: LifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(analysisExecutor, this::analyseFrame)
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.Builder()
                        .addCameraFilter { cameraInfos ->
                            cameraInfos.firstOrNull()?.let { listOf(it) } ?: cameraInfos
                        }
                        .build(),
                    analysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private var latestBitmap: Bitmap? = null

    private fun analyseFrame(imageProxy: ImageProxy) {
        if (!running) {
            imageProxy.close()
            return
        }
        try {
            val bmp = enhanceFrame(imageProxy.toBitmap())
            latestBitmap = bmp
            val mpImg = BitmapImageBuilder(bmp).build()
            val tsMs = imageProxy.imageInfo.timestamp / 1_000_000L
            mediaPipe?.detectAsync(mpImg, tsMs)
        } catch (e: Exception) {
            Log.w(TAG, "Frame analysis error", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun enhanceFrame(bitmap: Bitmap): Bitmap {
        val rgba = Mat()
        org.opencv.android.Utils.bitmapToMat(bitmap, rgba)
        val lab = Mat()
        Imgproc.cvtColor(rgba, lab, Imgproc.COLOR_RGBA2RGB)
        Imgproc.cvtColor(lab, lab, Imgproc.COLOR_RGB2Lab)
        val channels = mutableListOf<Mat>()
        Core.split(lab, channels)
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        clahe.apply(channels[0], channels[0])
        Core.merge(channels, lab)
        Imgproc.cvtColor(lab, rgba, Imgproc.COLOR_Lab2RGB)
        Imgproc.cvtColor(rgba, rgba, Imgproc.COLOR_RGB2RGBA)
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(rgba, result)
        return result
    }

    private val flowBridge = OpticalFlowBridge()

    private fun onMediaPipeResult(result: FaceLandmarkerResult, image: MPImage) {
        if (!running) return
        val detections = result.faceLandmarks().map { face ->
            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (lm in face) {
                val px = lm.x() * image.width
                val py = lm.y() * image.height
                if (px < minX) minX = px; if (px > maxX) maxX = px
                if (py < minY) minY = py; if (py > maxY) maxY = py
            }
            Rect(minX.toInt(), minY.toInt(), maxX.toInt(), maxY.toInt())
        }

        val mediaPipeRect = detections.firstOrNull()
        val bitmap = latestBitmap ?: return
        val bestRect = flowBridge.update(bitmap, mediaPipeRect)
        val activeDetections = if (detections.isEmpty() && bestRect != null) listOf(bestRect) else detections
        val confirmed = deepSort.update(activeDetections)

        if (targetTrackId == -1 && confirmed.isNotEmpty()) {
            targetTrackId = confirmed.maxByOrNull { it.predictedRect().width() }!!.id
        }

        val targetTrack = confirmed.firstOrNull { it.id == targetTrackId }
        if (targetTrack == null) targetTrackId = -1
        else lastTargetVelX = targetTrack.velocityX()

        val targetRect = targetTrack?.predictedRect()
        val distanceM = if (targetRect != null) estimateDistance(targetRect.width()) else Double.MAX_VALUE
        mainHandler.post { tick(targetRect, distanceM, targetTrack) }

        Log.d(TAG, "Detections: ${detections.size}, " +
                "Confirmed tracks: ${confirmed.size}, Target ID: $targetTrackId, Dist: ${"%.2f".format(distanceM)}m")

    }

    private fun estimateDistance(faceWidthPx: Int): Double {
        if (faceWidthPx <= 0) return Double.MAX_VALUE
        return (FACE_WIDTH_M * FOCAL_LENGTH_PX) / faceWidthPx
    }

    private fun tick(faceBounds: Rect?, distM: Double, targetTrack: KalmanTrack?) {
        if (!running) return
        currentDistance = distM

        // New: Aggressively check for face during any rotation/scanning state to snap to Following
        if (faceBounds != null && distM <= FOLLOW_DISTANCE_M) {
            if (fsmState == FsmState.SCAN_ROTATE || fsmState == FsmState.CLEAR_CHECK) {
                enterState(FsmState.FOLLOWING)
                return
            }
        }

        // If no face is found, use motion detector to bias the search direction
        if (faceBounds == null && fsmState == FsmState.SCAN_ROTATE) {
            latestBitmap?.let { bmp ->
                val motionCenter = motionDetector.detectMotionDirection(bmp)
                if (motionCenter != null) {
                    // motionCenter > 0.5 means motion is on the right
                    scanDirection = if (motionCenter > 0.5) -1f else 1f
                    Log.d(TAG, "Motion detected! Biasing scan direction: $scanDirection")
                }
            }
        }

        when (fsmState) {
            FsmState.SCAN_ROTATE -> {
                if (SystemClock.elapsedRealtime() < manoeuvreEndMs) {
                    sendVelocity(0f, TURN_SPEED_RAD_S * scanDirection)
                } else {
                    sendVelocity(0f, 0f)
                    if (faceBounds != null && distM <= FOLLOW_DISTANCE_M) {
                        enterState(FsmState.FOLLOWING)
                    } else {
                        startManoeuvre(DEG_45_RAD, scanDirection)
                    }
                }
            }
            FsmState.OBSTACLE_TURN -> {
                if (SystemClock.elapsedRealtime() < manoeuvreEndMs) {
                    sendVelocity(0f, -TURN_SPEED_RAD_S)
                } else {
                    sendVelocity(0f, 0f)
                    enterState(FsmState.CLEAR_CHECK)
                }
            }
            FsmState.CLEAR_CHECK -> enterState(FsmState.FOLLOWING)
            FsmState.COLLISION_STOP -> {
                sendVelocity(0f, 0f)
                if (SystemClock.elapsedRealtime() >= manoeuvreEndMs) {
                    enterState(FsmState.COLLISION_TURN)
                }
            }
            FsmState.COLLISION_TURN -> {
                if (SystemClock.elapsedRealtime() < manoeuvreEndMs) {
                    sendVelocity(0f, -TURN_SPEED_RAD_S)
                } else {
                    sendVelocity(0f, 0f)
                    enterState(FsmState.CLEAR_CHECK)
                }
            }
            FsmState.FOLLOWING -> {
                if (faceBounds == null || distM > FOLLOW_DISTANCE_M) {
                    enterState(FsmState.SCAN_ROTATE)
                } else {
                    driveTowardFace(faceBounds, distM, targetTrack!!)
                }
            }
        }
    }

    private fun driveTowardFace(face: Rect, distM: Double, targetTrack: KalmanTrack) {
        val nowMs = SystemClock.elapsedRealtime()
        val dt = if (lastFrameMs == 0L) 0.033 else ((nowMs - lastFrameMs) / 1000.0).coerceIn(0.01, 0.1)
        lastFrameMs = nowMs
        val dx = face.centerX() - FRAME_WIDTH_PX / 2.0
        val distError = distM - TARGET_FOLLOW_DISTANCE_M

        // Invert dx for angZ: face on right (dx > 0) -> turn right (angZ < 0)
        var angZ = -panPid.compute(dx, dt).coerceIn(-0.8, 0.8).toFloat()
        var linX = distPid.compute(distError, dt).coerceIn(-0.3, 0.3).toFloat()

        // Feed-forward also needs inversion to match angZ
        val velFeedForward = (-targetTrack.velocityX() * PAN_FF_GAIN).coerceIn(-0.3, 0.3).toFloat()
        angZ += velFeedForward

        if (distM < COLLISION_DISTANCE_M + 0.05) linX = 0f
        
        Log.d(TAG, "Drive: dist=%.2f, err=%.2f, linX=%.2f, angZ=%.2f".format(distM, distError, linX, angZ))
        sendVelocity(linX, angZ)
    }

    private fun enterState(next: FsmState) {
        fsmState = next
        currentState = next
        val now = SystemClock.elapsedRealtime()
        when (next) {
            FsmState.SCAN_ROTATE -> {
                val scanDir = if (lastTargetVelX >= 0) -1f else 1f
                startManoeuvre(DEG_45_RAD, scanDir)
            }
            FsmState.OBSTACLE_TURN -> {
                sendVelocity(0f, 0f)
                startManoeuvre(DEG_90_RAD)
            }
            FsmState.COLLISION_STOP -> {
                sendVelocity(0f, 0f)
                manoeuvreEndMs = now + COLLISION_PAUSE_MS
            }
            FsmState.COLLISION_TURN -> {
                sendVelocity(0f, 0f) // ensure stop before turn
                startManoeuvre(DEG_45_RAD)
            }
            else -> {}
        }
        onStateChanged?.invoke(next, currentDistance)
    }

    private fun startManoeuvre(angleRad: Double, dir: Float = 1f) {
        scanDirection = dir
        val durationMs = ((angleRad / TURN_SPEED_RAD_S) * 1000).toLong()
        manoeuvreEndMs = SystemClock.elapsedRealtime() + durationMs
    }

    private var lastLinX = 0f
    private var lastAngZ = 0f
    private var lastSendTimeMs = 0L

    private fun sendVelocity(linearX: Float, angularZ: Float, force: Boolean = false) {
        // Deadband compensation: ensure robot actually moves if requested
        var outX = linearX
        var outZ = angularZ
        
        if (outX != 0f && Math.abs(outX) < 0.12f) outX = Math.signum(outX) * 0.12f
        if (outZ != 0f && Math.abs(outZ) < 0.25f) outZ = Math.signum(outZ) * 0.25f

        val now = SystemClock.elapsedRealtime()
        val isSame = (outX == lastLinX && outZ == lastAngZ)

        if (!force) {
            // Rate limit: Max 20Hz for changes, Heartbeat 5Hz for same values
            if (isSame && (now - lastSendTimeMs < 200)) return
            if (!isSame && (now - lastSendTimeMs < 50)) return
        }

        lastLinX = outX
        lastAngZ = outZ
        lastSendTimeMs = now

        val msg = JSONObject().apply {
            put("op", "publish")
            put("topic", "/cmd_vel_mux/input/navi_override")
            put("msg", JSONObject().apply {
                put("linear", JSONObject().apply { put("x", outX.toDouble()); put("y", 0.0); put("z", 0.0) })
                put("angular", JSONObject().apply { put("x", 0.0); put("y", 0.0); put("z", outZ.toDouble()) })
            })
        }
        chassisSender(msg.toString())
    }
}
