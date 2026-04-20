package com.gow.smaitrobot.ui.conversation

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Surface
import com.gow.smaitrobot.data.websocket.WebSocketRepository
import java.io.ByteArrayOutputStream

/**
 * Manages continuous Camera2 video capture and sends JPEG frames to the SMAIT server
 * as binary 0x02 WebSocket frames.
 *
 * This is the background video pipeline for server-side vision (face detection, lip frames
 * for Dolphin speech separation). It is SEPARATE from [SelfieCapture] which is user-triggered.
 *
 * Frame format: [0x02] + JPEG bytes
 * Frame rate target: ~10fps (throttled via 100ms minimum interval between frames)
 * Resolution: 320x240 (low-res for bandwidth efficiency on WiFi)
 *
 * Camera preference: LENS_FACING_EXTERNAL (Jackie's USB camera), fallback to LENS_FACING_FRONT.
 * Uses Camera2 API to match the existing app's working pattern on Jackie's RK3588 SoC.
 *
 * @param wsRepo WebSocketRepository to send 0x02 binary frames through.
 */
class VideoStreamManager(private val wsRepo: WebSocketRepository) {

    companion object {
        private const val TAG = "VideoStreamManager"
        private const val VIDEO_FRAME_TYPE: Byte = 0x02
        private const val FRAME_WIDTH = 320
        private const val FRAME_HEIGHT = 240
        private const val JPEG_QUALITY = 60
        private const val MIN_FRAME_INTERVAL_MS = 50L // ~20fps (Nyquist for 7Hz lip oscillation)
        private const val MAX_SESSION_RETRIES = 15
        private const val SESSION_RETRY_DELAY_MS = 200L
    }

    private var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var dummyTexture: SurfaceTexture? = null
    private var dummySurface: Surface? = null
    private var lastFrameSentMs = 0L
    private var sessionRetryCount = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    // One-shot callback set by [snapshotNextFrame]. The next JPEG produced by
    // the live stream's image listener is delivered to this callback (in
    // addition to being sent as a normal 0x02 video frame), then the callback
    // is cleared. Used by the Photo Booth flow to avoid opening a second
    // Camera2 session — the USB camera on Jackie only allows one owner.
    @Volatile
    private var pendingSnapshot: ((ByteArray) -> Unit)? = null

    /**
     * Request a one-shot snapshot from the live video stream.
     *
     * The next JPEG frame encoded by the imageListener will be delivered to
     * [callback] (on an internal camera handler thread). The frame is also
     * sent as a normal 0x02 video frame so the server-side pipeline is
     * uninterrupted.
     *
     * If [start] has not been called, or the camera isn't actively producing
     * frames, the callback will never fire — the caller is responsible for
     * its own timeout / error UI.
     */
    fun snapshotNextFrame(callback: (ByteArray) -> Unit) {
        pendingSnapshot = callback
    }

