package com.gow.smaitrobot.ui.conversation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.util.Log

private const val PHOTO_TAG = "PhotoBoothCapture"

/**
 * Opens the external/front camera, fires a single JPEG still capture, decodes it to a [Bitmap],
 * and delivers it via [onBitmapReady]. Automatically closes the camera after capture.
 *
 * This is a fire-and-forget function designed for the Photo Booth flow where no live preview
 * is needed — the user is already looking at the countdown overlay when this fires.
 *
 * The [imageReader] must be configured with [ImageFormat.JPEG], width=1280, height=720.
 * The handler runs all Camera2 callbacks off the main thread.
 *
 * @param context       Android context (used to get CameraManager)
 * @param imageReader   Pre-configured JPEG ImageReader (1280x720, 2 images buffer)
 * @param handler       Background handler for Camera2 callbacks
 * @param onBitmapReady Callback delivered on [handler]'s thread with the captured Bitmap
 */
fun openCameraForPhotoBooth(
    context: Context,
    imageReader: ImageReader,
    handler: Handler,
    onBitmapReady: (Bitmap) -> Unit
) {
    try {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = selectBestCamera(manager) ?: run {
            Log.e(PHOTO_TAG, "No suitable camera found")
            return
        }

        @Suppress("MissingPermission")
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {

            override fun onOpened(camera: CameraDevice) {
                try {
                    @Suppress("DEPRECATION")
                    camera.createCaptureSession(
                        listOf(imageReader.surface),
                        object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: android.hardware.camera2.CameraCaptureSession) {
                                try {
                                    // Register listener before firing capture request
                                    imageReader.setOnImageAvailableListener({ reader ->
                                        val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                                        try {
                                            val buffer = image.planes[0].buffer
                                            val jpegBytes = ByteArray(buffer.remaining())
                                            buffer.get(jpegBytes)
                                            val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                                            if (bitmap != null) {
                                                onBitmapReady(bitmap)
                                                Log.i(PHOTO_TAG, "Photo captured: ${bitmap.width}x${bitmap.height}")
                                            } else {
                                                Log.e(PHOTO_TAG, "BitmapFactory returned null — JPEG decode failed")
                                            }
                                        } finally {
                                            image.close()
                                            session.close()
                                            camera.close()
                                        }
                                    }, handler)

                                    // Fire single still capture
                                    val captureReq = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                        addTarget(imageReader.surface)
                                        set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                                    }.build()
                                    session.capture(captureReq, null, handler)
                                } catch (e: Exception) {
                                    Log.e(PHOTO_TAG, "Capture request failed", e)
                                    session.close()
                                    camera.close()
                                }
                            }

                            override fun onConfigureFailed(session: android.hardware.camera2.CameraCaptureSession) {
                                Log.e(PHOTO_TAG, "CaptureSession configuration failed")
                                camera.close()
                            }
                        },
                        handler
                    )
                } catch (e: Exception) {
                    Log.e(PHOTO_TAG, "createCaptureSession failed", e)
                    camera.close()
                }
            }

            override fun onDisconnected(camera: CameraDevice) {
                Log.w(PHOTO_TAG, "Camera disconnected")
                camera.close()
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.e(PHOTO_TAG, "Camera error: $error")
                camera.close()
            }
        }, handler)
    } catch (e: Exception) {
        Log.e(PHOTO_TAG, "openCameraForPhotoBooth failed", e)
    }
}

/** Prefer external USB camera (Jackie RK3588), then front-facing, then any. */
private fun selectBestCamera(manager: CameraManager): String? {
    val ids = manager.cameraIdList
    for (id in ids) {
        val facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
        if (facing == CameraCharacteristics.LENS_FACING_EXTERNAL) return id
    }
    for (id in ids) {
        val facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
        if (facing == CameraCharacteristics.LENS_FACING_FRONT) return id
    }
    return ids.firstOrNull()
}
