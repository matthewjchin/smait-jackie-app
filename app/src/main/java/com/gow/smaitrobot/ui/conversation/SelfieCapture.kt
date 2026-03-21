package com.gow.smaitrobot.ui.conversation

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.io.OutputStream

private const val TAG = "SelfieCapture"
private const val CAPTURE_WIDTH = 1280
private const val CAPTURE_HEIGHT = 720

/**
 * User-triggered selfie capture composable with 3-2-1 countdown and preview.
 *
 * Uses Camera2 + AndroidView(TextureView) for Jackie RK3588 compatibility.
 * After capture, shows preview with Retake and Save buttons.
 * Saves to device MediaStore on confirmation.
 *
 * NOTE: This composable is separate from [VideoStreamManager] which runs continuously
 * in the background. If both are active simultaneously, VideoStreamManager should be
 * paused before capture — the calling screen handles this via [ConversationViewModel.toggleCamera].
 *
 * @param onDismiss  Called when the user dismisses without saving.
 * @param onCapture  Called with the captured [Bitmap] after the user taps Save.
 */
@Composable
fun SelfieCapture(
    onDismiss: () -> Unit,
    onCapture: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var countdownStarted by remember { mutableStateOf(false) }
    var countdownValue by remember { mutableIntStateOf(3) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showFlash by remember { mutableStateOf(false) }
    var cameraReady by remember { mutableStateOf(false) }

    // Camera2 state
    val cameraThread = remember { HandlerThread("SelfieCamera").also { it.start() } }
    val cameraHandler = remember { Handler(cameraThread.looper) }
    var cameraDevice: CameraDevice? by remember { mutableStateOf(null) }
    var captureSession: android.hardware.camera2.CameraCaptureSession? by remember { mutableStateOf(null) }
    val imageReader = remember {
        ImageReader.newInstance(CAPTURE_WIDTH, CAPTURE_HEIGHT, ImageFormat.YUV_420_888, 1)
    }

    // Cleanup on exit
    DisposableEffect(Unit) {
        onDispose {
            captureSession?.close()
            cameraDevice?.close()
            imageReader.close()
            cameraThread.quitSafely()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        if (capturedBitmap == null) {
            // Camera preview using TextureView
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).also { tv ->
                        tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                surface: android.graphics.SurfaceTexture, w: Int, h: Int
                            ) {
                                openCamera(
                                    context = ctx,
                                    previewSurface = android.view.Surface(surface),
                                    imageReader = imageReader,
                                    handler = cameraHandler,
                                    onDeviceReady = { device, session ->
                                        cameraDevice = device
                                        captureSession = session
                                        cameraReady = true
                                    }
                                )
                            }
                            override fun onSurfaceTextureSizeChanged(s: android.graphics.SurfaceTexture, w: Int, h: Int) {}
                            override fun onSurfaceTextureDestroyed(s: android.graphics.SurfaceTexture) = true
                            override fun onSurfaceTextureUpdated(s: android.graphics.SurfaceTexture) {}
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Before countdown: show capture button
            if (!countdownStarted) {
                Button(
                    onClick = { countdownStarted = true },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(32.dp)
                        .height(72.dp)
                        .width(200.dp)
                ) {
                    Text("Take Photo", fontSize = 22.sp)
                }
            }

            // 3-2-1 countdown overlay
            if (countdownStarted && countdownValue > 0) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = countdownValue.toString(),
                        fontSize = 120.sp,
                        color = Color.White,
                        style = MaterialTheme.typography.displayLarge
                    )
                }

                LaunchedEffect(countdownStarted) {
                    for (i in 3 downTo 1) {
                        countdownValue = i
                        delay(1_000L)
                    }
                    countdownValue = 0
                    // Set listener then fire a one-shot capture to the imageReader
                    imageReader.setOnImageAvailableListener({ reader ->
                        val image = reader.acquireNextImage() ?: return@setOnImageAvailableListener
                        val bmp = yuv420ToBitmap(image)
                        image.close()
                        if (bmp != null) {
                            capturedBitmap = bmp
                            showFlash = true
                        }
                    }, cameraHandler)
                    // Trigger a capture request that targets the imageReader
                    val cam = cameraDevice
                    val session = captureSession
                    if (cam != null && session != null) {
                        val captureReq = cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                            addTarget(imageReader.surface)
                            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        }.build()
                        session.capture(captureReq, null, cameraHandler)
                    }
                }
            }

            // Flash overlay
            AnimatedVisibility(
                visible = showFlash,
                enter = fadeIn(tween(50)),
                exit = fadeOut(tween(300))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                )
                LaunchedEffect(Unit) {
                    delay(350L)
                    showFlash = false
                }
            }

            // Dismiss button
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .height(60.dp)
            ) {
                Text("Cancel", fontSize = 16.sp)
            }

        } else {
            // Preview captured image with retake/save
            val bmp = capturedBitmap!!

            AndroidView(
                factory = { ctx ->
                    android.widget.ImageView(ctx).apply {
                        setImageBitmap(bmp)
                        scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Retake / Save buttons at bottom
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        capturedBitmap = null
                        countdownValue = 3
                        countdownStarted = false
                    },
                    modifier = Modifier.height(60.dp).weight(1f)
                ) {
                    Text("Retake", fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = {
                        saveBitmapToGallery(context, bmp)
                        onCapture(bmp)
                    },
                    modifier = Modifier.height(60.dp).weight(1f)
                ) {
                    Text("Save", fontSize = 18.sp)
                }
            }
        }
    }
}

