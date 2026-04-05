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
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import org.json.JSONObject
import java.lang.Math.hypot
import java.util.concurrent.Executors

/**
 * Person-following robot controller using MediaPipe face detection + DeepSORT tracking.
 *
 * Runs entirely on-device (no server needed). Uses the existing ChassisProxy
 * to send cmd_vel commands to Jackie's chassis via rosbridge.
 *
 * Behaviors (FSM):
 * - FOLLOW:    Face detected within range -> track with PID
 * - SCAN:      No face / out of range -> rotate 45 deg CCW, retry
 * - OBSTACLE:  Obstacle ahead -> rotate 90 deg CW, resume
 * - COLLISION: Object < 0.1m -> stop 3s, rotate 45 deg CW
 *
 * Ported from Jason's RobotController.java, integrated with Jackie app architecture.
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
        private const val FOLLOW_DISTANCE_M = 2.0
        private const val COLLISION_DISTANCE_M = 0.50
        private const val TARGET_FOLLOW_DISTANCE_M = 0.8   // ← was hardcoded to 0.5; now explicit
        private const val PAN_FF_GAIN = 0.002              // feed-forward gain (tune to taste)

        // Add a field to remember which way the target was last moving
        private var lastTargetVelX: Double = 0.0


        // Camera geometry
        private const val FRAME_WIDTH_PX = 640
        private const val FOCAL_LENGTH_PX = 600.0  // calibrate per camera
        private const val FACE_WIDTH_M = 0.165      // avg adult face ~16.5cm

        // Rotation speeds (rad/s)
        private const val TURN_SPEED_RAD_S = 0.4f
        private val DEG_45_RAD = Math.PI / 4.0
        private val DEG_90_RAD = Math.PI / 2.0
        // Updated startManoeuvre to accept direction:
        private var scanDirection = 1f


        // Timing
        private const val COLLISION_PAUSE_MS = 3_000L

    }

    // Sub-systems
    private var mediaPipe: FaceLandmarker? = null
    private val deepSort = DeepSortTracker()
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
    private val distPid = PidController(0.0003, 0.0, 0.0005)

    // Latest state for UI
    var currentState: FsmState = FsmState.SCAN_ROTATE
        private set
    var currentDistance: Double = Double.MAX_VALUE
        private set
    var onStateChanged: ((FsmState, Double) -> Unit)? = null

    // Last frame timestamp for real dt
    private var lastFrameMs = 0L


    enum class FsmState {
        FOLLOWING,
        SCAN_ROTATE,
        OBSTACLE_TURN,
        COLLISION_STOP,
        COLLISION_TURN,
        CLEAR_CHECK
    }



    /**
     * Initialize MediaPipe and start camera analysis.
     * Call from a LifecycleOwner (Activity or Fragment).
     */
    fun start(context: Context, lifecycleOwner: LifecycleOwner) {
        if (running) return
        running = true

        initMediaPipe(context)
        startCamera(context, lifecycleOwner)
        enterState(FsmState.SCAN_ROTATE)

        Log.i(TAG, "Follow mode started")
    }

    /**
     * Send a rosbridge subscribe message for /amcl_pose
     *
     */
    private fun subscribeToRobotPose(sender: (String) -> Unit) {
        val msg = JSONObject().apply {
            put("op", "subscribe")
            put("topic", "/amcl_pose")
            put("type", "geometry_msgs/PoseWithCovarianceStamped")
        }
        sender(msg.toString())
    }

    // Store the latest robot's positions
    private var robotX: Double = 0.0
    private var robotY: Double = 0.0
    private var robotYaw: Double = 0.0   // radians, derived from quaternion - "theta"

    // Call this when a /amcl_pose message arrives
    fun onPoseReceived(payload: JSONObject) {
        val pose = payload
            .getJSONObject("msg")
            .getJSONObject("pose")
            .getJSONObject("pose")
        robotX = pose.getJSONObject("position").getDouble("x")
        robotY = pose.getJSONObject("position").getDouble("y")
        robotYaw = quaternionToYaw(pose.getJSONObject("orientation"))
    }

    private fun quaternionToYaw(q: JSONObject): Double {
        val z = q.getDouble("z")
        val w = q.getDouble("w")
        return 2.0 * Math.atan2(z, w)   // yaw from quaternion for 2D navigation
    }



    /**
     * Stop following, release camera and MediaPipe resources.
     */
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

    // ── MediaPipe setup ────────────────────────────────────────────────────

    private fun initMediaPipe(context: Context) {
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath("face_landmarker.task")
                    .build()
            )
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

    // ── CameraX setup ──────────────────────────────────────────────────────

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
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    analysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // ── Per-frame analysis ─────────────────────────────────────────────────

    private fun analyseFrame(imageProxy: ImageProxy) {
        if (!running) {
            imageProxy.close()
            return
        }
        try {
            val bmp = imageProxy.toBitmap()
            val mpImg = BitmapImageBuilder(bmp).build()
            val tsMs = imageProxy.imageInfo.timestamp / 1_000_000L
            mediaPipe?.detectAsync(mpImg, tsMs)
        } catch (e: Exception) {
            Log.w(TAG, "Frame analysis error", e)
        } finally {
            imageProxy.close()
        }
    }

    // ── MediaPipe result callback ──────────────────────────────────────────

    private fun onMediaPipeResult(
        result: FaceLandmarkerResult,
        image: MPImage
    ) {
        if (!running) return

        // Convert landmarks to bounding rects
        val detections = result.faceLandmarks().map { face ->
            var minX = Float.MAX_VALUE;
            var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE;
            var maxY = -Float.MAX_VALUE
            for (lm in face) {
                val px = lm.x() * image.width
                val py = lm.y() * image.height
                if (px < minX) minX = px; if (px > maxX) maxX = px
                if (py < minY) minY = py; if (py > maxY) maxY = py
            }
            Rect(minX.toInt(), minY.toInt(), maxX.toInt(), maxY.toInt())
        }

        // Run DeepSORT
        val confirmed = deepSort.update(detections)

        // If current target is lost, find and pick the closest confirmed track using that person's
        // face width that is largest and closest
        if (targetTrackId == -1 && confirmed.isNotEmpty()) {
            targetTrackId = confirmed.maxByOrNull { it.predictedRect().width() } !!.id
        }

        // Pass live track object down to driveTowardFace
        val targetTrack = confirmed.firstOrNull { it.id == targetTrackId }
        if (targetTrack == null) targetTrackId = -1

        if (targetTrack != null) {
            lastTargetVelX = targetTrack.velocityX()   // remember direction
        }

        // Pass live track object to driveTowardFace
        val targetRect = targetTrack?.predictedRect()
        val distanceM = if (targetRect != null) estimateDistance(targetRect.width()) else Double.MAX_VALUE

        mainHandler.post { tick(targetRect, distanceM, targetTrack) }


    }

    private fun estimateDistance(faceWidthPx: Int): Double {
        if (faceWidthPx <= 0) return Double.MAX_VALUE
        return (FACE_WIDTH_M * FOCAL_LENGTH_PX) / faceWidthPx
    }

    // ── FSM tick ───────────────────────────────────────────────────────────

    private fun tick(faceBounds: Rect?, distM: Double, targetTrack: KalmanTrack?) {
        if (!running) return

        currentDistance = distM

        when (fsmState) {
            FsmState.SCAN_ROTATE -> {
                if (SystemClock.elapsedRealtime() < manoeuvreEndMs) {
                    // Adjust speed and direction of rotation
                    sendVelocity(0f, TURN_SPEED_RAD_S * scanDirection)
                } else {
                    sendVelocity(0f, 0f)
                    if (faceBounds != null && distM <= FOLLOW_DISTANCE_M) {
                        Log.d(TAG, "Face acquired - following")
                        enterState(FsmState.FOLLOWING)
                    } else {
                        startManoeuvre(DEG_45_RAD)
                    }
                }
            }

            FsmState.OBSTACLE_TURN -> {
                if (SystemClock.elapsedRealtime() < manoeuvreEndMs) {
                    sendVelocity(0f, -TURN_SPEED_RAD_S) // CW
                } else {
                    sendVelocity(0f, 0f)
                    enterState(FsmState.CLEAR_CHECK)
                }
            }

            FsmState.CLEAR_CHECK -> {
                // No proximity sensor data yet — just resume following
                Log.d(TAG, "Clear check - resuming")
                enterState(FsmState.FOLLOWING)
            }

            FsmState.COLLISION_STOP -> {
                sendVelocity(0f, 0f)
                if (SystemClock.elapsedRealtime() >= manoeuvreEndMs) {
                    Log.d(TAG, "Collision pause done - rotating 45 deg CW")
                    enterState(FsmState.COLLISION_TURN)
                }
            }

            FsmState.COLLISION_TURN -> {
                if (SystemClock.elapsedRealtime() < manoeuvreEndMs) {
                    sendVelocity(0f, -TURN_SPEED_RAD_S) // CW
                } else {
                    sendVelocity(0f, 0f)
                    enterState(FsmState.CLEAR_CHECK)
                }
            }

            FsmState.FOLLOWING -> {
                if (faceBounds == null || distM > FOLLOW_DISTANCE_M) {
                    enterState(FsmState.SCAN_ROTATE)
                    return
                }
                driveTowardFace(faceBounds, distM, targetTrack!!)

            }


        }
    }

    private fun estimateTargetWorldPosition(face: Rect, distM: Double): Pair<Double, Double> {
        // Horizontal angle offset from camera centre (positive = target is to the right)
        val dx = face.centerX() - FRAME_WIDTH_PX / 2.0
        val panAngleRad = Math.atan2(dx, FOCAL_LENGTH_PX)   // angle in camera frame

        // World angle = robot heading + camera pan angle
        val worldAngle = robotYaw + panAngleRad

        // Project forward by distM in world frame
        val targetX = robotX + distM * Math.cos(worldAngle)
        val targetY = robotY + distM * Math.sin(worldAngle)

        return Pair(targetX, targetY)
    }

    private fun sendNavigationGoal(x: Double, y: Double, yaw: Double = 0.0) {
        // Convert yaw back to quaternion for PoseStamped
        val qz = Math.sin(yaw / 2.0)
        val qw = Math.cos(yaw / 2.0)

        val msg = JSONObject().apply {
            put("op", "publish")
            put("topic", "/move_base_simple/goal")
            put("msg", JSONObject().apply {
                put("header", JSONObject().apply {
                    put("frame_id", "map")
                })
                put("pose", JSONObject().apply {
                    put("position", JSONObject().apply {
                        put("x", x); put("y", y); put("z", 0.0)
                    })
                    put("orientation", JSONObject().apply {
                        put("x", 0.0); put("y", 0.0)
                        put("z", qz); put("w", qw)
                    })
                })
            })
        }
        chassisSender(msg.toString())
    }


    // ── PID drive toward face ──────────────────────────────────────────────

    private fun driveTowardFace(face: Rect, distM: Double) {
        val dx = face.centerX() - FRAME_WIDTH_PX / 2.0
        val areaError = 18_000.0 - (face.width() * face.height()).toDouble()

        var angZ = panPid.compute(dx, 0.033).coerceIn(-0.8, 0.8).toFloat()
        var linX = distPid.compute(areaError, 0.033).coerceIn(-0.3, 0.3).toFloat()

        // Safety: slow down when very close
        if (distM < 0.15) linX = 0f

        sendVelocity(linX, angZ)
    }

    // ── State transitions ──────────────────────────────────────────────────

    private fun enterState(next: FsmState) {
        fsmState = next
        currentState = next
        val now = SystemClock.elapsedRealtime()
        when (next) {
//            FsmState.SCAN_ROTATE -> startManoeuvre(DEG_45_RAD)

            // In enterState, when transitioning to SCAN_ROTATE:
            FsmState.SCAN_ROTATE -> {
                // Rotate toward the direction the target was last moving
                val scanDir = if (lastTargetVelX >= 0) 1f else -1f   // CCW if moving right, CW if left
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

            FsmState.COLLISION_TURN -> startManoeuvre(DEG_45_RAD)
            FsmState.CLEAR_CHECK, FsmState.FOLLOWING -> { /* no setup */
            }
        }
        Log.d(TAG, "FSM -> $next")
        onStateChanged?.invoke(next, currentDistance)


    }

    private fun startManoeuvre(angleRad: Double) {
        val durationMs = ((angleRad / TURN_SPEED_RAD_S) * 1000).toLong()
        manoeuvreEndMs = SystemClock.elapsedRealtime() + durationMs
    }

    // ── Chassis command ────────────────────────────────────────────────────

    private var lastLinX = 0f
    private var lastAngZ = 0f

    private fun sendVelocity(linearX: Float, angularZ: Float) {
        if (linearX == lastLinX && angularZ == lastAngZ) return
        lastLinX = linearX
        lastAngZ = angularZ

        // Build rosbridge cmd_vel publish message
        val msg = JSONObject().apply {
            put("op", "publish")
            put("topic", "/cmd_vel")
            put("msg", JSONObject().apply {
                put("linear", JSONObject().apply {
                    put("x", linearX.toDouble())
                    put("y", 0.0)
                    put("z", 0.0)
                })
                put("angular", JSONObject().apply {
                    put("x", 0.0)
                    put("y", 0.0)
                    put("z", angularZ.toDouble())
                })
            })
        }

        chassisSender(msg.toString())
    }

    private var lastGoalX = 0.0
    private var lastGoalY = 0.0
    private val GOAL_UPDATE_THRESHOLD_M = 0.2   // only re-goal if person moved >20cm

    private fun maybeUpdateGoal(face: Rect, distM: Double) {
        val (tx, ty) = estimateTargetWorldPosition(face, distM)
        val moved = hypot(tx - lastGoalX, ty - lastGoalY)
        if (moved > GOAL_UPDATE_THRESHOLD_M) {
            sendNavigationGoal(tx, ty)
            lastGoalX = tx
            lastGoalY = ty
        }
    }


    private fun driveTowardFace(face: Rect, distM: Double, targetTrack: KalmanTrack) {
        // Real dt from actual frame cadence
        val nowMs = SystemClock.elapsedRealtime()
        val dt =
            if (lastFrameMs == 0L) 0.033 else ((nowMs - lastFrameMs) / 1000.0).coerceIn(0.01, 0.1)
        lastFrameMs = nowMs

        // Lateral error: how far face center is from frame center
        val dx = face.centerX() - FRAME_WIDTH_PX / 2.0

        // Distance error: use actual metres, not face area
        val distError =
            distM - TARGET_FOLLOW_DISTANCE_M   // ← positive = too far, negative = too close

        // PID outputs
        var angZ = panPid.compute(dx, dt).coerceIn(-0.8, 0.8).toFloat()
        var linX = distPid.compute(distError, dt).coerceIn(-0.3, 0.3).toFloat()

        // ── Velocity feed-forward ──────────────────────────────────────────────
        // Use the Kalman-estimated horizontal velocity to anticipate movement.
        // If the person is drifting right (positive vx), add extra CW rotation.
        val velFeedForward = (targetTrack.velocityX() * PAN_FF_GAIN).coerceIn(-0.3, 0.3).toFloat()
        angZ += velFeedForward

        // Safety: stop linear motion when very close
        if (distM < COLLISION_DISTANCE_M + 0.05) linX = 0f

        sendVelocity(linX, angZ)
        maybeUpdateGoal(face, distM)
    }

    private fun startManoeuvre(angleRad: Double, dir: Float = 1f) {
        scanDirection = dir
        val durationMs = ((angleRad / TURN_SPEED_RAD_S) * 1000).toLong()
        manoeuvreEndMs = SystemClock.elapsedRealtime() + durationMs
    }




}