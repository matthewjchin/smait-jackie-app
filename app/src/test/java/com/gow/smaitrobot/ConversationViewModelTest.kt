package com.gow.smaitrobot

import app.cash.turbine.test
import com.gow.smaitrobot.data.model.FeedbackData
import com.gow.smaitrobot.data.model.RobotState
import com.gow.smaitrobot.data.model.UiEvent
import com.gow.smaitrobot.data.websocket.WebSocketEvent
import com.gow.smaitrobot.data.websocket.WebSocketRepository
import com.gow.smaitrobot.navigation.Screen
import com.gow.smaitrobot.ui.conversation.ConversationViewModel
import com.gow.smaitrobot.ui.conversation.VideoStreamManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModelTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(testScheduler)
    private val testScope = TestScope(testDispatcher)

    private lateinit var wsRepo: WebSocketRepository
    private lateinit var ttsPlayer: TtsAudioPlayer
    private lateinit var caeAudio: CaeAudioManager
    private lateinit var videoManager: VideoStreamManager
    private lateinit var eventsFlow: MutableSharedFlow<WebSocketEvent>

    @Before
    fun setup() {
        eventsFlow = MutableSharedFlow(extraBufferCapacity = 64)
        wsRepo = mock()
        ttsPlayer = mock()
        caeAudio = mock()
        videoManager = mock()
        whenever(wsRepo.events).thenReturn(eventsFlow)
    }

    private fun createViewModel(): ConversationViewModel =
        ConversationViewModel(
            wsRepo = wsRepo,
            ttsPlayer = ttsPlayer,
            caeAudioManager = caeAudio,
            videoStreamManager = videoManager,
            coroutineScope = testScope
        )

    // Test 1: JSON message type="transcript" with text adds ChatMessage(isUser=true)
    @Test
    fun `transcript message adds user ChatMessage`() = testScope.runTest {
        val vm = createViewModel()
        eventsFlow.emit(WebSocketEvent.JsonMessage("transcript", """{"type":"transcript","text":"Hello robot"}"""))
        testScheduler.advanceTimeBy(100)

        val messages = vm.transcript.value
        assertEquals(1, messages.size)
        assertTrue(messages.first().isUser)
        assertEquals("Hello robot", messages.first().text)
    }

    // Test 2: JSON message type="response" with text adds ChatMessage(isUser=false)
    @Test
    fun `response message adds robot ChatMessage`() = testScope.runTest {
        val vm = createViewModel()
        eventsFlow.emit(WebSocketEvent.JsonMessage("response", """{"type":"response","text":"Hi there!"}"""))
        testScheduler.advanceTimeBy(100)

        val messages = vm.transcript.value
        assertEquals(1, messages.size)
        assertFalse(messages.first().isUser)
        assertEquals("Hi there!", messages.first().text)
    }

    // Test 3: state=listening updates robotState to LISTENING
    @Test
    fun `state listening updates robotState to LISTENING`() = testScope.runTest {
        val vm = createViewModel()
        eventsFlow.emit(WebSocketEvent.JsonMessage("state", """{"type":"state","value":"listening"}"""))
        testScheduler.advanceTimeBy(100)

        assertEquals(RobotState.LISTENING, vm.robotState.value)
    }

    // Test 4: state=thinking updates robotState to THINKING
    @Test
    fun `state thinking updates robotState to THINKING`() = testScope.runTest {
        val vm = createViewModel()
        eventsFlow.emit(WebSocketEvent.JsonMessage("state", """{"type":"state","value":"thinking"}"""))
        testScheduler.advanceTimeBy(100)

        assertEquals(RobotState.THINKING, vm.robotState.value)
    }

    // Test 5: state=speaking updates robotState to SPEAKING
    @Test
    fun `state speaking updates robotState to SPEAKING`() = testScope.runTest {
        val vm = createViewModel()
        eventsFlow.emit(WebSocketEvent.JsonMessage("state", """{"type":"state","value":"speaking"}"""))
        testScheduler.advanceTimeBy(100)

        assertEquals(RobotState.SPEAKING, vm.robotState.value)
    }

    // Test 6: Binary frame with type byte 0x05 is forwarded to TtsAudioPlayer
    @Test
    fun `binary 0x05 frame forwarded to TtsAudioPlayer`() = testScope.runTest {
        val vm = createViewModel()
        val ttsBytes = byteArrayOf(0x05, 0x10, 0x20, 0x30)
        eventsFlow.emit(WebSocketEvent.BinaryFrame(ttsBytes))
        testScheduler.advanceTimeBy(100)

        verify(ttsPlayer).handleBinaryFrame(ttsBytes)
    }

    // Test 7: Session end (state="idle" after "conversing") triggers showFeedback=true
    @Test
    fun `session end triggers showFeedback`() = testScope.runTest {
        val vm = createViewModel()

        // First: send a non-idle state to mark wasConversing=true
        eventsFlow.emit(WebSocketEvent.JsonMessage("state", """{"type":"state","value":"listening"}"""))
        testScheduler.advanceTimeBy(100)

        // Then: send idle state to trigger session end detection
        eventsFlow.emit(WebSocketEvent.JsonMessage("state", """{"type":"state","value":"idle"}"""))
        testScheduler.advanceTimeBy(100)

        assertTrue(vm.showFeedback.value)
    }

    // Test 8: sendFeedback() sends JSON message via WebSocketRepository
    @Test
    fun `sendFeedback sends JSON via wsRepo`() = testScope.runTest {
        val vm = createViewModel()
        val feedback = FeedbackData(rating = 4, sessionId = "test-session-1")
        vm.sendFeedback(feedback)
        testScheduler.advanceTimeBy(100)

        val captor = argumentCaptor<String>()
        verify(wsRepo).send(captor.capture())
        assertTrue(captor.firstValue.contains("\"type\":\"feedback\""))
        assertTrue(captor.firstValue.contains("\"rating\":4"))
    }

    // Test 9: Transcript list maintains message order (oldest first)
    @Test
    fun `transcript maintains oldest-first order`() = testScope.runTest {
        val vm = createViewModel()
        eventsFlow.emit(WebSocketEvent.JsonMessage("transcript", """{"type":"transcript","text":"First"}"""))
        testScheduler.advanceTimeBy(50)
        eventsFlow.emit(WebSocketEvent.JsonMessage("response", """{"type":"response","text":"Second"}"""))
        testScheduler.advanceTimeBy(50)

        val msgs = vm.transcript.value
        assertEquals(2, msgs.size)
        assertEquals("First", msgs[0].text)
        assertEquals("Second", msgs[1].text)
    }

    // Test 10: CaeAudioManager writer callback calls wsRepo.send(bytes) for outbound audio
    @Test
    fun `CaeAudioManager writer callback calls wsRepo send bytes`() = testScope.runTest {
        val vm = createViewModel()

        // Verify setWriterCallback was called on caeAudio
        val captor = argumentCaptor<(ByteArray) -> Unit>()
        verify(caeAudio).setWriterCallback(captor.capture())

        // Invoke the captured callback with test bytes
        val testBytes = byteArrayOf(0x01, 0x10, 0x20)
        captor.firstValue.invoke(testBytes)

        val bytesCaptor = argumentCaptor<ByteArray>()
        verify(wsRepo).send(bytesCaptor.capture())
        assertTrue(bytesCaptor.firstValue.contentEquals(testBytes))
    }

    // Test 11: After feedback dismissed, UiEvent.NavigateTo(Home) is emitted
    @Test
    fun `dismissFeedback emits NavigateTo Home`() = testScope.runTest {
        val vm = createViewModel()

        vm.uiEvents.test {
            vm.dismissFeedback()
            testScheduler.advanceTimeBy(100)
            val event = awaitItem()
            assertTrue(event is UiEvent.NavigateTo)
            assertEquals(Screen.Home, (event as UiEvent.NavigateTo).screen)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // Test 12: 30s silence timeout emits UiEvent.NavigateTo(Home)
    @Test
    fun `silence timeout of 30s emits NavigateTo Home`() = testScope.runTest {
        val vm = createViewModel()

        vm.uiEvents.test {
            // Advance time past the 30s silence window
            advanceTimeBy(31_000L)
            val event = awaitItem()
            assertTrue(event is UiEvent.NavigateTo)
            assertEquals(Screen.Home, (event as UiEvent.NavigateTo).screen)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // Test 13: Any incoming WS message resets the silence timeout timer
    @Test
    fun `incoming message resets silence timer`() = testScope.runTest {
        val vm = createViewModel()

        vm.uiEvents.test {
            // Advance 25s — not yet timed out
            advanceTimeBy(25_000L)

            // Emit a message to reset the timer
            eventsFlow.emit(WebSocketEvent.JsonMessage("transcript", """{"type":"transcript","text":"alive"}"""))
            testScheduler.advanceTimeBy(100)

            // Advance another 25s from reset — still within 30s window, so no timeout yet
            advanceTimeBy(25_000L)

            // Now advance past the 30s mark from the last reset (5s more)
            advanceTimeBy(6_000L)

            val event = awaitItem()
            assertTrue(event is UiEvent.NavigateTo)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