    /**
     * Opens the camera and starts continuous JPEG frame capture.
     * Prefers LENS_FACING_EXTERNAL (Jackie's USB camera), falls back to LENS_FACING_FRONT.
     *
     * @param context Application context for CameraManager access.
     */
    fun start(context: Context) {
        try {
            val thread = HandlerThread("VideoStream")
            thread.start()
            val handler = Handler(thread.looper)
            cameraThread = thread
            cameraHandler = handler

            val reader = ImageReader.newInstance(FRAME_WIDTH, FRAME_HEIGHT, ImageFormat.YUV_420_888, 2)
            reader.setOnImageAvailableListener(imageListener, handler)
            imageReader = reader

            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = selectCamera(cameraManager)

            if (cameraId == null) {
                Log.w(TAG, "No suitable camera found — video stream disabled")
                return
            }

            @Suppress("MissingPermission")
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    sessionRetryCount = 0
                    // RK3588 fix: createCaptureSession must run on UI thread —
                    // SurfaceTexture returns null from camera handler thread.
                    mainHandler.post { startCaptureSession(camera, reader, handler) }
                    Log.i(TAG, "Camera opened: $cameraId")
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.w(TAG, "Camera disconnected")
                    closeCamera()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera error: $error")
                    closeCamera()
                }
            }, handler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start video stream", e)
        }
    }

    /**
     * Stops the camera and releases all resources.
     * Safe to call if [start] was never called or already stopped.
     */
    fun stop() {
        closeCamera()
        try {
            imageReader?.close()
            imageReader = null
        } catch (e: Exception) {
            Log.w(TAG, "ImageReader close error", e)
        }
        cameraThread?.quitSafely()
        cameraThread = null
        cameraHandler = null
        Log.i(TAG, "Video stream stopped")
    }

    /**
     * Closes the camera device and releases surfaces.
     * Prevents camera self-eviction on RK3588 by explicitly releasing in onDisconnected.
     */
    private fun closeCamera() {
        try {
            cameraDevice?.close()
            cameraDevice = null
        } catch (e: Exception) {
            Log.w(TAG, "Camera close error", e)
        }
        dummySurface?.release()
        dummySurface = null
        dummyTexture?.release()
        dummyTexture = null
    }

    // ── Private implementation ────────────────────────────────────────────────

    /**
     * Select the best available camera.
     * Preference order: LENS_FACING_EXTERNAL (USB camera on Jackie) → LENS_FACING_FRONT.
     */
    private fun selectCamera(manager: CameraManager): String? {
        val ids = manager.cameraIdList
        // Prefer external (USB camera on Jackie RK3588)
        for (id in ids) {
            val facing = manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_EXTERNAL) return id
        }
        // Fallback: front camera
        for (id in ids) {
            val facing = manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_FRONT) return id
        }
        return ids.firstOrNull()
    }

    /**
     * Creates a Camera2 capture session with both a dummy preview surface and
     * the [imageReader] surface. The RK3588 external camera HAL requires a
     * preview-type Surface in the session for ImageReader callbacks to fire.
     * Uses TEMPLATE_PREVIEW for continuous frame delivery.
     */
    private fun startCaptureSession(
        camera: CameraDevice,
        reader: ImageReader,
        handler: Handler
    ) {
        // RK3588 fix: SurfaceTexture may not be ready on first attempt.
        // Retry up to MAX_SESSION_RETRIES times with delay.
        if (camera != cameraDevice) {
            Log.w(TAG, "Camera changed before session started — aborting")
            return
        }

        try {
            val readerSurface = reader.surface

            // RK3588 HAL fix: create a dummy 1x1 SurfaceTexture as preview target.
            // Without a preview-type surface, ImageReader.onImageAvailable never fires
            // on Jackie's external USB camera.
            val texture = SurfaceTexture(0)
            texture.setDefaultBufferSize(1, 1)
            val preview = Surface(texture)
            dummyTexture = texture
            dummySurface = preview

            @Suppress("DEPRECATION")
            camera.createCaptureSession(
                listOf(preview, readerSurface),
                object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: android.hardware.camera2.CameraCaptureSession) {
                        val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(preview)
                            addTarget(readerSurface)
                            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        }.build()
                        session.setRepeatingRequest(request, null, handler)
                        Log.i(TAG, "Capture session configured with preview surface — streaming at ~10fps")
                    }

                    override fun onConfigureFailed(session: android.hardware.camera2.CameraCaptureSession) {
                        Log.e(TAG, "Capture session configuration failed")
                    }
                },
                handler
            )
        } catch (e: Exception) {
            Log.e(TAG, "createCaptureSession failed (attempt ${sessionRetryCount + 1})", e)
            if (sessionRetryCount < MAX_SESSION_RETRIES) {
                sessionRetryCount++
                mainHandler.postDelayed({ startCaptureSession(camera, reader, handler) }, SESSION_RETRY_DELAY_MS)
            } else {
                Log.e(TAG, "Exhausted $MAX_SESSION_RETRIES retries — camera session failed")
            }
        }
    }

    /**
     * ImageReader listener — converts YUV_420_888 frames to JPEG and sends as 0x02 WS frames.
     * Throttled to ~10fps via [MIN_FRAME_INTERVAL_MS].
     */
    private val imageListener = ImageReader.OnImageAvailableListener { reader ->
        val image: Image = reader.acquireLatestImage() ?: return@OnImageAvailableListener
        try {
            val now = System.currentTimeMillis()
            val snapshot = pendingSnapshot
            // Honour the fps throttle for the normal live stream, but bypass
            // it when a photo-booth snapshot has been requested so the user
            // doesn't wait for the throttle window to elapse.
            if (snapshot == null && now - lastFrameSentMs < MIN_FRAME_INTERVAL_MS) {
                return@OnImageAvailableListener
            }

            val jpegBytes = yuv420ToJpeg(image) ?: return@OnImageAvailableListener

            // Prepend 0x02 type byte and send to the server's live pipeline
            val frame = ByteArray(1 + jpegBytes.size)
            frame[0] = VIDEO_FRAME_TYPE
            System.arraycopy(jpegBytes, 0, frame, 1, jpegBytes.size)
            wsRepo.send(frame)

            lastFrameSentMs = now

            // Deliver the same JPEG bytes to any waiting photo-booth snapshot
            // callback, then clear the slot so future frames go back to the
            // live stream only. Callback runs on the camera handler thread —
            // receivers must not block.
            if (snapshot != null) {
                pendingSnapshot = null
                try {
                    snapshot(jpegBytes)
                } catch (e: Exception) {
                    Log.w(TAG, "Snapshot callback raised", e)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Frame encode/send error", e)
        } finally {
            image.close()
        }
    }

    /**
     * Converts an [Image] in [ImageFormat.YUV_420_888] to a JPEG byte array.
     *
     * Converts YUV_420_888 → NV21 → JPEG via YuvImage.
     * Returns null on any conversion error.
     */
    private fun yuv420ToJpeg(image: Image): ByteArray? {
        return try {
            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]

            val yBytes = ByteArray(yPlane.buffer.remaining())
            val uBytes = ByteArray(uPlane.buffer.remaining())
            val vBytes = ByteArray(vPlane.buffer.remaining())

            yPlane.buffer.get(yBytes)
            uPlane.buffer.get(uBytes)
            vPlane.buffer.get(vBytes)

            // Build NV21: Y plane + interleaved VU
            val nv21 = ByteArray(yBytes.size + uBytes.size + vBytes.size)
            System.arraycopy(yBytes, 0, nv21, 0, yBytes.size)
            val uvOffset = yBytes.size
            for (i in uBytes.indices) {
                nv21[uvOffset + i * 2] = vBytes.getOrElse(i) { 0 }
                nv21[uvOffset + i * 2 + 1] = uBytes[i]
            }

            val yuvImage = android.graphics.YuvImage(
                nv21,
                android.graphics.ImageFormat.NV21,
                image.width,
                image.height,
                null
            )
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                android.graphics.Rect(0, 0, image.width, image.height),
                JPEG_QUALITY,
                out
            )
            out.toByteArray()
        } catch (e: Exception) {
            Log.w(TAG, "YUV→JPEG conversion error", e)
            null
        }
    }
}
