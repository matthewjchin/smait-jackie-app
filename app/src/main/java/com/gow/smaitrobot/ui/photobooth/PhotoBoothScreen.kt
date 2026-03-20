package com.gow.smaitrobot.ui.photobooth

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.gow.smaitrobot.data.websocket.WebSocketRepository
import com.gow.smaitrobot.ui.common.SubScreenTopBar
import com.gow.smaitrobot.ui.conversation.SelfieCapture
import com.gow.smaitrobot.ui.conversation.sendSelfieToServer

/**
 * Standalone photo booth screen accessible from the home screen.
 * Wraps [SelfieCapture] with a top bar and back navigation.
 * Sends captured selfies to the server for session logging.
 */
@Composable
fun PhotoBoothScreen(navController: NavHostController, wsRepo: WebSocketRepository) {
    Column(modifier = Modifier.fillMaxSize()) {
        SubScreenTopBar(
            title = "Photo Booth",
            onBack = { navController.popBackStack() }
        )
        Box(modifier = Modifier.weight(1f)) {
            SelfieCapture(
                onDismiss = { navController.popBackStack() },
                onCapture = { bitmap ->
                    sendSelfieToServer(bitmap, wsRepo)
                    navController.popBackStack()
                }
            )
        }
    }
}
