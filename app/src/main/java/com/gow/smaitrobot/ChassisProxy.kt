package com.gow.smaitrobot

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Proxy between the SMAIT server and Jackie's chassis rosbridge WebSocket.
 *
 * The server cannot reach the chassis directly (different network).
 * This proxy runs on Jackie (which is on both networks) and forwards
 * rosbridge JSON messages bidirectionally:
 *
 * Server → App (chassis_cmd) → Chassis (192.168.20.22:9090)
 * Chassis → App (chassis_msg) → Server
 *
 * The proxy is a dumb pipe — it does not parse rosbridge semantics.
 */
class ChassisProxy(
    private var chassisUrl: String = "ws://192.168.20.22:9090",
    private val serverSender: (String) -> Unit
) {
    companion object {
        private const val TAG = "ChassisProxy"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var chassisWs: WebSocket? = null
    private var isConnected = false

    /**
     * Connect to the chassis WebSocket.
     * Call once after the server connection is established.
     */
    fun connect() {
        Log.i(TAG, "Connecting to chassis at $chassisUrl")
        val request = Request.Builder().url(chassisUrl).build()
        chassisWs = client.newWebSocket(request, chassisListener)
    }

    /**
     * Disconnect from the chassis.
     */
    fun disconnect() {
        chassisWs?.close(1000, "App disconnect")
        chassisWs = null
        isConnected = false
    }

    /**
     * Reconnect to a new chassis URL (e.g. when the IP changes).
     * Disconnects the existing connection first, then opens a new one.
     */
    fun reconnect(newUrl: String) {
        Log.i(TAG, "Reconnecting chassis: $chassisUrl -> $newUrl")
        chassisWs?.close(1000, "URL change")
        chassisWs = null
        isConnected = false
        chassisUrl = newUrl
        connect()
    }

    /** True if currently connected to the chassis rosbridge. */
    val connected: Boolean get() = isConnected

    /**
     * Forward a chassis_cmd payload from the server to the chassis.
     * Called when the server sends {"type": "chassis_cmd", "payload": {...}}.
     *
     * @param payload The rosbridge JSON op as a string.
     */
    fun forwardToChassisRaw(payload: String) {
        if (!isConnected) {
            Log.w(TAG, "Cannot forward to chassis — not connected")
            return
        }
        chassisWs?.send(payload)
    }

    private val chassisListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "Chassis connected at $chassisUrl")
            isConnected = true

            // Advertise for both ROS 1 and ROS 2 topic types
            val topics = listOf("/cmd_vel", "cmd_vel")
            val types = listOf("geometry_msgs/Twist", "geometry_msgs/msg/Twist")

            for (topic in topics) {
                for (type in types) {
                    val advertise = JSONObject().apply {
                        put("op", "advertise")
                        put("topic", topic)
                        put("type", type)
                    }
                    webSocket.send(advertise.toString())
                }
            }

            // Notify server that chassis is connected
            val status = JSONObject().apply {
                put("type", "chassis_status")
                put("connected", true)
            }
            serverSender(status.toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            // Log incoming messages for diagnostics
            Log.d(TAG, "Incoming from Chassis: $text")

            // Forward chassis rosbridge message to server
            try {
                val envelope = JSONObject().apply {
                    put("type", "chassis_msg")
                    put("payload", JSONObject(text))
                }
                serverSender(envelope.toString())
            } catch (e: Exception) {
                // If the chassis message isn't valid JSON (shouldn't happen),
                // forward as raw string in payload
                Log.w(TAG, "Chassis message not JSON, forwarding raw: ${e.message}")
                val envelope = JSONObject().apply {
                    put("type", "chassis_msg")
                    put("payload", text)
                }
                serverSender(envelope.toString())
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "Chassis connection failed: ${t.message}")
            isConnected = false
            val status = JSONObject().apply {
                put("type", "chassis_status")
                put("connected", false)
            }
            serverSender(status.toString())

            // Auto-reconnect after 3 seconds
            Thread {
                Thread.sleep(3000)
                if (chassisWs != null) {
                    Log.i(TAG, "Reconnecting to chassis...")
                    connect()
                }
            }.start()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "Chassis disconnected: $reason")
            isConnected = false
            val status = JSONObject().apply {
                put("type", "chassis_status")
                put("connected", false)
            }
            serverSender(status.toString())
        }
    }
}
