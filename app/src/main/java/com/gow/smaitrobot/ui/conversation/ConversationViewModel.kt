package com.gow.smaitrobot.ui.conversation

import android.util.Log
import com.gow.smaitrobot.CaeAudioManager
import com.gow.smaitrobot.TtsAudioPlayer
import com.gow.smaitrobot.data.model.ChatMessage
import com.gow.smaitrobot.data.model.RobotState
import com.gow.smaitrobot.data.model.SurveyData
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
private const val SILENCE_TIMEOUT_MS = 30_000L

/**
 * ViewModel for the Conversation screen.
 *
 * Owns the full WebSocket event pipeline for a conversation session:
 * - Routes incoming JSON messages to transcript/robotState
 * - Routes incoming 0x05 binary frames to [TtsAudioPlayer]
 * - Wires [CaeAudioManager] outbound audio via writer callback -> [WebSocketRepository.send]
 * - Manages [VideoStreamManager] lifecycle for continuous 0x02 JPEG frames
 * - Tracks robot session state to detect session-end and trigger survey screen
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

    // -- Transcript state --

    private val _transcript = MutableStateFlow<List<ChatMessage>>(emptyList())
    /** Live chat transcript -- new messages appended at end (oldest first). */
    val transcript: StateFlow<List<ChatMessage>> = _transcript.asStateFlow()

    // -- Robot state --

    private val _robotState = MutableStateFlow(RobotState.IDLE)
    /** Current robot behavior state -- drives Lottie avatar animation. */
    val robotState: StateFlow<RobotState> = _robotState.asStateFlow()

    // -- Survey / Camera state --

    private val _showSurvey = MutableStateFlow(false)
    /** True when the post-session survey screen should be shown. */
    val showSurvey: StateFlow<Boolean> = _showSurvey.asStateFlow()

    private val _showCamera = MutableStateFlow(false)
    /** True when the selfie capture overlay should be shown. */
    val showCamera: StateFlow<Boolean> = _showCamera.asStateFlow()

    // -- UI Events channel (one-shot navigation commands) --

    private val _uiEvents = Channel<UiEvent>(Channel.BUFFERED)
    /**
     * One-shot events consumed by the Composable.
     * Collect in a [LaunchedEffect] to handle NavigateTo events.
     */
    val uiEvents: Flow<UiEvent> = _uiEvents.receiveAsFlow()

    // -- Session tracking --

    /**
     * Tracks whether the robot was in an active (non-idle) state during this session.
     * Set true on first non-IDLE state; used to detect session-end when state returns to IDLE.
     */
    private var wasConversing = false

    /** True while a session is active; prevents double session_command/end. */
    private var sessionActive = false

    // -- Navigation state --

    private var isNavigating = false

    // -- Silence timeout --

    private var silenceJob: Job? = null

    // -- Initialization --

    init {
        // Wire CaeAudioManager outbound: writer callback -> wsRepo.send(bytes)
        caeAudioManager.setWriterCallback { bytes ->
            wsRepo.send(bytes)
        }

        // Start silence timeout
        resetSilenceTimer()

        // Collect WebSocket events
        scope.launch {
            wsRepo.events.collect { event ->
                resetSilenceTimer()

                when (event) {
                    is WebSocketEvent.JsonMessage -> handleJsonMessage(event)
                    is WebSocketEvent.BinaryFrame -> handleBinaryFrame(event.bytes)
                    is WebSocketEvent.Connected -> {
                        Log.d(TAG, "WS connected")
                        // Send session start AFTER connection is established
                        sendSessionCommand("start")
                    }
                    is WebSocketEvent.Disconnected -> Log.d(TAG, "WS disconnected: ${event.reason}")
                }
            }
        }
    }

    // -- Public API --

    /**
     * Called when ConversationScreen appears. Starts a fresh session:
     * clears old transcript, resets state, tells server to start.
     */
    fun onScreenEntered() {
        clearTranscript()
        _robotState.value = RobotState.IDLE
        _showSurvey.value = false
        _showCamera.value = false
        sessionActive = true
        sendSessionCommand("start")
        resetSilenceTimer()
    }

    /**
     * Called when user presses back button. If conversation happened, shows survey.
     * Otherwise navigates straight home.
     */
    fun onBackPressed() {
        if (wasConversing) {
            _showSurvey.value = true
        } else {
            endSession()
            scope.launch {
                _uiEvents.send(UiEvent.NavigateTo(Screen.Home))
            }
        }
    }

    /**
     * Called when leaving ConversationScreen (DisposableEffect).
     * Ends the server session and clears local state. Safe to call multiple times.
     */
    fun onScreenExited() {
        if (sessionActive) {
            sendSessionCommand("end")
            sessionActive = false
        }
        clearTranscript()
        silenceJob?.cancel()
    }

    /**
     * Submit the post-session survey and return to Home.
     * Sends survey JSON to server, then session_command("end"), clears transcript, navigates home.
     */
    fun submitSurvey(survey: SurveyData) {
        val json = buildSurveyJson(survey)
        wsRepo.send(json)
        endSession()
        scope.launch {
            _uiEvents.send(UiEvent.NavigateTo(Screen.Home))
        }
    }

    /**
     * Dismiss the survey (auto-timeout case).
     * Sends the partial survey data, then session_command("end"), navigates home.
     */
    fun dismissSurvey(survey: SurveyData) {
        val json = buildSurveyJson(survey)
        wsRepo.send(json)
        endSession()
        scope.launch {
            _uiEvents.send(UiEvent.NavigateTo(Screen.Home))
        }
    }

    /** End the current session: tell server, clear state, mark inactive. */
    private fun endSession() {
        if (sessionActive) {
            sendSessionCommand("end")
            sessionActive = false
        }
        _showSurvey.value = false
        clearTranscript()
        silenceJob?.cancel()
    }

    /** Toggle the selfie camera overlay. */
    fun toggleCamera() {
        _showCamera.value = !_showCamera.value
    }

    /** Send a selfie bitmap to the server for logging alongside session data. */
    fun sendSelfie(bitmap: android.graphics.Bitmap) {
        sendSelfieToServer(bitmap, wsRepo)
    }

    /** Clear the transcript list (called on session end, survey submit/dismiss). */
    fun clearTranscript() {
        _transcript.value = emptyList()
        wasConversing = false
    }

    /** Clean up resources. Call when the screen is removed from composition. */
    fun onCleared() {
        sendSessionCommand("end")
        caeAudioManager.stop()
        videoStreamManager.stop()
        silenceJob?.cancel()
        scope.cancel()
    }

    // -- Private implementation --

    private fun handleJsonMessage(event: WebSocketEvent.JsonMessage) {
        val payload = event.payload
        when (event.type) {
            "transcript" -> {
                val text = parseTextField(payload, "text") ?: return
                val speaker = parseTextField(payload, "speaker") ?: "user"
                val isUser = speaker != "robot"
                appendMessage(ChatMessage(id = UUID.randomUUID().toString(), text = text, isUser = isUser))
            }
            "response" -> {
                val text = parseTextField(payload, "text") ?: return
                appendMessage(ChatMessage(id = UUID.randomUUID().toString(), text = text, isUser = false))
            }
            "state" -> {
                // Server sends: {"type":"state", "state":"idle"|"engaged", "robot_status":"listening"|...}
                val sessionState = parseTextField(payload, "state") ?: "engaged"
                val robotStatus = parseTextField(payload, "robot_status") ?: "listening"
                val newState = if (sessionState == "idle") RobotState.IDLE else mapRobotState(robotStatus)
                val prevState = _robotState.value

                _robotState.value = newState

                if (newState != RobotState.IDLE) {
                    wasConversing = true
                } else if (wasConversing && prevState != RobotState.IDLE) {
                    // Session ended (goodbye or server timeout): show survey
                    _showSurvey.value = true
                }
            }
            "nav_status" -> {
                val status = parseTextField(payload, "status") ?: return
                when (status) {
                    "navigating" -> {
                        isNavigating = true
                        silenceJob?.cancel()
                    }
                    "arrived", "failed" -> {
                        isNavigating = false
                        resetSilenceTimer()
                    }
                }
            }
            "tts_control" -> {
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
                ttsPlayer.handleBinaryFrame(bytes)
            }
            else -> {
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
     * Sends a session_command to the server to start or end a conversation session.
     */
    private fun sendSessionCommand(action: String) {
        val json = JSONObject().apply {
            put("type", "session_command")
            put("action", action)
        }.toString()
        wsRepo.send(json)
        Log.d(TAG, "Sent session_command: $action")
    }

    /**
     * Serializes [SurveyData] to JSON for server transmission.
     *
     * Format:
     * ```json
     * {
     *   "type": "survey",
     *   "star_rating": 4,
     *   "understood": 5,
     *   "helpful": 4,
     *   "natural": 3,
     *   "attentive": 4,
     *   "comment": "Great robot!",
     *   "completed": true,
     *   "time_to_complete_ms": 8500,
     *   "timestamp": 1711100000000
     * }
     * ```
     */
    private fun buildSurveyJson(survey: SurveyData): String {
        return JSONObject().apply {
            put("type", "survey")
            put("star_rating", survey.starRating)
            put("understood", survey.understood)
            put("helpful", survey.helpful)
            put("natural", survey.natural)
            put("attentive", survey.attentive)
            put("comment", survey.comment)
            put("completed", survey.completedInTime)
            put("time_to_complete_ms", survey.timeToCompleteMs)
            put("timestamp", survey.timestamp)
        }.toString()
    }

    /**
     * Resets (or starts) the 30-second silence timeout.
     */
    private fun resetSilenceTimer() {
        silenceJob?.cancel()
        if (isNavigating) return
        silenceJob = scope.launch {
            delay(SILENCE_TIMEOUT_MS)
            Log.d(TAG, "Silence timeout -- ending session and returning to Home")
            endSession()
            _uiEvents.send(UiEvent.NavigateTo(Screen.Home))
        }
    }
}
