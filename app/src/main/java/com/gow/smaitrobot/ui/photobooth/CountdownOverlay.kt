package com.gow.smaitrobot.ui.photobooth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val OverlayBackground = Color(0xCC000000) // 80% opaque black

/**
 * Full-screen countdown overlay displaying 3, 2, 1.
 *
 * Each number transition uses a scale+fade animation (150-300ms per UI/UX animation rules).
 * The ViewModel drives [secondsLeft] by decrementing every 1s via delay() in onTakePhoto().
 *
 * A light haptic fires on every number change (3, 2, 1) so users feel the
 * countdown through Jackie's touchscreen — makes the 3s feel intentional
 * rather than a dead wait.
 *
 * @param secondsLeft Current countdown value (3, 2, or 1).
 */
@Composable
fun CountdownOverlay(secondsLeft: Int) {
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(secondsLeft) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OverlayBackground),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = secondsLeft,
            transitionSpec = {
                // Scale up + fade in from 0.7x, fade out
                (scaleIn(initialScale = 0.7f) + fadeIn()) togetherWith
                        (scaleOut(targetScale = 1.3f) + fadeOut())
            },
            label = "countdown"
        ) { count ->
            Text(
                text = count.toString(),
                fontSize = 120.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }
    }
}
