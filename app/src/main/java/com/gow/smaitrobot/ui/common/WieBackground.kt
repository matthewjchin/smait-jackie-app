package com.gow.smaitrobot.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.gow.smaitrobot.R

/**
 * Full-screen WiE gradient background (pastel yellow/pink/purple/teal with
 * geometric wireframe overlay). Extracted from the WiE Program Committee
 * Meeting slides.
 *
 * Wraps [content] on top of the gradient image.
 */
@Composable
fun WieBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.wie_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        content()
    }
}
