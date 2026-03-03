package com.gow.smaitrobot

import android.content.Context
import android.util.Log
import com.iflytek.alsa.AlsaRecorder
import com.voice.osCaeHelper.CaeCoreHelper
import com.voice.caePk.OnCaeOperatorlistener
import com.voice.caePk.util.FileUtil
import okhttp3.WebSocket
import okio.ByteString.Companion.toByteString
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CAE SDK Audio Manager for Jackie Robot
 *
 * Replaces Android AudioRecord with iFlytek CAE SDK for:
 * - Hardware beamforming (4-mic array)
 * - Noise suppression
 * - Direction of Arrival (DOA)
 *
 * Audio flow:
 *   ALSA (8ch 16-bit) → channel adapter (6ch 32-bit) → CAE engine → beamformed mono 16-bit → WebSocket
 */
class CaeAudioManager(private val context: Context) {

    companion object {
        private const val TAG = "CaeAudio"

        // ALSA device config for Jackie's Bothlent UAC Dongle
        private const val PCM_CARD = 2          // Card 2: USB mic array
        private const val PCM_DEVICE = 0
        private const val PCM_CHANNELS = 8      // 8ch as reported by /proc/asound/card2/stream0
        private const val PCM_SAMPLE_RATE = 16000
        private const val PCM_PERIOD_SIZE = 1024
        private const val PCM_PERIOD_COUNT = 4
        private const val PCM_FORMAT = 0        // PCM_FORMAT_S16_LE

        // WebSocket message types (must match Python server)
        private const val AUDIO_TYPE: Byte = 0x01
        private const val DOA_TYPE: Byte = 0x03

        // CAE resource paths on device
        private const val CAE_WORK_DIR = "/sdcard/cae/"

        // Asset files to copy
        private val ASSET_FILES = listOf(
            "hlw.ini",
            "hlw.param",
            "res_cae_model.bin",
            "res_ivw_model.bin"
        )
    }

    private var alsaRecorder: AlsaRecorder? = null
    private var caeCoreHelper: CaeCoreHelper? = null
    private var webSocket: WebSocket? = null
    private val isRunning = AtomicBoolean(false)

    // Callback for DOA angle updates
    var onDoaAngle: ((Int) -> Unit)? = null

    // Last known DOA angle
    var lastDoaAngle: Int = -1
        private set

    /**
     * Copy CAE model/config files from assets to /sdcard/cae/
     * Must be called once before first use (e.g., in onCreate)
     */
    fun copyAssetsIfNeeded() {
        try {
            val dir = java.io.File(CAE_WORK_DIR)
            if (!dir.exists()) dir.mkdirs()

            for (fileName in ASSET_FILES) {
                val target = java.io.File(CAE_WORK_DIR + fileName)
                if (!target.exists()) {
                    Log.i(TAG, "Copying asset: $fileName → $CAE_WORK_DIR")
                    FileUtil.CopyAssets2Sdcard(context, fileName, CAE_WORK_DIR + fileName)
                }
            }
            Log.i(TAG, "CAE assets ready")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy CAE assets", e)
        }
    }

    /**
     * Initialize CAE engine and start beamformed audio capture
     */
    fun start(ws: WebSocket) {
        if (isRunning.get()) {
            Log.w(TAG, "Already running")
            return
        }

        webSocket = ws

        try {
            // Initialize CAE engine with our listener
            val caeListener = object : OnCaeOperatorlistener {
                override fun onAudio(audioData: ByteArray, dataLen: Int) {
                    caeCallbackCount++
                    if (caeCallbackCount % 100 == 0L) {
                        Log.i(TAG, "CAE onAudio #$caeCallbackCount: $dataLen bytes")
                    }
                    // This is the beamformed, noise-suppressed mono audio
                    // Send directly over WebSocket
                    if (isRunning.get()) {
                        sendAudio(audioData, dataLen)
                    }
                }

                override fun onWakeup(angle: Int, beam: Int) {
                    // DOA angle from beamforming
                    Log.i(TAG, "DOA: angle=$angle, beam=$beam")
                    lastDoaAngle = angle
                    onDoaAngle?.invoke(angle)
                    sendDoaAngle(angle)
                }
            }

            caeCoreHelper = CaeCoreHelper(caeListener, false) // false = not 2-mic mode
            // Force beam 0 on startup for continuous audio output
            // (default -1 means "wait for wake word" which blocks onAudioCallback)
            com.iflytek.iflyos.cae.CAE.CAESetRealBeam(0)

            // Create ALSA recorder instance for USB mic array
            alsaRecorder = AlsaRecorder.createInstance(
                PCM_CARD,
                PCM_DEVICE,
                PCM_CHANNELS,
                PCM_SAMPLE_RATE,
                PCM_PERIOD_SIZE,
                PCM_PERIOD_COUNT,
                PCM_FORMAT
            )
            alsaRecorder?.setLogShow(false)

            // Start recording — audio flows: ALSA → adapter → CAE → onAudio callback
            val result = alsaRecorder?.startRecording(pcmListener)
            if (result == 0) {
                isRunning.set(true)
                Log.i(TAG, "CAE beamforming started (Card $PCM_CARD, ${PCM_CHANNELS}ch, ${PCM_SAMPLE_RATE}Hz)")
                // Report CAE status to PC server
                sendCaeStatus(true)
            } else {
                Log.e(TAG, "ALSA recording failed to start: $result")
                cleanup()
            }

        } catch (e: Exception) {
            Log.e(TAG, "CAE start failed", e)
            cleanup()
        }
    }

