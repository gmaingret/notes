package com.gmaingret.notes.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import com.gmaingret.notes.MainActivity
import com.gmaingret.notes.R
import com.gmaingret.notes.data.model.VoiceCommandRequest
import com.gmaingret.notes.data.api.VoiceApi
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.vosk.Model
import org.vosk.android.StorageService
import java.util.Locale
import javax.inject.Inject

/**
 * Foreground service that manages the full voice command pipeline:
 *
 * 1. LISTENING_WAKE_WORD — Vosk keyword spotter listens for "hey notes"
 * 2. LISTENING_COMMAND — Vosk full recognizer captures the user's command
 * 3. PROCESSING — Sends transcribed text to backend voice endpoint
 * 4. SPEAKING_RESPONSE — TextToSpeech speaks the confirmation
 * 5. Returns to LISTENING_WAKE_WORD
 */
@AndroidEntryPoint
class VoiceCommandService : Service(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "VoiceCommandService"
        const val CHANNEL_ID = "voice_commands"
        const val NOTIFICATION_ID = 2001

        fun start(context: Context) {
            val intent = Intent(context, VoiceCommandService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VoiceCommandService::class.java))
        }
    }

    @Inject
    lateinit var voiceApi: VoiceApi

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var model: Model? = null
    private var wakeWordEngine: WakeWordEngine? = null
    private var speechRecognizer: SpeechRecognizerManager? = null
    private var tts: TextToSpeech? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var ttsReady = false

    private enum class State {
        INITIALIZING,
        LISTENING_WAKE_WORD,
        LISTENING_COMMAND,
        PROCESSING,
        SPEAKING_RESPONSE
    }

    private var state = State.INITIALIZING

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        tts = TextToSpeech(this, this)
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Initializing voice commands..."))
        loadModelAndStart()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        state = State.INITIALIZING
        wakeWordEngine?.stop()
        speechRecognizer?.stop()
        tts?.shutdown()
        wakeLock?.let { if (it.isHeld) it.release() }
        model?.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            ttsReady = true
            Log.d(TAG, "TTS initialized")
        } else {
            Log.e(TAG, "TTS initialization failed")
        }
    }

    private fun loadModelAndStart() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                StorageService.unpack(
                    this@VoiceCommandService,
                    "model-en-us",
                    "model",
                    { loadedModel ->
                        model = loadedModel
                        Log.d(TAG, "Vosk model loaded")
                        startWakeWordListening()
                    },
                    { e ->
                        Log.e(TAG, "Failed to load Vosk model", e)
                        updateNotification("Voice model failed to load")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Model loading error", e)
                updateNotification("Voice model error: ${e.message}")
            }
        }
    }

    private fun startWakeWordListening() {
        state = State.LISTENING_WAKE_WORD
        updateNotification("Listening for \"Hey Notes\"...")

        speechRecognizer?.stop()
        wakeWordEngine?.stop()

        wakeWordEngine = WakeWordEngine(model!!) {
            onWakeWordDetected()
        }
        wakeWordEngine?.start(serviceScope)
    }

    private fun onWakeWordDetected() {
        state = State.LISTENING_COMMAND
        updateNotification("Listening for your command...")

        // Vibrate to acknowledge wake word
        vibrate()

        // Stop wake word, start full recognition
        wakeWordEngine?.stop()

        speechRecognizer = SpeechRecognizerManager(
            model = model!!,
            onResult = { text -> onCommandRecognized(text) },
            onError = { error ->
                speak(error)
                startWakeWordListening()
            }
        )
        speechRecognizer?.start(serviceScope)
    }

    private fun onCommandRecognized(text: String) {
        state = State.PROCESSING
        updateNotification("Processing: \"$text\"")
        Log.d(TAG, "Command recognized: $text")

        serviceScope.launch {
            try {
                val response = voiceApi.sendCommand(VoiceCommandRequest(text))
                speak(response.message)
            } catch (e: Exception) {
                Log.e(TAG, "API call failed", e)
                speak("Sorry, I couldn't process that command. Please try again.")
            }
        }
    }

    private fun speak(text: String) {
        state = State.SPEAKING_RESPONSE
        updateNotification("Speaking: \"$text\"")

        if (ttsReady && tts != null) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice_response")
            // Wait for TTS to finish, then resume wake word listening
            serviceScope.launch {
                // Poll TTS speaking state
                while (tts?.isSpeaking == true) {
                    kotlinx.coroutines.delay(200)
                }
                startWakeWordListening()
            }
        } else {
            Log.w(TAG, "TTS not ready, skipping speech")
            startWakeWordListening()
        }
    }

    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed", e)
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "notes:voice_command_wake_lock"
        ).apply { acquire() }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Commands",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when voice command listening is active"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Notes Voice")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }
}