// ── Camera2 helpers ───────────────────────────────────────────────────────────

private fun openCamera(
    context: android.content.Context,
    previewSurface: android.view.Surface,
    imageReader: ImageReader,
    handler: Handler,
    onDeviceReady: (CameraDevice, android.hardware.camera2.CameraCaptureSession) -> Unit
) {
    try {
        val manager = context.getSystemService(android.content.Context.CAMERA_SERVICE) as CameraManager
        val cameraId = selectFrontOrExternalCamera(manager) ?: return

        @Suppress("MissingPermission")
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                val surfaces = listOf(previewSurface, imageReader.surface)
                @Suppress("DEPRECATION")
                camera.createCaptureSession(
                    surfaces,
                    object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: android.hardware.camera2.CameraCaptureSession) {
                            val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                addTarget(previewSurface)
                                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                            }.build()
                            session.setRepeatingRequest(req, null, handler)
                            onDeviceReady(camera, session)
                        }
                        override fun onConfigureFailed(session: android.hardware.camera2.CameraCaptureSession) {
                            Log.e(TAG, "SelfieCapture session config failed")
                        }
                    },
                    handler
                )
            }
            override fun onDisconnected(camera: CameraDevice) { camera.close() }
            override fun onError(camera: CameraDevice, error: Int) {
                Log.e(TAG, "SelfieCapture camera error: $error")
                camera.close()
            }
        }, handler)
    } catch (e: Exception) {
        Log.e(TAG, "openCamera failed", e)
    }
}

private fun selectFrontOrExternalCamera(manager: CameraManager): String? {
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

private fun yuv420ToBitmap(image: Image): Bitmap? {
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

        val nv21 = ByteArray(yBytes.size + uBytes.size + vBytes.size)
        System.arraycopy(yBytes, 0, nv21, 0, yBytes.size)
        val off = yBytes.size
        for (i in uBytes.indices) {
            nv21[off + i * 2] = vBytes.getOrElse(i) { 0 }
            nv21[off + i * 2 + 1] = uBytes[i]
        }

        val yuvImg = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImg.compressToJpeg(Rect(0, 0, image.width, image.height), 95, out)
        val jpegBytes = out.toByteArray()

        // Flip horizontally for selfie mirror effect
        val raw = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
        val matrix = Matrix().apply { preScale(-1f, 1f) }
        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    } catch (e: Exception) {
        Log.e(TAG, "YUV→Bitmap conversion error", e)
        null
    }
}

private fun saveBitmapToGallery(context: android.content.Context, bitmap: Bitmap) {
    try {
        val filename = "Jackie_Selfie_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Jackie")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            val stream: OutputStream? = context.contentResolver.openOutputStream(it)
            stream?.use { os -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, os) }
        }
        Log.i(TAG, "Selfie saved locally: $filename")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to save selfie locally", e)
    }
}

/**
 * Compresses a Bitmap to JPEG bytes and sends to server as a selfie binary frame.
 * Frame format: 0x07 (type byte) + JPEG payload.
 * Server saves it in the session log folder alongside audio and session JSON.
 */
fun sendSelfieToServer(bitmap: Bitmap, wsRepo: com.gow.smaitrobot.data.websocket.WebSocketRepository) {
    try {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        val jpegBytes = out.toByteArray()

        // Frame: 0x07 (selfie type) + JPEG bytes
        val frame = ByteArray(1 + jpegBytes.size)
        frame[0] = 0x07
        System.arraycopy(jpegBytes, 0, frame, 1, jpegBytes.size)
        wsRepo.send(frame)
        Log.i(TAG, "Selfie sent to server (${jpegBytes.size} bytes)")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to send selfie to server", e)
    }
}
