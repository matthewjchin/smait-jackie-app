package com.gow.smaitrobot.ui.conversation

import android.util.Log
import com.gow.smaitrobot.CaeAudioManager
import com.gow.smaitrobot.TtsAudioPlayer
import com.gow.smaitrobot.data.model.ChatMessage
import com.gow.smaitrobot.data.model.FeedbackData
import com.gow.smaitrobot.data.model.RobotState
import com.gow.smaitrobot.data.model.UiEvent
import com.gow.smaitrobot.data.websocket.WebSocketEvent
import com.gow.smaitrobot.data.websocket.WebSocketRepository
import com.gow.smaitrobot.navigation.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

private const val TAG = "ConversationVM"
private const val SILENCE_TIMEOUT_MS = 120_000L  // 2 min — navigation can take a while

/**
 * ViewModel for the Conversation screen.
 *
 * Owns the full WebSocket event pipeline for a conversation session:
 * - Routes incoming JSON messages to transcript/robotState
 * - Routes incoming 0x05 binary frames to [TtsAudioPlayer]
 * - Wires [CaeAudioManager] outbound audio via writer callback → [WebSocketRepository.send]
 * - Manages [VideoStreamManager] lifecycle for continuous 0x02 JPEG frames
 * - Tracks robot session state to detect session-end and trigger feedback dialog
 * - Implements 30s silence timeout: auto-returns to Home if no WS messages arrive
 *
 * @param wsRepo            WebSocket data source (SharedFlow<WebSocketEvent>)
 * @param ttsPlayer         TTS audio player; receives 0x05 binary frames
 * @param caeAudioManager   CAE microphone manager; writer callback sends 0x01+0x03 outbound
 * @param videoStreamManager Camera2 JPEG frame sender; sends 0x02 outbound
 * @param coroutineScope    Injectable scope for testing (defaults to IO + SupervisorJob)
 */
