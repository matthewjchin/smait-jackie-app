package com.gow.smaitrobot

import com.gow.smaitrobot.data.model.ChatMessage
import com.gow.smaitrobot.ui.conversation.ChatBubbleAlignment
import com.gow.smaitrobot.ui.conversation.SurveyBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for ConversationScreen UI logic.
 *
 * Tests focus on pure logic functions extracted from Composables:
 * - [ChatBubbleAlignment.isEndAligned] -- user messages right-aligned, robot left-aligned
 * - [SurveyBuilder.buildCompleted] -- builds SurveyData for completed submissions
 * - [SurveyBuilder.buildDismissed] -- builds SurveyData for auto-dismissed surveys
 * - [SurveyBuilder.isValidRating] -- validates 1-5 star rating
 * - [SurveyBuilder.isValidLikert] -- validates 1-5 Likert responses
 * - [SurveyBuilder.isAutoTimeoutDue] -- checks if 20s timeout has elapsed
 *
 * These are plain JVM tests (no Compose test rule needed).
 */
class ConversationUiTest {

    // -- ChatBubble alignment tests --

    // Test 1: ChatBubble with isUser=true uses right alignment (Arrangement.End)
    @Test
    fun `user message ChatBubble is end-aligned`() {
        val message = ChatMessage(id = "1", text = "Hello", isUser = true)
        assertTrue(ChatBubbleAlignment.isEndAligned(message))
    }

    // Test 2: ChatBubble with isUser=false uses left alignment (Arrangement.Start)
    @Test
    fun `robot message ChatBubble is start-aligned`() {
        val message = ChatMessage(id = "2", text = "Hi there!", isUser = false)
        assertFalse(ChatBubbleAlignment.isEndAligned(message))
    }

    // -- Survey auto-dismiss timeout tests --

    // Test 3: Survey auto-dismisses after ~20 seconds
    @Test
    fun `isAutoTimeoutDue returns true after 20 seconds elapsed`() {
        val startTime = System.currentTimeMillis() - 21_000L
        assertTrue(SurveyBuilder.isAutoTimeoutDue(startTime, timeoutMs = 20_000L))
    }

    @Test
    fun `isAutoTimeoutDue returns false before 20 seconds elapsed`() {
        val startTime = System.currentTimeMillis() - 10_000L
        assertFalse(SurveyBuilder.isAutoTimeoutDue(startTime, timeoutMs = 20_000L))
    }

    // -- Star rating validation tests --

    // Test 4: Star rating validates selected rating state on tap
    @Test
    fun `isValidRating returns true for ratings 1 to 5`() {
        for (r in 1..5) {
            assertTrue("Expected valid for rating=$r", SurveyBuilder.isValidRating(r))
        }
    }

    @Test
    fun `isValidRating returns false for out-of-range ratings`() {
        assertFalse(SurveyBuilder.isValidRating(0))
        assertFalse(SurveyBuilder.isValidRating(6))
        assertFalse(SurveyBuilder.isValidRating(-1))
    }

    // -- Likert validation tests --

    @Test
    fun `isValidLikert returns true for values 1 to 5`() {
        for (v in 1..5) {
            assertTrue("Expected valid for likert=$v", SurveyBuilder.isValidLikert(v))
        }
    }

    @Test
    fun `isValidLikert returns false for out-of-range values`() {
        assertFalse(SurveyBuilder.isValidLikert(0))
        assertFalse(SurveyBuilder.isValidLikert(6))
        assertFalse(SurveyBuilder.isValidLikert(-1))
    }

    // -- SurveyBuilder data construction tests --

    // Test 5: buildCompleted creates SurveyData with all fields
    @Test
    fun `buildCompleted creates SurveyData with correct fields`() {
        val startTime = System.currentTimeMillis() - 8500L
        val survey = SurveyBuilder.buildCompleted(
            starRating = 4,
            understood = 5,
            helpful = 4,
            natural = 3,
            attentive = 4,
            comment = "Great robot!",
            startTimeMs = startTime,
            sessionId = "session-abc"
        )

        assertEquals(4, survey.starRating)
        assertEquals(5, survey.understood)
        assertEquals(4, survey.helpful)
        assertEquals(3, survey.natural)
        assertEquals(4, survey.attentive)
        assertEquals("Great robot!", survey.comment)
        assertTrue(survey.completedInTime)
        assertEquals("session-abc", survey.sessionId)
        assertTrue(survey.timeToCompleteMs >= 8400) // approximately 8500ms
    }

    @Test
    fun `buildCompleted with empty comment`() {
        val startTime = System.currentTimeMillis() - 5000L
        val survey = SurveyBuilder.buildCompleted(
            starRating = 3,
            understood = 3,
            helpful = 3,
            natural = 3,
            attentive = 3,
            comment = "",
            startTimeMs = startTime,
            sessionId = "s1"
        )
        assertEquals(3, survey.starRating)
        assertEquals("", survey.comment)
        assertTrue(survey.completedInTime)
    }

    // Test 6: buildDismissed creates SurveyData with completedInTime=false
    @Test
    fun `buildDismissed creates SurveyData with completedInTime false`() {
        val startTime = System.currentTimeMillis() - 20_000L
        val survey = SurveyBuilder.buildDismissed(
            starRating = 0,
            startTimeMs = startTime,
            sessionId = "s2"
        )

        assertEquals(0, survey.starRating)
        assertEquals(0, survey.understood)
        assertEquals(0, survey.helpful)
        assertEquals(0, survey.natural)
        assertEquals(0, survey.attentive)
        assertEquals("", survey.comment)
        assertFalse(survey.completedInTime)
        assertEquals("s2", survey.sessionId)
    }

    @Test
    fun `buildDismissed preserves partial answers`() {
        val startTime = System.currentTimeMillis() - 15_000L
        val survey = SurveyBuilder.buildDismissed(
            starRating = 4,
            understood = 3,
            helpful = 0,
            natural = 0,
            attentive = 0,
            comment = "",
            startTimeMs = startTime,
            sessionId = "s3"
        )

        assertEquals(4, survey.starRating)
        assertEquals(3, survey.understood)
        assertEquals(0, survey.helpful)
        assertFalse(survey.completedInTime)
    }

    @Test
    fun `buildCompleted includes non-zero timestamp`() {
        val before = System.currentTimeMillis()
        val survey = SurveyBuilder.buildCompleted(
            starRating = 5,
            understood = 5,
            helpful = 5,
            natural = 5,
            attentive = 5,
            comment = "",
            startTimeMs = before - 1000L,
            sessionId = "s4"
        )
        val after = System.currentTimeMillis()
        assertTrue(survey.timestamp in before..after)
    }

    // -- Survey timeout constant test --

    @Test
    fun `survey timeout is 20 seconds`() {
        assertEquals(20_000L, SurveyBuilder.SURVEY_TIMEOUT_MS)
    }
}
