package com.eliel.asistente

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class AssistantWakeService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val ACTION_START = "com.eliel.asistente.START"
        const val ACTION_PAUSE_LISTENING = "com.eliel.asistente.PAUSE_LISTENING"
        const val ACTION_RESUME_LISTENING = "com.eliel.asistente.RESUME_LISTENING"
        const val EXTRA_VOICE_COMMAND = "voice_command"

        private const val CHANNEL_ID = "mia_wake_channel"
        private const val NOTIFICATION_ID = 2001
    }

    private val handler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var speechIntent: Intent
    private lateinit var tts: TextToSpeech

    private var shouldListen = false
    private var isListening = false
    private var speechReady = false
    private var waitingForCommand = false
    private var pendingCommand: String? = null
    private val wakeWord = "mia"

    override fun onCreate() {
        super.onCreate()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }

        createNotificationChannel()
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (_: SecurityException) {
            stopSelf()
            return
        }

        setupRecognizer()
        tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE_LISTENING -> {
                shouldListen = false
                cancelRecognition()
            }
            ACTION_RESUME_LISTENING, ACTION_START, null -> {
                shouldListen = true
                scheduleListening(350)
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mía en segundo plano",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene a Mía atenta a la palabra de activación."
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Mía está atenta")
            .setContentText("Di “Mía” para activarla.")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun setupRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PA")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).also { recognizer ->
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    isListening = false
                    if (!shouldListen) return
                    val delay = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 900L else 500L
                    scheduleListening(delay)
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val spoken = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()

                    if (!spoken.isNullOrBlank()) {
                        processSpeech(spoken)
                    } else {
                        scheduleListening(400)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    private fun processSpeech(raw: String) {
        val normalized = normalize(raw)

        if (waitingForCommand) {
            waitingForCommand = false
            pendingCommand = raw
            handler.postDelayed({ launchPendingCommand() }, 80)
            return
        }

        val wakeIndex = normalized.indexOf(wakeWord)
        if (wakeIndex < 0) {
            scheduleListening(250)
            return
        }

        val afterWake = normalized.substring(wakeIndex + wakeWord.length)
            .trim()
            .trimStart(',', '.', ':', ';', '-', ' ')

        playWakeTone()

        if (afterWake.isBlank()) {
            waitingForCommand = true
            pendingCommand = null
            scheduleListening(120)
        } else {
            pendingCommand = afterWake
            handler.postDelayed({ launchPendingCommand() }, 120)
        }
    }

    private fun sayYesTellMe() {
        cancelRecognition()
        if (speechReady) {
            tts.speak("Sí, dime.", TextToSpeech.QUEUE_FLUSH, null, "wake_only")
        } else {
            scheduleListening(250)
        }
    }

    private fun sayYesTellMeAndThenLaunch() {
        cancelRecognition()
        if (speechReady) {
            tts.speak("Sí, dime.", TextToSpeech.QUEUE_FLUSH, null, "wake_command")
        } else {
            launchPendingCommand()
        }
    }

    private fun launchPendingCommand() {
        val command = pendingCommand ?: run {
            scheduleListening(250)
            return
        }
        pendingCommand = null
        shouldListen = false
        cancelRecognition()

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_VOICE_COMMAND, command)
        }

        try {
            startActivity(intent)
        } catch (_: Exception) {
            shouldListen = true
            scheduleListening(700)
        }
    }

    private fun playWakeTone() {
        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80).apply {
                startTone(ToneGenerator.TONE_PROP_BEEP2, 90)
                handler.postDelayed({ release() }, 130)
            }
        } catch (_: Exception) {
        }
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return

        var result = tts.setLanguage(Locale("es", "PA"))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            result = tts.setLanguage(Locale("es"))
        }

        speechReady = result != TextToSpeech.LANG_MISSING_DATA &&
            result != TextToSpeech.LANG_NOT_SUPPORTED

        selectPreferredVoice()
        tts.setSpeechRate(1.02f)
        tts.setPitch(1.05f)

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                handler.post {
                    when (utteranceId) {
                        "wake_command" -> launchPendingCommand()
                        "wake_only" -> scheduleListening(180)
                        else -> scheduleListening(250)
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                handler.post {
                    if (utteranceId == "wake_command") launchPendingCommand()
                    else scheduleListening(250)
                }
            }
        })
    }

    private fun selectPreferredVoice() {
        val voices = tts.voices ?: return
        val spanishVoices = voices.filter { it.locale.language == "es" }
        if (spanishVoices.isEmpty()) return

        val preferred = spanishVoices.maxByOrNull { voice ->
            var score = voice.quality
            val name = voice.name.lowercase(Locale.getDefault())
            if (voice.locale.country == "PA") score += 500
            if (voice.locale.country == "US") score += 250
            if (voice.locale.country == "MX") score += 200
            if (voice.isNetworkConnectionRequired) score += 150
            if (listOf("female", "fem", "mujer", "esf", "neural", "natural", "wavenet")
                    .any { name.contains(it) }) score += 400
            score
        }
        if (preferred != null) tts.voice = preferred
    }

    private fun scheduleListening(delayMs: Long) {
        if (!shouldListen) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ startListening() }, delayMs)
    }

    private fun startListening() {
        if (!shouldListen || isListening) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        try {
            isListening = true
            speechRecognizer?.startListening(speechIntent)
        } catch (_: Exception) {
            isListening = false
            scheduleListening(700)
        }
    }

    private fun cancelRecognition() {
        if (!isListening) return
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }
        isListening = false
    }

    private fun normalize(value: String): String = value.lowercase(Locale.getDefault())
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        speechRecognizer?.destroy()
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
