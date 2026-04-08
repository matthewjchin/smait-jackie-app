package com.gow.smaitrobot.ui.photobooth

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
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
import java.io.ByteArrayOutputStream

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
     * Style result received. [rawBitmap] is the original capture.
     * [styledBitmap] arrives from server; null if style transfer failed (graceful degradation).
     * [downloadUrl] arrives from server's qr_code message.
     * [styleKey] is the registry key the user picked (e.g. "cyberpunk") — lets the
     * ResultScreen pick a style-matched theme and confetti palette.
     */
    data class Result(
        val rawBitmap: Bitmap?,
        val styledBitmap: Bitmap?,
        val downloadUrl: String?,
        val styleKey: String? = null
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
 * - Sends `photo_booth_style` JSON + 0x08 binary frame after capture
 * - Collects server events: style_processing, styled_result, qr_code, photo_booth_error
 *
 * [coroutineScope] is injectable for unit testing (use TestScope with StandardTestDispatcher).
 * At runtime, pass null to use an internal Main-based scope.
 *
 * [bitmapToJpeg] and [base64ToBitmap] are injectable for unit testing (avoids android.util.*
 * not available in JVM-only test environments). Defaults use Android's real implementations.
 */
private const val PROCESSING_TIMEOUT_MS = 45_000L

class PhotoBoothViewModel(
    private val wsRepo: WebSocketRepository,
    coroutineScope: CoroutineScope? = null,
    private val bitmapToJpeg: (Bitmap, Int) -> ByteArray = { bitmap, quality ->
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        out.toByteArray()
    },
    private val base64ToBitmap: (String) -> Bitmap? = { b64 ->
        try {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }
) {
    private val scope: CoroutineScope =
        coroutineScope ?: CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _uiState = MutableStateFlow<PhotoBoothUiState>(PhotoBoothUiState.StylePicker())

    /** Current UI state. Observed by [PhotoBoothScreen] to drive which composable is shown. */
    val uiState: StateFlow<PhotoBoothUiState> = _uiState.asStateFlow()

    /**
     * Holds the raw capture bitmap. Set by [onPhotoCaptured] and included in [PhotoBoothUiState.Result].
     * Exposed as `var` so tests can inject directly without invoking Camera2.
     */
    var rawBitmap: Bitmap? = null

    /** Countdown coroutine job — cancelled on retake. */
    private var countdownJob: Job? = null

    /**
     * Watchdog for server-side SD inference. Armed when entering Processing
     * state, cancelled on any styled_result / photo_booth_error / retake.
     * If it fires, transitions to Result with a null styled bitmap (error
     * state) so the user isn't stuck on a spinner forever.
     */
    private var processingWatchdog: Job? = null

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
     *
     * After countdown completes, the PhotoBoothScreen composable triggers Camera2 capture
     * and calls [onPhotoCaptured] with the resulting bitmap.
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
            // PhotoBoothScreen detects Countdown(secondsLeft=0) and triggers Camera2 capture,
            // then calls onPhotoCaptured(bitmap). Transition to Processing here as a signal.
            _uiState.value = PhotoBoothUiState.Processing(selectedStyle)
        }
    }

    /**
     * Called by [PhotoBoothScreen] after Camera2 has captured a bitmap.
     * Sends the high-res photo to the server and transitions to Processing state.
     *
     * @param bitmap The captured bitmap from Camera2 ImageReader (1280x720 JPEG decoded).
     */
    fun onPhotoCaptured(bitmap: Bitmap) {
        val current = _uiState.value
        val selectedStyle = when (current) {
            is PhotoBoothUiState.Processing -> current.styleName
            is PhotoBoothUiState.Countdown -> current.selectedStyle
            else -> return
        }
        _uiState.value = PhotoBoothUiState.Processing(selectedStyle)
        sendHighResPhoto(bitmap, selectedStyle)
    }

    /**
     * Compresses [bitmap] to JPEG and sends it to the server as a 0x08 binary frame,
     * preceded by a `photo_booth_style` JSON message.
     *
     * Frame format: `[0x08][JPEG bytes...]`
     * The 0x08 type byte distinguishes this from selfie frames (0x07).
     */
    fun sendHighResPhoto(bitmap: Bitmap, style: String) {
        rawBitmap = bitmap
        val jpegBytes = bitmapToJpeg(bitmap, 95)
        sendJpegFrame(jpegBytes, style)
    }

    /**
     * Fast path: send raw JPEG bytes directly without Bitmap decode/re-encode.
     *
     * Called when the Photo Booth reuses a frame from [VideoStreamManager]'s
     * live capture. The raw JPEG is what the server already receives as a
     * 0x02 video frame, so re-encoding it loses quality and wastes CPU.
     *
     * Also decodes the JPEG into [rawBitmap] so [ResultScreen] can still
     * crossfade from the original capture into the styled result.
     */
    fun onPhotoJpegCaptured(jpegBytes: ByteArray) {
        val current = _uiState.value
        val selectedStyle = when (current) {
            is PhotoBoothUiState.Processing -> current.styleName
            is PhotoBoothUiState.Countdown -> current.selectedStyle
            else -> return
        }
        rawBitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
        _uiState.value = PhotoBoothUiState.Processing(selectedStyle)
        sendJpegFrame(jpegBytes, selectedStyle)
        armProcessingWatchdog()
    }

    /**
     * Shared send path for both Bitmap and raw-JPEG capture flows.
     * Emits the `photo_booth_style` JSON then the 0x08-prefixed binary frame.
     */
    private fun sendJpegFrame(jpegBytes: ByteArray, style: String) {
        wsRepo.send("""{"type":"photo_booth_style","style":"$style"}""")
        val frame = ByteArray(1 + jpegBytes.size)
        frame[0] = 0x08
        System.arraycopy(jpegBytes, 0, frame, 1, jpegBytes.size)
        wsRepo.send(frame)
    }

    /**
     * Arms a watchdog that will fail the Processing state to a Result with
     * null styledBitmap if the server does not return a styled_result in
     * [PROCESSING_TIMEOUT_MS]. Prevents the user from being stuck on a
     * spinner if the server crashes, OOMs, or the SD model hangs.
     */
    private fun armProcessingWatchdog() {
        processingWatchdog?.cancel()
        processingWatchdog = scope.launch {
            delay(PROCESSING_TIMEOUT_MS)
            val current = _uiState.value
            if (current is PhotoBoothUiState.Processing) {
                _uiState.value = PhotoBoothUiState.Result(
                    rawBitmap = rawBitmap,
                    styledBitmap = null,
                    downloadUrl = null,
                    styleKey = current.styleName
                )
            }
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
        processingWatchdog?.cancel()
        rawBitmap = null
        _uiState.value = PhotoBoothUiState.StylePicker()
    }

    // ── Server message handler ─────────────────────────────────────────────────

    internal fun handleServerMessage(type: String, payload: String) {
        when (type) {
            "style_processing" -> {
                // Server acknowledged the style and started processing — no UI change needed
            }
            "styled_result" -> {
                val current = _uiState.value
                if (current is PhotoBoothUiState.Processing) {
                    processingWatchdog?.cancel()
                    val b64 = extractJsonString(payload, "styled_b64")
                    val styledBitmap = if (b64 != null) base64ToBitmap(b64) else null
                    _uiState.value = PhotoBoothUiState.Result(
                        rawBitmap = rawBitmap,
                        styledBitmap = styledBitmap,
                        downloadUrl = null,
                        styleKey = current.styleName
                    )
                }
            }
            "qr_code" -> {
                val current = _uiState.value
                if (current is PhotoBoothUiState.Result) {
                    val downloadUrl = extractJsonString(payload, "download_url")
                    _uiState.value = current.copy(downloadUrl = downloadUrl)
                }
            }
            "photo_booth_error" -> {
                // Graceful degradation: show raw photo if style transfer failed
                val current = _uiState.value
                if (current is PhotoBoothUiState.Processing) {
                    processingWatchdog?.cancel()
                    _uiState.value = PhotoBoothUiState.Result(
                        rawBitmap = rawBitmap,
                        styledBitmap = null,
                        downloadUrl = null,
                        styleKey = current.styleName
                    )
                }
            }
        }
    }
}

/**
 * Extracts a string value for [key] from a raw JSON string.
 * Returns null if the key is absent or parsing fails.
 *
 * Simple regex-based extraction used to avoid org.json in unit test context.
 */
private fun extractJsonString(json: String, key: String): String? {
    return try {
        val pattern = Regex(""""$key"\s*:\s*"([^"]*?)"""")
        pattern.find(json)?.groupValues?.get(1)
    } catch (_: Exception) {
        null
    }
}
