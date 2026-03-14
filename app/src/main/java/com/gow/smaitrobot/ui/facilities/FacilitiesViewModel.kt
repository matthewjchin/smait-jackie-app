package com.gow.smaitrobot.ui.facilities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gow.smaitrobot.data.model.PoiItem
import com.gow.smaitrobot.data.websocket.WebSocketEvent
import com.gow.smaitrobot.data.websocket.WebSocketRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * ViewModel for the Facilities screen.
 *
 * Consumes [WebSocketEvent.JsonMessage] with type == "poi_list" to populate the POI list.
 * Exposes a [filteredPois] StateFlow combining the full POI list with [searchQuery] for
 * case-insensitive, real-time filtering by humanName.
 *
 * Sends `{"type":"navigate_to","poi":"<poiName>"}` via [WebSocketRepository.send] when the
 * user taps "Take me there" on a POI card.
 *
 * [dispatcher] is injectable for unit testing with UnconfinedTestDispatcher. When null (default),
 * [viewModelScope] is used at runtime (correct Android ViewModel lifecycle).
 */
class FacilitiesViewModel(
    private val wsRepo: WebSocketRepository,
    private val dispatcher: CoroutineDispatcher? = null
) : ViewModel() {

    // Use injected dispatcher scope for tests; fall back to viewModelScope at runtime.
    private val effectiveScope: CoroutineScope by lazy {
        if (dispatcher != null) CoroutineScope(dispatcher + SupervisorJob()) else viewModelScope
    }

    private val _allPois = MutableStateFlow<List<PoiItem>>(emptyList())

    private val _searchQuery = MutableStateFlow("")

    /** Current search query string. Empty string means no filter (show all POIs). */
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filteredPois = MutableStateFlow<List<PoiItem>>(emptyList())

    /** POIs filtered by [searchQuery]. Empty query returns all POIs. */
    val filteredPois: StateFlow<List<PoiItem>> = _filteredPois.asStateFlow()

    private val _isLoading = MutableStateFlow(true)

    /** True until the first poi_list message is received from the server. */
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        // Collect WebSocket events and handle poi_list messages
        effectiveScope.launch {
            wsRepo.events.collect { event ->
                if (event is WebSocketEvent.JsonMessage && event.type == "poi_list") {
                    handlePoiList(event.payload)
                }
            }
        }

        // Combine POI list with search query to produce filtered list
        effectiveScope.launch {
            combine(_allPois, _searchQuery) { pois, query ->
                if (query.isBlank()) {
                    pois
                } else {
                    pois.filter { it.humanName.contains(query, ignoreCase = true) }
                }
            }.collect { filtered ->
                _filteredPois.value = filtered
            }
        }
    }

    /**
     * Updates the search query. The [filteredPois] StateFlow updates automatically via
     * the combine operator in init.
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Sends a navigate_to command to the server for the specified POI.
     *
     * @param poiName The internal machine-readable POI identifier (e.g., "eng192").
     */
    fun navigateTo(poiName: String) {
        val json = """{"type":"navigate_to","poi":"$poiName"}"""
        wsRepo.send(json)
    }

    private fun handlePoiList(payload: String) {
        try {
            val json = JSONObject(payload)
            val poisArray: JSONArray = json.optJSONArray("pois") ?: JSONArray()
            val pois = mutableListOf<PoiItem>()
            for (i in 0 until poisArray.length()) {
                val item = poisArray.getJSONObject(i)
                pois.add(
                    PoiItem(
                        name = item.optString("name", ""),
                        humanName = item.optString("humanName", ""),
                        category = item.optString("category", ""),
                        floor = item.optString("floor", "")
                    )
                )
            }
            _allPois.value = pois
            _isLoading.value = false
        } catch (_: Exception) {
            // Malformed JSON — ignore, keep existing list
        }
    }
}
