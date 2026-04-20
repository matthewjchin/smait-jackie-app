package com.gow.smaitrobot.navigation

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.gow.smaitrobot.CaeAudioManager
import com.gow.smaitrobot.ChassisProxy
// StandardAudioManager removed — Jackie-only build
import com.gow.smaitrobot.TtsAudioPlayer
import com.gow.smaitrobot.data.model.ThemeConfig
import com.gow.smaitrobot.data.websocket.WebSocketRepository
import com.gow.smaitrobot.follow.FollowController
import com.gow.smaitrobot.jackieApp
import com.gow.smaitrobot.ui.conversation.ConversationScreen
import com.gow.smaitrobot.ui.conversation.ConversationViewModel
import com.gow.smaitrobot.ui.follow.FollowScreen
import com.gow.smaitrobot.ui.home.HomeScreen
import com.gow.smaitrobot.ui.home.HomeViewModel
import com.gow.smaitrobot.ui.settings.SettingsScreen

private const val TAG = "AppNavigation"

/**
 * Root navigation for the Jackie app.
 *
 * Hosts the [NavHost] and global state objects (repos, managers) that must
 * persist across screen changes.
 */
@Composable
fun AppScaffold(
    navController: NavHostController,
    themeConfig: ThemeConfig
) {
    val context = LocalContext.current
    val wsRepo = context.jackieApp.webSocketRepository
    val themeRepo = context.jackieApp.themeRepository
    // Jackie-only build — no emulator detection needed

    // Core Repositories + Managers
    val wsRepo = remember { context.jackieApp.webSocketRepository }
    val themeRepo = remember { context.jackieApp.themeRepository }
    val ttsPlayer = remember { context.jackieApp.ttsAudioPlayer }
    val caeAudioManager = remember { CaeAudioManager(context) }
    // No StandardAudioManager — Jackie uses CaeAudioManager only
    val videoStreamManager = remember { VideoStreamManager(wsRepo) }
    val homeViewModel = remember { HomeViewModel(themeRepo) }
    val conversationViewModel = remember {
        ConversationViewModel(
            wsRepo = wsRepo,
            ttsPlayer = ttsPlayer,
            caeAudioManager = caeAudioManager,
            videoStreamManager = videoStreamManager
        )
    }

    val followController = remember {
        FollowController(
            chassisSender = { json ->
                context.jackieApp.chassisProxy.forwardToChassisRaw(json)
            }
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

    // Start audio + video capture once WebSocket connects
    val isConnected by wsRepo.isConnected.collectAsStateWithLifecycle()
    LaunchedEffect(isConnected) {
        if (isConnected) {
            // Tell server which event app is connected — server injects event context into LLM
            val appMode = context.jackieApp.themeRepository.config.value.appMode
            if (appMode.isNotEmpty()) {
                wsRepo.send(org.json.JSONObject().apply {
                    put("type", "app_mode")
                    put("mode", appMode)
                }.toString())
                Log.i(TAG, "Sent app_mode: $appMode")
            }

            // Jackie: copy CAE assets and start beamformed audio
            caeAudioManager.copyAssetsIfNeeded()
            val ws = wsRepo.currentWebSocket
            if (ws != null) {
                caeAudioManager.start(ws)
                Log.i(TAG, "Started CaeAudioManager for Jackie")
            } else {
                Log.w(TAG, "WebSocket connected but currentWebSocket is null")
            }

            // Start chassis proxy — bridges server ↔ chassis (192.168.20.22:9090)
            val proxy = ChassisProxy(
                chassisUrl = "ws://192.168.20.22:9090",
                serverSender = { json: String -> wsRepo.send(json) }
            )
            proxy.connect()
            context.jackieApp.chassisProxy = proxy
            wsRepo.chassisProxy = proxy
            Log.i(TAG, "Started ChassisProxy")
            videoStreamManager.start(context)
            Log.i(TAG, "Started VideoStreamManager")
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            caeAudioManager.stop()
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
            HomeScreen(
                viewModel = homeViewModel,
                navController = navController
            )
        }
        composable<Screen.Chat> {
            ConversationScreen(
                viewModel = conversationViewModel,
                navController = navController
            )
        }
        composable<Screen.PhotoBooth> {
            PhotoBoothScreen(
                navController = navController,
                wsRepo = wsRepo,
            )
        }
        composable<Screen.Settings> {
            SettingsScreen(
                navController = navController
            )
        }
        composable<Screen.Follow> {
            FollowScreen(followController = followController, navController = navController)
        }
    }
}

