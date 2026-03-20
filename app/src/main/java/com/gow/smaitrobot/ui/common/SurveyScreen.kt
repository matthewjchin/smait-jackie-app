package com.gow.smaitrobot.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gow.smaitrobot.data.model.SurveyData
import com.gow.smaitrobot.ui.conversation.SurveyBuilder
import kotlinx.coroutines.delay
import java.util.UUID

private val DeepPurple = Color(0xFF1B0A6E)
private val Gold = Color(0xFFFFD700)
private val LightPurple = Color(0xFF3D2B9E)
private val SurfaceWhite = Color(0xFFFAFAFA)
private val SubtleGray = Color(0xFF888888)

/**
 * Full-screen post-interaction survey overlay for WiE 2026 data collection.
 *
 * Collects 5 items (star rating + 4 Likert questions) plus an optional comment.
 * Auto-dismisses after 20 seconds with a visible countdown timer.
 *
 * @param onSubmit   Called with [SurveyData] when the user taps Submit or timer expires.
 * @param onDismiss  Called when the survey auto-dismisses (timer expired, no interaction).
 */
@Composable
fun SurveyScreen(
    onSubmit: (SurveyData) -> Unit,
    onDismiss: (SurveyData) -> Unit
) {
    val surveyStartTime = remember { System.currentTimeMillis() }
    val sessionId = remember { UUID.randomUUID().toString() }

    var starRating by remember { mutableIntStateOf(0) }
    var understood by remember { mutableIntStateOf(0) }
    var helpful by remember { mutableIntStateOf(0) }
    var natural by remember { mutableIntStateOf(0) }
    var attentive by remember { mutableIntStateOf(0) }
    var comment by remember { mutableStateOf("") }

    var remainingSeconds by remember { mutableLongStateOf(20L) }
    var hasInteracted by remember { mutableStateOf(false) }

    // Countdown timer: ticks every second, auto-dismisses at 0
    LaunchedEffect(Unit) {
        val endTime = surveyStartTime + SurveyBuilder.SURVEY_TIMEOUT_MS
        while (true) {
            val now = System.currentTimeMillis()
            val remaining = (endTime - now) / 1000L
            remainingSeconds = remaining.coerceAtLeast(0)
            if (remaining <= 0) {
                // Auto-dismiss: send whatever was filled in so far
                onDismiss(
                    SurveyBuilder.buildDismissed(
                        starRating = starRating,
                        understood = understood,
                        helpful = helpful,
                        natural = natural,
                        attentive = attentive,
                        comment = comment,
                        startTimeMs = surveyStartTime,
                        sessionId = sessionId
                    )
                )
                break
            }
            delay(1000L)
        }
    }

    WieBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            // Countdown timer — top-right corner
            Text(
                text = "${remainingSeconds}s",
                fontSize = 16.sp,
                color = DeepPurple.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 20.dp)
            )

            // Centered survey content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Title
                Text(
                    text = "How was your experience?",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeepPurple,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Your feedback helps us improve Jackie",
                    fontSize = 18.sp,
                    color = DeepPurple.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Item 1: Star rating
                Text(
                    text = "How was your experience talking with Jackie?",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = DeepPurple,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                StarRatingRow(
                    selected = starRating,
                    onSelect = { rating ->
                        starRating = rating
                        hasInteracted = true
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Item 2: Understood
                LikertQuestion(
                    question = "Jackie understood what I said",
                    selected = understood,
                    onSelect = { value ->
                        understood = value
                        hasInteracted = true
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Item 3: Helpful
                LikertQuestion(
                    question = "Jackie's responses were helpful and relevant",
                    selected = helpful,
                    onSelect = { value ->
                        helpful = value
                        hasInteracted = true
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Item 4: Natural
                LikertQuestion(
                    question = "The conversation felt natural",
                    selected = natural,
                    onSelect = { value ->
                        natural = value
                        hasInteracted = true
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Item 5: Attentive
                LikertQuestion(
                    question = "I felt Jackie was paying attention to me",
                    selected = attentive,
                    onSelect = { value ->
                        attentive = value
                        hasInteracted = true
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Item 6: Optional comment
                OutlinedTextField(
                    value = comment,
                    onValueChange = {
                        comment = it
                        hasInteracted = true
                    },
                    placeholder = {
                        Text(
                            "Any other feedback? (optional)",
                            fontSize = 18.sp,
                            color = SubtleGray
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp),
                    maxLines = 3,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DeepPurple,
                        unfocusedBorderColor = DeepPurple.copy(alpha = 0.3f),
                        cursorColor = DeepPurple
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Submit button
                Button(
                    onClick = {
                        onSubmit(
                            SurveyBuilder.buildCompleted(
                                starRating = starRating,
                                understood = understood,
                                helpful = helpful,
                                natural = natural,
                                attentive = attentive,
                                comment = comment,
                                startTimeMs = surveyStartTime,
                                sessionId = sessionId
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 400.dp)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DeepPurple,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Submit", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Row of 5 tappable star icons. Stars up to and including [selected] are filled gold.
 */
@Composable
private fun StarRatingRow(
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        for (star in 1..5) {
            IconButton(
                onClick = { onSelect(star) },
                modifier = Modifier.size(60.dp)
            ) {
                Icon(
                    imageVector = if (star <= selected) Icons.Filled.Star
                    else Icons.Outlined.StarOutline,
                    contentDescription = "$star star",
                    tint = if (star <= selected) Gold
                    else DeepPurple.copy(alpha = 0.25f),
                    modifier = Modifier.size(52.dp)
                )
            }
        }
    }
}

/**
 * A single Likert-scale question with 5 numbered circle buttons.
 * Labels "Strongly Disagree" and "Strongly Agree" at the ends.
 */
@Composable
private fun LikertQuestion(
    question: String,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = question,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = DeepPurple,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Scale labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Strongly\nDisagree",
                fontSize = 14.sp,
                color = DeepPurple.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
            Text(
                text = "Strongly\nAgree",
                fontSize = 14.sp,
                color = DeepPurple.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 5 circle buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            for (value in 1..5) {
                LikertButton(
                    value = value,
                    isSelected = value == selected,
                    onClick = { onSelect(value) }
                )
            }
        }
    }
}

/**
 * A single numbered circle button for the Likert scale.
 * 44dp minimum touch target. Filled purple when selected, outlined when not.
 */
@Composable
private fun LikertButton(
    value: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) DeepPurple else Color.Transparent
    val textColor = if (isSelected) Color.White else DeepPurple
    val borderColor = if (isSelected) DeepPurple else DeepPurple.copy(alpha = 0.3f)

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(bgColor, CircleShape)
            .border(2.dp, borderColor, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = value.toString(),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}
