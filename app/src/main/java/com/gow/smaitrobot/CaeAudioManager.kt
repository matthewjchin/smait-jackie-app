package com.gow.smaitrobot

import android.content.Context
import android.util.Log
import com.iflytek.alsa.AlsaRecorder
import com.voice.osCaeHelper.CaeCoreHelper
import com.voice.caePk.OnCaeOperatorlistener
import com.voice.caePk.util.FileUtil
import okhttp3.WebSocket
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
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
 *   ALSA (8ch 16-bit) → adapt8chTo6chCaeFormat (6ch 32-bit) → CAE engine → onAudio → 0x01 WS frame
 *   ALSA (8ch 16-bit) → extract4chRaw (4ch 16-bit) → 0x03 WS frame
 *   CAE onWakeup → DOA JSON text → WS text frame
 *
 * Fixes applied vs cae-work-march2 branch:
 *   FIX 1 (AUD-02): Channel adapter now uses [0x00, channel_id, pcm_lo, pcm_hi] with IDs 1..6
 *   FIX 2 (AUD-04): DOA sent as JSON text frame, not binary 0x03 frame
 *   FIX 3 (AUD-01, AUD-03): Bypass CAE removed; onAudio sends beamformed 0x01; pcmListener sends raw 0x03
 */
class CaeAudioManager(private val context: Context) {

    companion object {
        private const val TAG = "CaeAudio"

        // ALSA device config for Jackie's Bothlent UAC Dongle
        private const val PCM_CARD = 2           // Card 2: USB mic array
        private const val PCM_DEVICE = 0
        private const val PCM_CHANNELS = 8       // 8ch as reported by /proc/asound/card2/stream0
        private const val PCM_SAMPLE_RATE = 16000
        private const val PCM_PERIOD_SIZE = 1024
        private const val PCM_PERIOD_COUNT = 4
        private const val PCM_FORMAT = 0         // PCM_FORMAT_S16_LE

        // WebSocket binary frame type bytes (must match Python server protocol.py)
        private const val AUDIO_CAE_TYPE: Byte = 0x01  // CAE beamformed output
        private const val AUDIO_RAW_TYPE: Byte = 0x03  // Raw 4-channel audio

        // CAE resource paths on device
        private const val CAE_WORK_DIR = "/sdcard/cae/"

        // Asset files to copy to /sdcard/cae/ before CAE init
        private val ASSET_FILES = listOf(
            "hlw.ini",
            "hlw.param",
            "res_cae_model.bin",
            "res_ivw_model.bin"
        )

        // ─── Pure functions exposed as internal for unit testing ───

        /**
         * Adapt 8-channel 16-bit interleaved PCM to 6-channel 32-bit CAE format.
         *
         * Input:  N frames × 8ch × 2 bytes = N×16 bytes (S16_LE interleaved)
         * Output: N frames × 6ch × 4 bytes = N×24 bytes (channel-ID-prefixed S16 in 32-bit slot)
         *
         * Each output slot: [0x00, channel_id, pcm_lo, pcm_hi]
         * Channel IDs 1..6 (required by CAE engine — zero causes onAudio to never fire)
         * Source mapping: ALSA ch0-3 = mics 1-4, ch6 = ref1 (slot 4), ch7 = ref2 (slot 5)
         *
         * Reference: docs/hardware-sdk/CAEDemoAIUI-4 MIC/CaeOperator.java adapeter4Mic32bit()
         */
        @JvmStatic
        internal fun adapt8chTo6chCaeFormat(data: ByteArray): ByteArray {
            val frames = data.size / 16
            val out = ByteArray(frames * 24)
            // Source byte offsets within each 8ch frame: ch0=0, ch1=2, ch2=4, ch3=6, ch6=12, ch7=14
            val srcOffsets = intArrayOf(0, 2, 4, 6, 12, 14)
            for (j in 0 until frames) {
                val inOff = j * 16
                val outOff = j * 24
                for (ch in 0 until 6) {
                    val sOff = inOff + srcOffsets[ch]
                    val dOff = outOff + ch * 4
                    out[dOff + 0] = 0x00
                    out[dOff + 1] = (ch + 1).toByte()  // Channel IDs 1..6 (FIX 1: was 0x00)
                    out[dOff + 2] = data[sOff + 0]     // PCM lo byte
                    out[dOff + 3] = data[sOff + 1]     // PCM hi byte
                }
            }
            return out
        }

        /**
         * Extract channels 0-3 (mic 1-4) from 8-channel 16-bit interleaved data.
         *
         * Input:  N frames × 8ch × 2 bytes = N×16 bytes
         * Output: N frames × 4ch × 2 bytes = N×8 bytes (S16_LE interleaved, ch0-ch3 only)
         *
         * This raw 4ch stream is sent as 0x03 (AUDIO_RAW) to the server for Dolphin speaker separation.
         */
        @JvmStatic
        internal fun extract4chRaw(alsaData: ByteArray): ByteArray {
            val frames = alsaData.size / 16
            val out = ByteArray(frames * 8)
            for (j in 0 until frames) {
                System.arraycopy(alsaData, j * 16, out, j * 8, 8)  // ch0-ch3 = first 8 bytes
            }
            return out
        }

        /**
         * Build a binary WebSocket frame for CAE beamformed audio (type 0x01).
         * Frame format: [0x01] + audioData
         */
        @JvmStatic
        internal fun buildAudioFrame(audioData: ByteArray): ByteArray {
            val frame = ByteArray(1 + audioData.size)
            frame[0] = AUDIO_CAE_TYPE
            System.arraycopy(audioData, 0, frame, 1, audioData.size)
            return frame
        }

        /**
         * Build a binary WebSocket frame for raw 4-channel audio (type 0x03).
         * Frame format: [0x03] + raw4chData
         */
        @JvmStatic
        internal fun buildRaw4chFrame(raw4chData: ByteArray): ByteArray {
            val frame = ByteArray(1 + raw4chData.size)
            frame[0] = AUDIO_RAW_TYPE
            System.arraycopy(raw4chData, 0, frame, 1, raw4chData.size)
            return frame
        }

        /**
         * Build a DOA JSON text string for WebSocket text frame.
         * Format: {"type":"doa","angle":N,"beam":N}
         *
         * NOTE: This is a TEXT frame, NOT binary. The server's _handle_text() parses JSON.
         * Sending DOA as binary 0x03 would collide with AUDIO_RAW and corrupt the stream.
         * (FIX 2: replaces binary ByteBuffer DOA send from cae-work-march2 branch)
         */
        @JvmStatic
        internal fun buildDoaJson(angle: Int, beam: Int): String {
            return JSONObject().apply {
                put("type", "doa")
                put("angle", angle)
                put("beam", beam)
            }.toString()
        }
    }

