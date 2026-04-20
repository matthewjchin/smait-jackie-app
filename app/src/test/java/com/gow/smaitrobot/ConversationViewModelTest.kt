package com.gow.smaitrobot

import app.cash.turbine.test
import com.gow.smaitrobot.data.model.RobotState
import com.gow.smaitrobot.data.model.SurveyData
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
import org.mockito.Mockito.atLeast
import org.mockito.kotlin.argumentCaptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

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

    // Test 7: Session end (state="idle" after "conversing") triggers showSurvey=true
    @Test
    fun `session end triggers showSurvey`() = testScope.runTest {
        val vm = createViewModel()

        eventsFlow.emit(WebSocketEvent.JsonMessage("state", """{"type":"state","value":"listening"}"""))
        testScheduler.advanceTimeBy(100)

        eventsFlow.emit(WebSocketEvent.JsonMessage("state", """{"type":"state","value":"idle"}"""))
        testScheduler.advanceTimeBy(100)

        assertTrue(vm.showSurvey.value)
    }

    // Test 8: submitSurvey() sends survey JSON via WebSocketRepository
    @Test
    fun `submitSurvey sends JSON via wsRepo`() = testScope.runTest {
        val vm = createViewModel()
        val survey = SurveyData(
            starRating = 4,
            understood = 5,
            helpful = 4,
            natural = 3,
            attentive = 4,
            comment = "Great robot!",
            completedInTime = true,
            timeToCompleteMs = 8500,
            sessionId = "test-session-1"
        )
        vm.submitSurvey(survey)
        testScheduler.advanceTimeBy(100)

        // Captures all send(String) calls: session_command("start"), survey, session_command("end")
        val captor = argumentCaptor<String>()
        org.mockito.Mockito.verify(wsRepo, atLeast(2)).send(captor.capture())

        val surveyJson = captor.allValues.first { it.contains("\"type\":\"survey\"") }
        assertTrue(surveyJson.contains("\"star_rating\":4"))
        assertTrue(surveyJson.contains("\"understood\":5"))
        assertTrue(surveyJson.contains("\"completed\":true"))
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

        val captor = argumentCaptor<(ByteArray) -> Unit>()
        verify(caeAudio).setWriterCallback(captor.capture())

        val testBytes = byteArrayOf(0x01, 0x10, 0x20)
        captor.firstValue.invoke(testBytes)

        val bytesCaptor = argumentCaptor<ByteArray>()
        verify(wsRepo).send(bytesCaptor.capture())
        assertTrue(bytesCaptor.firstValue.contentEquals(testBytes))
    }

    // Test 10b: CaeAudioManager text writer callback calls wsRepo.send(json) for DOA frames
    @Test
    fun `CaeAudioManager text writer callback calls wsRepo send json`() = testScope.runTest {
        val vm = createViewModel()

        val captor = argumentCaptor<(String) -> Unit>()
        verify(caeAudio).setTextWriterCallback(captor.capture())

        val doaJson = """{"type":"doa","angle":45,"beam":2}"""
        captor.firstValue.invoke(doaJson)

        val jsonCaptor = argumentCaptor<String>()
        verify(wsRepo).send(jsonCaptor.capture())
        assertEquals(doaJson, jsonCaptor.firstValue)
    }

    // Test 11: After survey dismissed, UiEvent.NavigateTo(Home) is emitted
    @Test
    fun `dismissSurvey emits NavigateTo Home`() = testScope.runTest {
        val vm = createViewModel()
        val dismissedSurvey = SurveyData(
            starRating = 0,
            understood = 0,
            helpful = 0,
            natural = 0,
            attentive = 0,
            comment = "",
            completedInTime = false,
            timeToCompleteMs = 20000,
            sessionId = "test-session"
        )

        vm.uiEvents.test {
            vm.dismissSurvey(dismissedSurvey)
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
            advanceTimeBy(25_000L)

            eventsFlow.emit(WebSocketEvent.JsonMessage("transcript", """{"type":"transcript","text":"alive"}"""))
            testScheduler.advanceTimeBy(100)

            advanceTimeBy(25_000L)
            advanceTimeBy(6_000L)

            val event = awaitItem()
            assertTrue(event is UiEvent.NavigateTo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // Test 14: submitSurvey sends session_command("end") after survey data
    @Test
    fun `submitSurvey sends session end after survey data`() = testScope.runTest {
        val vm = createViewModel()
        val survey = SurveyData(
            starRating = 5,
            understood = 4,
            helpful = 5,
            natural = 4,
            attentive = 5,
            comment = "",
            completedInTime = true,
            timeToCompleteMs = 5000,
            sessionId = "test-session-2"
        )
        vm.submitSurvey(survey)
        testScheduler.advanceTimeBy(100)

        val captor = argumentCaptor<String>()
        org.mockito.Mockito.verify(wsRepo, atLeast(3)).send(captor.capture())

        val allValues = captor.allValues
        assertTrue(allValues.any { it.contains("\"type\":\"survey\"") })
        val surveyIdx = allValues.indexOfFirst { it.contains("\"type\":\"survey\"") }
        val endIdx = allValues.indexOfFirst { json ->
            json.contains("\"type\":\"session_command\"") && json.contains("\"action\":\"end\"")
        }
        assertTrue("session_command end should be sent after survey data", endIdx > surveyIdx)
    }

    // Test 15: dismissSurvey also sends survey data (with completedInTime=false)
    @Test
    fun `dismissSurvey sends partial survey data`() = testScope.runTest {
        val vm = createViewModel()
        val dismissedSurvey = SurveyData(
            starRating = 3,
            understood = 0,
            helpful = 0,
            natural = 0,
            attentive = 0,
            comment = "",
            completedInTime = false,
            timeToCompleteMs = 20000,
            sessionId = "test-session-3"
        )
        vm.dismissSurvey(dismissedSurvey)
        testScheduler.advanceTimeBy(100)

        val captor = argumentCaptor<String>()
        org.mockito.Mockito.verify(wsRepo, atLeast(2)).send(captor.capture())

        val surveyJson = captor.allValues.first { it.contains("\"type\":\"survey\"") }
        assertTrue(surveyJson.contains("\"completed\":false"))
        assertTrue(surveyJson.contains("\"star_rating\":3"))
    }
}
