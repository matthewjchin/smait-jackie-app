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

// WiE slide colors
private val WieNavy = Color(0xFF1B0A6E)
private val WieTeal = Color(0xFF00A99D)
private val CardPurple = Color(0xFF2D1B69)
private val CardTeal = Color(0xFF007C87)

/**
 * Home screen — the primary landing screen on Jackie's kiosk display.
 *
 * Layout:
 * 1. Top: Logo bar (SJSU | WiE | BioRob) — long press opens hidden Settings
 * 2. Middle: WiE conference graphic (left) + 4 cards in 2x2 grid (right)
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
            // 1. Top logo bar — long press to open hidden Settings
            TopLogoBar(
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = {
                        navController.navigate(Screen.Settings) {
                            launchSingleTop = true
                        }
                    }
                )
            )

            // 2. Main content: WiE graphic (left) + cards (right)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: WiE conference graphic
                Image(
                    painter = painterResource(id = R.drawable.wie_conference_graphic),
                    contentDescription = "WiE Conference — Engineering Beyond Imagination",
                    modifier = Modifier
                        .weight(0.38f)
                        .fillMaxHeight()
                        .padding(8.dp),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Right: 2x2 card grid — each row gets equal vertical space
                Column(
                    modifier = Modifier
                        .weight(0.62f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
                ) {
                    // Top row
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                    // Third row — Follow Me (full width)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        cards.getOrNull(4)?.let { card ->
                            HomeCard(
                                card = card,
                                cardIndex = 4,
                                onClick = { handleCardClick(card, viewModel, navController) { inlineContentKey = it } },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                }
            }

            // 4. Sponsor bar
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
 * Home screen card — WiE-themed with alternating purple/teal.
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
        CardPurple.copy(alpha = 0.85f)
    } else {
        CardTeal.copy(alpha = 0.85f)
    }

    Card(
        onClick = onClick,
        modifier = modifier.padding(2.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = cardIcon(card.icon),
                contentDescription = card.label,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(60.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = card.label,
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                lineHeight = 46.sp
            )
            if (card.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = card.description,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 26.sp
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
                color = WieNavy
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
                        color = WieNavy.copy(alpha = 0.6f)
                    )
                } else {
                    speakers.forEach { speaker ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Initial circle
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(CardTeal.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = speaker.name.take(1),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CardTeal
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = speaker.name,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WieNavy
                                )
                                Text(
                                    text = speaker.title,
                                    fontSize = 22.sp,
                                    color = WieTeal
                                )
                                if (speaker.bio.isNotBlank()) {
                                    Text(
                                        text = speaker.bio,
                                        fontSize = 20.sp,
                                        color = WieNavy.copy(alpha = 0.6f),
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
                Text("Close", fontSize = 26.sp, color = WieTeal)
            }
        }
    )
}
