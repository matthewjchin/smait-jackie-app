package com.gow.smaitrobot.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.layout.layout
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gow.smaitrobot.data.model.SponsorConfig

/**
 * Horizontal sponsor logo bar with infinite seamless marquee scroll.
 *
 * Places two identical logo strips back-to-back. Animates the offset from 0
 * to exactly -stripWidth so when it resets, the second strip is pixel-perfect
 * where the first one started. No gap, no jump.
 */
@Composable
fun SponsorBar(
    sponsors: List<SponsorConfig>,
    modifier: Modifier = Modifier
) {
    if (sponsors.isEmpty()) return
    // Always marquee — even a short sponsor list should roll.
    MarqueeRow(sponsors = sponsors, modifier = modifier)
}

@Composable
private fun MarqueeRow(
    sponsors: List<SponsorConfig>,
    modifier: Modifier = Modifier
) {
    // Repeat the list so one strip is always wider than the viewport.
    // With 3 generously-spaced sponsors, 4x guarantees overlap on wide tablets.
    val repeatedSponsors = remember(sponsors) {
        val reps = when {
            sponsors.size <= 3 -> 4
            sponsors.size < 6 -> 3
            else -> 2
        }
        (1..reps).flatMap { sponsors }
    }

    // Width of one full set of logos (measured from the first strip)
    var stripWidthPx by remember { mutableIntStateOf(0) }

    val durationMs = repeatedSponsors.size * 3500

    val infiniteTransition = rememberInfiniteTransition(label = "marquee")
    val offsetFraction by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "marquee_offset"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(Color.Transparent)
            .clipToBounds()
    ) {
        // Strip 1 — also used to measure the exact width of one set
        LogoStrip(
            sponsors = repeatedSponsors,
            modifier = Modifier
                .unboundedWidth()
                .onSizeChanged { stripWidthPx = it.width }
                .graphicsLayer {
                    translationX = offsetFraction * stripWidthPx
                }
        )

        // Strip 2 — positioned exactly one stripWidth to the right of strip 1
        if (stripWidthPx > 0) {
            LogoStrip(
                sponsors = repeatedSponsors,
                modifier = Modifier
                    .unboundedWidth()
                    .graphicsLayer {
                        translationX = stripWidthPx + (offsetFraction * stripWidthPx)
                    }
            )
        }
    }
}

/**
 * One full set of sponsor logos in a row.
 */
@Composable
private fun LogoStrip(
    sponsors: List<SponsorConfig>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        sponsors.forEach { sponsor ->
            SponsorLogo(
                sponsor = sponsor,
                modifier = Modifier.padding(horizontal = 72.dp)
            )
        }
    }
}

@Composable
private fun SponsorLogo(
    sponsor: SponsorConfig,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resName = sponsor.logoAsset.substringBeforeLast(".")
    val resId = if (resName.isNotEmpty()) {
        context.resources.getIdentifier(resName, "drawable", context.packageName)
    } else 0

    if (resId != 0) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = "${sponsor.name} logo",
            modifier = modifier
                .height(140.dp)
                .widthIn(max = 400.dp),
            contentScale = ContentScale.Fit
        )
    } else if (sponsor.name.isNotEmpty()) {
        androidx.compose.material3.Text(
            text = sponsor.name,
            modifier = modifier.padding(horizontal = 12.dp),
            fontSize = 64.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** Remove the parent's max-width constraint so the Row measures at its full content width. */
private fun Modifier.unboundedWidth() = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(maxWidth = 100_000))
    layout(placeable.width, placeable.height) {
        placeable.place(0, 0)
    }
}
