package com.gow.smaitrobot.ui.photobooth

import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.gow.smaitrobot.data.websocket.WebSocketRepository
import com.gow.smaitrobot.ui.conversation.openCameraForPhotoBooth

private const val TAG = "PhotoBoothScreen"

/**
 * Top-level Photo Booth screen composable.
 *
 * Delegates rendering to the appropriate sub-composable based on [PhotoBoothUiState]:
 * - [StylePickerGrid]    — style selection (StylePicker state)
 * - [CountdownOverlay]   — 3-2-1 countdown (Countdown state)
 * - [ProcessingScreen]   — server-side SD inference in progress (Processing state)
 * - [ResultScreen]       — styled result with crossfade + QR + retake (Result state)
 *
 * Camera2 capture is triggered when Countdown reaches secondsLeft=0. The captured bitmap
 * is passed to [PhotoBoothViewModel.onPhotoCaptured] which sends it to the server.
 *
 * Uses `remember { PhotoBoothViewModel(wsRepo) }` to avoid Android ViewModel lifecycle
 * dependency, matching the ConversationViewModel pattern in AppNavigation.kt.
 */
@Composable
fun PhotoBoothScreen(navController: NavHostController, wsRepo: WebSocketRepository) {
    val context = LocalContext.current
    val viewModel = remember { PhotoBoothViewModel(wsRepo) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Camera2 resources — created once and cleaned up on screen exit
    val cameraThread = remember { HandlerThread("PhotoBoothCamera").also { it.start() } }
    val cameraHandler = remember { Handler(cameraThread.looper) }
    val imageReader = remember {
        ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 2)
    }

    // Notify server when screen enters / exits
    LaunchedEffect(Unit) { viewModel.onScreenEntered() }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.onScreenExited()
            imageReader.close()
            cameraThread.quitSafely()
        }
    }

    // Trigger Camera2 capture when countdown reaches 0
    // LaunchedEffect key on uiState so it fires each time Countdown(0) is reached
    LaunchedEffect(uiState) {
        val state = uiState
        if (state is PhotoBoothUiState.Processing && viewModel.rawBitmap == null) {
            // We just entered Processing state — capture the photo
            openCameraForPhotoBooth(
                context = context,
                imageReader = imageReader,
                handler = cameraHandler,
                onBitmapReady = { bitmap ->
                    viewModel.onPhotoCaptured(bitmap)
                }
            )
        }
    }

    when (val state = uiState) {
        is PhotoBoothUiState.StylePicker -> StylePickerGrid(
            selectedStyle = state.selectedStyle,
            onStyleSelected = viewModel::onStyleSelected,
            onTakePhoto = viewModel::onTakePhoto,
            onBack = { navController.popBackStack() }
        )
        is PhotoBoothUiState.Countdown -> CountdownOverlay(
            secondsLeft = state.secondsLeft
        )
        is PhotoBoothUiState.Processing -> ProcessingScreen(
            styleName = state.styleName
        )
        is PhotoBoothUiState.Result -> ResultScreen(
            state = state,
            onRetake = viewModel::onRetake,
            onExit = {
                viewModel.onScreenExited()
                navController.popBackStack()
            }
        )
    }
}
