package com.gow.smaitrobot.ui.conversation

import com.gow.smaitrobot.data.model.SurveyData

/**
 * Pure logic object for building [SurveyData] from survey screen state.
 * Extracted to a plain Kotlin object for JVM testability without Compose runtime.
 */
object SurveyBuilder {

    /** Survey auto-dismiss timeout in milliseconds. */
    const val SURVEY_TIMEOUT_MS = 20_000L

    /**
     * Returns true if [rating] is within the valid 1-5 range.
     */
    fun isValidRating(rating: Int): Boolean = rating in 1..5

    /**
     * Returns true if [value] is a valid Likert response (1-5).
     */
    fun isValidLikert(value: Int): Boolean = value in 1..5

    /**
     * Returns true if the auto-dismiss timeout has elapsed.
     *
     * @param startTimeMs   Unix epoch ms when the survey was shown.
     * @param timeoutMs     Duration before auto-dismiss (default 20 seconds).
     */
    fun isAutoTimeoutDue(startTimeMs: Long, timeoutMs: Long = SURVEY_TIMEOUT_MS): Boolean {
        return System.currentTimeMillis() - startTimeMs >= timeoutMs
    }

    /**
     * Builds a [SurveyData] for a completed (user-submitted) survey.
     */
    fun buildCompleted(
        starRating: Int,
        understood: Int,
        helpful: Int,
        natural: Int,
        attentive: Int,
        comment: String,
        startTimeMs: Long,
        sessionId: String
    ): SurveyData = SurveyData(
        starRating = starRating,
        understood = understood,
        helpful = helpful,
        natural = natural,
        attentive = attentive,
        comment = comment,
        completedInTime = true,
        timeToCompleteMs = System.currentTimeMillis() - startTimeMs,
        sessionId = sessionId
    )

    /**
     * Builds a [SurveyData] for an auto-dismissed (timed out) survey.
     * All ratings default to 0 (not answered).
     */
    fun buildDismissed(
        starRating: Int = 0,
        understood: Int = 0,
        helpful: Int = 0,
        natural: Int = 0,
        attentive: Int = 0,
        comment: String = "",
        startTimeMs: Long,
        sessionId: String
    ): SurveyData = SurveyData(
        starRating = starRating,
        understood = understood,
        helpful = helpful,
        natural = natural,
        attentive = attentive,
        comment = comment,
        completedInTime = false,
        timeToCompleteMs = System.currentTimeMillis() - startTimeMs,
        sessionId = sessionId
    )
}
