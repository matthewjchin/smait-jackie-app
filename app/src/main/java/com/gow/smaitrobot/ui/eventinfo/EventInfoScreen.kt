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

import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.gow.smaitrobot.ui.common.WieBackground

private val ThemeDark = Color(0xFF1B2838)
private val ThemeAccent = Color(0xFF8BC53F)
private val ThemePrimary = Color(0xFF7B52A8)

/**
 * Event Info screen — schedule, speakers, and venue info.
 * Uses WiE gradient background and large accessible fonts.
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

    WieBackground(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            SubScreenTopBar(
                title = "Schedule & Speakers",
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
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = eventName,
                            color = Color.White,
                            fontSize = 52.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = tagline,
                            color = ThemeAccent,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Schedule section
                item {
                    SectionHeading(
                        text = "Schedule",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }

                if (schedule.isEmpty()) {
                    item {
                        EmptyState(
                            message = "Schedule coming soon",
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    items(schedule, key = { "${it.time}_${it.title}" }) { item ->
                        ScheduleCard(
                            item = item,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp)
                        )
                    }
                }

                // Speakers section
                item {
                    SectionHeading(
                        text = "Keynote Speakers",
                        modifier = Modifier.padding(
                            start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp
                        )
                    )
                }

                if (speakers.isEmpty()) {
                    item {
                        EmptyState(
                            message = "Speakers coming soon",
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    items(speakers, key = { it.name }) { speaker ->
                        SpeakerCard(
                            speaker = speaker,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp)
                        )
                    }
                }

                // Venue section
                item {
                    SectionHeading(
                        text = "Venue",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                    VenueInfoCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            SponsorBar(sponsors = sponsors)
        }
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
        color = Color.White.copy(alpha = 0.9f),
        fontSize = 48.sp,
        fontWeight = FontWeight.ExtraBold
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
        color = Color.White.copy(alpha = 0.5f),
        fontSize = 36.sp,
        fontWeight = FontWeight.Normal
    )
}

@Composable
private fun ScheduleCard(
    item: ScheduleItem,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.85f)
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = item.time,
                color = ThemeAccent,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(180.dp)
            )

            Spacer(modifier = Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = ThemeDark,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 44.sp
                )

                if (item.speaker.isNotBlank()) {
                    Text(
                        text = item.speaker,
                        color = ThemePrimary,
                        fontSize = 32.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.location.isNotBlank()) {
                        Text(
                            text = item.location,
                            color = ThemeDark.copy(alpha = 0.6f),
                            fontSize = 28.sp
                        )
                    }
                    if (item.track.isNotBlank()) {
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = item.track,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            modifier = Modifier.height(44.dp),
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = ThemeAccent.copy(alpha = 0.15f),
                                labelColor = ThemeAccent
                            )
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
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.9f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(ThemePrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = speaker.name.take(1).uppercase(),
                    color = ThemePrimary,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = speaker.name,
                    color = ThemeDark,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (speaker.title.isNotBlank()) {
                    Text(
                        text = speaker.title,
                        color = ThemeAccent,
                        fontSize = 28.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                if (speaker.bio.isNotBlank()) {
                    Text(
                        text = speaker.bio,
                        color = ThemeDark.copy(alpha = 0.6f),
                        fontSize = 24.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VenueInfoCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.85f)
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "San Jose State University",
                    color = ThemeDark,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Diaz Compean Student Union",
                    color = ThemeAccent,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Ballroom & Studio Room 1",
                    color = ThemePrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}
