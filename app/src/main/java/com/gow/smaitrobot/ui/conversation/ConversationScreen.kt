package com.gow.smaitrobot.ui.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.gow.smaitrobot.data.model.UiEvent
import com.gow.smaitrobot.ui.common.SubScreenTopBar
import com.gow.smaitrobot.ui.common.SurveyScreen
import com.gow.smaitrobot.ui.common.WieBackground

/**
 * Primary interaction screen for conversing with Jackie.
 *
 * Layout:
 * - SubScreenTopBar with back arrow
 * - Left: RobotAvatar (40%)
 * - Right: Chat transcript (60%)
 *
 * When the session ends (robot state returns to IDLE after conversing),
 * a full-screen [SurveyScreen] overlay replaces the conversation view.
 */
@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    navController: NavHostController
) {
    val messages by viewModel.transcript.collectAsState()
    val robotState by viewModel.robotState.collectAsState()
    val showSurvey by viewModel.showSurvey.collectAsState()

    val listState = rememberLazyListState()

    // Fresh session every time this screen appears; clean up on leave
    LaunchedEffect(Unit) {
        viewModel.onScreenEntered()
    }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.onScreenExited()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvent.NavigateTo -> {
                    navController.navigate(event.screen) {
                        popUpTo(event.screen) { inclusive = true }
                    }
                }
            }
        }
    }

    // When survey is visible, show full-screen survey overlay instead of conversation
    if (showSurvey) {
        SurveyScreen(
            onSubmit = { survey -> viewModel.submitSurvey(survey) },
            onDismiss = { survey -> viewModel.dismissSurvey(survey) }
        )
        return
    }

    WieBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            SubScreenTopBar(
                title = "Chat with Jackie",
                onBack = {
                    viewModel.onBackPressed()
                }
            )

            Box(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left: Robot avatar (40%)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.4f),
                        contentAlignment = Alignment.Center
                    ) {
                        RobotAvatar(
                            robotState = robotState,
                            modifier = Modifier.size(240.dp)
                        )
                    }

                    // Right: Transcript + camera button (60%)
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.6f)
                            .padding(end = 12.dp)
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            items(messages, key = { it.id }) { message ->
                                ChatBubble(message = message)
                            }

                            if (messages.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Say something to start the conversation",
                                            fontSize = 32.sp,
                                            color = Color(0xFF1B2838).copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }

                    }
                }

            }
        }
    }
}
