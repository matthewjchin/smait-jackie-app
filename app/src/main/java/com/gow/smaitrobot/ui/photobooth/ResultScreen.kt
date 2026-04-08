package com.gow.smaitrobot.ui.photobooth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightspark.composeqr.QrCodeView
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Angle
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.Spread
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.util.concurrent.TimeUnit

/**
 * Branded share-card result screen for Photo Booth v2.
 *
 * Layout (top → bottom):
 *   1. Branded header       — "Jackie × [Style Name]" on a style-tinted gradient
 *   2. Styled photo hero    — large, shadowed, rounded card. Crossfade raw → styled.
 *   3. QR panel             — "Scan to save your photo" above a white QR card
 *   4. Action bar           — [Retake] [Exit]
 *
 * Visual effects:
 *   - Crossfade animation (600ms) from raw capture → styled result
 *   - Confetti burst from top-center on first entry, tinted by the style palette
 *   - Medium haptic feedback on first reveal
 *   - Header + QR panel slide up on entry
 *
 * The ACTION_SEND "save to phone" flow is intentionally absent — this app
 * runs on Jackie's touchscreen, so the user's phone is not present. The
 * QR code IS the save mechanism (scan → open URL → save image to their
 * phone from the browser).
 *
 * @param state     Current [PhotoBoothUiState.Result] — provides raw/styled bitmaps and URL
 * @param onRetake  Called when user taps "Retake" — resets to StylePicker without server roundtrip
 * @param onExit    Called when user taps "Exit" — sends photo_booth_exit and navigates back
 */
