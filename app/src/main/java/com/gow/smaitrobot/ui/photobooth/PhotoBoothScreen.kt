package com.gow.smaitrobot.ui.photobooth

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
// R import removed — style cards use gradient designs, not drawable thumbnails
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
// painterResource not needed — style cards use gradient + emoji design
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.gow.smaitrobot.data.websocket.WebSocketRepository
import com.gow.smaitrobot.ui.conversation.SelfieCapture
import com.gow.smaitrobot.data.websocket.WebSocketEvent
import kotlinx.coroutines.flow.filterIsInstance
import org.json.JSONObject
import java.io.ByteArrayOutputStream

private const val TAG = "PhotoBooth"

// Theme colors
private val DarkBg = Color(0xFF121212)
private val CardBg = Color(0xFF1E1E2E)
private val AccentPurple = Color(0xFF8B5CF6)
private val AccentCyan = Color(0xFF06B6D4)
private val TextPrimary = Color(0xFFE2E8F0)
private val TextSecondary = Color(0xFF94A3B8)
private val SelectedBorder = Color(0xFF8B5CF6)

/**
 * Style metadata for the picker grid.
 * Keys must match STYLE_REGISTRY on the server.
 * Each style has a gradient color pair and icon for visual identity.
 */
data class StyleOption(
    val key: String,
    val name: String,
    val gradientStart: Color,
    val gradientEnd: Color
)

private val STYLES = listOf(
    StyleOption("ghibli", "Ghibli",
        Color(0xFF4ADE80), Color(0xFF059669)),         // green meadow
    StyleOption("cyberpunk", "Cyberpunk",
        Color(0xFFE879F9), Color(0xFF6D28D9)),         // neon magenta-purple
    StyleOption("pop_art", "Pop Art",
        Color(0xFFFACC15), Color(0xFFEF4444)),         // yellow-red bold
    StyleOption("oil_painting", "Oil Painting",
        Color(0xFFD4A574), Color(0xFF78350F)),         // warm umber
    StyleOption("pixel_art", "Pixel Art",
        Color(0xFF60A5FA), Color(0xFF1D4ED8)),         // retro blue
    StyleOption("claymation", "Claymation",
        Color(0xFFFDA4AF), Color(0xFFF59E0B)),         // clay pink-amber
    StyleOption("pixar", "Pixar 3D",
        Color(0xFF67E8F9), Color(0xFF8B5CF6)),         // cyan-purple
    StyleOption("action_figure", "Action Figure",
        Color(0xFFA78BFA), Color(0xFFEC4899)),         // purple-pink toy
)

/**
 * Photo booth states.
 */
private sealed class BoothState {
    /** Style selection — two tabs: Themes and Custom */
    object Picking : BoothState()
    /** Camera capture with countdown */
    data class Capturing(val style: String, val mode: String, val customPrompt: String = "") : BoothState()
    /** Waiting for server to process */
    data class Processing(val style: String) : BoothState()
    /** Result ready with styled image + QR */
    data class Result(
        val styledBitmap: Bitmap,
        val qrBitmap: Bitmap? = null,
        val downloadUrl: String = ""
    ) : BoothState()
    /** Error from server */
    data class Error(val message: String) : BoothState()
}

/**
 * Full photo booth screen with style picker, camera, processing, and result display.
 *
 * Two modes:
 * - **Themes**: 10 preset styles in a grid
 * - **Custom**: User types a description of what they want
 */
