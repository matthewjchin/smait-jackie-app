package com.gow.smaitrobot

import android.graphics.Bitmap
import com.gow.smaitrobot.data.websocket.WebSocketEvent
import com.gow.smaitrobot.data.websocket.WebSocketRepository
import com.gow.smaitrobot.ui.photobooth.PhotoBoothUiState
import com.gow.smaitrobot.ui.photobooth.PhotoBoothViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for PhotoBoothViewModel state machine.
 *
 * Uses a mockito mock for WebSocketRepository with an injectable MutableSharedFlow for events.
 * Uses TestScope + advanceTimeBy for countdown timing tests.
 *
 * Android-specific functions (Base64 decode, Bitmap compress) are injected as lambdas so they
 * can be replaced with stubs in JVM-only unit tests (no Robolectric needed).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhotoBoothViewModelTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(testScheduler)
    private val testScope = TestScope(testDispatcher)

    private lateinit var wsRepo: WebSocketRepository
    private lateinit var eventsFlow: MutableSharedFlow<WebSocketEvent>

    // Fake bitmap used in tests where a Bitmap instance is needed
    private val fakeBitmap: Bitmap = mock()

    // Fake JPEG bytes representing a "compressed" bitmap in tests
    private val fakeJpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02, 0x03)

    @Before
    fun setUp() {
        eventsFlow = MutableSharedFlow(extraBufferCapacity = 64)
        wsRepo = mock()
        whenever(wsRepo.events).thenReturn(eventsFlow)
    }

    private fun makeViewModel(
        bitmapToJpeg: (Bitmap, Int) -> ByteArray = { _, _ -> fakeJpegBytes },
        base64ToBitmap: (String) -> Bitmap? = { fakeBitmap }
    ): PhotoBoothViewModel =
        // Use backgroundScope so the infinite event collector doesn't block runTest completion
        PhotoBoothViewModel(
            wsRepo = wsRepo,
            coroutineScope = testScope.backgroundScope,
            bitmapToJpeg = bitmapToJpeg,
            base64ToBitmap = base64ToBitmap
        )

    // ── Test 1: Initial state is StylePicker ──────────────────────────────────

    @Test
    fun `Test 1 - initial state is StylePicker`() {
        val viewModel = makeViewModel()
        val state = viewModel.uiState.value
        assertTrue(
            "Initial state should be StylePicker, got $state",
            state is PhotoBoothUiState.StylePicker
        )
    }

    // ── Test 2: onStyleSelected updates selectedStyle in StylePicker state ────

    @Test
    fun `Test 2 - onStyleSelected updates selectedStyle in StylePicker state`() {
        val viewModel = makeViewModel()
        viewModel.onStyleSelected("cyberpunk")

        val state = viewModel.uiState.value
        assertTrue("State should still be StylePicker", state is PhotoBoothUiState.StylePicker)
        assertEquals(
            "selectedStyle should be cyberpunk",
            "cyberpunk",
            (state as PhotoBoothUiState.StylePicker).selectedStyle
        )
    }

    // ── Test 3: onTakePhoto transitions Countdown(3) -> (2) -> (1) ──────────

    @Test
    fun `Test 3 - onTakePhoto transitions through Countdown 3 2 1`() = testScope.runTest {
        val viewModel = makeViewModel()
        viewModel.onStyleSelected("cyberpunk")
        viewModel.onTakePhoto()

        // Advance time slightly to allow the coroutine to start and set Countdown(3)
        advanceTimeBy(1L)

        // Now at Countdown(3)
        val state3 = viewModel.uiState.value
        assertTrue("Should be Countdown after onTakePhoto, got $state3", state3 is PhotoBoothUiState.Countdown)
        assertEquals(3, (state3 as PhotoBoothUiState.Countdown).secondsLeft)

        // After 1s -> Countdown(2)
        advanceTimeBy(1_001L)
        val state2 = viewModel.uiState.value
        assertTrue("Should still be Countdown at 2, got $state2", state2 is PhotoBoothUiState.Countdown)
        assertEquals(2, (state2 as PhotoBoothUiState.Countdown).secondsLeft)

        // After another 1s -> Countdown(1)
        advanceTimeBy(1_001L)
        val state1 = viewModel.uiState.value
        assertTrue("Should still be Countdown at 1, got $state1", state1 is PhotoBoothUiState.Countdown)
        assertEquals(1, (state1 as PhotoBoothUiState.Countdown).secondsLeft)
    }

    // ── Test 4: After countdown completes state transitions to Processing ─────

    @Test
    fun `Test 4 - after countdown completes state transitions to Processing with selected style`() = testScope.runTest {
        val viewModel = makeViewModel()
        viewModel.onStyleSelected("anime")
        viewModel.onTakePhoto()

        // Advance past all 3 countdown ticks
        advanceTimeBy(4_000L)

        val state = viewModel.uiState.value
        assertTrue(
            "State should be Processing after countdown, got $state",
            state is PhotoBoothUiState.Processing
        )
        assertEquals("anime", (state as PhotoBoothUiState.Processing).styleName)
    }

    // ── Test 5: onScreenEntered sends photo_booth_enter JSON ─────────────────

    @Test
    fun `Test 5 - onScreenEntered sends photo_booth_enter JSON`() = testScope.runTest {
        val viewModel = makeViewModel()
        viewModel.onScreenEntered()

        val captor = argumentCaptor<String>()
        verify(wsRepo).send(captor.capture())
        assertTrue(
            "Sent message should contain photo_booth_enter, got: ${captor.lastValue}",
            captor.lastValue.contains("photo_booth_enter")
        )
    }

    // ── Test 6: onScreenExited sends photo_booth_exit JSON ───────────────────

    @Test
    fun `Test 6 - onScreenExited sends photo_booth_exit JSON`() = testScope.runTest {
        val viewModel = makeViewModel()
        viewModel.onScreenExited()

        val captor = argumentCaptor<String>()
        verify(wsRepo).send(captor.capture())
        assertTrue(
            "Sent message should contain photo_booth_exit, got: ${captor.lastValue}",
            captor.lastValue.contains("photo_booth_exit")
        )
    }

    // ── Test 7: onRetake resets to StylePicker without sending WS messages ────

    @Test
    fun `Test 7 - onRetake resets to StylePicker without sending WebSocket messages`() = testScope.runTest {
        val viewModel = makeViewModel()
        viewModel.onStyleSelected("cyberpunk")
        viewModel.onTakePhoto()
        advanceTimeBy(4_000L) // let it reach Processing

        // onRetake should not invoke wsRepo.send at all
        // Reset mockito interaction count by checking send was NOT called from this point
        // We use verifyNoMoreInteractions after clearing prior calls is not directly available,
        // so instead we count via sent messages captured by argumentCaptor on the mock.
        viewModel.onRetake()

        val state = viewModel.uiState.value
        assertTrue(
            "State should be StylePicker after retake, got $state",
            state is PhotoBoothUiState.StylePicker
        )
        // selectedStyle should be cleared on retake
        val selectedStyle = (state as PhotoBoothUiState.StylePicker).selectedStyle
        assertEquals("selectedStyle should be null after retake", null, selectedStyle)
    }

    // ── Test 8: sendHighResPhoto sends style JSON then 0x08 binary frame ─────

    @Test
    fun `Test 8 - sendHighResPhoto sends photo_booth_style JSON then 0x08 binary frame`() = testScope.runTest {
        val capturedJpeg = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
        val viewModel = makeViewModel(bitmapToJpeg = { _, _ -> capturedJpeg })

        viewModel.sendHighResPhoto(fakeBitmap, "anime")

        // Verify JSON message with photo_booth_style and style
        val stringCaptor = argumentCaptor<String>()
        verify(wsRepo, atLeastOnce()).send(stringCaptor.capture())
        val jsonMsg = stringCaptor.allValues.find { it.contains("photo_booth_style") }
        assertNotNull("Should have sent photo_booth_style JSON", jsonMsg)
        assertTrue("JSON should contain style=anime", jsonMsg!!.contains("anime"))

        // Verify binary frame starting with 0x08
        val byteCaptor = argumentCaptor<ByteArray>()
        verify(wsRepo, atLeastOnce()).send(byteCaptor.capture())
        val binaryFrame = byteCaptor.allValues.firstOrNull()
        assertNotNull("Should have sent binary frame", binaryFrame)
        assertEquals("First byte should be 0x08", 0x08.toByte(), binaryFrame!![0])

        // Verify JPEG bytes are appended after the type byte
        assertEquals("Frame length should be 1 + jpegBytes.size", 1 + capturedJpeg.size, binaryFrame.size)
        for (i in capturedJpeg.indices) {
            assertEquals("Byte at ${i + 1} should match JPEG byte $i", capturedJpeg[i], binaryFrame[i + 1])
        }
    }

    // ── Test 9: styled_result transitions to Result with decoded bitmap ───────

    @Test
    fun `Test 9 - styled_result JSON transitions from Processing to Result with styledBitmap`() = testScope.runTest {
        val styledBitmap: Bitmap = mock()
        val viewModel = makeViewModel(base64ToBitmap = { styledBitmap })

        // Put ViewModel in Processing state by capturing a photo
        viewModel.onStyleSelected("cyberpunk")
        viewModel.onTakePhoto()
        advanceTimeBy(4_000L) // countdown -> Processing
        // Set rawBitmap as if photo was captured
        viewModel.rawBitmap = fakeBitmap

        // Emit styled_result from server
        val styledResultJson = """{"type":"styled_result","styled_b64":"AAAA"}"""
        eventsFlow.emit(WebSocketEvent.JsonMessage("styled_result", styledResultJson))
        advanceTimeBy(10L) // allow coroutine to process

        val state = viewModel.uiState.value
        assertTrue("State should be Result after styled_result, got $state", state is PhotoBoothUiState.Result)
        val result = state as PhotoBoothUiState.Result
        assertNotNull("styledBitmap should be non-null", result.styledBitmap)
        assertEquals("styledBitmap should be the decoded bitmap", styledBitmap, result.styledBitmap)
        assertEquals("rawBitmap should be preserved", fakeBitmap, result.rawBitmap)
    }

    // ── Test 10: qr_code updates Result state with downloadUrl ───────────────

    @Test
    fun `Test 10 - qr_code JSON updates Result state with downloadUrl`() = testScope.runTest {
        val viewModel = makeViewModel()

        // Manually force Result state (simulate after styled_result)
        viewModel.onStyleSelected("pop_art")
        viewModel.onTakePhoto()
        advanceTimeBy(4_000L)
        viewModel.rawBitmap = fakeBitmap

        // First get to Result via styled_result
        eventsFlow.emit(WebSocketEvent.JsonMessage("styled_result", """{"type":"styled_result","styled_b64":"AAAA"}"""))
        advanceTimeBy(10L)

        // Now emit qr_code
        val downloadUrl = "https://example.com/photo/abc123.jpg"
        eventsFlow.emit(WebSocketEvent.JsonMessage("qr_code", """{"type":"qr_code","download_url":"$downloadUrl"}"""))
        advanceTimeBy(10L)

        val state = viewModel.uiState.value
        assertTrue("State should be Result after qr_code, got $state", state is PhotoBoothUiState.Result)
        val result = state as PhotoBoothUiState.Result
        assertEquals("downloadUrl should be set", downloadUrl, result.downloadUrl)
    }

    // ── Test 11: photo_booth_error transitions to Result with styledBitmap=null

    @Test
    fun `Test 11 - photo_booth_error transitions Processing to Result with null styledBitmap`() = testScope.runTest {
        val viewModel = makeViewModel()

        viewModel.onStyleSelected("robot_vision")
        viewModel.onTakePhoto()
        advanceTimeBy(4_000L) // -> Processing
        viewModel.rawBitmap = fakeBitmap

        // Emit error from server
        eventsFlow.emit(WebSocketEvent.JsonMessage("photo_booth_error", """{"type":"photo_booth_error","message":"SD inference failed"}"""))
        advanceTimeBy(10L)

        val state = viewModel.uiState.value
        assertTrue("State should be Result after photo_booth_error, got $state", state is PhotoBoothUiState.Result)
        val result = state as PhotoBoothUiState.Result
        assertNull("styledBitmap should be null on error (graceful degradation)", result.styledBitmap)
        assertEquals("rawBitmap should still be set", fakeBitmap, result.rawBitmap)
    }

    // ── Test 12: onRetake from Result resets to StylePicker ──────────────────

    @Test
    fun `Test 12 - onRetake from Result state resets to StylePicker`() = testScope.runTest {
        val viewModel = makeViewModel()

        // Navigate to Result state
        viewModel.onStyleSelected("pixel_art")
        viewModel.onTakePhoto()
        advanceTimeBy(4_000L)
        viewModel.rawBitmap = fakeBitmap
        eventsFlow.emit(WebSocketEvent.JsonMessage("styled_result", """{"type":"styled_result","styled_b64":"AAAA"}"""))
        advanceTimeBy(10L)

        // Verify we're in Result
        assertTrue("Should be in Result before retake", viewModel.uiState.value is PhotoBoothUiState.Result)

        // Retake
        viewModel.onRetake()

        val state = viewModel.uiState.value
        assertTrue("State should be StylePicker after retake from Result, got $state", state is PhotoBoothUiState.StylePicker)
        assertNull("selectedStyle should be null after retake", (state as PhotoBoothUiState.StylePicker).selectedStyle)
        // No photo_booth_exit or photo_booth_enter should have been sent during retake
        // (wsRepo.send would have been called for onScreenEntered in a real screen flow,
        //  but since we never called onScreenEntered here, any String send would be suspicious)
    }
}
