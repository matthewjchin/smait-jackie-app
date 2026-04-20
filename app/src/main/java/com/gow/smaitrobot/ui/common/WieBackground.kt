package com.gow.smaitrobot.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Full-screen event gradient background.
 *
 * Wraps [content] on top of the HFES dark navy gradient.
 */
@Composable
fun WieBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0D1526),
                        Color(0xFF1A2838),
                        Color(0xFF121E30)
                    )
                )
            )
    ) {
        content()
    }
}
