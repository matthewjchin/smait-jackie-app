package com.gow.smaitrobot

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.YuvImage
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Surface
import android.graphics.SurfaceTexture
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "Jackie"
        private const val PERMISSION_REQUEST_CODE = 100
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val INITIAL_RECONNECT_DELAY_MS = 1000L
        private const val MAX_RECONNECT_DELAY_MS = 30000L
        private const val AUDIO_TYPE: Byte = 0x01
        private const val VIDEO_TYPE: Byte = 0x02
    }

    // ─── UI ───
    private lateinit var idleScreen: FrameLayout
    private lateinit var activeScreen: LinearLayout
    private lateinit var selfieOverlay: FrameLayout
    private lateinit var settingsOverlay: FrameLayout

    // Idle
    private lateinit var jackieTitle: TextView
    private lateinit var approachText: TextView
    private lateinit var pulseRing: View
    private lateinit var pulseRingOuter: View
    private lateinit var idleConnectionDot: View
    private lateinit var waveContainer: LinearLayout
    private lateinit var particleCanvas: View

    // Active
    private lateinit var cameraPreview: TextureView
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var statusDot: View
    private lateinit var robotStatusText: TextView
    private lateinit var sessionTimer: TextView
    private lateinit var userNameText: TextView
    private lateinit var selfieButton: TextView

    // Selfie
    private lateinit var countdownText: TextView
    private lateinit var selfiePreview: ImageView
    private lateinit var selfiePreviewCard: MaterialCardView
    private lateinit var selfieActions: LinearLayout
    private lateinit var retakeButton: TextView
    private lateinit var saveButton: TextView
    private lateinit var flashOverlay: View

    // Settings
    private lateinit var settingsIpInput: TextInputEditText
    private lateinit var settingsPortInput: TextInputEditText
    private lateinit var settingsSheet: LinearLayout

    // ─── State ───
    private enum class AppState { IDLE, ENGAGED }
    private var currentState = AppState.IDLE
    private var robotStatus = "idle"

    // ─── Chat ───
    private val chatMessages = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter

    // ─── WebSocket ───
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val isConnected = AtomicBoolean(false)
    private val isStreaming = AtomicBoolean(false)
    private var shouldReconnect = true
    private var reconnectDelay = INITIAL_RECONNECT_DELAY_MS
    private val mainHandler = Handler(Looper.getMainLooper())

    // ─── Camera ───
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private lateinit var cameraHandler: Handler
    private lateinit var cameraThread: HandlerThread
    private var lastJpegBytes: ByteArray? = null

    // ─── Audio ───
    private var audioRecord: AudioRecord? = null
    private var audioThread: Thread? = null

    // ─── TTS ───
    private var tts: TextToSpeech? = null

    // ─── Prefs ───
    private lateinit var prefs: SharedPreferences

    // ─── Selfie ───
    private var selfieBitmap: Bitmap? = null

    // ─── Session Timer ───
    private var sessionStartTime = 0L
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (currentState == AppState.ENGAGED && sessionStartTime > 0) {
                val elapsed = (System.currentTimeMillis() - sessionStartTime) / 1000
                val min = elapsed / 60
                val sec = elapsed % 60
                sessionTimer.text = String.format("%d:%02d", min, sec)
                mainHandler.postDelayed(this, 1000)
            }
        }
    }

    // ─── Animations ───
    private val animators = mutableListOf<ValueAnimator>()
    private val waveBars = mutableListOf<View>()

    // ─── Particles ───
    private data class Particle(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var alpha: Float, var radius: Float
    )
    private val particles = mutableListOf<Particle>()
    private var particleAnimator: ValueAnimator? = null

    // ─── Status Dot Pulse ───
    private var statusDotAnimator: AnimatorSet? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Immersive mode
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )

        prefs = getSharedPreferences("jackie_prefs", Context.MODE_PRIVATE)
        initUI()
        initTTS()
        initChat()
        checkPermissions()
        startCameraThread()
        setupWaveBars()
        setupParticles()
        startIdleAnimations()

        mainHandler.postDelayed({ autoConnect() }, 1500)
    }

    // ═══════════════════════════════════════════════════════════════
    // UI INIT
    // ═══════════════════════════════════════════════════════════════

    private fun initUI() {
        // Screens
        idleScreen = findViewById(R.id.idleScreen)
        activeScreen = findViewById(R.id.activeScreen)
        selfieOverlay = findViewById(R.id.selfieOverlay)
        settingsOverlay = findViewById(R.id.settingsOverlay)

        // Idle
        jackieTitle = findViewById(R.id.jackieTitle)
        approachText = findViewById(R.id.approachText)
        pulseRing = findViewById(R.id.pulseRing)
        pulseRingOuter = findViewById(R.id.pulseRingOuter)
        idleConnectionDot = findViewById(R.id.idleConnectionDot)
        waveContainer = findViewById(R.id.waveContainer)
        particleCanvas = findViewById(R.id.particleCanvas)

        // Active
        cameraPreview = findViewById(R.id.cameraPreview)
        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        statusDot = findViewById(R.id.statusDot)
        robotStatusText = findViewById(R.id.robotStatusText)
        sessionTimer = findViewById(R.id.sessionTimer)
        userNameText = findViewById(R.id.userNameText)
        selfieButton = findViewById(R.id.selfieButton)

        // Selfie
        countdownText = findViewById(R.id.countdownText)
        selfiePreview = findViewById(R.id.selfiePreview)
        selfiePreviewCard = findViewById(R.id.selfiePreviewCard)
        selfieActions = findViewById(R.id.selfieActions)
        retakeButton = findViewById(R.id.retakeButton)
        saveButton = findViewById(R.id.saveButton)
        flashOverlay = findViewById(R.id.flashOverlay)

        // Settings
        settingsIpInput = findViewById(R.id.settingsIpInput)
        settingsPortInput = findViewById(R.id.settingsPortInput)
        settingsSheet = findViewById(R.id.settingsSheet)

        // Load saved settings
        settingsIpInput.setText(prefs.getString("server_ip", "10.251.45.100"))
        settingsPortInput.setText(prefs.getString("server_port", "8765"))

        // Long-press idle screen for settings
        idleScreen.setOnLongClickListener {
            showSettings()
            true
        }

        // Also long-press active screen for settings
        activeScreen.setOnLongClickListener {
            showSettings()
            true
        }

        // Settings buttons
        findViewById<MaterialButton>(R.id.settingsCancelButton).setOnClickListener { hideSettings() }
        findViewById<MaterialButton>(R.id.settingsDisconnectButton).setOnClickListener {
            shouldReconnect = false
            stopStreaming()
            hideSettings()
        }
        findViewById<MaterialButton>(R.id.settingsSaveButton).setOnClickListener {
            saveSettings()
            hideSettings()
            reconnect()
        }

        // Click overlay background to dismiss settings
        settingsOverlay.setOnClickListener { hideSettings() }
        settingsSheet.setOnClickListener { /* consume click */ }

        // Selfie buttons
        selfieButton.setOnClickListener { startSelfieCountdown() }
        retakeButton.setOnClickListener { startSelfieCountdown() }
        saveButton.setOnClickListener { saveSelfie() }

        // Camera preview
        cameraPreview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                if (hasPermissions()) openCamera()
            }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }

    private fun initChat() {
        chatAdapter = ChatAdapter(chatMessages)
        chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
            itemAnimator = null // We handle animations ourselves
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                    outRect.bottom = 4
                }
            })
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // WAVE VISUALIZATION
    // ═══════════════════════════════════════════════════════════════

    private fun setupWaveBars() {
        val barCount = 40
        for (i in 0 until barCount) {
            val bar = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(6, 8).apply {
                    marginStart = 6
                    marginEnd = 6
                }
                setBackgroundResource(R.drawable.bg_wave_bar)
                alpha = 0.4f
            }
            waveBars.add(bar)
            waveContainer.addView(bar)
        }
    }

    private fun startWaveAnimation() {
        val waveAnimator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val phase = animator.animatedValue as Float
                waveBars.forEachIndexed { index, bar ->
                    val normalizedPos = index.toFloat() / waveBars.size
                    val height = (12 + 30 * sin((normalizedPos * 4 * Math.PI + phase).toDouble())).toInt()
                        .coerceIn(6, 50)
                    val params = bar.layoutParams
                    params.height = (height * resources.displayMetrics.density).toInt()
                    bar.layoutParams = params
                    bar.alpha = 0.3f + 0.4f * (height / 50f)
                }
            }
            start()
        }
        animators.add(waveAnimator)
    }

    // ═══════════════════════════════════════════════════════════════
    // PARTICLES
    // ═══════════════════════════════════════════════════════════════

    private fun setupParticles() {
        particleCanvas.post {
            val w = particleCanvas.width.toFloat()
            val h = particleCanvas.height.toFloat()
            if (w <= 0 || h <= 0) return@post

            for (i in 0 until 60) {
                particles.add(Particle(
                    x = Random.nextFloat() * w,
                    y = Random.nextFloat() * h,
                    vx = (Random.nextFloat() - 0.5f) * 0.5f,
                    vy = (Random.nextFloat() - 0.5f) * 0.3f,
                    alpha = Random.nextFloat() * 0.4f + 0.1f,
                    radius = Random.nextFloat() * 2f + 0.5f
                ))
            }

            val paint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
            }

            particleCanvas.setLayerType(View.LAYER_TYPE_SOFTWARE, null)

            particleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 50
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener {
                    val canvas = Canvas()
                    val bitmap = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
                    canvas.setBitmap(bitmap)

                    particles.forEach { p ->
                        p.x += p.vx
                        p.y += p.vy
                        if (p.x < 0) p.x = w
                        if (p.x > w) p.x = 0f
                        if (p.y < 0) p.y = h
                        if (p.y > h) p.y = 0f

                        paint.color = Color.argb((p.alpha * 120).toInt(), 0, 102, 255)
                        canvas.drawCircle(p.x, p.y, p.radius * resources.displayMetrics.density, paint)
                    }

                    particleCanvas.background = android.graphics.drawable.BitmapDrawable(resources, bitmap)
                }
                start()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ANIMATIONS
    // ═══════════════════════════════════════════════════════════════

    private fun startIdleAnimations() {
        // Inner pulse ring
        val innerSet = createPulseAnimation(pulseRing, 0.85f, 1.35f, 3000, 0.25f, 0.05f)
        innerSet.start()

        // Outer pulse ring (offset timing)
        val outerSet = createPulseAnimation(pulseRingOuter, 0.9f, 1.5f, 4000, 0.15f, 0.02f)
        outerSet.startDelay = 1000
        outerSet.start()

        // Approach text: smooth fade in/out
        ObjectAnimator.ofFloat(approachText, "alpha", 0.3f, 1.0f).apply {
            duration = 2500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = DecelerateInterpolator()
            start()
        }

        // Wave animation
        startWaveAnimation()
    }

    private fun createPulseAnimation(view: View, from: Float, to: Float, dur: Long, alphaFrom: Float, alphaTo: Float): AnimatorSet {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", from, to).apply {
            duration = dur; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            interpolator = DecelerateInterpolator()
        }
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", from, to).apply {
            duration = dur; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            interpolator = DecelerateInterpolator()
        }
        val alpha = ObjectAnimator.ofFloat(view, "alpha", alphaFrom, alphaTo).apply {
            duration = dur; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
        }
        return AnimatorSet().apply { playTogether(scaleX, scaleY, alpha) }
    }

    private fun startStatusDotPulse() {
        statusDotAnimator?.cancel()
        statusDotAnimator = AnimatorSet().apply {
            val scaleX = ObjectAnimator.ofFloat(statusDot, "scaleX", 1f, 1.4f).apply {
                duration = 800; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            }
            val scaleY = ObjectAnimator.ofFloat(statusDot, "scaleY", 1f, 1.4f).apply {
                duration = 800; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            }
            val alpha = ObjectAnimator.ofFloat(statusDot, "alpha", 1f, 0.5f).apply {
                duration = 800; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            }
            playTogether(scaleX, scaleY, alpha)
            start()
        }
    }

    private fun stopStatusDotPulse() {
        statusDotAnimator?.cancel()
        statusDot.scaleX = 1f
        statusDot.scaleY = 1f
        statusDot.alpha = 1f
    }

    // ═══════════════════════════════════════════════════════════════
    // STATE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════

    private fun switchToState(state: AppState) {
        if (currentState == state) return
        runOnUiThread {
            currentState = state
            when (state) {
                AppState.IDLE -> {
                    // Fade out active, fade in idle
                    activeScreen.animate()
                        .alpha(0f)
                        .scaleX(0.97f).scaleY(0.97f)
                        .setDuration(300)
                        .setInterpolator(DecelerateInterpolator())
                        .withEndAction {
                            activeScreen.visibility = View.GONE
                            activeScreen.scaleX = 1f
                            activeScreen.scaleY = 1f
                        }.start()

                    idleScreen.visibility = View.VISIBLE
                    idleScreen.alpha = 0f
                    idleScreen.animate()
                        .alpha(1f)
                        .setDuration(400)
                        .setInterpolator(DecelerateInterpolator())
                        .start()

                    selfieOverlay.visibility = View.GONE
                    stopSessionTimer()
                    stopStatusDotPulse()
                }
                AppState.ENGAGED -> {
                    // Fade out idle, fade in active
                    idleScreen.animate()
                        .alpha(0f)
                        .setDuration(250)
                        .withEndAction {
                            idleScreen.visibility = View.GONE
                        }.start()

                    activeScreen.visibility = View.VISIBLE
                    activeScreen.alpha = 0f
                    activeScreen.scaleX = 1.02f
                    activeScreen.scaleY = 1.02f
                    activeScreen.animate()
                        .alpha(1f)
                        .scaleX(1f).scaleY(1f)
                        .setDuration(350)
                        .setInterpolator(DecelerateInterpolator())
                        .start()

                    selfieOverlay.visibility = View.GONE
                    startSessionTimer()
                    startStatusDotPulse()
                }
            }
        }
    }

    private fun updateRobotStatus(status: String) {
        robotStatus = status
        runOnUiThread {
            val (text, color) = when (status) {
                "listening" -> "Listening…" to ContextCompat.getColor(this, R.color.status_listening)
                "thinking" -> "Thinking…" to ContextCompat.getColor(this, R.color.status_thinking)
                "speaking" -> "Speaking…" to ContextCompat.getColor(this, R.color.status_speaking)
                else -> "Connected" to ContextCompat.getColor(this, R.color.status_connected)
            }
            robotStatusText.text = text
            (statusDot.background as? GradientDrawable)?.setColor(color)

            // Pulse only when listening
            if (status == "listening") {
                startStatusDotPulse()
            } else {
                stopStatusDotPulse()
            }
        }
    }

    private fun updateConnectionDot(connected: Boolean) {
        runOnUiThread {
            val color = if (connected)
                ContextCompat.getColor(this, R.color.status_connected)
            else
                ContextCompat.getColor(this, R.color.status_disconnected)
            (idleConnectionDot.background as? GradientDrawable)?.setColor(color)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SESSION TIMER
    // ═══════════════════════════════════════════════════════════════

    private fun startSessionTimer() {
        sessionStartTime = System.currentTimeMillis()
        sessionTimer.text = "0:00"
        mainHandler.post(timerRunnable)
    }

    private fun stopSessionTimer() {
        sessionStartTime = 0
        mainHandler.removeCallbacks(timerRunnable)
    }

    // ═══════════════════════════════════════════════════════════════
    // SETTINGS (Bottom Sheet)
    // ═══════════════════════════════════════════════════════════════

    private fun showSettings() {
        settingsOverlay.visibility = View.VISIBLE
        settingsOverlay.alpha = 0f
        settingsOverlay.animate().alpha(1f).setDuration(200).start()

        settingsSheet.translationY = 500f
        settingsSheet.animate()
            .translationY(0f)
            .setDuration(350)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun hideSettings() {
        settingsSheet.animate()
            .translationY(500f)
            .setDuration(250)
            .setInterpolator(DecelerateInterpolator())
            .start()

        settingsOverlay.animate()
            .alpha(0f)
            .setDuration(250)
            .setStartDelay(100)
            .withEndAction {
                settingsOverlay.visibility = View.GONE
            }.start()
    }

    private fun saveSettings() {
        prefs.edit()
            .putString("server_ip", settingsIpInput.text.toString())
            .putString("server_port", settingsPortInput.text.toString())
            .apply()
    }

    // ═══════════════════════════════════════════════════════════════
    // CHAT
    // ═══════════════════════════════════════════════════════════════

    data class ChatMessage(val text: String, val isUser: Boolean, val id: Long = System.nanoTime())

    private fun addChatMessage(text: String, isUser: Boolean) {
        runOnUiThread {
            val oldList = chatMessages.toList()
            chatMessages.add(ChatMessage(text, isUser))
            val newList = chatMessages.toList()

            val diff = DiffUtil.calculateDiff(ChatDiffCallback(oldList, newList))
            diff.dispatchUpdatesTo(chatAdapter)
            chatRecyclerView.scrollToPosition(chatMessages.size - 1)
        }
    }

    private fun clearChat() {
        runOnUiThread {
            chatMessages.clear()
            chatAdapter.notifyDataSetChanged()
        }
    }

    class ChatDiffCallback(
        private val oldList: List<ChatMessage>,
        private val newList: List<ChatMessage>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size
        override fun areItemsTheSame(oldPos: Int, newPos: Int) = oldList[oldPos].id == newList[newPos].id
        override fun areContentsTheSame(oldPos: Int, newPos: Int) = oldList[oldPos] == newList[newPos]
    }

    class ChatAdapter(private val messages: List<ChatMessage>) :
        RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val container: LinearLayout = view.findViewById(R.id.bubbleContainer)
            val bubbleCard: LinearLayout = view.findViewById(R.id.bubbleCard)
            val bubbleText: TextView = view.findViewById(R.id.bubbleText)
            val bubbleLabel: TextView = view.findViewById(R.id.bubbleLabel)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat_bubble, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val msg = messages[position]
            holder.bubbleText.text = msg.text

            if (msg.isUser) {
                holder.container.gravity = Gravity.START
                holder.bubbleLabel.text = "You"
                holder.bubbleLabel.setTextColor(
                    ContextCompat.getColor(holder.itemView.context, R.color.speaker_user))
                holder.bubbleCard.setBackgroundResource(R.drawable.bg_user_bubble)
            } else {
                holder.container.gravity = Gravity.END
                holder.bubbleLabel.text = "Jackie"
                holder.bubbleLabel.setTextColor(
                    ContextCompat.getColor(holder.itemView.context, R.color.speaker_robot))
                holder.bubbleCard.setBackgroundResource(R.drawable.bg_robot_bubble)
            }

            // Slide-in animation
            val translateX = if (msg.isUser) -60f else 60f
            holder.container.translationX = translateX
            holder.container.alpha = 0f
            holder.container.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(200)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        override fun getItemCount() = messages.size
    }

    // ═══════════════════════════════════════════════════════════════
    // TTS
    // ═══════════════════════════════════════════════════════════════

    private fun initTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                Log.i(TAG, "TTS initialized")
            } else {
                Log.e(TAG, "TTS init failed")
            }
        }
    }

    private fun speakText(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_${System.currentTimeMillis()}")
    }

    // ═══════════════════════════════════════════════════════════════
    // PERMISSIONS
    // ═══════════════════════════════════════════════════════════════

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    private fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && hasPermissions()) {
            if (cameraPreview.isAvailable) openCamera()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CAMERA
    // ═══════════════════════════════════════════════════════════════

    private fun startCameraThread() {
        cameraThread = HandlerThread("CameraThread").also { it.start() }
        cameraHandler = Handler(cameraThread.looper)
    }

    private fun openCamera() {
        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            } ?: cameraManager.cameraIdList[0]

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return

            imageReader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2)
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                image?.let {
                    processVideoFrame(it)
                    it.close()
                }
            }, cameraHandler)

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCaptureSession()
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    Log.e(TAG, "Camera error: $error")
                }
            }, cameraHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun createCaptureSession() {
        try {
            val surfaces = mutableListOf<Surface>()

            // Add preview surface only if TextureView is available (it may be GONE on idle screen)
            var previewSurface: Surface? = null
            val texture = cameraPreview.surfaceTexture
            if (texture != null) {
                texture.setDefaultBufferSize(640, 480)
                previewSurface = Surface(texture)
                surfaces.add(previewSurface)
            } else {
                Log.i(TAG, "Camera preview not visible — using ImageReader-only capture (streaming mode)")
            }

            // Always capture to ImageReader for WebSocket streaming
            surfaces.add(imageReader!!.surface)

            cameraDevice?.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    val builder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    previewSurface?.let { builder?.addTarget(it) }
                    builder?.addTarget(imageReader!!.surface)
                    builder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    builder?.let { session.setRepeatingRequest(it.build(), null, cameraHandler) }
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Capture session config failed")
                }
            }, cameraHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create capture session", e)
        }
    }

    private fun processVideoFrame(image: Image) {
        try {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 75, out)
            val jpegBytes = out.toByteArray()

            lastJpegBytes = jpegBytes

            if (isStreaming.get()) {
                val frame = ByteArray(1 + jpegBytes.size)
                frame[0] = VIDEO_TYPE
                System.arraycopy(jpegBytes, 0, frame, 1, jpegBytes.size)
                webSocket?.send(frame.toByteString(0, frame.size))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Video frame error", e)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // AUDIO
    // ═══════════════════════════════════════════════════════════════

    private fun startAudioCapture() {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, // enables hardware AEC (echo cancel)
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize * 2
        )
        audioRecord?.startRecording()

        audioThread = Thread {
            val buffer = ByteArray(bufferSize)
            while (isStreaming.get()) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    val frame = ByteArray(1 + read)
                    frame[0] = AUDIO_TYPE
                    System.arraycopy(buffer, 0, frame, 1, read)
                    webSocket?.send(frame.toByteString(0, frame.size))
                }
            }
        }.also { it.start() }
    }

    private fun stopAudioCapture() {
        audioThread?.interrupt()
        audioThread = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    // ═══════════════════════════════════════════════════════════════
    // WEBSOCKET
    // ═══════════════════════════════════════════════════════════════

    private fun autoConnect() {
        val ip = prefs.getString("server_ip", "") ?: ""
        val port = prefs.getString("server_port", "") ?: ""
        if (ip.isNotBlank() && port.isNotBlank()) {
            shouldReconnect = true
            reconnectDelay = INITIAL_RECONNECT_DELAY_MS
            connectToServer(ip, port)
        }
    }

    private fun reconnect() {
        stopStreaming()
        shouldReconnect = true
        reconnectDelay = INITIAL_RECONNECT_DELAY_MS
        val ip = prefs.getString("server_ip", "") ?: ""
        val port = prefs.getString("server_port", "") ?: ""
        if (ip.isNotBlank() && port.isNotBlank()) {
            mainHandler.postDelayed({ connectToServer(ip, port) }, 500)
        }
    }

    private fun connectToServer(ip: String, port: String) {
        if (isConnected.get()) return
        val url = "ws://$ip:$port"
        Log.i(TAG, "Connecting to $url")

        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                isConnected.set(true)
                isStreaming.set(true)
                reconnectDelay = INITIAL_RECONNECT_DELAY_MS
                updateConnectionDot(true)
                startAudioCapture()
                // Start camera immediately on connect (don't wait for TextureView)
                runOnUiThread {
                    if (hasPermissions()) openCamera()
                }
                Log.i(TAG, "Connected to $url")
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failed: ${t.message}")
                onDisconnected()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $reason")
                onDisconnected()
            }
        })
    }

    private fun onDisconnected() {
        isConnected.set(false)
        isStreaming.set(false)
        stopAudioCapture()
        updateConnectionDot(false)

        if (shouldReconnect) {
            Log.i(TAG, "Reconnecting in ${reconnectDelay}ms")
            mainHandler.postDelayed({
                val ip = prefs.getString("server_ip", "") ?: ""
                val port = prefs.getString("server_port", "") ?: ""
                if (ip.isNotBlank() && port.isNotBlank()) {
                    connectToServer(ip, port)
                }
            }, reconnectDelay)
            // Exponential backoff
            reconnectDelay = (reconnectDelay * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
        }
    }

    private fun stopStreaming() {
        isStreaming.set(false)
        isConnected.set(false)
        webSocket?.close(1000, "User stopped")
        webSocket = null
        stopAudioCapture()
        updateConnectionDot(false)
    }

    // ═══════════════════════════════════════════════════════════════
    // MESSAGE HANDLING
    // ═══════════════════════════════════════════════════════════════

    private fun handleServerMessage(text: String) {
        try {
            val json = JSONObject(text)
            when (json.optString("type")) {
                "tts" -> {
                    val ttsText = json.getString("text")
                    Log.i(TAG, "TTS: $ttsText")
                    speakText(ttsText)
                }
                "transcript" -> {
                    val msg = json.getString("text")
                    val speaker = json.optString("speaker", "user")
                    addChatMessage(msg, speaker == "user")
                }
                "state" -> {
                    val state = json.getString("state")
                    when (state) {
                        "idle" -> {
                            switchToState(AppState.IDLE)
                            clearChat()
                        }
                        "engaged", "active" -> {
                            switchToState(AppState.ENGAGED)
                        }
                    }
                    json.optString("robot_status", "").let {
                        if (it.isNotBlank()) updateRobotStatus(it)
                    }
                }
                "status" -> {
                    val status = json.optString("status", json.optString("state", ""))
                    if (status.isNotBlank()) updateRobotStatus(status)
                }
                "take_photo" -> {
                    if (currentState == AppState.ENGAGED) {
                        runOnUiThread { startSelfieCountdown() }
                    }
                }
                "response" -> {
                    val respText = json.getString("text")
                    addChatMessage(respText, false)
                    speakText(respText)
                }
                "user_name" -> {
                    val name = json.getString("name")
                    runOnUiThread {
                        userNameText.text = name
                        userNameText.visibility = View.VISIBLE
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Message parse error", e)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SELFIE
    // ═══════════════════════════════════════════════════════════════

    private fun startSelfieCountdown() {
        selfieOverlay.visibility = View.VISIBLE
        selfieOverlay.alpha = 0f
        selfieOverlay.animate().alpha(1f).setDuration(200).start()

        selfiePreviewCard.visibility = View.GONE
        selfieActions.visibility = View.GONE
        countdownText.visibility = View.VISIBLE
        flashOverlay.visibility = View.GONE

        object : CountDownTimer(3500, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val sec = (millisUntilFinished / 1000).toInt() + 1
                if (sec <= 3) {
                    countdownText.text = sec.toString()
                    // Scale down animation
                    countdownText.scaleX = 2.0f
                    countdownText.scaleY = 2.0f
                    countdownText.alpha = 1f
                    countdownText.animate()
                        .scaleX(0.8f).scaleY(0.8f)
                        .alpha(0.3f)
                        .setDuration(900)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
            }

            override fun onFinish() {
                countdownText.visibility = View.GONE
                // Flash effect
                flashOverlay.visibility = View.VISIBLE
                flashOverlay.alpha = 0.9f
                flashOverlay.animate()
                    .alpha(0f)
                    .setDuration(400)
                    .withEndAction {
                        flashOverlay.visibility = View.GONE
                    }.start()
                capturePhoto()
            }
        }.start()
    }

    private fun capturePhoto() {
        val jpeg = lastJpegBytes
        if (jpeg != null) {
            selfieBitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            selfiePreview.setImageBitmap(selfieBitmap)
            showSelfiePreview()
        } else {
            selfieBitmap = cameraPreview.bitmap
            if (selfieBitmap != null) {
                selfiePreview.setImageBitmap(selfieBitmap)
                showSelfiePreview()
            } else {
                selfieOverlay.visibility = View.GONE
            }
        }
    }

    private fun showSelfiePreview() {
        selfiePreviewCard.visibility = View.VISIBLE
        selfiePreviewCard.scaleX = 0.8f
        selfiePreviewCard.scaleY = 0.8f
        selfiePreviewCard.alpha = 0f
        selfiePreviewCard.animate()
            .scaleX(1f).scaleY(1f)
            .alpha(1f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .start()

        selfieActions.visibility = View.VISIBLE
        selfieActions.alpha = 0f
        selfieActions.animate().alpha(1f).setStartDelay(200).setDuration(200).start()
    }

    private fun saveSelfie() {
        val bitmap = selfieBitmap ?: return
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "jackie_selfie_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Jackie")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                contentResolver.openOutputStream(it)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
            }
            Log.i(TAG, "Selfie saved")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save selfie", e)
        }
        // Animate out
        selfieOverlay.animate()
            .alpha(0f)
            .setDuration(250)
            .withEndAction {
                selfieOverlay.visibility = View.GONE
            }.start()
    }

    // ═══════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    override fun onResume() {
        super.onResume()
        // Re-apply immersive mode
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        shouldReconnect = false
        tts?.shutdown()
        stopStreaming()
        stopSessionTimer()
        captureSession?.close()
        cameraDevice?.close()
        imageReader?.close()
        cameraThread.quitSafely()
        animators.forEach { it.cancel() }
        particleAnimator?.cancel()
        statusDotAnimator?.cancel()
    }
}
