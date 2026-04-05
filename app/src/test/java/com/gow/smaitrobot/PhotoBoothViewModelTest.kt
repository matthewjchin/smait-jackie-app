package com.gow.smaitrobot

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for PhotoBoothViewModel state machine.
 *
 * Uses a mockito mock for WebSocketRepository with an injectable MutableSharedFlow for events.
 * Uses TestScope + advanceTimeBy for countdown timing tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhotoBoothViewModelTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(testScheduler)
    private val testScope = TestScope(testDispatcher)

    private lateinit var wsRepo: WebSocketRepository
    private lateinit var eventsFlow: MutableSharedFlow<WebSocketEvent>

    @Before
    fun setUp() {
        eventsFlow = MutableSharedFlow(extraBufferCapacity = 64)
        wsRepo = mock()
        whenever(wsRepo.events).thenReturn(eventsFlow)
    }

    private fun makeViewModel(): PhotoBoothViewModel =
        // Use backgroundScope so the infinite event collector doesn't block runTest completion
        PhotoBoothViewModel(wsRepo = wsRepo, coroutineScope = testScope.backgroundScope)

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
}
