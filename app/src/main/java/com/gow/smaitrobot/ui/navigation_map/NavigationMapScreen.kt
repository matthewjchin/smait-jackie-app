package com.gow.smaitrobot.ui.navigation_map

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.gow.smaitrobot.ui.common.SubScreenTopBar
import com.gow.smaitrobot.ui.common.WieBackground

private val WieNavy = Color(0xFF1B2838)
private val WieTeal = Color(0xFF8BC53F)

/**
 * Navigation Map screen with WiE gradient background.
 */
@Composable
fun NavigationMapScreen(
    viewModel: NavigationMapViewModel,
    navController: NavHostController
) {
    val mapBitmap: Bitmap? by viewModel.mapBitmap.collectAsState()
    val navStatus by viewModel.navStatus.collectAsState()
    val isNavigating by viewModel.isNavigating.collectAsState()

    WieBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            SubScreenTopBar(
                title = "Navigation Map",
                onBack = { navController.popBackStack() }
            )

            // Map display
            Box(
                modifier = Modifier
                    .weight(0.8f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = mapBitmap
                if (bitmap != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.9f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Navigation map",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            color = WieTeal
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Waiting for map data...",
                            fontSize = 28.sp,
                            color = WieNavy.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Navigation status bar
            Card(
                modifier = Modifier
                    .weight(0.2f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.85f)
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        navStatus == null -> {
                            Text(
                                text = "Ready",
                                fontSize = 28.sp,
                                color = WieNavy.copy(alpha = 0.5f)
                            )
                        }
                        isNavigating -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = "Navigating to ${navStatus!!.destination}...",
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WieNavy
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                LinearProgressIndicator(
                                    progress = { navStatus!!.progress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = WieTeal
                                )
                            }
                        }
                        navStatus!!.status == "arrived" -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Arrived",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = "Arrived at ${navStatus!!.destination}",
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WieNavy
                                )
                            }
                        }
                        navStatus!!.status == "failed" -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = "Navigation failed",
                                    tint = Color(0xFFF44336),
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = "Navigation failed",
                                        fontSize = 30.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF44336)
                                    )
                                    Text(
                                        text = "Please ask a staff member for assistance.",
                                        fontSize = 24.sp,
                                        color = WieNavy.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                        else -> {
                            Text(
                                text = navStatus!!.destination.ifEmpty { "Ready" },
                                fontSize = 28.sp,
                                color = WieNavy.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}
