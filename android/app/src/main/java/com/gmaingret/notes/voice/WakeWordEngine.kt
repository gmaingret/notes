package com.gmaingret.notes.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Continuously listens for the "hey notes" wake word using Vosk keyword spotting.
 *
 * Uses a restricted grammar so Vosk only matches "hey notes" or unknown speech,
 * keeping CPU/battery usage low compared to full recognition.
 */
class WakeWordEngine(
    private val model: Model,
    private val onWakeWordDetected: () -> Unit
) {
    companion object {
        private const val TAG = "WakeWordEngine"
        private const val SAMPLE_RATE = 16000
        private val WAKE_PHRASES = listOf("hey notes", "hey note")
    }

    private var audioRecord: AudioRecord? = null
    private var recognizer: Recognizer? = null
    private var listeningJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (listeningJob?.isActive == true) return

        listeningJob = scope.launch(Dispatchers.IO) {
            try {
                val bufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(4096)

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                // Keyword spotting grammar — Vosk will only attempt to match these phrases
                val grammar = """["hey notes", "hey note", "[unk]"]"""
                recognizer = Recognizer(model, SAMPLE_RATE.toFloat(), grammar)

                audioRecord?.startRecording()
                val recordingState = audioRecord?.recordingState
                Log.d(TAG, "Wake word listening started (recordingState=$recordingState)")

                val buffer = ByteArray(4096)
                var frameCount = 0
                while (isActive) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (bytesRead > 0) {
                        frameCount++
                        if (frameCount % 100 == 0) {
                            Log.d(TAG, "Audio frames processed: $frameCount")
                        }
                        val accepted = recognizer?.acceptWaveForm(buffer, bytesRead) == true

                        // Check PARTIAL results for faster wake word detection
                        if (!accepted) {
                            val partial = recognizer?.partialResult ?: ""
                            val partialMatch = """"partial"\s*:\s*"([^"]*)"""".toRegex().find(partial)
                            val partialText = partialMatch?.groupValues?.get(1) ?: ""
                            if (partialText.isNotBlank() && WAKE_PHRASES.any { phrase -> partialText.contains(phrase) }) {
                                Log.d(TAG, "Wake word detected from partial: $partialText")
                                onWakeWordDetected()
                                return@launch
                            }
                        }

                        // Also check final results
                        if (accepted) {
                            val result = recognizer?.result ?: ""
                            val textMatch = """"text"\s*:\s*"([^"]*)"""".toRegex().find(result)
                            val text = textMatch?.groupValues?.get(1) ?: ""
                            if (text.isNotBlank() && WAKE_PHRASES.any { phrase -> text.contains(phrase) }) {
                                Log.d(TAG, "Wake word detected from final: $text")
                                onWakeWordDetected()
                                return@launch
                            }
                        }
                    } else {
                        Log.w(TAG, "AudioRecord read returned $bytesRead")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Wake word engine error", e)
            } finally {
                cleanup()
            }
        }
    }

    fun stop() {
        listeningJob?.cancel()
        listeningJob = null
        cleanup()
    }

    private fun cleanup() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) { }
        audioRecord = null

        try {
            recognizer?.close()
        } catch (_: Exception) { }
        recognizer = null
    }
}