    private var alsaRecorder: AlsaRecorder? = null
    private var caeCoreHelper: CaeCoreHelper? = null
    private var webSocket: WebSocket? = null
    private val isRunning = AtomicBoolean(false)

    // Callback for DOA angle updates (optional — for UI integration)
    var onDoaAngle: ((Int) -> Unit)? = null

    // Last known DOA angle (accessible for logging/debug)
    var lastDoaAngle: Int = -1
        private set

    /**
     * Optional writer callback for dependency-injection style audio delivery.
     *
     * When set, audio frames built by [buildAudioFrame] and [buildRaw4chFrame] are
     * forwarded to this callback instead of (or in addition to) the WebSocket.
     *
     * Used by [ConversationViewModel] to wire CaeAudioManager into [WebSocketRepository]:
     * ```kotlin
     * caeAudioManager.setWriterCallback { bytes -> wsRepo.send(bytes) }
     * ```
     *
     * This allows the ViewModel to own the WebSocket reference and keeps
     * CaeAudioManager decoupled from the repository layer in tests.
     */
    private var writerCallback: ((ByteArray) -> Unit)? = null

    /**
     * Set the writer callback for outbound audio frames.
     * Called from ConversationViewModel's init block.
     */
    fun setWriterCallback(callback: (ByteArray) -> Unit) {
        writerCallback = callback
    }

    /**
     * Copy CAE model/config files from assets to /sdcard/cae/
     * Must be called once before first use (e.g., in onCreate).
     * Safe to call multiple times — skips files that already exist.
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
     * Initialize CAE engine and start beamformed audio capture.
     *
     * @param ws WebSocket to send audio frames on. Must be open and connected.
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
                /**
                 * Called by CAE engine with beamformed mono audio output.
                 * Send as 0x01 (AUDIO_CAE) binary frame.
                 * (FIX 3: CAE bypass removed — this now sends real CAE output)
                 */
                override fun onAudio(audioData: ByteArray, dataLen: Int) {
                    caeCallbackCount++
                    if (caeCallbackCount % 100 == 0L) {
                        Log.i(TAG, "CAE onAudio #$caeCallbackCount: $dataLen bytes")
                    }
                    // Send beamformed CAE output as 0x01 frame (AUD-01, AUD-03)
                    sendAudio(audioData, dataLen)
                }

