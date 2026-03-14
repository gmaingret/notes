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
 * Captures a full voice command using Vosk in unrestricted recognition mode.
 *
 * Activated after the wake word is detected. Listens until the user stops speaking
 * (Vosk returns a final result after silence), then returns the transcribed text.
 */
class SpeechRecognizerManager(
    private val model: Model,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "SpeechRecognizerMgr"
        private const val SAMPLE_RATE = 16000
        private const val SILENCE_TIMEOUT_MS = 3000L
    }

    private var audioRecord: AudioRecord? = null
    private var recognizer: Recognizer? = null
    private var recognitionJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (recognitionJob?.isActive == true) return

        recognitionJob = scope.launch(Dispatchers.IO) {
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

                // Full recognition mode — no grammar restriction
                recognizer = Recognizer(model, SAMPLE_RATE.toFloat())

                audioRecord?.startRecording()
                Log.d(TAG, "Command recognition started")

                val buffer = ByteArray(4096)
                var lastPartialTime = System.currentTimeMillis()
                var hasHeardSpeech = false

                while (isActive) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (bytesRead > 0) {
                        if (recognizer?.acceptWaveForm(buffer, bytesRead) == true) {
                            // Final result — user stopped speaking
                            val result = recognizer?.result ?: ""
                            val text = extractText(result)
                            if (text.isNotBlank()) {
                                Log.d(TAG, "Final result: $text")
                                onResult(text)
                                return@launch
                            }
                        } else {
                            // Partial result — check if user is speaking
                            val partial = recognizer?.partialResult ?: ""
                            val partialText = extractPartial(partial)
                            if (partialText.isNotBlank()) {
                                hasHeardSpeech = true
                                lastPartialTime = System.currentTimeMillis()
                            }
                        }
                    }

                    // Timeout if user hasn't spoken after wake word
                    val elapsed = System.currentTimeMillis() - lastPartialTime
                    if (!hasHeardSpeech && elapsed > SILENCE_TIMEOUT_MS) {
                        Log.d(TAG, "Silence timeout — no speech detected")
                        onError("I didn't hear a command. Please try again.")
                        return@launch
                    }
                    // Also timeout if speech stopped for a while
                    if (hasHeardSpeech && elapsed > SILENCE_TIMEOUT_MS) {
                        val finalResult = recognizer?.finalResult ?: ""
                        val text = extractText(finalResult)
                        if (text.isNotBlank()) {
                            Log.d(TAG, "Silence timeout final: $text")
                            onResult(text)
                        } else {
                            onError("I couldn't understand what you said.")
                        }
                        return@launch
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Recognition error", e)
                onError("Speech recognition failed: ${e.message}")
            } finally {
                cleanup()
            }
        }
    }

    fun stop() {
        recognitionJob?.cancel()
        recognitionJob = null
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

    private fun extractText(json: String): String {
        // Vosk result format: {"text" : "some recognized text"}
        val regex = """"text"\s*:\s*"([^"]*)"""".toRegex()
        return regex.find(json)?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun extractPartial(json: String): String {
        val regex = """"partial"\s*:\s*"([^"]*)"""".toRegex()
        return regex.find(json)?.groupValues?.get(1)?.trim() ?: ""
    }
}
