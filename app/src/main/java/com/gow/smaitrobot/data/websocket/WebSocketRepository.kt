package com.gow.smaitrobot.data.websocket

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject

private const val TAG = "WebSocketRepository"

/**
 * OkHttp3 WebSocket client that emits all incoming frames as [WebSocketEvent] via SharedFlow.
 *
 * Binary frames are passed through as-is ([BinaryFrame]) — the type byte at index 0 is
 * preserved so consumers can route frames without transformation in the repository layer.
 *
 * JSON text messages are parsed minimally: only the "type" field is extracted to populate
 * [JsonMessage.type]. The full raw JSON string is passed as [JsonMessage.payload] for
 * consumers to deserialize into domain objects.
 *
 * Auto-reconnects on failure with exponential backoff: 1s → 2s → 4s → 8s → … → 30s max.
 *
 * @param client Injectable [OkHttpClient] — supply a mock in tests.
 */
class WebSocketRepository(
    private val client: OkHttpClient
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _events = MutableSharedFlow<WebSocketEvent>(extraBufferCapacity = 64)

    /** All WebSocket events: [Connected], [BinaryFrame], [JsonMessage], [Disconnected]. */
    val events: SharedFlow<WebSocketEvent> = _events.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)

    /** True when the WebSocket connection is open. */
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var webSocket: WebSocket? = null
    private var lastUrl: String? = null
    private var reconnectDelayMs: Long = 1_000L
    private var reconnecting = false

    /**
     * Opens a WebSocket connection to [url].
     * Registers the internal [WebSocketListener] to route all frames to [events].
     */
    fun connect(url: String) {
        lastUrl = url
        reconnectDelayMs = 1_000L
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, listener)
    }

    /**
     * Sends raw binary data to the server.
     * No-op if not connected.
     */
    fun send(bytes: ByteArray) {
        webSocket?.send(bytes.toByteString())
    }

    /**
     * Sends a JSON text message to the server.
     * No-op if not connected.
     */
    fun send(json: String) {
        webSocket?.send(json)
    }

    /**
     * Closes the WebSocket connection cleanly.
     * Cancels any pending reconnect.
     */
    fun disconnect() {
        reconnecting = false
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
    }

    /** Releases the coroutine scope. Call when the repository is no longer needed. */
    fun shutdown() {
        disconnect()
        scope.cancel()
    }

    // ── WebSocketListener ────────────────────────────────────────────────────

    private val listener = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "onOpen: connected")
            _isConnected.value = true
            reconnectDelayMs = 1_000L
            reconnecting = false
            emitEvent(WebSocketEvent.Connected)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            emitEvent(WebSocketEvent.BinaryFrame(bytes.toByteArray()))
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val type = try {
                JSONObject(text).optString("type", "unknown")
            } catch (e: Exception) {
                "unknown"
            }
            emitEvent(WebSocketEvent.JsonMessage(type, text))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "onFailure: ${t.message}")
            _isConnected.value = false
            emitEvent(WebSocketEvent.Disconnected(t.message))
            scheduleReconnect()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "onClosed: code=$code reason=$reason")
            _isConnected.value = false
            emitEvent(WebSocketEvent.Disconnected("closed"))
        }
    }

    /** Emits an event on the SharedFlow using a fire-and-forget coroutine. */
    private fun emitEvent(event: WebSocketEvent) {
        // tryEmit is safe here because extraBufferCapacity=64 absorbs bursts
        _events.tryEmit(event)
    }

    /**
     * Schedules a reconnect attempt after an exponential backoff delay.
     * Backoff: 1s → 2s → 4s → 8s → 16s → 30s (capped).
     */
    private fun scheduleReconnect() {
        val url = lastUrl ?: return
        if (reconnecting) return
        reconnecting = true

        val delayMs = reconnectDelayMs
        reconnectDelayMs = minOf(reconnectDelayMs * 2, 30_000L)

        scope.launch {
            Log.d(TAG, "Reconnecting in ${delayMs}ms…")
            delay(delayMs)
            if (reconnecting) {
                reconnecting = false
                connect(url)
            }
        }
    }
}
