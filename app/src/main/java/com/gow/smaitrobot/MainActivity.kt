package com.gow.smaitrobot

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.gow.smaitrobot.navigation.AppScaffold
import com.gow.smaitrobot.ui.theme.AppTheme

/**
 * Single-activity host for the SMAIT Jackie robot Compose app.
 *
 * Responsibilities:
 * 1. Configure immersive (kiosk) mode — hides system bars for a full-screen robot UI.
 * 2. Request runtime permissions for CAMERA and RECORD_AUDIO.
 * 3. Provide the Compose root: [AppTheme] wrapping [AppScaffold] with 5-tab navigation.
 *
 * All WebSocket, audio, and camera logic lives in repositories and ViewModels.
 * MainActivity only sets up the UI shell.
 *
 * The old monolithic MainActivity.kt (1376 lines) has been replaced by this file.
 * Protocol patterns from the old code are preserved in:
 * - [com.gow.smaitrobot.data.websocket.WebSocketRepository] — WebSocket + binary routing
 * - CaeAudioManager.kt / TtsAudioPlayer.kt — untouched; wrapped in ConversationViewModel (Plan 04)
 */
class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Permissions are best-effort — the app degrades gracefully if denied.
        // Audio: VAD/ASR won't work; Camera: selfie and video streaming unavailable.
        // No-op handler — the app handles missing permissions in individual screens.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureImmersiveMode()
        requestRequiredPermissions()

        val themeRepo = jackieApp.themeRepository

        setContent {
            val themeConfig by themeRepo.config.collectAsStateWithLifecycle()

            AppTheme(config = themeConfig) {
                AppScaffold(
                    navController = rememberNavController(),
                    themeConfig = themeConfig
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-apply immersive mode after dialogs or system UI interactions restore bars.
        configureImmersiveMode()
    }

    // ── Immersive mode (kiosk) ────────────────────────────────────────────────

    /**
     * Hides the status bar and navigation bar for kiosk/robot display use.
     *
     * API 30+: Uses [WindowInsetsController] (non-deprecated).
     * API 23-29: Uses the legacy [View.SYSTEM_UI_FLAG_*] flags.
     */
    private fun configureImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
                )
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
        }
    }

    // ── Runtime permissions ───────────────────────────────────────────────────

    private fun requestRequiredPermissions() {
        val required = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}
