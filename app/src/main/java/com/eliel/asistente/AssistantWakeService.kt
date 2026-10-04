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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class AssistantWakeService : Service() {

    companion object {
        const val ACTION_START = "com.eliel.asistente.START"
        const val ACTION_PAUSE_LISTENING = "com.eliel.asistente.PAUSE_LISTENING"
        const val ACTION_RESUME_LISTENING = "com.eliel.asistente.RESUME_LISTENING"
        const val ACTION_STOP_VOICE = "com.eliel.asistente.STOP_VOICE"
        const val ACTION_COMMAND_ACCEPTED = "com.eliel.asistente.COMMAND_ACCEPTED"
        const val EXTRA_VOICE_COMMAND = "voice_command"

        private const val CHANNEL_ID = "nexo_wake_channel"
        private const val NOTIFICATION_ID = 2001
    }

    private val handler = Handler(Looper.getMainLooper())
    private val listenHandler = Handler(Looper.getMainLooper())
    private val commandWindowHandler = Handler(Looper.getMainLooper())
    private val ackHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var speechIntent: Intent

    private var shouldListen = false
    private var isListening = false
    private var waitingForCommand = false
    private var pendingCommand: String? = null
    private var awaitingCommandAck = false
    private var fallbackCommand: String? = null
    private var recognitionErrorStreak = 0
    private val wakeWord = NexoWakeConfig.WAKE_WORD

    override fun onCreate() {
        super.onCreate()

        if (!NexoVoiceState.isEnabled(this)) {
            stopSelf()
            return
        }

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

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            NexoRuntimeState.markIssue(
                this,
                "Voice Core",
                "Reconocimiento de voz Android no disponible"
            )
            stopSelf()
            return
        }

        setupRecognizer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_COMMAND_ACCEPTED -> {
                awaitingCommandAck = false
                fallbackCommand = null
                ackHandler.removeCallbacksAndMessages(null)
                shouldListen = false
                cancelRecognition()
                refreshNotification()
            }
            ACTION_STOP_VOICE -> {
                fallbackCommand = null
                NexoVoiceState.setEnabled(this, false)
                shouldListen = false
                cancelRecognition()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE_LISTENING -> {
                shouldListen = false
                cancelRecognition()
            }
            ACTION_RESUME_LISTENING, ACTION_START, null -> {
                if (!NexoVoiceState.isEnabled(this)) {
                    shouldListen = false
                    stopSelf()
                    return START_NOT_STICKY
                }
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
                "NEXO en segundo plano",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene a NEXO atento a la palabra de activación."
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        pendingVoiceCommand: String? = fallbackCommand
    ): android.app.Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (!pendingVoiceCommand.isNullOrBlank()) {
                putExtra(EXTRA_VOICE_COMMAND, pendingVoiceCommand)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            if (pendingVoiceCommand.isNullOrBlank()) 0 else 2,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseVoiceIntent = Intent(this, AssistantWakeService::class.java).apply {
            action = ACTION_STOP_VOICE
        }
        val pauseVoicePendingIntent = PendingIntent.getService(
            this,
            1,
            pauseVoiceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nexo_notification)
            .setContentTitle(
                if (pendingVoiceCommand.isNullOrBlank()) {
                    "NEXO está atento"
                } else {
                    "NEXO te escuchó"
                }
            )
            .setContentText(
                if (pendingVoiceCommand.isNullOrBlank()) {
                    "Di “NEXO” para activarlo. · motor compatible"
                } else {
                    "Toca para continuar la orden de forma segura."
                }
            )
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_nexo_notification,
                "Pausar escucha",
                pauseVoicePendingIntent
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (!pendingVoiceCommand.isNullOrBlank()) {
            builder.addAction(
                R.drawable.ic_nexo_notification,
                "Continuar",
                pendingIntent
            )
        }

        return builder.build()
    }

    private fun refreshNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ||
            NexoVoiceState.isEnabled(this)
        ) {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun setupRecognizer() {
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
                    recognitionErrorStreak = 0
                    NexoRuntimeState.clearIssue(this@AssistantWakeService, "Voice Core")
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    isListening = false
                    if (!shouldListen) return

                    recognitionErrorStreak = (recognitionErrorStreak + 1).coerceAtMost(6)

                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        NexoRuntimeState.markIssue(
                            this@AssistantWakeService,
                            "Voice Core",
                            "El permiso de micrófono dejó de estar disponible"
                        )
                        shouldListen = false
                        stopSelf()
                        return
                    }

                    val delay = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 500L

                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1200L

                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                        SpeechRecognizer.ERROR_SERVER,
                        SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
                            (1500L * recognitionErrorStreak).coerceAtMost(9000L)

                        else -> (700L * recognitionErrorStreak).coerceAtMost(4200L)
                    }

                    if (recognitionErrorStreak >= 4 &&
                        error !in setOf(
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                        )
                    ) {
                        NexoRuntimeState.markIssue(
                            this@AssistantWakeService,
                            "Voice Core",
                            "Reconocimiento de voz inestable · reintento automático"
                        )
                    }

                    scheduleListening(delay)
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    recognitionErrorStreak = 0
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
            commandWindowHandler.removeCallbacksAndMessages(null)

            val command = normalized.trim()
            if (command.isBlank()) {
                pendingCommand = null
                scheduleListening(250)
                return
            }

            pendingCommand = command
            handler.postDelayed({ launchPendingCommand() }, 80)
            return
        }

        val wake = NexoWakePhrase.extract(normalized)
        if (!wake.found) {
            scheduleListening(250)
            return
        }

        playWakeTone()

        if (wake.command.isBlank()) {
            waitingForCommand = true
            pendingCommand = null
            scheduleListening(120)

            commandWindowHandler.removeCallbacksAndMessages(null)
            commandWindowHandler.postDelayed({
                if (waitingForCommand) {
                    waitingForCommand = false
                    pendingCommand = null
                    scheduleListening(250)
                }
            }, 6500)
        } else {
            pendingCommand = wake.command
            handler.postDelayed({ launchPendingCommand() }, 120)
        }
    }

    private fun launchPendingCommand() {
        val command = pendingCommand ?: run {
            scheduleListening(250)
            return
        }
        pendingCommand = null
        fallbackCommand = null
        refreshNotification()
        shouldListen = false
        cancelRecognition()

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_VOICE_COMMAND, command)
        }

        awaitingCommandAck = true
        try {
            startActivity(intent)
            ackHandler.removeCallbacksAndMessages(null)
            ackHandler.postDelayed({
                if (awaitingCommandAck && NexoVoiceState.isEnabled(this)) {
                    awaitingCommandAck = false
                    fallbackCommand = command
                    NexoRuntimeState.markIssue(
                        this,
                        "Voice Core",
                        "Android no confirmó la apertura automática; orden disponible desde la notificación"
                    )
                    refreshNotification()
                    shouldListen = true
                    scheduleListening(350)
                }
            }, 5000)
        } catch (_: Exception) {
            awaitingCommandAck = false
            fallbackCommand = command
            NexoRuntimeState.markIssue(
                this,
                "Voice Core",
                "Android bloqueó la apertura automática; orden disponible desde la notificación"
            )
            refreshNotification()
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

    private fun scheduleListening(delayMs: Long) {
        if (!shouldListen) return
        listenHandler.removeCallbacksAndMessages(null)
        listenHandler.postDelayed({ startListening() }, delayMs)
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
        listenHandler.removeCallbacksAndMessages(null)
        commandWindowHandler.removeCallbacksAndMessages(null)
        ackHandler.removeCallbacksAndMessages(null)
        speechRecognizer?.destroy()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