@Composable
fun PhotoBoothScreen(navController: NavHostController, wsRepo: WebSocketRepository) {
    var state: BoothState by remember { mutableStateOf(BoothState.Picking) }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Themes, 1 = Custom
    var selectedStyle by remember { mutableStateOf("ghibli") }
    var customPrompt by remember { mutableStateOf("") }

    // Listen for server responses
    LaunchedEffect(Unit) {
        wsRepo.events.filterIsInstance<WebSocketEvent.JsonMessage>().collect { event ->
            try {
                val json = JSONObject(event.payload)
                when (json.optString("type")) {
                    "styled_result" -> {
                        val b64 = json.getString("styled_b64")
                        val bytes = Base64.decode(b64, Base64.DEFAULT)
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) {
                            state = BoothState.Result(styledBitmap = bmp)
                        }
                    }
                    "qr_code" -> {
                        val currentState = state
                        if (currentState is BoothState.Result) {
                            val qrB64 = json.getString("qr_b64")
                            val qrBytes = Base64.decode(qrB64, Base64.DEFAULT)
                            val qrBmp = BitmapFactory.decodeByteArray(qrBytes, 0, qrBytes.size)
                            state = currentState.copy(
                                qrBitmap = qrBmp,
                                downloadUrl = json.optString("download_url", "")
                            )
                        }
                    }
                    "photo_booth_error" -> {
                        val error = json.optString("error", "Unknown error")
                        state = BoothState.Error(error)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse server message", e)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                fadeIn(tween(300)) togetherWith fadeOut(tween(200))
            },
            label = "booth_state"
        ) { currentState ->
            when (currentState) {
                is BoothState.Picking -> {
                    PickerScreen(
                        selectedTab = selectedTab,
                        onTabChange = { selectedTab = it },
                        selectedStyle = selectedStyle,
                        onStyleSelect = { selectedStyle = it },
                        customPrompt = customPrompt,
                        onCustomPromptChange = { customPrompt = it },
                        onNext = {
                            val style = if (selectedTab == 0) selectedStyle else "custom"
                            val prompt = if (selectedTab == 1) customPrompt else ""
                            // Send style selection to server
                            val json = JSONObject().apply {
                                put("type", "photo_booth_style")
                                put("style", style)
                                put("mode", "portrait")
                                if (prompt.isNotBlank()) {
                                    put("custom_prompt", prompt)
                                }
                            }
                            wsRepo.send(json.toString())
                            state = BoothState.Capturing(style, "portrait", prompt)
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                is BoothState.Capturing -> {
                    SelfieCapture(
                        onDismiss = { state = BoothState.Picking },
                        onCapture = { bitmap ->
                            sendPhotoToServer(bitmap, wsRepo)
                            state = BoothState.Processing(currentState.style)
                        }
                    )
                }
                is BoothState.Processing -> {
                    ProcessingScreen(styleName = currentState.style)
                }
                is BoothState.Result -> {
                    ResultScreen(
                        styledBitmap = currentState.styledBitmap,
                        qrBitmap = currentState.qrBitmap,
                        onRetake = { state = BoothState.Picking },
                        onBack = { navController.popBackStack() }
                    )
                }
                is BoothState.Error -> {
                    ErrorScreen(
                        message = currentState.message,
                        onRetry = { state = BoothState.Picking }
                    )
                }
            }
        }
    }
}

// ── Picker Screen ────────────────────────────────────────────────────────────

@Composable
private fun PickerScreen(
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    selectedStyle: String,
    onStyleSelect: (String) -> Unit,
    customPrompt: String,
    onCustomPromptChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = "Photo Booth",
                color = TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Mode tabs: Themes | Custom
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CardBg)
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TabButton(
                label = "Themes",
                icon = Icons.Filled.Palette,
                selected = selectedTab == 0,
                onClick = { onTabChange(0) },
                modifier = Modifier.weight(1f)
            )
            TabButton(
                label = "Custom",
                icon = Icons.Filled.Edit,
                selected = selectedTab == 1,
                onClick = { onTabChange(1) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Content area
        Box(modifier = Modifier.weight(1f)) {
            if (selectedTab == 0) {
                ThemesGrid(
                    selectedStyle = selectedStyle,
                    onStyleSelect = onStyleSelect
                )
            } else {
                CustomPromptInput(
                    prompt = customPrompt,
                    onPromptChange = onCustomPromptChange
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Take Photo button
        val canProceed = selectedTab == 0 || customPrompt.isNotBlank()
        Button(
            onClick = onNext,
            enabled = canProceed,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentPurple,
                disabledContainerColor = AccentPurple.copy(alpha = 0.3f)
            )
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Take Photo", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TabButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) AccentPurple else Color.Transparent
    val textColor = if (selected) Color.White else TextSecondary

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, color = textColor, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Themes Grid ──────────────────────────────────────────────────────────────

@Composable
private fun ThemesGrid(
    selectedStyle: String,
    onStyleSelect: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(STYLES) { style ->
            StyleCard(
                style = style,
                selected = style.key == selectedStyle,
                onClick = { onStyleSelect(style.key) }
            )
        }
    }
}

@Composable
private fun StyleCard(
    style: StyleOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 3.dp else 0.dp,
        label = "border"
    )

    Card(
        onClick = onClick,
        modifier = Modifier
            .aspectRatio(1.6f)
            .then(
                if (selected) Modifier.border(borderWidth, SelectedBorder, RoundedCornerShape(16.dp))
                else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) CardBg.copy(alpha = 0.9f) else CardBg
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (selected) 8.dp else 2.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            style.gradientStart.copy(alpha = if (selected) 0.7f else 0.4f),
                            style.gradientEnd.copy(alpha = if (selected) 0.8f else 0.5f)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = style.name,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

// ── Custom Prompt ────────────────────────────────────────────────────────────

@Composable
private fun CustomPromptInput(
    prompt: String,
    onPromptChange: (String) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Describe your look",
            color = TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "e.g. \"a medieval wizard in an enchanted forest\" or \"underwater with tropical fish\"",
            color = TextSecondary,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(120.dp),
            placeholder = { Text("Type your idea here...", color = TextSecondary) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = AccentPurple,
                focusedBorderColor = AccentPurple,
                unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                focusedContainerColor = CardBg,
                unfocusedContainerColor = CardBg
            ),
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
            maxLines = 4
        )
    }
}

// ── Processing Screen ────────────────────────────────────────────────────────

@Composable
private fun ProcessingScreen(styleName: String) {
    val displayName = STYLES.find { it.key == styleName }?.name ?: "Custom"

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(80.dp),
            color = AccentPurple,
            strokeWidth = 6.dp
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Creating your $displayName photo...",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "This takes about 10 seconds",
            color = TextSecondary,
            fontSize = 16.sp
        )
    }
}

// ── Result Screen ────────────────────────────────────────────────────────────

@Composable
private fun ResultScreen(
    styledBitmap: Bitmap,
    qrBitmap: Bitmap?,
    onRetake: () -> Unit,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Styled photo (left, larger)
        Box(
            modifier = Modifier
                .weight(2f)
                .clip(RoundedCornerShape(20.dp))
                .border(2.dp, AccentPurple.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
        ) {
            Image(
                bitmap = styledBitmap.asImageBitmap(),
                contentDescription = "Styled photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        // Right panel: QR + buttons
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Scan to Save",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // QR code
            if (qrBitmap != null) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(8.dp)
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR code",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            } else {
                Box(
                    modifier = Modifier.size(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentPurple)
                    Text("Generating QR...", color = TextSecondary, fontSize = 14.sp,
                        modifier = Modifier.padding(top = 60.dp))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Try another
            Button(
                onClick = onRetake,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
            ) {
                Text("Try Another Style", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Done", color = TextPrimary, fontSize = 16.sp)
            }
        }
    }
}

// ── Error Screen ─────────────────────────────────────────────────────────────

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Something went wrong", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Text(message, color = TextSecondary, fontSize = 16.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 48.dp))
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
        ) {
            Text("Try Again", fontSize = 18.sp)
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Sends photo as HIGH_RES_PHOTO (0x08) binary frame to server.
 */
private fun sendPhotoToServer(bitmap: Bitmap, wsRepo: WebSocketRepository) {
    try {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        val jpegBytes = out.toByteArray()

        // Frame: 0x08 (HIGH_RES_PHOTO) + JPEG bytes
        val frame = ByteArray(1 + jpegBytes.size)
        frame[0] = 0x08
        System.arraycopy(jpegBytes, 0, frame, 1, jpegBytes.size)
        wsRepo.send(frame)
        Log.i(TAG, "Photo sent to server (${jpegBytes.size} bytes)")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to send photo", e)
    }
}
