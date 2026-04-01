package com.gow.smaitrobot.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gow.smaitrobot.data.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// BABMDC 2026 green theme
private val BabmdcGreen = Color(0xFF66BB6A)
private val BabmdcDarkGreen = Color(0xFF1B5E20)
private val UserBlue = Color(0xFF1565C0)

/**
 * Pure logic object for ChatBubble alignment decisions.
 */
object ChatBubbleAlignment {
    fun isEndAligned(message: ChatMessage): Boolean = message.isUser
}

/**
 * Chat bubble with clear visual distinction between user and robot.
 * Uses WiE color scheme and large accessible fonts.
 *
 * - User messages: right-aligned, teal tint, "You" label
 * - Robot messages: left-aligned, white/frost, "Jackie" label
 */
@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.isUser
    val arrangement = if (isUser) Arrangement.End else Arrangement.Start
    val bubbleColor = if (isUser) {
        BabmdcGreen
    } else {
        Color.White.copy(alpha = 0.85f)
    }
    val textColor = if (isUser) Color.White else Color(0xFF1A1A2E)
    val labelColor = if (isUser) Color.White.copy(alpha = 0.85f) else BabmdcDarkGreen

    val shape = if (isUser) {
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = arrangement
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .clip(shape)
                .background(bubbleColor)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Text(
                text = if (isUser) "You" else "Jackie",
                color = labelColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message.text,
                color = textColor,
                fontSize = 36.sp,
                lineHeight = 44.sp
            )
            Text(
                text = formatTimestamp(message.timestamp),
                color = textColor.copy(alpha = 0.4f),
                fontSize = 22.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

private fun formatTimestamp(epochMs: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(epochMs))
}
