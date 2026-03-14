package com.gow.smaitrobot

import com.gow.smaitrobot.data.model.ChatMessage
import com.gow.smaitrobot.data.model.FeedbackData
import com.gow.smaitrobot.ui.conversation.ChatBubbleAlignment
import com.gow.smaitrobot.ui.conversation.FeedbackBuilder
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for ConversationScreen UI logic.
 *
 * Tests focus on pure logic functions extracted from Composables:
 * - [ChatBubbleAlignment.isEndAligned] — user messages right-aligned, robot left-aligned
 * - [FeedbackBuilder.build] — builds FeedbackData from rating + survey responses
 * - [FeedbackBuilder.isValidRating] — validates 1-5 star rating
 * - [FeedbackBuilder.isAutoTimeoutDue] — checks if 10s timeout has elapsed
 *
 * These are plain JVM tests (no Compose test rule needed) that test the alignment
 * decision and feedback data construction logic independent of UI rendering.
 */
class ConversationUiTest {

    // ── ChatBubble alignment tests ────────────────────────────────────────────

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

    // ── FeedbackDialog auto-dismiss timeout test ──────────────────────────────

    // Test 3: FeedbackDialog auto-dismisses after ~10 seconds
    @Test
    fun `isAutoTimeoutDue returns true after 10 seconds elapsed`() {
        val startTime = System.currentTimeMillis() - 11_000L // 11 seconds ago
        assertTrue(FeedbackBuilder.isAutoTimeoutDue(startTime, timeoutMs = 10_000L))
    }

    @Test
    fun `isAutoTimeoutDue returns false before 10 seconds elapsed`() {
        val startTime = System.currentTimeMillis() - 5_000L // 5 seconds ago
        assertFalse(FeedbackBuilder.isAutoTimeoutDue(startTime, timeoutMs = 10_000L))
    }

    // ── FeedbackDialog star rating tests ─────────────────────────────────────

    // Test 4: FeedbackDialog star rating validates selected rating state on tap
    @Test
    fun `isValidRating returns true for ratings 1 to 5`() {
        for (r in 1..5) {
            assertTrue(FeedbackBuilder.isValidRating(r), "Expected valid for rating=$r")
        }
    }

    @Test
    fun `isValidRating returns false for out-of-range ratings`() {
        assertFalse(FeedbackBuilder.isValidRating(0))
        assertFalse(FeedbackBuilder.isValidRating(6))
        assertFalse(FeedbackBuilder.isValidRating(-1))
    }

    // ── FeedbackBuilder data construction test ────────────────────────────────

    // Test 5: FeedbackDialog submit calls onSubmit with correct FeedbackData including rating
    @Test
    fun `build creates FeedbackData with correct rating and sessionId`() {
        val responses = mapOf("q1" to "5", "q2" to "yes", "q3" to "Great robot!")
        val feedback = FeedbackBuilder.build(
            rating = 4,
            sessionId = "session-abc",
            surveyResponses = responses
        )

        assertEquals(4, feedback.rating)
        assertEquals("session-abc", feedback.sessionId)
        assertEquals(responses, feedback.surveyResponses)
    }

    @Test
    fun `build creates FeedbackData with empty survey when no responses`() {
        val feedback = FeedbackBuilder.build(rating = 3, sessionId = "s1")
        assertEquals(3, feedback.rating)
        assertEquals("s1", feedback.sessionId)
        assertTrue(feedback.surveyResponses.isEmpty())
    }

    @Test
    fun `build includes non-zero timestamp`() {
        val before = System.currentTimeMillis()
        val feedback = FeedbackBuilder.build(rating = 5, sessionId = "s2")
        val after = System.currentTimeMillis()
        assertTrue(feedback.timestamp in before..after)
    }
}
