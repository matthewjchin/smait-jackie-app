package com.gow.smaitrobot.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.gow.smaitrobot.data.model.CardConfig
import com.gow.smaitrobot.ui.common.SponsorBar
import com.gow.smaitrobot.ui.common.TopLogoBar

/**
 * Home screen — the primary landing screen on Jackie's kiosk display.
 *
 * Layout (top to bottom):
 * 1. [TopLogoBar] — SJSU + BioRob logos, 80dp height
 * 2. Event name (32sp bold) + tagline (20sp)
 * 3. Card grid — 3-column grid with larger cards (120dp min height), icon + label + description
 * 4. [SponsorBar] — sponsor logos at the bottom
 *
 * Cards are the primary navigation — no bottom nav bar.
 */
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

    var inlineContentKey by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 1. Top logo bar
        TopLogoBar()

        // 2. Event name + tagline
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = eventName,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = tagline,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
        }

        // 3. Card grid — bigger cards with descriptions
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(cards) { card ->
                HomeCard(
                    card = card,
                    onClick = {
                        when (val action = viewModel.parseCardAction(card.action)) {
                            is CardAction.NavigateToTab -> {
                                navController.navigate(action.screen) {
                                    popUpTo(
                                        navController.graph.startDestinationId
                                    ) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                            is CardAction.ShowInlineContent -> {
                                inlineContentKey = action.contentKey
                            }
                        }
                    }
                )
            }
        }

        // 4. Sponsor bar
        SponsorBar(sponsors = sponsors)
    }

    // Inline content dialog
    inlineContentKey?.let { key ->
        InlineContentDialog(
            contentKey = key,
            onDismiss = { inlineContentKey = null }
        )
    }
}

/**
 * Home screen card — larger with icon, label, and description.
 * Min height 120dp for visual weight as primary navigation.
 */
@Composable
private fun HomeCard(
    card: CardConfig,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = cardIcon(card.icon),
                contentDescription = card.label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(36.dp)
                    .padding(bottom = 8.dp)
            )
            Text(
                text = card.label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            if (card.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = card.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
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
    else -> Icons.Filled.Info
}

@Composable
private fun InlineContentDialog(
    contentKey: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = contentKey.replaceFirstChar { it.uppercase() },
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Detailed $contentKey information will be displayed here.",
                fontSize = 18.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontSize = 18.sp)
            }
        }
    )
}
