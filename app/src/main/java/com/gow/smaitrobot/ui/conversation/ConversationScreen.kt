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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.gow.smaitrobot.data.model.UiEvent
import com.gow.smaitrobot.ui.common.FeedbackDialog
import com.gow.smaitrobot.ui.common.SubScreenTopBar

/**
 * Primary interaction screen for conversing with Jackie.
 *
 * Layout:
 * - SubScreenTopBar with back arrow
 * - Left: RobotAvatar (40%)
 * - Right: Chat transcript + camera button (60%)
 */
@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    navController: NavHostController
) {
    val messages by viewModel.transcript.collectAsState()
    val robotState by viewModel.robotState.collectAsState()
    val showCamera by viewModel.showCamera.collectAsState()
    val showFeedback by viewModel.showFeedback.collectAsState()

    val listState = rememberLazyListState()

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

    Column(modifier = Modifier.fillMaxSize()) {
        SubScreenTopBar(
            title = "Chat with Jackie",
            onBack = { navController.popBackStack() }
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
                        modifier = Modifier.size(200.dp)
                    )
                }

                // Right: Transcript + camera button (60%)
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.6f)
                        .padding(end = 8.dp)
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
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Say something to start the conversation",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.toggleCamera() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = "Take selfie",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            if (showCamera) {
                SelfieCapture(
                    onDismiss = { viewModel.toggleCamera() },
                    onCapture = { _ -> viewModel.toggleCamera() }
                )
            }
        }
    }

    if (showFeedback) {
        FeedbackDialog(
            onSubmit = { feedback -> viewModel.sendFeedback(feedback) },
            onDismiss = { viewModel.dismissFeedback() }
        )
    }
}
