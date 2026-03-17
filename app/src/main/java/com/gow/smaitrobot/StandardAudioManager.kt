package com.gow.smaitrobot

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Standard Android AudioRecord-based audio manager for emulator/non-Jackie devices.
 *
 * Provides the same writer-callback interface as [CaeAudioManager] so that
 * [AppNavigation] can swap between them based on device type.
 *
 * On emulator: uses standard mic → mono 16kHz PCM → 0x01 frames → server.
 * On Jackie: [CaeAudioManager] handles ALSA + CAE beamforming instead.
 */
class StandardAudioManager {

    companion object {
        private const val TAG = "StandardAudio"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val AUDIO_TYPE: Byte = 0x01
    }

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val isRunning = AtomicBoolean(false)
    private var writerCallback: ((ByteArray) -> Unit)? = null

    fun setWriterCallback(callback: (ByteArray) -> Unit) {
        writerCallback = callback
    }

    fun start() {
        if (isRunning.get()) return

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "Invalid buffer size: $bufferSize")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioRecord?.startRecording()
            isRunning.set(true)

            recordingThread = Thread({
                val buffer = ByteArray(1024) // 512 samples @ 16kHz = 32ms
                while (isRunning.get()) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        val frame = ByteArray(1 + read)
                        frame[0] = AUDIO_TYPE
                        System.arraycopy(buffer, 0, frame, 1, read)
                        writerCallback?.invoke(frame)
                    }
                }
            }, "StandardAudio-Record").also { it.start() }

            Log.i(TAG, "Standard audio recording started (${SAMPLE_RATE}Hz mono)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording", e)
            cleanup()
        }
    }

    fun stop() {
        isRunning.set(false)
        cleanup()
    }

    private fun cleanup() {
        recordingThread?.join(1000)
        recordingThread = null
        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        audioRecord?.release()
        audioRecord = null
        Log.i(TAG, "Standard audio stopped")
    }
}
