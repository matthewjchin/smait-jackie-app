package com.gow.smaitrobot.navigation

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.gow.smaitrobot.CaeAudioManager
import com.gow.smaitrobot.StandardAudioManager
import com.gow.smaitrobot.TtsAudioPlayer
import com.gow.smaitrobot.data.model.ThemeConfig
import com.gow.smaitrobot.jackieApp
import com.gow.smaitrobot.ui.conversation.ConversationScreen
import com.gow.smaitrobot.ui.conversation.ConversationViewModel
import com.gow.smaitrobot.ui.conversation.VideoStreamManager
import com.gow.smaitrobot.ui.eventinfo.EventInfoScreen
import com.gow.smaitrobot.ui.eventinfo.EventInfoViewModel
import com.gow.smaitrobot.ui.facilities.FacilitiesScreen
import com.gow.smaitrobot.ui.facilities.FacilitiesViewModel
import com.gow.smaitrobot.ui.home.HomeScreen
import com.gow.smaitrobot.ui.home.HomeViewModel
import com.gow.smaitrobot.ui.navigation_map.NavigationMapScreen
import com.gow.smaitrobot.ui.navigation_map.NavigationMapViewModel
import com.gow.smaitrobot.ui.settings.SettingsScreen

private const val TAG = "AppNavigation"

/**
 * Server URL for WebSocket connection.
 * - Emulator: 10.0.2.2 maps to the host machine's localhost
 * - Jackie: use the lab PC's IP address on the WiFi network
 */
private const val EMULATOR_WS_URL = "ws://10.0.2.2:8765"
private const val JACKIE_WS_URL = "ws://192.168.1.100:8765" // Override per-lab

@Composable
fun AppScaffold(
    navController: NavHostController,
    themeConfig: ThemeConfig
) {
    val context = LocalContext.current
    val wsRepo = context.jackieApp.webSocketRepository
    val themeRepo = context.jackieApp.themeRepository
    val isEmulator = remember { isEmulatorDevice() }

    val homeViewModel: HomeViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(themeRepo) as T
        }
    )
    val eventInfoViewModel: EventInfoViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                EventInfoViewModel(themeRepo) as T
        }
    )
    val navMapViewModel: NavigationMapViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                NavigationMapViewModel(wsRepo) as T
        }
    )
    val facilitiesViewModel: FacilitiesViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                FacilitiesViewModel(wsRepo) as T
        }
    )
    val ttsPlayer = remember { context.jackieApp.ttsAudioPlayer }
    val caeAudioManager = remember { CaeAudioManager(context) }
    val standardAudioManager = remember { if (isEmulator) StandardAudioManager() else null }
    val videoStreamManager = remember { VideoStreamManager(wsRepo) }
    val conversationViewModel = remember {
        ConversationViewModel(
            wsRepo = wsRepo,
            ttsPlayer = ttsPlayer,
            caeAudioManager = caeAudioManager,
            videoStreamManager = videoStreamManager
        )
    }

    // Auto-connect using saved IP from Settings (if available)
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("smait_settings", Context.MODE_PRIVATE)
        val savedIp = prefs.getString("server_ip", null)
        val savedPort = prefs.getString("server_port", "8765")
        if (savedIp != null) {
            val url = "ws://$savedIp:$savedPort"
            Log.i(TAG, "Auto-connecting WebSocket to $url")
            wsRepo.connect(url)
        } else {
            Log.i(TAG, "No saved server IP — connect via Settings")
        }
    }

    // Start audio + video capture on launch
    LaunchedEffect(Unit) {
        if (isEmulator) {
            standardAudioManager?.setWriterCallback { bytes -> wsRepo.send(bytes) }
            standardAudioManager?.start()
            Log.i(TAG, "Started StandardAudioManager for emulator")
        }
        videoStreamManager.start(context)
        Log.i(TAG, "Started VideoStreamManager")
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            standardAudioManager?.stop()
            videoStreamManager.stop()
            wsRepo.disconnect()
        }
    }

    // No Scaffold/bottom bar — just NavHost filling the screen.
    // Sub-screens use their own top bar with a back/home button.
    NavHost(
        navController = navController,
        startDestination = Screen.Home,
        modifier = Modifier.fillMaxSize()
    ) {
        composable<Screen.Home> {
            HomeScreen(viewModel = homeViewModel, navController = navController)
        }
        composable<Screen.Chat> {
            ConversationScreen(viewModel = conversationViewModel, navController = navController)
        }
        composable<Screen.Map> {
            NavigationMapScreen(viewModel = navMapViewModel, navController = navController)
        }
        composable<Screen.Facilities> {
            FacilitiesScreen(viewModel = facilitiesViewModel, navController = navController)
        }
        composable<Screen.EventInfo> {
            EventInfoScreen(viewModel = eventInfoViewModel, navController = navController)
        }
        composable<Screen.Settings> {
            SettingsScreen(navController = navController)
        }
    }
}

/**
 * Detects whether the app is running on an Android emulator.
 */
private fun isEmulatorDevice(): Boolean {
    return (Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.MODEL.contains("google_sdk")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86")
            || Build.MODEL.contains("sdk_gphone")
            || Build.MANUFACTURER.contains("Genymotion")
            || Build.BRAND.startsWith("generic")
            || Build.DEVICE.startsWith("generic")
            || Build.PRODUCT.contains("sdk")
            || Build.HARDWARE.contains("goldfish")
            || Build.HARDWARE.contains("ranchu"))
}
