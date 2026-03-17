package com.gow.smaitrobot.ui.eventinfo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.gow.smaitrobot.data.model.ScheduleItem
import com.gow.smaitrobot.data.model.SpeakerInfo
import com.gow.smaitrobot.ui.common.SponsorBar
import com.gow.smaitrobot.ui.common.SubScreenTopBar

/**
 * Event Info screen — schedule, speakers, and venue info.
 */
@Composable
fun EventInfoScreen(
    viewModel: EventInfoViewModel,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val schedule by viewModel.schedule.collectAsStateWithLifecycle()
    val speakers by viewModel.speakers.collectAsStateWithLifecycle()
    val eventName by viewModel.eventName.collectAsStateWithLifecycle()
    val tagline by viewModel.tagline.collectAsStateWithLifecycle()
    val sponsors by viewModel.sponsors.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        SubScreenTopBar(
            title = "Event Info",
            onBack = { navController.popBackStack() }
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Event header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = eventName,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tagline,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Schedule section
            item {
                SectionHeading(
                    text = "Schedule",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (schedule.isEmpty()) {
                item {
                    EmptyState(
                        message = "Schedule coming soon",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                items(schedule, key = { "${it.time}_${it.title}" }) { item ->
                    ScheduleCard(
                        item = item,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            // Speakers section
            item {
                SectionHeading(
                    text = "Speakers",
                    modifier = Modifier.padding(
                        start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp
                    )
                )
            }

            if (speakers.isEmpty()) {
                item {
                    EmptyState(
                        message = "Speakers coming soon",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(speakers, key = { it.name }) { speaker ->
                            SpeakerCard(speaker = speaker)
                        }
                    }
                }
            }

            // Venue section
            item {
                SectionHeading(
                    text = "Venue",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                VenueInfoPlaceholder(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        SponsorBar(sponsors = sponsors)
    }
}

@Composable
private fun SectionHeading(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun EmptyState(
    message: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = message,
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 18.sp,
        fontWeight = FontWeight.Normal
    )
}

@Composable
private fun ScheduleCard(
    item: ScheduleItem,
    modifier: Modifier = Modifier
) {
    ElevatedCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = item.time,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(80.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )

                if (item.speaker.isNotBlank()) {
                    Text(
                        text = item.speaker,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.location.isNotBlank()) {
                        Text(
                            text = item.location,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                    if (item.track.isNotBlank()) {
                        AssistChip(
                            onClick = {},
                            label = { Text(text = item.track, fontSize = 12.sp) },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeakerCard(
    speaker: SpeakerInfo,
    modifier: Modifier = Modifier
) {
    ElevatedCard(modifier = modifier.width(160.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = speaker.name.take(1).uppercase(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = speaker.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (speaker.title.isNotBlank()) {
                Text(
                    text = speaker.title,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            if (speaker.bio.isNotBlank()) {
                Text(
                    text = speaker.bio,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun VenueInfoPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(120.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Venue Map",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Map image will appear here",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
    }
}
