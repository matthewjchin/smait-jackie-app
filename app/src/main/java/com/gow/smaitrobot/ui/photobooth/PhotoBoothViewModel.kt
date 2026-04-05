package com.gow.smaitrobot.ui.photobooth

import android.graphics.Bitmap
import com.gow.smaitrobot.R
import com.gow.smaitrobot.data.websocket.WebSocketEvent
import com.gow.smaitrobot.data.websocket.WebSocketRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Sealed class representing all Photo Booth UI states.
 *
 * State machine flow: StylePicker -> Countdown -> Processing -> Result
 * onRetake() resets from any state back to StylePicker.
 */
sealed class PhotoBoothUiState {

    /** User is selecting a style. [selectedStyle] is null until the user taps a card. */
    data class StylePicker(val selectedStyle: String? = null) : PhotoBoothUiState()

    /** 3-2-1 countdown in progress. [secondsLeft] decrements 3 -> 2 -> 1 each second. */
    data class Countdown(val secondsLeft: Int, val selectedStyle: String) : PhotoBoothUiState()

    /** SD style transfer in progress. Spinner + style name shown. */
    data class Processing(val styleName: String) : PhotoBoothUiState()

    /**
     * Style result received. [rawBitmap] is the original capture (set by Plan 02).
     * [styledBitmap] and [downloadUrl] arrive from server events.
     */
    data class Result(
        val rawBitmap: Bitmap?,
        val styledBitmap: Bitmap?,
        val downloadUrl: String?
    ) : PhotoBoothUiState()
}

/**
 * Represents a single style option in the style picker grid.
 *
 * @param key       Server-side key (must match STYLE_REGISTRY keys in smait server)
 * @param label     Human-readable display name
 * @param thumbResId Drawable resource ID for the style thumbnail
 */
data class StyleOption(
    val key: String,
    val label: String,
    val thumbResId: Int
)

/** All 6 available style options, mapped to bundled thumbnail drawables. */
val STYLE_OPTIONS = listOf(
    StyleOption("cyberpunk", "Cyberpunk", R.drawable.style_thumb_cyberpunk),
    StyleOption("anime", "Anime", R.drawable.style_thumb_anime),
    StyleOption("pop_art", "Pop Art", R.drawable.style_thumb_pop_art),
    StyleOption("robot_vision", "Robot Vision", R.drawable.style_thumb_robot_vision),
    StyleOption("oil_painting", "Oil Painting", R.drawable.style_thumb_oil_painting),
    StyleOption("pixel_art", "Pixel Art", R.drawable.style_thumb_pixel_art)
)

/**
 * ViewModel for the Photo Booth screen.
 *
 * Manages the state machine: StylePicker -> Countdown -> Processing -> Result.
 * Communicates with the server via [wsRepo]:
 * - Sends `photo_booth_enter` / `photo_booth_exit` lifecycle messages
 * - Collects server events: style_processing, styled_result, qr_code, photo_booth_error
 *
 * [coroutineScope] is injectable for unit testing (use TestScope with StandardTestDispatcher).
 * At runtime, pass null to use an internal IO-based scope.
 *
 * NOTE: Plan 02 wires the actual camera capture. For now, onTakePhoto() transitions to
 * Processing immediately after the countdown, without a real capture.
 */
