package com.gow.smaitrobot.data.websocket

/**
 * Sealed class representing all events emitted by [WebSocketRepository].
 *
 * Consumers (ViewModels, AudioManagers) collect the SharedFlow<WebSocketEvent>
 * and handle each variant:
 *
 * - [BinaryFrame] — raw binary frame with type byte preserved at index 0
 *   (0x01=CAE audio, 0x02=video, 0x03=raw 4ch, 0x05=TTS, 0x06=map PNG)
 * - [JsonMessage] — JSON text message with extracted type field and raw payload
 *   (transcript, response, tts_control, nav_status, doa, cae_status, feedback)
 * - [Connected] — WebSocket connection opened
 * - [Disconnected] — WebSocket connection closed or failed, with optional reason
 */
sealed class WebSocketEvent {

    /**
     * A binary WebSocket frame received from the server.
     *
     * The first byte ([bytes][0]) is the frame type per the SMAIT protocol:
     * 0x01=CAE audio, 0x02=video frame, 0x03=raw 4-channel audio,
     * 0x05=TTS audio, 0x06=map PNG.
     *
     * The full byte array is passed through without transformation — consumers
     * inspect [bytes][0] to determine routing.
     */
    data class BinaryFrame(val bytes: ByteArray) : WebSocketEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is BinaryFrame) return false
            return bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = bytes.contentHashCode()
    }

    /**
     * A JSON text message received from the server.
     *
     * @param type    The value of the "type" field extracted from the JSON object.
     *                Falls back to "unknown" if the field is absent.
     * @param payload The full raw JSON string, unmodified. Consumers parse this
     *                into domain objects using their own deserialization logic.
     */
    data class JsonMessage(
        val type: String,
        val payload: String
    ) : WebSocketEvent()

    /** WebSocket connection successfully opened. */
    object Connected : WebSocketEvent()

    /**
     * WebSocket connection closed or failed.
     *
     * @param reason Human-readable reason, or null if not available.
     *               Set to "closed" for normal closure ([onClosed]) or
     *               the exception message for failures ([onFailure]).
     */
    data class Disconnected(val reason: String?) : WebSocketEvent()
}

// Type aliases for convenience imports
typealias BinaryFrame = WebSocketEvent.BinaryFrame
typealias JsonMessage = WebSocketEvent.JsonMessage
typealias Connected = WebSocketEvent.Connected
typealias Disconnected = WebSocketEvent.Disconnected
