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
    private val pcmListener = AlsaRecorder.PcmListener { bytes, length ->
        // Jackie: 8ch 16-bit → adapt to 6ch 32-bit with channel IDs for CAE engine
        val adapted = adapt8ch16bitTo6ch32bit(bytes)
        caeCoreHelper?.writeAudio(adapted)
    }

    /**
     * Adapt 8-channel 16-bit PCM to 6-channel 32-bit format expected by CAE engine.
     *
     * Input:  8 channels × 2 bytes = 16 bytes per frame
     * Output: 6 channels × 4 bytes = 24 bytes per frame
     *
     * Channel mapping (USB Audio → CAE):
     *   Ch 0 (FL)  → Mic 1 (with channel ID 0x01)
     *   Ch 1 (FR)  → Mic 2 (with channel ID 0x02)
     *   Ch 2 (FC)  → Mic 3 (with channel ID 0x03)
     *   Ch 3 (LFE) → Mic 4 (with channel ID 0x04)
     *   Ch 6 (FLC) → Ref 1 (with channel ID 0x05)
     *   Ch 7 (FRC) → Ref 2 (with channel ID 0x06)
     *
     * Each output sample: [0x00, channel_id, sample_low, sample_high]
     */
    private fun adapt8ch16bitTo6ch32bit(data: ByteArray): ByteArray {
        val framesCount = data.size / 16  // 16 bytes per 8ch frame
        val output = ByteArray(framesCount * 24) // 24 bytes per 6ch frame

        for (j in 0 until framesCount) {
            val inOff = j * 16   // input offset
            val outOff = j * 24  // output offset

            // Mic 1 (Ch 0 - FL)
            output[outOff + 0] = 0x00
            output[outOff + 1] = 0x01
            output[outOff + 2] = data[inOff + 0]
            output[outOff + 3] = data[inOff + 1]

            // Mic 2 (Ch 1 - FR)
            output[outOff + 4] = 0x00
            output[outOff + 5] = 0x02
            output[outOff + 6] = data[inOff + 2]
            output[outOff + 7] = data[inOff + 3]

            // Mic 3 (Ch 2 - FC)
            output[outOff + 8] = 0x00
            output[outOff + 9] = 0x03
            output[outOff + 10] = data[inOff + 4]
            output[outOff + 11] = data[inOff + 5]

            // Mic 4 (Ch 3 - LFE)
            output[outOff + 12] = 0x00
            output[outOff + 13] = 0x04
            output[outOff + 14] = data[inOff + 6]
            output[outOff + 15] = data[inOff + 7]

            // Ref 1 (Ch 6 - FLC)
            output[outOff + 16] = 0x00
            output[outOff + 17] = 0x05
            output[outOff + 18] = data[inOff + 12]
            output[outOff + 19] = data[inOff + 13]

            // Ref 2 (Ch 7 - FRC)
            output[outOff + 20] = 0x00
            output[outOff + 21] = 0x06
            output[outOff + 22] = data[inOff + 14]
            output[outOff + 23] = data[inOff + 15]
        }

        return output
    }

    /**
     * Send beamformed audio over WebSocket with 0x01 type prefix
     */
    private fun sendAudio(audioData: ByteArray, dataLen: Int) {
        try {
            val frame = ByteArray(1 + dataLen)
            frame[0] = AUDIO_TYPE
            System.arraycopy(audioData, 0, frame, 1, dataLen)
            webSocket?.send(frame.toByteString(0, frame.size))
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