class PhotoBoothViewModel(
    private val wsRepo: WebSocketRepository,
    coroutineScope: CoroutineScope? = null
) {
    private val scope: CoroutineScope =
        coroutineScope ?: CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _uiState = MutableStateFlow<PhotoBoothUiState>(PhotoBoothUiState.StylePicker())

    /** Current UI state. Observed by [PhotoBoothScreen] to drive which composable is shown. */
    val uiState: StateFlow<PhotoBoothUiState> = _uiState.asStateFlow()

    /** Holds the raw capture bitmap for the Result state. Set by Plan 02 capture callback. */
    var rawBitmap: Bitmap? = null

    /** Countdown coroutine job — cancelled on retake. */
    private var countdownJob: Job? = null

    init {
        // Collect server events to handle style_processing / styled_result / qr_code / error
        scope.launch {
            wsRepo.events.collect { event ->
                if (event is WebSocketEvent.JsonMessage) {
                    handleServerMessage(event.type, event.payload)
                }
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * User tapped a style card. Updates [selectedStyle] in the StylePicker state.
     * No-op if current state is not StylePicker.
     */
    fun onStyleSelected(key: String) {
        val current = _uiState.value
        if (current is PhotoBoothUiState.StylePicker) {
            _uiState.value = current.copy(selectedStyle = key)
        }
    }

    /**
     * User tapped the Take Photo button.
     * Launches the 3-2-1 countdown coroutine, then transitions to Processing.
     * Only valid when a style is selected in StylePicker state.
     */
    fun onTakePhoto() {
        val current = _uiState.value
        val selectedStyle = when (current) {
            is PhotoBoothUiState.StylePicker -> current.selectedStyle ?: return
            else -> return
        }

        countdownJob?.cancel()
        countdownJob = scope.launch {
            _uiState.value = PhotoBoothUiState.Countdown(3, selectedStyle)
            delay(1_000L)
            _uiState.value = PhotoBoothUiState.Countdown(2, selectedStyle)
            delay(1_000L)
            _uiState.value = PhotoBoothUiState.Countdown(1, selectedStyle)
            delay(1_000L)
            // Plan 02 will invoke actual camera capture here.
            // For now, transition directly to Processing.
            _uiState.value = PhotoBoothUiState.Processing(selectedStyle)
        }
    }

    /**
     * Sends `photo_booth_enter` JSON to the server when the screen is shown.
     * Called from [PhotoBoothScreen] via LaunchedEffect(Unit).
     */
    fun onScreenEntered() {
        wsRepo.send("""{"type":"photo_booth_enter"}""")
    }

    /**
     * Sends `photo_booth_exit` JSON to the server and cancels the coroutine scope
     * when the screen is disposed.
     * Called from [PhotoBoothScreen] via DisposableEffect onDispose.
     */
    fun onScreenExited() {
        wsRepo.send("""{"type":"photo_booth_exit"}""")
        scope.cancel()
    }

    /**
     * Resets state to StylePicker (preserves no selection).
     * Does NOT send any WebSocket messages — server remains in PHOTO_BOOTH state for retake.
     */
    fun onRetake() {
        countdownJob?.cancel()
        _uiState.value = PhotoBoothUiState.StylePicker()
    }

    // ── Server message handler ─────────────────────────────────────────────────

    private fun handleServerMessage(type: String, payload: String) {
        when (type) {
            "style_processing" -> {
                // Server acknowledged the style and started processing
                // State is already Processing — no UI change needed
            }
            "styled_result" -> {
                // Plan 02/03 decodes styledBitmap from base64 payload
                val current = _uiState.value
                if (current is PhotoBoothUiState.Processing) {
                    _uiState.value = PhotoBoothUiState.Result(
                        rawBitmap = rawBitmap,
                        styledBitmap = null, // Plan 02 decodes and sets this
                        downloadUrl = null
                    )
                }
            }
            "qr_code" -> {
                // Update Result state with download URL
                val current = _uiState.value
                if (current is PhotoBoothUiState.Result) {
                    // Extract download_url from JSON using simple string parsing
                    // (avoids org.json in runtime; Plan 03 can use kotlinx.serialization)
                    val downloadUrl = extractJsonString(payload, "download_url")
                    _uiState.value = current.copy(downloadUrl = downloadUrl)
                }
            }
            "photo_booth_error" -> {
                // Return to StylePicker on error (graceful degradation)
                _uiState.value = PhotoBoothUiState.StylePicker()
            }
        }
    }
}

/**
 * Extracts a string value for [key] from a raw JSON string.
 * Returns null if the key is absent or parsing fails.
 *
 * Simple regex-based extraction used to avoid org.json in unit test context.
 * For production use, Plan 03 will switch to kotlinx.serialization.
 */
private fun extractJsonString(json: String, key: String): String? {
    return try {
        val pattern = Regex(""""$key"\s*:\s*"([^"]*?)"""")
        pattern.find(json)?.groupValues?.get(1)
    } catch (_: Exception) {
        null
    }
}
