package com.gow.smaitrobot.ui.navigation_map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gow.smaitrobot.data.model.NavStatus
import com.gow.smaitrobot.data.websocket.WebSocketEvent
import com.gow.smaitrobot.data.websocket.WebSocketRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * ViewModel for the Navigation Map screen.
 *
 * Consumes [WebSocketEvent] from [WebSocketRepository]:
 * - [WebSocketEvent.BinaryFrame] with bytes[0] == 0x06: decoded as map PNG → [mapBitmap]
 * - [WebSocketEvent.JsonMessage] with type == "nav_status": parsed → [navStatus]
 *
 * [bitmapDecoder] is injectable for unit testing (avoids Android's BitmapFactory at JVM time).
 * [dispatcher] is injectable so tests can use UnconfinedTestDispatcher; when null, [viewModelScope]
 * is used at runtime (correct Android ViewModel lifecycle).
 *
 * Map frames arrive infrequently (only on map changes / navigation updates), NOT at audio rates,
 * so StateFlow recomposition overhead is acceptable.
 */
class NavigationMapViewModel(
    private val wsRepo: WebSocketRepository,
    private val bitmapDecoder: (ByteArray, Int, Int) -> Bitmap? = { bytes, offset, length ->
        BitmapFactory.decodeByteArray(bytes, offset, length)
    },
    private val dispatcher: CoroutineDispatcher? = null
) : ViewModel() {

    // Use the injected dispatcher scope for tests; fall back to viewModelScope at runtime.
    // Stored as a val to avoid creating a new scope on every access.
    private val effectiveScope: CoroutineScope by lazy {
        if (dispatcher != null) CoroutineScope(dispatcher + SupervisorJob()) else viewModelScope
    }

    private val _mapBitmap = MutableStateFlow<Bitmap?>(null)

    /** Live map image decoded from 0x06 binary frames. Null until first frame received. */
    val mapBitmap: StateFlow<Bitmap?> = _mapBitmap.asStateFlow()

    private val _navStatus = MutableStateFlow<NavStatus?>(null)

    /** Current navigation status from "nav_status" JSON messages. Null until first message. */
    val navStatus: StateFlow<NavStatus?> = _navStatus.asStateFlow()

    private val _isNavigating = MutableStateFlow(false)

    /** True when [navStatus].status == "navigating". Derived from [navStatus]. */
    val isNavigating: StateFlow<Boolean> = _isNavigating.asStateFlow()

    init {
        effectiveScope.launch {
            wsRepo.events.collect { event ->
                when (event) {
                    is WebSocketEvent.BinaryFrame -> handleBinaryFrame(event.bytes)
                    is WebSocketEvent.JsonMessage -> handleJsonMessage(event.type, event.payload)
                    else -> Unit
                }
            }
        }
    }

    private fun handleBinaryFrame(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        if (bytes[0] != 0x06.toByte()) return

        // Skip the type byte (index 0); the rest is PNG data
        val bitmap = bitmapDecoder(bytes, 1, bytes.size - 1) ?: return
        _mapBitmap.value = bitmap
    }

    private fun handleJsonMessage(type: String, payload: String) {
        if (type != "nav_status") return
        try {
            val json = JSONObject(payload)
            val destination = json.optString("destination", "")
            val status = json.optString("status", "")
            val progress = json.optDouble("progress", 0.0).toFloat()
            val navStatus = NavStatus(destination, status, progress)
            _navStatus.value = navStatus
            _isNavigating.value = status == "navigating"
        } catch (_: Exception) {
            // Malformed JSON — ignore, leave navStatus unchanged
        }
    }
}