                /**
                 * Called by CAE engine with DOA angle from beamforming.
                 * Send as JSON text frame (not binary) — server parses via _handle_text().
                 * (FIX 2: replaces binary ByteBuffer DOA from branch)
                 */
                override fun onWakeup(angle: Int, beam: Int) {
                    Log.i(TAG, "DOA: angle=$angle, beam=$beam")
                    lastDoaAngle = angle
                    onDoaAngle?.invoke(angle)
                    sendDoaAngle(angle, beam)
                }
            }

            caeCoreHelper = CaeCoreHelper(caeListener, false)  // false = not 2-mic mode
            // Force beam 0 on startup for continuous audio output.
            // Default -1 = "wait for wake word" which blocks onAudio callback (Pitfall 1).
            com.iflytek.iflyos.cae.CAE.CAESetRealBeam(0)

            // Auto-fix ALSA permissions (resets on reboot, no adb needed)
            try {
                Runtime.getRuntime().exec("chmod 666 /dev/snd/pcmC${PCM_CARD}D${PCM_DEVICE}c").waitFor()
                Log.i(TAG, "ALSA permissions set for pcmC${PCM_CARD}D${PCM_DEVICE}c")
            } catch (e: Exception) {
                Log.w(TAG, "Could not set ALSA permissions (non-fatal): ${e.message}")
            }

            // Create ALSA recorder instance for USB mic array (Bothlent UAC Dongle)
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
     * Stop CAE audio capture and release all resources.
     */
    fun stop() {
        isRunning.set(false)
        cleanup()
    }

    // ─── Private implementation ───

    private var pcmFrameCount = 0L
    private var caeCallbackCount = 0L

    /**
     * ALSA PCM listener — receives raw 8-channel 16-bit audio from USB mic array.
     *
     * Per callback:
     * 1. Adapt 8ch to 6ch 32-bit CAE format and feed CAE engine (triggers onAudio for beamformed output)
     * 2. Extract raw 4ch audio and send as 0x03 frame (for Dolphin speaker separation)
     *
     * (FIX 3: Removed "BYPASS CAE" mono extraction + 16x gain code from branch)
     */
    private val pcmListener = AlsaRecorder.PcmListener { bytes, length ->
        pcmFrameCount++
        if (pcmFrameCount % 100 == 0L) {
            Log.d(TAG, "PCM frame #$pcmFrameCount: ${bytes.size} bytes")
        }

        // Step 1: Adapt 8ch 16-bit to 6ch 32-bit with channel IDs, feed CAE engine (AUD-02)
        val adapted = adapt8chTo6chCaeFormat(bytes)
        caeCoreHelper?.writeAudio(adapted)

        // Step 2: Extract raw 4ch (ch0-ch3) and send as 0x03 AUDIO_RAW frame (AUD-03)
        val raw4ch = extract4chRaw(bytes)
        sendRaw4ch(raw4ch)
    }

    /**
     * Send CAE beamformed audio as 0x01 (AUDIO_CAE) binary WebSocket frame.
     * Called from onAudio CAE callback.
     *
     * Delivers to [writerCallback] if set (ViewModel/Repository pattern), otherwise
     * falls through to direct [webSocket] send (legacy mode).
     */
    private fun sendAudio(audioData: ByteArray, dataLen: Int) {
        try {
            val frame = buildAudioFrame(audioData.copyOfRange(0, dataLen))
            writerCallback?.invoke(frame) ?: webSocket?.send(frame.toByteString(0, frame.size))
        } catch (e: Exception) {
            Log.e(TAG, "Send CAE audio error", e)
        }
    }

    /**
     * Send raw 4-channel audio as 0x03 (AUDIO_RAW) binary WebSocket frame.
     * Called from pcmListener after extracting 4ch from 8ch ALSA data.
     *
     * Delivers to [writerCallback] if set (ViewModel/Repository pattern), otherwise
     * falls through to direct [webSocket] send (legacy mode).
     */
    private fun sendRaw4ch(raw4chData: ByteArray) {
        try {
            val frame = buildRaw4chFrame(raw4chData)
            writerCallback?.invoke(frame) ?: webSocket?.send(frame.toByteString(0, frame.size))
        } catch (e: Exception) {
            Log.e(TAG, "Send raw 4ch error", e)
        }
    }

    /**
     * Send DOA angle as JSON text WebSocket frame.
     * Format: {"type":"doa","angle":N,"beam":N}
     *
     * Uses WebSocket.send(String) — text frame, NOT binary.
     * (FIX 2: was binary ByteBuffer with 0x03 type byte on branch)
     */
    private fun sendDoaAngle(angle: Int, beam: Int) {
        try {
            webSocket?.send(buildDoaJson(angle, beam))
        } catch (e: Exception) {
            Log.e(TAG, "Send DOA error", e)
        }
    }

    /**
     * Send CAE status notification to server for logging/diagnostics.
     */
    private fun sendCaeStatus(active: Boolean) {
        try {
            val json = JSONObject().apply {
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
        Log.i(TAG, "CAE audio stopped and resources released")
    }
}
