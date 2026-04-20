package com.gow.smaitrobot.ui.follow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.gow.smaitrobot.follow.FollowController
import com.gow.smaitrobot.ui.common.SubScreenTopBar

/**
 * Follow Mode screen — lets user start/stop person-following behavior.
 *
 * Uses on-device MediaPipe face detection + DeepSORT tracking.
 * Sends cmd_vel to chassis through ChassisProxy.
 */
@Composable
fun FollowScreen(
    followController: FollowController,
    navController: NavHostController
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isFollowing by remember { mutableStateOf(false) }
    var stateText by remember { mutableStateOf("Ready") }
    var distanceText by remember { mutableStateOf("--") }

    // Wire state change callback
    followController.onStateChanged = { state, distance ->
        stateText = when (state) {
            FollowController.FsmState.FOLLOWING -> "Following"
            FollowController.FsmState.SCAN_ROTATE -> "Scanning..."
            FollowController.FsmState.OBSTACLE_TURN -> "Avoiding obstacle"
            FollowController.FsmState.COLLISION_STOP -> "Collision! Stopped"
            FollowController.FsmState.COLLISION_TURN -> "Turning away"
            FollowController.FsmState.CLEAR_CHECK -> "Checking path"
        }
        distanceText = if (distance < 100.0) "%.2f m".format(distance) else "--"
    }

    // Cleanup on leave
    DisposableEffect(Unit) {
        onDispose {
            if (isFollowing) {
                followController.stop()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SubScreenTopBar(title = "Follow Mode", onBack = { navController.popBackStack() })

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Status
            Text(
                text = stateText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (isFollowing) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Distance: $distanceText",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Big start/stop button
            Button(
                onClick = {
                    if (isFollowing) {
                        followController.stop()
                        isFollowing = false
                        stateText = "Stopped"
                        distanceText = "--"
                    } else {
                        followController.start(context, lifecycleOwner)
                        isFollowing = true
                        stateText = "Starting..."
                    }
                },
                modifier = Modifier.size(160.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isFollowing) Color(0xFFE53935) else Color(0xFF43A047)
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (isFollowing) Icons.Default.Stop
                        else Icons.Default.DirectionsWalk,
                        contentDescription = if (isFollowing) "Stop" else "Start",
                        modifier = Modifier.size(48.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isFollowing) "STOP" else "START",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Robot will follow the nearest face within 0.5m",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}