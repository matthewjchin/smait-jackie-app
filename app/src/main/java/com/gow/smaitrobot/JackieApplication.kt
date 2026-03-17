package com.gow.smaitrobot

import android.app.Application
import android.content.Context
import com.gow.smaitrobot.data.theme.ThemeRepository
import com.gow.smaitrobot.data.websocket.WebSocketRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Application subclass for the SMAIT Jackie robot app.
 *
 * Holds app-scoped singletons:
 * - [webSocketRepository] — OkHttp3 WebSocket client with SharedFlow event emission
 * - [themeRepository] — Loads WiE 2026 theme config from assets JSON
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

    override fun onCreate() {
        super.onCreate()

        // Build OkHttpClient with timeouts suitable for a persistent WebSocket
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)   // 0 = no timeout (WebSocket stays open)
            .writeTimeout(10, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS) // Keep-alive pings every 30s
            .build()

        webSocketRepository = WebSocketRepository(okHttpClient)
        themeRepository = ThemeRepository(this)

        // Load default BioRob theme synchronously — required before the first frame is rendered.
        // loadSync() uses IO on the calling thread; acceptable in Application.onCreate()
        // since it runs before any Activity starts.
        themeRepository.loadSync("default_theme.json")
    }
}

/** Extension property for convenient access to [JackieApplication] from any [Context]. */
val Context.jackieApp: JackieApplication
    get() = applicationContext as JackieApplication
