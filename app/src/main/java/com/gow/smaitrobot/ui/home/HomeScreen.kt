package com.gow.smaitrobot.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.gow.smaitrobot.R
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.gow.smaitrobot.data.model.CardConfig
import com.gow.smaitrobot.data.model.SpeakerInfo
import com.gow.smaitrobot.navigation.Screen
import com.gow.smaitrobot.ui.common.SponsorBar
import com.gow.smaitrobot.ui.common.TopLogoBar
import com.gow.smaitrobot.ui.common.WieBackground

// BABMDC event colors (biomedical green palette)
private val EventDark = Color(0xFF1A3D1A)
private val EventAccent = Color(0xFF6EC26E)
private val CardPrimary = Color(0xFF2D5A2D)
private val CardSecondary = Color(0xFF376137)

/**
 * Home screen — the primary landing screen on Jackie's kiosk display.
 *
 * Layout:
 * 1. Top: Logo bar (SJSU | Event | BioRob) — long press opens hidden Settings
 * 2. Middle: Event conference graphic (left) + 4 cards in 2x2 grid (right)
 * 3. Bottom: Sponsor bar
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val eventName by viewModel.eventName.collectAsStateWithLifecycle()
    val tagline by viewModel.tagline.collectAsStateWithLifecycle()
    val sponsors by viewModel.sponsors.collectAsStateWithLifecycle()
    val speakers by viewModel.speakers.collectAsStateWithLifecycle()

    var inlineContentKey by remember { mutableStateOf<String?>(null) }

    WieBackground(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Top row: BioRob (left) | Banner (center) | SJSU ME (right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            navController.navigate(Screen.Settings) {
                                launchSingleTop = true
                            }
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // BioRob Lab logo (left, inset from edge)
                Image(
                    painter = painterResource(id = R.drawable.biorob_logo),
                    contentDescription = "BioRob Lab",
                    modifier = Modifier
                        .height(200.dp)
                        .padding(start = 24.dp),
                    contentScale = ContentScale.Fit
                )

                // BABMDC banner (center, nudged left to visually center)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.babmdc_logo),
                        contentDescription = "BABMDC 2026 Banner",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit
                    )
                }

                // SJSU ME logo (right, 2x bigger, inset from edge)
                Image(
                    painter = painterResource(id = R.drawable.sjsu_logo),
                    contentDescription = "SJSU Mechanical Engineering",
                    modifier = Modifier
                        .height(400.dp)
                        .padding(end = 40.dp),
                    contentScale = ContentScale.Fit
                )
            }

            // 2. Cards — centered 2x2 grid, double spread
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(0.80f)
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically)
            ) {
                // Top row
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    cards.getOrNull(0)?.let { card ->
                        HomeCard(
                            card = card,
                            cardIndex = 0,
                            onClick = { handleCardClick(card, viewModel, navController) { inlineContentKey = it } },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                    cards.getOrNull(1)?.let { card ->
                        HomeCard(
                            card = card,
                            cardIndex = 1,
                            onClick = { handleCardClick(card, viewModel, navController) { inlineContentKey = it } },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
                // Bottom row
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    cards.getOrNull(2)?.let { card ->
                        HomeCard(
                            card = card,
                            cardIndex = 2,
                            onClick = { handleCardClick(card, viewModel, navController) { inlineContentKey = it } },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                    cards.getOrNull(3)?.let { card ->
                        HomeCard(
                            card = card,
                            cardIndex = 3,
                            onClick = { handleCardClick(card, viewModel, navController) { inlineContentKey = it } },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            }

            // 3. Sponsor bar
            SponsorBar(sponsors = sponsors)
        }
    }

    // Keynote info dialog
    if (inlineContentKey == "keynote") {
        KeynoteDialog(
            speakers = speakers,
            onDismiss = { inlineContentKey = null }
        )
    }
}

private fun handleCardClick(
    card: CardConfig,
    viewModel: HomeViewModel,
    navController: NavHostController,
    onInlineContent: (String) -> Unit
) {
    when (val action = viewModel.parseCardAction(card.action)) {
        is CardAction.NavigateToTab -> {
            navController.navigate(action.screen) {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        is CardAction.ShowInlineContent -> {
            onInlineContent(action.contentKey)
        }
        is CardAction.OpenUrl -> {
            // Open in-app WebView instead of external browser
            navController.navigate(Screen.Web(url = action.url)) {
                launchSingleTop = true
            }
        }
    }
}

/**
 * Home screen card — event-themed with alternating green tones.
 */
@Composable
private fun HomeCard(
    card: CardConfig,
    cardIndex: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Alternating: purple, teal, teal, purple — diagonal pattern
    val cardColor = if (cardIndex == 0 || cardIndex == 3) {
        CardPrimary.copy(alpha = 0.85f)
    } else {
        CardSecondary.copy(alpha = 0.85f)
    }

    Card(
        onClick = onClick,
        modifier = modifier.padding(4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = cardIcon(card.icon),
                contentDescription = card.label,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(56.dp)
            )
            Text(
                text = card.label,
                color = Color.White,
                fontSize = 74.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                lineHeight = 78.sp
            )
            if (card.description.isNotBlank()) {
                Text(
                    text = card.description,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 42.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 46.sp
                )
            }
        }
    }
}

private fun cardIcon(iconName: String): ImageVector = when (iconName) {
    "chat" -> Icons.AutoMirrored.Filled.Chat
    "map" -> Icons.Filled.Map
    "star" -> Icons.Filled.Star
    "schedule" -> Icons.Filled.Schedule
    "location" -> Icons.Filled.LocationOn
    "info" -> Icons.Filled.Info
    "settings" -> Icons.Filled.Settings
    "web" -> Icons.Filled.Language
    "camera" -> Icons.Filled.PhotoCamera
    "follow" -> Icons.Filled.DirectionsWalk
    else -> Icons.Filled.Info
}

@Composable
private fun KeynoteDialog(
    speakers: List<SpeakerInfo>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Keynote Speakers",
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = EventDark
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (speakers.isEmpty()) {
                    Text(
                        text = "Speaker details coming soon.",
                        fontSize = 26.sp,
                        color = EventDark.copy(alpha = 0.6f)
                    )
                } else {
                    speakers.forEach { speaker ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Initial circle
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(CardSecondary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = speaker.name.take(1),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CardSecondary
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = speaker.name,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EventDark
                                )
                                Text(
                                    text = speaker.title,
                                    fontSize = 22.sp,
                                    color = EventAccent
                                )
                                if (speaker.bio.isNotBlank()) {
                                    Text(
                                        text = speaker.bio,
                                        fontSize = 20.sp,
                                        color = EventDark.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontSize = 26.sp, color = EventAccent)
            }
        }
    )
}
