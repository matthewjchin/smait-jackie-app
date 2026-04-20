package com.gow.smaitrobot

import android.app.Application
import android.content.Context
import android.util.Log
import com.gow.smaitrobot.data.theme.ThemeRepository
import com.gow.smaitrobot.data.websocket.WebSocketRepository
import com.gow.smaitrobot.ChassisProxy
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Application subclass for the SMAIT Jackie robot app.
 *
 * Holds app-scoped singletons:
 * - [webSocketRepository] — OkHttp3 WebSocket client with SharedFlow event emission
 * - [themeRepository] — Loads event theme config from assets JSON
 *
 * Access from any [Context] via the [jackieApp] extension property:
 * ```kotlin
 * val repo = context.jackieApp.webSocketRepository
 * ```
 */
class JackieApplication : Application() {

    lateinit var webSocketRepository: WebSocketRepository
        private set

    lateinit var themeRepository: ThemeRepository
        private set

    lateinit var ttsAudioPlayer: TtsAudioPlayer
        private set

    lateinit var chassisProxy: ChassisProxy
        private set

    override fun onCreate() {
        super.onCreate()

        System.loadLibrary("opencv_java4")

        // Build OkHttpClient with timeouts suitable for a persistent WebSocket
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)   // 0 = no timeout (WebSocket stays open)
            .writeTimeout(10, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS) // Keep-alive pings every 30s
            .build()

        webSocketRepository = WebSocketRepository(okHttpClient)
        themeRepository = ThemeRepository(this)
        ttsAudioPlayer = TtsAudioPlayer().also { player ->
            player.start()
            // Restore saved volume from SharedPreferences
            val prefs = getSharedPreferences("smait_settings", MODE_PRIVATE)
            player.setVolume(prefs.getFloat("tts_volume", 0.5f))
        }

        // Load event theme synchronously — required before the first frame is rendered.
        // loadSync() uses IO on the calling thread; acceptable in Application.onCreate()
        // since it runs before any Activity starts.
        themeRepository.loadSync("wie2026_theme.json")

        // Chassis Proxy lifecycle: Start immediately so Follow Mode works offline
        val isEmulator = android.os.Build.PRODUCT.contains("sdk") || android.os.Build.MODEL.contains("Emulator")
        val appPrefs = getSharedPreferences("smait_settings", MODE_PRIVATE)
        val chassisIp   = if (isEmulator) "10.0.2.2"
                          else appPrefs.getString("chassis_ip", "192.168.20.22") ?: "192.168.20.22"
        val chassisPort = appPrefs.getString("chassis_port", "9090") ?: "9090"
        Log.i("JackieApplication", "Connecting to Chassis at ws://$chassisIp:$chassisPort (Emulator=$isEmulator)")

        chassisProxy = ChassisProxy(
            chassisUrl = "ws://$chassisIp:$chassisPort",
            serverSender = { json: String ->
                Log.v("JackieApplication", "Forwarding to Server: $json")
                webSocketRepository.send(json)
            }
        )
        chassisProxy.connect()
        themeRepository.loadSync("hfes2026_theme.json")
    }
}

/** Extension property for convenient access to [JackieApplication] from any [Context]. */
val Context.jackieApp: JackieApplication
    get() = applicationContext as JackieApplication