class ConversationViewModel(
    private val wsRepo: WebSocketRepository,
    private val ttsPlayer: TtsAudioPlayer,
    private val caeAudioManager: CaeAudioManager,
    private val videoStreamManager: VideoStreamManager,
    coroutineScope: CoroutineScope? = null
) {
    private val scope: CoroutineScope = coroutineScope
        ?: CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── Transcript state ──────────────────────────────────────────────────────

    private val _transcript = MutableStateFlow<List<ChatMessage>>(emptyList())
    /** Live chat transcript — new messages appended at end (oldest first). */
    val transcript: StateFlow<List<ChatMessage>> = _transcript.asStateFlow()

    // ── Robot state ───────────────────────────────────────────────────────────

    private val _robotState = MutableStateFlow(RobotState.IDLE)
    /** Current robot behavior state — drives Lottie avatar animation. */
    val robotState: StateFlow<RobotState> = _robotState.asStateFlow()

    // ── Feedback / Camera state ───────────────────────────────────────────────

    private val _showFeedback = MutableStateFlow(false)
    /** True when the post-session feedback dialog should be shown. */
    val showFeedback: StateFlow<Boolean> = _showFeedback.asStateFlow()

    private val _showCamera = MutableStateFlow(false)
    /** True when the selfie capture overlay should be shown. */
    val showCamera: StateFlow<Boolean> = _showCamera.asStateFlow()

    // ── UI Events channel (one-shot navigation commands) ──────────────────────

    private val _uiEvents = Channel<UiEvent>(Channel.BUFFERED)
    /**
     * One-shot events consumed by the Composable.
     * Collect in a [LaunchedEffect] to handle NavigateTo events.
     */
    val uiEvents: Flow<UiEvent> = _uiEvents.receiveAsFlow()

    // ── Session tracking ──────────────────────────────────────────────────────

    /**
     * Tracks whether the robot was in an active (non-idle) state during this session.
     * Set true on first non-IDLE state; used to detect session-end when state returns to IDLE.
     */
    private var wasConversing = false

    // ── Silence timeout ───────────────────────────────────────────────────────

    private var silenceJob: Job? = null

    // ── Initialization ────────────────────────────────────────────────────────

    init {
        // Wire CaeAudioManager outbound: writer callback → wsRepo.send(bytes)
        // This is how 0x01 (beamformed) and 0x03 (raw 4ch) frames reach the server.
        caeAudioManager.setWriterCallback { bytes ->
            wsRepo.send(bytes)
        }

        // Start silence timeout — auto-return to Home if no WS activity for 30s
        resetSilenceTimer()

        // Collect WebSocket events
        scope.launch {
            wsRepo.events.collect { event ->
                // Any event resets the silence timer (server is active)
                resetSilenceTimer()

                when (event) {
                    is WebSocketEvent.JsonMessage -> handleJsonMessage(event)
                    is WebSocketEvent.BinaryFrame -> handleBinaryFrame(event.bytes)
                    is WebSocketEvent.Connected -> Log.d(TAG, "WS connected")
                    is WebSocketEvent.Disconnected -> Log.d(TAG, "WS disconnected: ${event.reason}")
                }
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Submit user feedback and auto-return to Home.
     * Sends JSON to server, hides feedback dialog, clears transcript, navigates home.
     */
    fun sendFeedback(feedback: FeedbackData) {
        val json = buildFeedbackJson(feedback)
        wsRepo.send(json)
        _showFeedback.value = false
        clearTranscript()
        scope.launch {
            _uiEvents.send(UiEvent.NavigateTo(Screen.Home))
        }
    }

    /**
     * Dismiss the feedback dialog without submitting.
     * Auto-returns to Home via UiEvent.
     */
    fun dismissFeedback() {
        _showFeedback.value = false
        clearTranscript()
        scope.launch {
            _uiEvents.send(UiEvent.NavigateTo(Screen.Home))
        }
    }

    /** Toggle the selfie camera overlay. */
    fun toggleCamera() {
        _showCamera.value = !_showCamera.value
    }

    /** Clear the transcript list (called on session end, feedback submit/dismiss). */
    fun clearTranscript() {
        _transcript.value = emptyList()
        wasConversing = false
    }

    /** Clean up resources. Call when the screen is removed from composition. */
    fun onCleared() {
        caeAudioManager.stop()
        videoStreamManager.stop()
        silenceJob?.cancel()
        scope.cancel()
    }

    // ── Private implementation ────────────────────────────────────────────────

    private fun handleJsonMessage(event: WebSocketEvent.JsonMessage) {
        val payload = event.payload
        when (event.type) {
            "transcript" -> {
                val text = parseTextField(payload, "text") ?: return
                appendMessage(ChatMessage(id = UUID.randomUUID().toString(), text = text, isUser = true))
            }
            "response" -> {
                val text = parseTextField(payload, "text") ?: return
                appendMessage(ChatMessage(id = UUID.randomUUID().toString(), text = text, isUser = false))
            }
            "state" -> {
                val value = parseTextField(payload, "value") ?: return
                val newState = mapRobotState(value)
                val prevState = _robotState.value

                _robotState.value = newState

                if (newState != RobotState.IDLE) {
                    wasConversing = true
                } else if (wasConversing && prevState != RobotState.IDLE) {
                    // Transitioned from active → idle: session ended
                    _showFeedback.value = true
                }
            }
            "tts_control" -> {
                // TTS control messages (start/stop) — forward to TTS player for stop handling
                val cmd = parseTextField(payload, "command")
                if (cmd == "stop") ttsPlayer.stop()
            }
            else -> Log.v(TAG, "Ignoring JSON message type: ${event.type}")
        }
    }

    private fun handleBinaryFrame(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        when (bytes[0]) {
            0x05.toByte() -> {
                // TTS audio from Kokoro — play it
                ttsPlayer.handleBinaryFrame(bytes)
            }
            else -> {
                // Other binary frames (0x01, 0x03 are outbound CAE; 0x02 is outbound video)
                // Nothing to do for inbound unknown frames
                Log.v(TAG, "Ignoring inbound binary frame type: 0x${bytes[0].toUByte().toString(16)}")
            }
        }
    }

    /**
     * Appends a [ChatMessage] to the end of the transcript list.
     * Creates a new immutable list (no mutation).
     */
    private fun appendMessage(message: ChatMessage) {
        _transcript.value = _transcript.value + message
    }

    /**
     * Maps a state value string from the server to a [RobotState] enum.
     * Unknown values fall back to [RobotState.IDLE].
     */
    private fun mapRobotState(value: String): RobotState = when (value.lowercase()) {
        "listening" -> RobotState.LISTENING
        "thinking"  -> RobotState.THINKING
        "speaking"  -> RobotState.SPEAKING
        "idle"      -> RobotState.IDLE
        else        -> {
            Log.w(TAG, "Unknown robot state value: $value")
            RobotState.IDLE
        }
    }

    /**
     * Parses a string field from a JSON payload string.
     * Returns null if the field is absent or empty.
     */
    private fun parseTextField(payload: String, field: String): String? {
        return try {
            JSONObject(payload).optString(field).takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w(TAG, "JSON parse error for field '$field': ${e.message}")
            null
        }
    }

    /**
     * Serializes [FeedbackData] to JSON for server transmission.
     * Format: {"type":"feedback","rating":N,"session_id":"...",
     *          "survey_responses":{...},"timestamp":N}
     */
    private fun buildFeedbackJson(feedback: FeedbackData): String {
        return JSONObject().apply {
            put("type", "feedback")
            put("rating", feedback.rating)
            put("session_id", feedback.sessionId)
            put("timestamp", feedback.timestamp)
            val responses = JSONObject()
            feedback.surveyResponses.forEach { (k, v) -> responses.put(k, v) }
            put("survey_responses", responses)
        }.toString()
    }

    /**
     * Resets (or starts) the 30-second silence timeout.
     *
     * Called on every incoming WebSocket event. If no events arrive within
     * [SILENCE_TIMEOUT_MS] ms, emits [UiEvent.NavigateTo] to return to Home.
     */
    private fun resetSilenceTimer() {
        silenceJob?.cancel()
        silenceJob = scope.launch {
            delay(SILENCE_TIMEOUT_MS)
            Log.d(TAG, "Silence timeout — returning to Home")
            clearTranscript()
            _uiEvents.send(UiEvent.NavigateTo(Screen.Home))
        }
    }
}
