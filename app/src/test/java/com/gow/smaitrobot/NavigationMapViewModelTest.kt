package com.gow.smaitrobot

import android.graphics.Bitmap
import app.cash.turbine.test
import com.gow.smaitrobot.data.model.NavStatus
import com.gow.smaitrobot.data.websocket.WebSocketEvent
import com.gow.smaitrobot.data.websocket.WebSocketRepository
import com.gow.smaitrobot.ui.navigation_map.NavigationMapViewModel
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Unit tests for NavigationMapViewModel.
 *
 * Uses an injectable bitmap decoder to avoid depending on Android's BitmapFactory
 * at JVM test time. The decoder is a (ByteArray, Int, Int) -> Bitmap? lambda.
 *
 * Tests verify:
 * - 0x06 binary frames decode to non-null Bitmap and update mapBitmap StateFlow
 * - Non-0x06 binary frames do NOT update mapBitmap
 * - nav_status JSON messages update navStatus StateFlow with correct fields
 * - mapBitmap starts null (no map received)
 * - navStatus transitions from "navigating" to "arrived" update isNavigating correctly
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigationMapViewModelTest {

    private lateinit var fakeEventsFlow: MutableSharedFlow<WebSocketEvent>
    private lateinit var mockRepo: WebSocketRepository
    private lateinit var mockBitmap: Bitmap

    // Fake decoder always returns a non-null Bitmap
    private val fakeDecoder: (ByteArray, Int, Int) -> Bitmap? = { _, _, _ -> mock() }

    private lateinit var viewModel: NavigationMapViewModel

    @Before
    fun setUp() {
        fakeEventsFlow = MutableSharedFlow(extraBufferCapacity = 64)
        mockRepo = mock()
        mockBitmap = mock()
        whenever(mockRepo.events).thenReturn(fakeEventsFlow as SharedFlow<WebSocketEvent>)
        viewModel = NavigationMapViewModel(
            wsRepo = mockRepo,
            bitmapDecoder = fakeDecoder,
            dispatcher = UnconfinedTestDispatcher()
        )
    }

    // ── Test 1: 0x06 binary frame updates mapBitmap to non-null ─────────────

    @Test
    fun `Test 1 - binary frame with first byte 0x06 updates mapBitmap to non-null Bitmap`() = runTest {
        val pngData = byteArrayOf(0x01, 0x02, 0x03) // arbitrary PNG data (decoder is faked)
        val frame = byteArrayOf(0x06.toByte()) + pngData

        fakeEventsFlow.emit(WebSocketEvent.BinaryFrame(frame))

        assertNotNull("mapBitmap should be non-null after 0x06 frame", viewModel.mapBitmap.value)
    }

    // ── Test 2: Non-0x06 binary frame does NOT update mapBitmap ─────────────

    @Test
    fun `Test 2 - binary frame with first byte not 0x06 does NOT update mapBitmap`() = runTest {
        // 0x05 = TTS audio frame, should be ignored
        val audioFrame = byteArrayOf(0x05.toByte(), 0x00, 0x01, 0x02, 0x03)
        fakeEventsFlow.emit(WebSocketEvent.BinaryFrame(audioFrame))

        assertNull("mapBitmap should remain null for non-0x06 frames", viewModel.mapBitmap.value)
    }

    // ── Test 3: nav_status JSON message updates navStatus StateFlow ──────────

    @Test
    fun `Test 3 - JSON message type nav_status updates navStatus with correct destination and status`() = runTest {
        val json = """{"type":"nav_status","destination":"eng192","status":"navigating","progress":0.5}"""
        fakeEventsFlow.emit(WebSocketEvent.JsonMessage("nav_status", json))

        val status = viewModel.navStatus.value
        assertNotNull("navStatus should be non-null after nav_status message", status)
        assertEquals("eng192", status!!.destination)
        assertEquals("navigating", status.status)
        assertEquals(0.5f, status.progress, 0.01f)
    }

    // ── Test 4: mapBitmap starts null before any frame received ─────────────

    @Test
    fun `Test 4 - mapBitmap is null before any map frame received`() {
        assertNull("mapBitmap should start null (no map received)", viewModel.mapBitmap.value)
    }

    // ── Test 5: navStatus transitions from navigating to arrived ────────────

    @Test
    fun `Test 5 - navStatus status transitions from navigating to arrived update isNavigating correctly`() = runTest {
        val navigatingJson = """{"type":"nav_status","destination":"eng192","status":"navigating","progress":0.3}"""
        fakeEventsFlow.emit(WebSocketEvent.JsonMessage("nav_status", navigatingJson))

        val navigating = viewModel.navStatus.value
        assertNotNull(navigating)
        assertEquals("navigating", navigating!!.status)
        assertTrue("isNavigating should be true while navigating", viewModel.isNavigating.value)

        val arrivedJson = """{"type":"nav_status","destination":"eng192","status":"arrived","progress":1.0}"""
        fakeEventsFlow.emit(WebSocketEvent.JsonMessage("nav_status", arrivedJson))

        val arrived = viewModel.navStatus.value
        assertNotNull(arrived)
        assertEquals("arrived", arrived!!.status)
        assertFalse("isNavigating should be false after arriving", viewModel.isNavigating.value)
    }
}
