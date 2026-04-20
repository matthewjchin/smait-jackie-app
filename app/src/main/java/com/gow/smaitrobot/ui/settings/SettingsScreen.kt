package com.gow.smaitrobot.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.gow.smaitrobot.data.websocket.WebSocketRepository
import com.gow.smaitrobot.jackieApp
import com.gow.smaitrobot.ui.common.SubScreenTopBar
import com.gow.smaitrobot.ui.common.WieBackground

private const val PREFS_NAME = "smait_settings"
private const val KEY_VOLUME = "tts_volume"
private const val KEY_SERVER_IP = "server_ip"
private const val KEY_SERVER_PORT = "server_port"
private const val KEY_CHASSIS_IP = "chassis_ip"
private const val KEY_CHASSIS_PORT = "chassis_port"

private val WieNavy = Color(0xFF1B0A6E)
private val WieTeal = Color(0xFF00A99D)

/**
 * Settings screen with server connection and TTS volume control.
 * WiE gradient background with semi-transparent cards.
 */
@Composable
fun SettingsScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val wsRepo = context.jackieApp.webSocketRepository

    WieBackground(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            SubScreenTopBar(
                title = "Settings",
                onBack = { navController.popBackStack() }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 24.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.85f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        ServerConnectionSection(wsRepo = wsRepo, context = context)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.85f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        ChassisConnectionSection(context = context)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.85f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        VolumeSection(context = context)
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerConnectionSection(wsRepo: WebSocketRepository, context: Context) {
    val isConnected by wsRepo.isConnected.collectAsStateWithLifecycle()
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var ipAddress by remember { mutableStateOf(prefs.getString(KEY_SERVER_IP, "10.31.51.164") ?: "10.31.51.164") }
    var port by remember { mutableStateOf(prefs.getString(KEY_SERVER_PORT, "8765") ?: "8765") }

    Text(
        text = "Server Connection",
        color = WieNavy,
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(16.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = if (isConnected) "Connected" else "Disconnected",
            color = if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336),
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = ipAddress,
            onValueChange = { ipAddress = it },
            label = { Text("Server IP", fontSize = 20.sp) },
            modifier = Modifier.weight(2f),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp)
        )
        OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text("Port", fontSize = 20.sp) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp)
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        if (isConnected) {
            OutlinedButton(
                onClick = { wsRepo.disconnect() },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFF44336)
                )
            ) {
                Text("Disconnect", fontSize = 22.sp)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Button(
            onClick = {
                prefs.edit()
                    .putString(KEY_SERVER_IP, ipAddress)
                    .putString(KEY_SERVER_PORT, port)
                    .apply()
                val url = "ws://$ipAddress:$port"
                wsRepo.disconnect()
                wsRepo.connect(url)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = WieTeal
            )
        ) {
            Text(
                if (isConnected) "Reconnect" else "Connect",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ChassisConnectionSection(context: Context) {
    val chassisProxy = context.jackieApp.chassisProxy
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var chassisIp   by remember { mutableStateOf(prefs.getString(KEY_CHASSIS_IP,   "192.168.20.22") ?: "192.168.20.22") }
    var chassisPort by remember { mutableStateOf(prefs.getString(KEY_CHASSIS_PORT,  "9090")          ?: "9090") }
    var isConnected by remember { mutableStateOf(chassisProxy.connected) }

    Text(
        text = "Chassis (Rosbridge)",
        color = WieNavy,
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(16.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = if (isConnected) "Connected" else "Disconnected",
            color = if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336),
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = chassisIp,
            onValueChange = { chassisIp = it },
            label = { Text("Chassis IP", fontSize = 20.sp) },
            modifier = Modifier.weight(2f),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp)
        )
        OutlinedTextField(
            value = chassisPort,
            onValueChange = { chassisPort = it },
            label = { Text("Port", fontSize = 20.sp) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp)
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Button(
            onClick = {
                prefs.edit()
                    .putString(KEY_CHASSIS_IP, chassisIp)
                    .putString(KEY_CHASSIS_PORT, chassisPort)
                    .apply()
                chassisProxy.reconnect("ws://$chassisIp:$chassisPort")
                isConnected = chassisProxy.connected
            },
            colors = ButtonDefaults.buttonColors(containerColor = WieTeal)
        ) {
            Text(
                text = if (isConnected) "Reconnect" else "Connect",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun VolumeSection(context: Context) {
    val ttsPlayer = context.jackieApp.ttsAudioPlayer
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var volume by remember {
        mutableFloatStateOf(prefs.getFloat(KEY_VOLUME, 0.5f))
    }

    Text(
        text = "Speaker Volume: ${(volume * 100).toInt()}%",
        color = WieNavy,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(12.dp))

    Slider(
        value = volume,
        onValueChange = { newValue ->
            volume = newValue
            ttsPlayer.setVolume(newValue)
        },
        onValueChangeFinished = {
            prefs.edit().putFloat(KEY_VOLUME, volume).apply()
        },
        valueRange = 0f..1f,
        modifier = Modifier.fillMaxWidth(),
        colors = SliderDefaults.colors(
            thumbColor = WieTeal,
            activeTrackColor = WieTeal
        )
    )

    Text(
        text = "MUTE \u2190 \u2192 MAX",
        color = WieNavy.copy(alpha = 0.5f),
        fontSize = 20.sp
    )
}
