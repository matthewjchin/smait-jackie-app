package com.gow.smaitrobot.data.model

/**
 * Post-interaction survey data collected for IMECE conference paper.
 *
 * Collected at the end of each conversation session via a full-screen survey overlay.
 * All Likert fields use 1-5 scale (0 = not answered / auto-dismissed).
 *
 * @param starRating        Overall experience rating (1-5 stars, 0 = not rated).
 * @param understood        "Jackie understood what I said" (1-5 Likert, 0 = unanswered).
 * @param helpful           "Jackie's responses were helpful and relevant" (1-5 Likert).
 * @param natural           "The conversation felt natural" (1-5 Likert).
 * @param attentive         "I felt Jackie was paying attention to me" (1-5 Likert).
 * @param comment           Optional free-text feedback.
 * @param completedInTime   False if the survey auto-dismissed before user submitted.
 * @param timeToCompleteMs  Milliseconds spent on the survey screen.
 * @param sessionId         Unique conversation session ID.
 * @param timestamp         Unix epoch ms when survey was submitted/dismissed.
 */
data class SurveyData(
    val starRating: Int,
    val understood: Int,
    val helpful: Int,
    val natural: Int,
    val attentive: Int,
    val comment: String,
    val completedInTime: Boolean,
    val timeToCompleteMs: Long,
    val sessionId: String,
    val timestamp: Long = System.currentTimeMillis()
)
