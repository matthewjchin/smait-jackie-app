package com.gow.smaitrobot.ui.photobooth

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightspark.composeqr.QrCodeView
import androidx.compose.foundation.Image

/**
 * Result screen showing the styled photo with:
 * - Crossfade animation from raw capture to styled result (600ms)
 * - QR code overlay in bottom-right corner when [state.downloadUrl] is available
 * - Bottom bar with Retake and Exit buttons
 * - Graceful degradation: if [state.styledBitmap] is null, raw photo is shown with error message
 *
 * @param state     Current [PhotoBoothUiState.Result] — provides raw/styled bitmaps and URL
 * @param onRetake  Called when user taps "Retake" — resets to StylePicker without server roundtrip
 * @param onExit    Called when user taps "Exit" — sends photo_booth_exit and navigates back
 */
@Composable
fun ResultScreen(
    state: PhotoBoothUiState.Result,
    onRetake: () -> Unit,
    onExit: () -> Unit
) {
    // Display bitmap: styled if available, otherwise raw (graceful degradation)
    val displayBitmap = state.styledBitmap ?: state.rawBitmap
    val isError = state.styledBitmap == null

    Box(modifier = Modifier.fillMaxSize()) {

        // Crossfade animates from raw to styled when styledBitmap arrives
        Crossfade(
            targetState = displayBitmap,
            animationSpec = tween(600),
            modifier = Modifier.fillMaxSize()
        ) { bitmap ->
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = if (isError) "Original photo (style transfer failed)" else "Styled photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // No bitmap at all — show dark placeholder
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1A1A2E)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading...",
                        color = Color.White,
                        fontSize = 20.sp
                    )
                }
            }
        }

        // Error banner: style transfer failed
        if (isError && state.rawBitmap != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Style transfer failed — showing original photo",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }

        // QR code overlay: bottom-right corner, 240dp square, white rounded box
        if (state.downloadUrl != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 88.dp, end = 16.dp) // above the bottom bar
                    .size(240.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                QrCodeView(
                    data = state.downloadUrl,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Bottom action bar: Retake | Exit
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xCC000000))
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onRetake,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Text(
                    text = "Retake",
                    fontSize = 18.sp,
                    color = Color.White
                )
            }

            Button(
                onClick = onExit,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "Exit",
                    fontSize = 18.sp
                )
            }
        }
    }
}
