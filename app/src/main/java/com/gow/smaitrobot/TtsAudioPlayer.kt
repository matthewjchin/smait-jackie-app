package com.gow.smaitrobot

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.HandlerThread
import android.util.Log

/**
 * AudioTrack-based PCM16 24kHz streaming TTS player.
 *
 * Receives binary WebSocket frames with type byte 0x05 followed by PCM16 mono 24kHz
 * audio data (Kokoro TTS output format). Plays audio via AudioTrack on a background
 * HandlerThread to avoid blocking OkHttp's dispatch thread.
 *
 * Lifecycle:
 *   start()   → creates AudioTrack and HandlerThread, begins playback
 *   write()   → enqueues PCM bytes for background playback (non-blocking)
 *   stop()    → stops current utterance (reusable; does not release)
 *   release() → releases AudioTrack and quits HandlerThread (call in onDestroy)
 *
 * @param audioWriter Injectable writer for unit testing. Production code leaves this
 *   null and uses the real AudioTrack.write() via the background HandlerThread.
 */
class TtsAudioPlayer(
    private val audioWriter: ((data: ByteArray, offset: Int, length: Int) -> Unit)? = null
) {

    companion object {
        private const val TAG = "TtsAudioPlayer"
        private const val SAMPLE_RATE = 24000
        private const val TTS_FRAME_TYPE: Byte = 0x05

        /**
         * Clamp a volume value to the valid AudioTrack range [0.0, 1.0].
         * Exposed for unit testing.
         */
        fun clampVolume(volume: Float): Float = volume.coerceIn(0f, 1f)
    }

    private var audioTrack: AudioTrack? = null
    private var playbackThread: HandlerThread? = null
    private var playbackHandler: Handler? = null

    /**
     * Create and start the AudioTrack and background HandlerThread.
     * Call once after construction (e.g., in Activity.onCreate).
     *
     * No-op if called when testing with an injected audioWriter.
     */
    fun start() {
        if (audioWriter != null) {
            // Test mode: no real AudioTrack needed
            Log.d(TAG, "start() in test mode — AudioTrack skipped")
            return
        }

        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBuf * 4)
            .build()

        val thread = HandlerThread("TtsPlayback")
        thread.start()
        val handler = Handler(thread.looper)

        audioTrack = track
        playbackThread = thread
        playbackHandler = handler

        handler.post { track.play() }
        Log.i(TAG, "AudioTrack started: ${SAMPLE_RATE}Hz PCM16 mono, buffer=${minBuf * 4} bytes")
    }

    /**
     * Enqueue PCM bytes for background playback.
     *
     * MUST NOT be called on the OkHttp dispatch thread directly for the real
     * AudioTrack path — use [handleBinaryFrame] which posts to the HandlerThread.
     * When called via [handleBinaryFrame], this posts the write to the background thread.
     *
     * @param data   byte array containing PCM16 samples
     * @param offset byte offset into [data] where PCM starts
     * @param length number of bytes to write
     */
    fun write(data: ByteArray, offset: Int, length: Int) {
        if (audioWriter != null) {
            audioWriter.invoke(data, offset, length)
            return
        }

        val track = audioTrack ?: return
        val handler = playbackHandler ?: return

        // Copy slice so caller's buffer can be GC'd safely
        val slice = data.copyOfRange(offset, offset + length)
        handler.post {
            track.write(slice, 0, slice.size)
        }
    }

    /**
     * Stop and flush the current TTS utterance.
     * The AudioTrack is NOT released — call [start] flow continues for next utterance.
     * Safe to call from any thread.
     */
    fun stop() {
        if (audioWriter != null) return

        val track = audioTrack ?: return
        val handler = playbackHandler ?: return
        handler.post {
            try {
                track.pause()
                track.flush()
            } catch (e: Exception) {
                Log.w(TAG, "stop() error: ${e.message}")
            }
        }
        Log.d(TAG, "TTS playback stopped")
    }

    /**
     * Ensure AudioTrack is in PLAYING state before writing.
     * Called automatically by [handleBinaryFrame] so that playback resumes
     * after a [stop] without needing an explicit restart.
     */
    private fun ensurePlaying() {
        if (audioWriter != null) return

        val track = audioTrack ?: return
        val handler = playbackHandler ?: return
        handler.post {
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                track.play()
                Log.d(TAG, "AudioTrack restarted")
            }
        }
    }

    /**
     * Apply volume to the AudioTrack. Volume is clamped to [0.0, 1.0].
     * Safe to call from any thread; takes effect immediately.
     */
    fun setVolume(volume: Float) {
        if (audioWriter != null) return
        audioTrack?.setVolume(clampVolume(volume))
    }

    /**
     * Release AudioTrack and quit the HandlerThread.
     * Call in Activity.onDestroy or when the player is no longer needed.
     * Do NOT call write() or stop() after release().
     */
    fun release() {
        if (audioWriter != null) return

        val track = audioTrack
        val thread = playbackThread

        audioTrack = null
        playbackThread = null
        playbackHandler = null

        track?.let {
            try {
                it.stop()
                it.release()
            } catch (e: Exception) {
                Log.w(TAG, "release() AudioTrack error: ${e.message}")
            }
        }
        thread?.quitSafely()
        Log.i(TAG, "TtsAudioPlayer released")
    }

    /**
     * Parse a binary WebSocket frame and route TTS audio to [write].
     *
     * Frame layout: [type_byte (1)] [pcm_data (N)]
     *   - 0x05 = TTS_AUDIO → PCM16 24kHz mono from Kokoro TTS
     *   - all other types are ignored (returned false)
     *
     * @param data raw bytes from OkHttp [onMessage(ws, bytes: ByteString)]
     * @return true if the frame was a 0x05 TTS frame and was dispatched to [write];
     *         false if empty or an unrecognised frame type
     */
    fun handleBinaryFrame(data: ByteArray): Boolean {
        if (data.isEmpty()) return false

        return when (data[0]) {
            TTS_FRAME_TYPE -> {
                // Restart AudioTrack if it was stopped (e.g., after tts_control:end)
                ensurePlaying()
                write(data, 1, data.size - 1)
                true
            }
            else -> {
                Log.v(TAG, "Ignoring binary frame type: 0x${data[0].toUByte().toString(16)}")
                false
            }
        }
    }
}
