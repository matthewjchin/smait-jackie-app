package com.gow.smaitrobot

import app.cash.turbine.test
import com.gow.smaitrobot.data.model.PoiItem
import com.gow.smaitrobot.data.websocket.WebSocketEvent
import com.gow.smaitrobot.data.websocket.WebSocketRepository
import com.gow.smaitrobot.ui.facilities.FacilitiesViewModel
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for FacilitiesViewModel.
 *
 * Tests verify:
 * - poi_list JSON message populates poiList StateFlow with PoiItem objects
 * - searchQuery="eng" filters poiList to items containing "eng" in humanName (case-insensitive)
 * - Empty searchQuery returns full poiList
 * - navigateTo("eng192") sends {"type":"navigate_to","poi":"eng192"} via WebSocketRepository
 * - Empty poi_list results in empty list (no crash)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FacilitiesViewModelTest {

    private lateinit var fakeEventsFlow: MutableSharedFlow<WebSocketEvent>
    private lateinit var mockRepo: WebSocketRepository
    private lateinit var viewModel: FacilitiesViewModel

    @Before
    fun setUp() {
        fakeEventsFlow = MutableSharedFlow(extraBufferCapacity = 64)
        mockRepo = mock()
        whenever(mockRepo.events).thenReturn(fakeEventsFlow as SharedFlow<WebSocketEvent>)
        viewModel = FacilitiesViewModel(
            wsRepo = mockRepo,
            dispatcher = UnconfinedTestDispatcher()
        )
    }

    // ── Test 1: poi_list JSON message populates poiList ───────────────────────

    @Test
    fun `Test 1 - poi_list JSON message populates filteredPois with PoiItem objects`() = runTest {
        val json = """
            {
              "type": "poi_list",
              "pois": [
                {"name": "eng192", "humanName": "Room ENG192", "category": "room", "floor": "1F"},
                {"name": "restroom_1f", "humanName": "Restroom 1F", "category": "restroom"}
              ]
            }
        """.trimIndent()

        fakeEventsFlow.emit(WebSocketEvent.JsonMessage("poi_list", json))

        val pois = viewModel.filteredPois.value
        assertEquals(2, pois.size)
        assertEquals("eng192", pois[0].name)
        assertEquals("Room ENG192", pois[0].humanName)
        assertEquals("room", pois[0].category)
        assertEquals("1F", pois[0].floor)
        assertEquals("restroom_1f", pois[1].name)
    }

    // ── Test 2: searchQuery filters poiList (case-insensitive) ───────────────

    @Test
    fun `Test 2 - searchQuery eng filters filteredPois to items containing eng in humanName`() = runTest {
        val json = """
            {
              "type": "poi_list",
              "pois": [
                {"name": "eng192", "humanName": "Room ENG192", "category": "room"},
                {"name": "restroom_1f", "humanName": "Restroom 1F", "category": "restroom"},
                {"name": "eng_lobby", "humanName": "Engineering Lobby", "category": "lobby"}
              ]
            }
        """.trimIndent()

        fakeEventsFlow.emit(WebSocketEvent.JsonMessage("poi_list", json))
        viewModel.updateSearchQuery("eng")

        val filtered = viewModel.filteredPois.value
        assertEquals(2, filtered.size)
        assertTrue("Should contain ENG192", filtered.any { it.name == "eng192" })
        assertTrue("Should contain Engineering Lobby", filtered.any { it.name == "eng_lobby" })
        assertFalse("Should NOT contain Restroom", filtered.any { it.name == "restroom_1f" })
    }

    // ── Test 3: Empty searchQuery returns full poiList ────────────────────────

    @Test
    fun `Test 3 - empty searchQuery returns full filteredPois list`() = runTest {
        val json = """
            {
              "type": "poi_list",
              "pois": [
                {"name": "eng192", "humanName": "Room ENG192", "category": "room"},
                {"name": "restroom_1f", "humanName": "Restroom 1F", "category": "restroom"}
              ]
            }
        """.trimIndent()

        fakeEventsFlow.emit(WebSocketEvent.JsonMessage("poi_list", json))
        viewModel.updateSearchQuery("") // explicit empty query

        val filtered = viewModel.filteredPois.value
        assertEquals(2, filtered.size)
    }

    // ── Test 4: navigateTo sends correct navigate_to JSON ─────────────────────

    @Test
    fun `Test 4 - navigateTo eng192 sends navigate_to JSON via WebSocketRepository`() {
        viewModel.navigateTo("eng192")

        val captor = argumentCaptor<String>()
        verify(mockRepo).send(captor.capture())

        val sentJson = captor.firstValue
        assertTrue("JSON should contain navigate_to type", sentJson.contains("\"navigate_to\""))
        assertTrue("JSON should contain eng192 poi", sentJson.contains("\"eng192\""))
    }

    // ── Test 5: Empty poi_list results in empty list ──────────────────────────

    @Test
    fun `Test 5 - empty poi_list results in empty filteredPois without crash`() = runTest {
        val json = """{"type":"poi_list","pois":[]}"""
        fakeEventsFlow.emit(WebSocketEvent.JsonMessage("poi_list", json))

        val pois = viewModel.filteredPois.value
        assertEquals(0, pois.size)
    }
}