@Composable
fun ResultScreen(
    state: PhotoBoothUiState.Result,
    onRetake: () -> Unit,
    onExit: () -> Unit
) {
    val displayBitmap = state.styledBitmap ?: state.rawBitmap
    val isError = state.styledBitmap == null

    val haptic = LocalHapticFeedback.current
    var celebrated by remember { mutableStateOf(false) }
    var revealChrome by remember { mutableStateOf(false) }

    // Style-specific theming: header label, background gradient, accent color,
    // confetti palette. Defaults to the SMAIT brand violet if the Result state
    // didn't carry a styleKey (e.g. from an older server build).
    val styleOption = remember(state.styleKey) {
        STYLE_OPTIONS.firstOrNull { it.key == state.styleKey }
    }
    val styleLabel = remember(styleOption) {
        styleOption?.let { "Jackie × ${it.label}" } ?: "Jackie × Photo Booth"
    }
    val theme = remember(state.styleKey) { styleThemeFor(state.styleKey) }

    LaunchedEffect(state.styledBitmap) {
        if (state.styledBitmap != null && !celebrated) {
            celebrated = true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    LaunchedEffect(Unit) {
        revealChrome = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(theme.topBg, theme.bottomBg)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            // ─── Branded header ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = revealChrome,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { -it / 2 },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "JACKIE",
                        color = theme.accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 4.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = styleLabel,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // ─── Hero styled photo ───────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(
                    targetState = displayBitmap,
                    animationSpec = tween(600),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .shadow(
                            elevation = 24.dp,
                            shape = RoundedCornerShape(20.dp),
                            clip = false,
                        )
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1A1A2E))
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(
                                listOf(theme.accent.copy(alpha = 0.6f), Color.White.copy(alpha = 0.2f))
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                ) { bitmap ->
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = if (isError) {
                                "Original photo (style transfer failed)"
                            } else "Styled photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Loading...",
                                color = Color.White,
                                fontSize = 20.sp
                            )
                        }
                    }
                }
            }

            // Inline error banner — only if the styler failed
            if (isError && state.rawBitmap != null) {
                Text(
                    text = "Style transfer failed — showing original photo",
                    color = Color(0xFFFFB4B4),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                )
            }

            // ─── QR "scan to save" panel ─────────────────────────────────────
            AnimatedVisibility(
                visible = revealChrome && state.downloadUrl != null,
                enter = fadeIn(tween(400, delayMillis = 200)) +
                        slideInVertically(tween(400, delayMillis = 200)) { it / 3 },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "SCAN TO SAVE",
                            color = theme.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Point your phone camera at the QR code",
                            color = Color(0xFFE0E0F0),
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "jackie.sjsu",
                            color = Color(0xFF9090B0),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.sp,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    state.downloadUrl?.let { url ->
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .shadow(12.dp, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(10.dp)
                        ) {
                            QrCodeView(
                                data = url,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            // ─── Action bar: Retake | Exit ───────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onRetake,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        text = "Retake",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
                Button(
                    onClick = onExit,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.accent
                    )
                ) {
                    Text(
                        text = "Done",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // ─── Confetti reveal layer ───────────────────────────────────────────
        if (celebrated) {
            KonfettiView(
                modifier = Modifier.fillMaxSize(),
                parties = rememberParties(theme.confettiColors)
            )
        }
    }
}

/**
 * Visual theme bundle for a given style. Keeps the ResultScreen colors
 * responsive to which style the user picked without threading the style
 * key through the UI layer. Defaults to the SMAIT brand violet.
 */
private data class StyleTheme(
    val topBg: Color,
    val bottomBg: Color,
    val accent: Color,
    val confettiColors: List<Color>,
)

private fun styleThemeFor(styleKey: String?): StyleTheme = when (styleKey) {
    "cyberpunk" -> StyleTheme(
        topBg = Color(0xFF0E0020),
        bottomBg = Color(0xFF1A0030),
        accent = Color(0xFFFF2EA8),
        confettiColors = listOf(Color(0xFFFF2EA8), Color(0xFF00E5FF), Color(0xFF7C4DFF)),
    )
    "anime" -> StyleTheme(
        topBg = Color(0xFF0F1A2B),
        bottomBg = Color(0xFF1C2C42),
        accent = Color(0xFFFFCC70),
        confettiColors = listOf(Color(0xFFFFCC70), Color(0xFFFFADAD), Color(0xFF9DE0F7)),
    )
    "pop_art" -> StyleTheme(
        topBg = Color(0xFF1A0A02),
        bottomBg = Color(0xFF2A1200),
        accent = Color(0xFFFFCA00),
        confettiColors = listOf(Color(0xFFFF3B30), Color(0xFFFFCA00), Color(0xFF0E8AFF)),
    )
    "robot_vision" -> StyleTheme(
        topBg = Color(0xFF001810),
        bottomBg = Color(0xFF002A1A),
        accent = Color(0xFF00FF9C),
        confettiColors = listOf(Color(0xFF00FF9C), Color(0xFF00E5FF), Color(0xFFB0FFD0)),
    )
    "oil_painting" -> StyleTheme(
        topBg = Color(0xFF1A0F00),
        bottomBg = Color(0xFF2A1A05),
        accent = Color(0xFFDCA85A),
        confettiColors = listOf(Color(0xFFDCA85A), Color(0xFFA83F2C), Color(0xFFE8D9B3)),
    )
    "pixel_art" -> StyleTheme(
        topBg = Color(0xFF0A0020),
        bottomBg = Color(0xFF15003A),
        accent = Color(0xFF8A63FF),
        confettiColors = listOf(Color(0xFF8A63FF), Color(0xFFFF4D7E), Color(0xFF4DE4C4)),
    )
    else -> StyleTheme(
        topBg = Color(0xFF0D0D14),
        bottomBg = Color(0xFF1A1A2E),
        accent = Color(0xFF7C4DFF),
        confettiColors = listOf(Color(0xFF7C4DFF), Color(0xFFFF4D7E), Color(0xFF4DE4C4), Color(0xFFFFCC70)),
    )
}

@Composable
private fun rememberParties(colors: List<Color>): List<Party> = remember(colors) {
    listOf(
        Party(
            speed = 0f,
            maxSpeed = 30f,
            damping = 0.9f,
            angle = Angle.BOTTOM,
            spread = Spread.ROUND,
            colors = colors.map { it.toArgb() },
            emitter = Emitter(duration = 1200, TimeUnit.MILLISECONDS).perSecond(120),
            position = Position.Relative(0.5, 0.0),
        )
    )
}