    /**
     * ALSA PCM listener — receives raw 8-channel audio, adapts to CAE format
     */
    private var pcmFrameCount = 0L
    private var caeCallbackCount = 0L

    private val pcmListener = AlsaRecorder.PcmListener { bytes, length ->
        pcmFrameCount++
        if (pcmFrameCount % 100 == 0L) {
            Log.i(TAG, "PCM read #$pcmFrameCount: ${bytes.size} bytes")
        }
        // Jackie: 8ch 16-bit → adapt to 6ch 32-bit interleaved (no channel IDs) for CAE engine
        val adapted = adapt8ch16bitTo6ch32bit(bytes)
        caeCoreHelper?.writeAudio(adapted)
    }

    /**
     * Adapt 8-channel 16-bit PCM to 6-channel 32-bit format expected by CAE engine.
     *
     * Input:  8 channels × 2 bytes = 16 bytes per frame (interleaved S16_LE)
     * Output: 6 channels × 4 bytes = 24 bytes per frame (interleaved S32_LE)
     *
     * Channel mapping (USB Audio → CAE):
     *   Ch 0 (FL)  → Mic 1  (position 0)
     *   Ch 1 (FR)  → Mic 2  (position 1)
     *   Ch 2 (FC)  → Mic 3  (position 2)
     *   Ch 3 (LFE) → Mic 4  (position 3)
     *   Ch 6 (FLC) → Ref 1  (position 4)
     *   Ch 7 (FRC) → Ref 2  (position 5)
     *
     * 16-bit → 32-bit conversion: left-shift by 16 (standard bit-depth promotion).
     * In LE bytes: 16-bit [lo, hi] → 32-bit [0x00, 0x00, lo, hi]
     * No channel IDs — CAE identifies channels by interleaved position.
     */
    private fun adapt8ch16bitTo6ch32bit(data: ByteArray): ByteArray {
        val framesCount = data.size / 16  // 16 bytes per 8ch frame
        val output = ByteArray(framesCount * 24) // 24 bytes per 6ch frame

        // Source channel byte offsets within a 16-byte input frame (each ch = 2 bytes)
        // Ch0=0, Ch1=2, Ch2=4, Ch3=6, Ch6=12, Ch7=14
        val srcOffsets = intArrayOf(0, 2, 4, 6, 12, 14)

        for (j in 0 until framesCount) {
            val inOff = j * 16
            val outOff = j * 24

            for (ch in 0 until 6) {
                val sOff = inOff + srcOffsets[ch]
                val dOff = outOff + ch * 4
                // 32-bit LE sign-extension: 16-bit [lo, hi] → 32-bit [lo, hi, sign, sign]
                // Sign byte = 0xFF if negative (hi bit set), 0x00 if positive
                val sign: Byte = if (data[sOff + 1].toInt() < 0) 0xFF.toByte() else 0x00
                output[dOff + 0] = data[sOff + 0]  // sample low byte
                output[dOff + 1] = data[sOff + 1]  // sample high byte
                output[dOff + 2] = sign
                output[dOff + 3] = sign
            }
        }

        return output
    }

    /**
     * Send beamformed audio over WebSocket with 0x01 type prefix
     */
    private var sendCount = 0L
    private fun sendAudio(audioData: ByteArray, dataLen: Int) {
        try {
            val frame = ByteArray(1 + dataLen)
            frame[0] = AUDIO_TYPE
            System.arraycopy(audioData, 0, frame, 1, dataLen)
            val sent = webSocket?.send(frame.toByteString(0, frame.size))
            sendCount++
            if (sendCount % 100 == 0L) {
                Log.i(TAG, "WS send #$sendCount: ${frame.size} bytes, result=$sent, ws=${webSocket != null}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Send audio error", e)
        }
    }

    /**
     * Send DOA angle over WebSocket with 0x03 type prefix
     */
    private fun sendDoaAngle(angle: Int) {
        try {
            val buffer = ByteBuffer.allocate(5)
            buffer.order(ByteOrder.BIG_ENDIAN)
            buffer.put(DOA_TYPE)
            buffer.putFloat(angle.toFloat())
            val frame = buffer.array()
            webSocket?.send(frame.toByteString(0, frame.size))
        } catch (e: Exception) {
            Log.e(TAG, "Send DOA error", e)
        }
    }

    /**
     * Send CAE status to PC server for logging
     */
    private fun sendCaeStatus(active: Boolean) {
        try {
            val json = org.json.JSONObject().apply {
                put("type", "cae_status")
                put("aec", active)
                put("beamforming", active)
                put("noise_suppression", active)
            }
            webSocket?.send(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Send CAE status error", e)
        }
    }

    /**
     * Stop capture and release resources
     */
    fun stop() {
        isRunning.set(false)
        cleanup()
    }

    private fun cleanup() {
        try {
            alsaRecorder?.stopRecording()
        } catch (_: Exception) {}
        try {
            caeCoreHelper?.DestoryEngine()
        } catch (_: Exception) {}
        alsaRecorder = null
        caeCoreHelper = null
        webSocket = null
        Log.i(TAG, "CAE audio stopped")
    }
}
