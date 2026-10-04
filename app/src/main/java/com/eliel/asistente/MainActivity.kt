package com.eliel.asistente

import android.Manifest
import android.content.Intent
import android.app.SearchManager
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.graphics.drawable.GradientDrawable
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var statusText: TextView
    private lateinit var healthText: TextView
    private lateinit var skillsText: TextView
    private lateinit var systemStateText: TextView
    private lateinit var versionText: TextView
    private lateinit var voiceToggleButton: Button
    private lateinit var nowRunningCard: android.view.View
    private lateinit var planTitleText: TextView
    private lateinit var planProgressText: TextView
    private lateinit var planStepsText: TextView
    private lateinit var orbView: android.view.View
    private lateinit var textToSpeech: TextToSpeech
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var speechIntent: Intent

    private val handler = Handler(Looper.getMainLooper())
    private val planHandler = Handler(Looper.getMainLooper())
    private var planGeneration = 0
    private val preferences by lazy { getSharedPreferences("assistant_places", MODE_PRIVATE) }

    private var speechReady = false
    private var assistantActive = true
    private var isListening = false
    private var isSpeaking = false
    private var pendingAction: (() -> Unit)? = null
    private var pendingContactName: String? = null
    private var waitingForCommand = false
    private var planCancelled = false
    private var planActive = false
    private var activityResumed = false
    private val wakeWord = NexoWakeConfig.WAKE_WORD

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            safeStartWakeService()
            startVoiceRecognition()
        } else {
            assistantActive = true
            statusText.text = "Necesito permiso de micrófono para escucharte."
            Toast.makeText(this, "Necesito permiso de micrófono.", Toast.LENGTH_LONG).show()
        }
    }

    private val contactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val contactName = pendingContactName
        pendingContactName = null
        if (granted && !contactName.isNullOrBlank()) {
            openWhatsAppContact(contactName)
        } else {
            respond("Necesito permiso para leer tus contactos.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        healthText = findViewById(R.id.healthText)
        skillsText = findViewById(R.id.skillsText)
        systemStateText = findViewById(R.id.systemStateText)
        versionText = findViewById(R.id.versionText)
        val versionName = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull().orEmpty()
        versionText.text = "AGENT OS · " + versionName.ifBlank { "3.0" }
        nowRunningCard = findViewById(R.id.nowRunningCard)
        planTitleText = findViewById(R.id.planTitleText)
        planProgressText = findViewById(R.id.planProgressText)
        planStepsText = findViewById(R.id.planStepsText)
        orbView = findViewById(R.id.orbView)
        voiceToggleButton = findViewById(R.id.voiceToggleButton)
        assistantActive = NexoVoiceState.isEnabled(this)
        updateVoiceControl()
        setOrbIdle()
        findViewById<Button>(R.id.aiSettingsButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.intelligenceButton).setOnClickListener {
            startActivity(Intent(this, AgentSettingsActivity::class.java))
        }
        findViewById<Button>(R.id.carModeButton).setOnClickListener {
            activateCarMode()
        }
        voiceToggleButton.setOnClickListener {
            setVoiceActive(!assistantActive, speak = false)
        }
        findViewById<Button>(R.id.historyButton).setOnClickListener {
            startActivity(Intent(this, ActionHistoryActivity::class.java))
        }
        findViewById<Button>(R.id.diagnosticsButton).setOnClickListener {
            startActivity(Intent(this, SystemDiagnosticsActivity::class.java))
        }
        findViewById<Button>(R.id.securityButton).setOnClickListener {
            startActivity(Intent(this, SecurityCenterActivity::class.java))
        }
        findViewById<Button>(R.id.visionButton).setOnClickListener {
            startActivity(Intent(this, VisionActivity::class.java))
        }
        findViewById<Button>(R.id.setupButton).setOnClickListener {
            startActivity(Intent(this, SetupCenterActivity::class.java))
        }
        findViewById<Button>(R.id.modesButton).setOnClickListener {
            startActivity(Intent(this, ModesActivity::class.java))
        }
        findViewById<Button>(R.id.skillsButton).setOnClickListener {
            startActivity(Intent(this, SkillsActivity::class.java))
        }
        findViewById<Button>(R.id.cancelPlanButton).setOnClickListener {
            cancelCurrentPlan()
        }

        findViewById<Button>(R.id.micPermissionButton).setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                safeStartWakeService()
                startVoiceRecognition()
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                handler.postDelayed({
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED &&
                        !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
                    ) {
                        startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:$packageName")
                            )
                        )
                    }
                }, 1200)
            }
        }
        setupSpeechRecognizer()
        textToSpeech = TextToSpeech(this, this)

        if (assistantActive &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        ) {
            safeStartWakeService()
        }

        intent.getStringExtra(AssistantWakeService.EXTRA_VOICE_COMMAND)?.let { command ->
            intent.removeExtra(AssistantWakeService.EXTRA_VOICE_COMMAND)
            handler.postDelayed({ handleCommand(command) }, 500)
        }

        handler.postDelayed({
            if (!isFinishing && NexoOnboardingState.shouldPresent(this)) {
                NexoOnboardingState.markPresented(this)
                startActivity(Intent(this, SetupCenterActivity::class.java))
            }
        }, 700)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(AssistantWakeService.EXTRA_VOICE_COMMAND)?.let { command ->
            intent.removeExtra(AssistantWakeService.EXTRA_VOICE_COMMAND)
            handler.postDelayed({ handleCommand(command) }, 350)
        }
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText.text = "Este teléfono no tiene disponible el reconocimiento de voz."
            assistantActive = false
            return
        }

        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PA")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 900L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 650L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 450L)
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).also { recognizer ->
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    statusText.text = "Te escucho…"
                    setOrbListening()
                }

                override fun onBeginningOfSpeech() {
                    statusText.text = "Escuchando…"
                    setOrbListening()
                }

                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    statusText.text = "Procesando…"
                    setOrbProcessing()
                }

                override fun onError(error: Int) {
                    isListening = false
                    if (!assistantActive || isFinishing || isDestroyed) return

                    when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            assistantActive = false
                            statusText.text = "Necesito permiso de micrófono para escucharte."
                        }
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> scheduleListening(600)
                        else -> scheduleListening(300)
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val spoken = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()

                    if (spoken.isNullOrBlank()) {
                        statusText.text = "Escuchando…"
                        setOrbListening()
                        scheduleListening(220)
                    } else {
                        processRecognizedSpeech(spoken)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                    if (!partial.isNullOrBlank() && waitingForCommand) {
                        statusText.text = "Escuchando: $partial"
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            statusText.text = "Voz no disponible. Activando escucha automática…"
            ensureMicPermissionAndListen()
            return
        }

        var result = textToSpeech.setLanguage(Locale("es", "PA"))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            result = textToSpeech.setLanguage(Locale("es"))
        }

        speechReady = result != TextToSpeech.LANG_MISSING_DATA &&
            result != TextToSpeech.LANG_NOT_SUPPORTED

        selectPreferredVoice()
        textToSpeech.setSpeechRate(1.02f)
        textToSpeech.setPitch(1.05f)
        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
            }

            override fun onDone(utteranceId: String?) {
                runOnUiThread {
                    isSpeaking = false
                    finishSpeechCycle()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                runOnUiThread {
                    isSpeaking = false
                    finishSpeechCycle()
                }
            }
        })

        statusText.text = "Listo. Estoy escuchando."
        ensureMicPermissionAndListen()
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        assistantActive = NexoVoiceState.isEnabled(this)
        updateVoiceControl()
        NexoModeManager.applyWindowProfile(this)

        val micButton = findViewById<Button>(R.id.micPermissionButton)
        val accessButton = findViewById<Button>(R.id.aiSettingsButton)
        micButton.visibility =
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                android.view.View.GONE else android.view.View.VISIBLE
        accessButton.text =
            if (isAccessibilityServiceEnabled()) "Control de apps activado" else "Activar control de apps"

        refreshSystemOverview()

        if (assistantActive &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        ) {
            safeSendWakeServiceAction(AssistantWakeService.ACTION_PAUSE_LISTENING)
        }

        if (assistantActive && !isListening && !isSpeaking && ::statusText.isInitialized) {
            scheduleListening(350)
        }
    }

    private fun refreshSystemOverview() {
        val health = NexoSystemHealth.snapshot(this)

        healthText.text = health.checks
            .filter { it.id in setOf("microphone", "accessibility", "intelligence") }
            .joinToString(" · ") {
                if (it.ready) it.label + " listo" else it.label + " pendiente"
            }

        val mode = NexoModeManager.current(this)
        val enabledSkills = NexoSkillPolicy.enabledNames(this)
        skillsText.text = "Modo actual: " + mode.displayName + " · " +
            enabledSkills.take(3).joinToString(" · ")

        systemStateText.text = "●  " + health.summary
        systemStateText.setTextColor(
            ContextCompat.getColor(
                this,
                when (health.state) {
                    NexoHealthState.READY -> R.color.nexo_success
                    NexoHealthState.DEGRADED -> R.color.nexo_warning
                    NexoHealthState.BLOCKED -> R.color.nexo_error
                }
            )
        )
    }

    override fun onPause() {
        activityResumed = false
        stopListening()
        if (assistantActive && !planActive &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        ) {
            safeSendWakeServiceAction(AssistantWakeService.ACTION_RESUME_LISTENING)
        }
        super.onPause()
    }

    private fun updateVoiceControl() {
        if (!::voiceToggleButton.isInitialized) return
        voiceToggleButton.text = if (assistantActive) {
            "Pausar escucha"
        } else {
            "Reanudar escucha"
        }
    }

    private fun setVoiceActive(enabled: Boolean, speak: Boolean) {
        assistantActive = enabled
        NexoVoiceState.setEnabled(this, enabled)
        updateVoiceControl()

        if (enabled) {
            statusText.text = "NEXO listo. Di “NEXO” para activarme."
            safeStartWakeService()
            scheduleListening(180)
            if (speak) respond("Escucha activada.")
        } else {
            waitingForCommand = false
            stopListening()
            stopService(Intent(this, AssistantWakeService::class.java))
            statusText.text = "Escucha pausada."
            setOrbIdle()
            if (speak && speechReady) {
                textToSpeech.speak(
                    "De acuerdo. La escucha quedó pausada.",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "voice_paused"
                )
            }
        }

        refreshSystemOverview()
    }

    private fun safeStartWakeService() {
        if (!assistantActive) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return

        try {
            val serviceIntent = Intent(this, AssistantWakeService::class.java).apply {
                action = AssistantWakeService.ACTION_START
            }
            ContextCompat.startForegroundService(this, serviceIntent)
        } catch (_: Exception) {
            statusText.text = "NEXO está listo. La escucha en segundo plano se activará cuando Android lo permita."
        }
    }

    private fun safeSendWakeServiceAction(action: String) {
        try {
            val serviceIntent = Intent(this, AssistantWakeService::class.java).apply {
                this.action = action
            }
            startService(serviceIntent)
        } catch (_: Exception) {
            // Evita cerrar la app si Android restringe temporalmente el servicio.
        }
    }

    private fun ensureMicPermissionAndListen() {
        if (!NexoVoiceState.isEnabled(this)) {
            assistantActive = false
            updateVoiceControl()
            stopListening()
            return
        }
        if (!assistantActive || isListening || isSpeaking) return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            safeStartWakeService()
            startVoiceRecognition()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun scheduleListening(delayMs: Long = 450) {
        if (!assistantActive || isFinishing || isDestroyed) return

        if (!activityResumed) {
            if (!planActive) {
                safeSendWakeServiceAction(AssistantWakeService.ACTION_RESUME_LISTENING)
            }
            return
        }

        handler.postDelayed({
            if (activityResumed && !planActive) {
                ensureMicPermissionAndListen()
            }
        }, delayMs)
    }

    private fun startVoiceRecognition() {
        if (!assistantActive || isListening || isSpeaking) return
        val recognizer = speechRecognizer ?: return

        try {
            isListening = true
            statusText.text = "Te escucho…"
            recognizer.startListening(speechIntent)
        } catch (e: Exception) {
            isListening = false
            statusText.text = "Reintentando escucha…"
            scheduleListening(800)
        }
    }

    private fun stopListening() {
        if (isListening) {
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) {
            }
            isListening = false
        }
    }

    private fun processRecognizedSpeech(raw: String) {
        if (!NexoVoiceState.isEnabled(this)) {
            assistantActive = false
            updateVoiceControl()
            stopListening()
            statusText.text = "Escucha pausada."
            setOrbIdle()
            return
        }

        val normalized = normalize(raw)

        if (waitingForCommand) {
            waitingForCommand = false
            val command = normalized
                .trim()
                .trimStart(',', '.', ':', ';', '-', ' ')

            if (command.isBlank()) {
                statusText.text = "No escuché una orden."
                setOrbIdle()
                scheduleListening(250)
                return
            }

            statusText.text = "Entendido…"
            setOrbProcessing()
            handleCommand(command)
            return
        }

        val wake = NexoWakePhrase.extract(normalized)
        if (!wake.found) {
            statusText.text = "Di “NEXO” para activarme."
            setOrbIdle()
            scheduleListening(220)
            return
        }

        playWakeTone()
        setOrbActivated()

        if (wake.command.isBlank()) {
            waitingForCommand = true
            statusText.text = "Sí, dime…"
            setOrbListening()
            scheduleListening(120)

            handler.postDelayed({
                if (waitingForCommand) {
                    waitingForCommand = false
                    statusText.text = "Di “NEXO” para activarme."
                    setOrbIdle()
                    scheduleListening(250)
                }
            }, 6500)
            return
        }

        statusText.text = "Entendido…"
        setOrbProcessing()
        handleCommand(wake.command)
    }

    private fun playWakeTone() {
        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85).apply {
                startTone(ToneGenerator.TONE_PROP_BEEP, 140)
                handler.postDelayed({ release() }, 220)
            }
        } catch (_: Exception) {
            // El tono es una confirmación útil, pero no debe bloquear la escucha.
        }
    }

    private fun handleCommand(raw: String) {
        NexoActionLog.add(this, "Comando de voz", raw)
        val command = normalize(raw)
        val savedPlace = extractSavedPlace(command)
        val youtubeQuery = extractYouTubeQuery(command)
        val spotifyQuery = extractSpotifyQuery(command)
        val contactName = extractContactName(command)
        val destination = extractDestination(command)
        val chatGptRequest = extractChatGptRequest(command)

        when {
            containsAny(command, "modo carro", "activa modo carro", "activar modo carro") -> activateCarMode()
            containsAny(command, "modo normal", "desactiva modo carro", "salir de modo carro") -> deactivateCarMode()
            containsAny(command, "abre skills", "skills de nexo", "capacidades de nexo", "habilidades de nexo") -> {
                respondAndThen("Abriendo Skills.") {
                    startActivity(Intent(this, SkillsActivity::class.java))
                }
            }
            containsAny(command, "abre modos", "modos de nexo", "modes", "perfiles de nexo") -> {
                respondAndThen("Abriendo Modes.") {
                    startActivity(Intent(this, ModesActivity::class.java))
                }
            }
            containsAny(command, "configurar nexo", "setup", "setup center", "preparar nexo") -> {
                respondAndThen("Abriendo Setup Center.") {
                    startActivity(Intent(this, SetupCenterActivity::class.java))
                }
            }
            containsAny(command, "que ves", "qué ves", "mira esto", "analiza esto", "usa vision") -> {
                if (!requireSkill("vision")) return
                respondAndThen("Voy a mirar.") {
                    startActivity(
                        Intent(this, VisionActivity::class.java).apply {
                            putExtra(VisionActivity.EXTRA_AUTO_ANALYZE, true)
                        }
                    )
                }
            }
            containsAny(command, "abre vision", "vision", "abre la camara", "camara de nexo") -> {
                if (!requireSkill("vision")) return
                respondAndThen("Abriendo Vision.") {
                    startActivity(Intent(this, VisionActivity::class.java))
                }
            }
            containsAny(command, "historial", "actividad de nexo", "que hiciste") -> {
                respondAndThen("Abriendo mi actividad reciente.") {
                    startActivity(Intent(this, ActionHistoryActivity::class.java))
                }
            }
            containsAny(command, "configura tu inteligencia", "configurar inteligencia", "inteligencia de nexo") -> {
                respondAndThen("Abriendo la configuración de inteligencia.") {
                    startActivity(Intent(this, AgentSettingsActivity::class.java))
                }
            }
            containsAny(command, "deja de escuchar", "detente", "pausa asistente", "para de escuchar") -> {
                setVoiceActive(false, speak = true)
            }
            savedPlace != null -> savePlace(savedPlace.first, savedPlace.second)
            chatGptRequest != null -> automateChatGpt(chatGptRequest.first, chatGptRequest.second)
            spotifyQuery != null -> playMediaSearch("com.spotify.music", "Spotify", spotifyQuery)
            youtubeQuery != null -> playMediaSearch("com.google.android.youtube", "YouTube", youtubeQuery)
            command.contains("whatsapp") && contactName != null -> requestContactAndOpen(contactName)
            command.contains("whatsapp") && containsAny(command, "abre", "abrir", "abreme", "abra", "inicia", "lanza") -> openPackage("com.whatsapp", "WhatsApp")
            destination != null -> openWazeDestination(resolveDestination(destination))
            command.contains("waze") && containsAny(command, "abre", "abrir", "abreme", "abra", "inicia", "lanza") -> openPackage("com.waze", "Waze")
            command.contains("youtube") && containsAny(command, "abre", "abrir", "abreme", "abra", "inicia", "lanza") -> openPackage("com.google.android.youtube", "YouTube")
            containsAny(command, "abre disney", "abrir disney", "disney plus", "disney+") -> openPackage("com.disney.disneyplus", "Disney Plus")
            command.contains("spotify") && containsAny(command, "abre", "abrir", "abreme", "abra", "inicia", "lanza") -> openPackage("com.spotify.music", "Spotify")
            containsAny(command, "abre maps", "abre mapas", "google maps", "mapas") -> openPackage("com.google.android.apps.maps", "Google Maps")
            containsAny(command, "abre gmail", "abrir gmail", "gmail") -> openPackage("com.google.android.gm", "Gmail")
            containsAny(command, "abre chrome", "abrir chrome", "chrome") -> openPackage("com.android.chrome", "Chrome")
            containsAny(command, "abre configuracion", "abre ajustes", "configuracion", "ajustes") -> {
                respondAndThen("Abriendo Ajustes") { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            }
            containsAny(command, "abre calculadora", "abrir calculadora", "calculadora") -> openCalculator()
            containsAny(command, "como estas", "como te va") -> respond("Muy bien, gracias. Estoy listo para ayudarte.")
            containsAny(command, "que puedes hacer", "ayuda", "comandos") -> respond(
                "Puedo abrir aplicaciones, usar Waze, Spotify, YouTube y ejecutar comandos de voz directamente."
            )
            else -> runAgentPlanner(raw)
        }
    }

    private fun extractChatGptRequest(command: String): Pair<String, Boolean>? {
        if (!command.contains("chatgpt") && !command.contains("chat gpt")) return null

        val wantsNewChat = containsAny(
            command,
            "nuevo chat", "nueva conversacion", "abre un chat nuevo",
            "crea un chat", "crear un chat"
        )

        val markers = listOf(
            "preguntale a chatgpt ", "pregunta a chatgpt ",
            "dile a chatgpt ", "escribe en chatgpt ",
            "escribeme en chatgpt ", "consulta en chatgpt ",
            "chatgpt preguntale ", "chatgpt pregunta ",
            "chatgpt escribe ", "chatgpt dile "
        )

        var text = valueAfterMarker(command, markers)

        if (text == null && wantsNewChat) {
            text = command
                .replace("abre chatgpt", "")
                .replace("abrir chatgpt", "")
                .replace("abre chat gpt", "")
                .replace("abrir chat gpt", "")
                .replace("nuevo chat", "")
                .replace("nueva conversacion", "")
                .replace("crea un chat", "")
                .replace("crear un chat", "")
                .replace("y preguntale", "")
                .replace("y pregunta", "")
                .replace("y escribe", "")
                .trim()
                .takeIf { it.isNotBlank() }
        }

        return text?.let { it to wantsNewChat }
    }

    private fun automateChatGpt(text: String, newChat: Boolean) {
        if (!requireSkill("chatgpt")) return
        if (!isAccessibilityServiceEnabled()) {
            respondAndThen(
                "Para controlar aplicaciones necesito que actives el acceso de NEXO una sola vez. Te llevo a la pantalla para habilitarlo."
            ) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            return
        }

        val launchIntent = packageManager.getLaunchIntentForPackage("com.openai.chatgpt")
        if (launchIntent == null) {
            respond("ChatGPT no está instalado.")
            return
        }

        MiaAccessibilityService.queueChatGptRequest(this, text, newChat)
        respondAndThen("Listo. Voy a escribirlo en ChatGPT.") {
            startActivity(launchIntent)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val component = "${packageName}/${MiaAccessibilityService::class.java.name}"
        return enabledServices.split(':').any { it.equals(component, ignoreCase = true) }
    }

    private fun delegateToChatGpt(raw: String) {
        if (!isAccessibilityServiceEnabled()) {
            respondAndThen(
                "Para usar ChatGPT como mi inteligencia necesito que actives el acceso de NEXO una sola vez."
            ) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            return
        }

        val launchIntent = packageManager.getLaunchIntentForPackage("com.openai.chatgpt")
        if (launchIntent == null) {
            respond("ChatGPT no está instalado.")
            return
        }

        MiaAccessibilityService.queueChatGptRequest(this, raw, true)
        respondAndThen("Voy a consultarlo en ChatGPT.") {
            startActivity(launchIntent)
        }
    }

    private fun cancelCurrentPlan() {
        planCancelled = true
        planActive = false
        planGeneration++
        planHandler.removeCallbacksAndMessages(null)
        nowRunningCard.visibility = android.view.View.GONE
        NexoActionLog.add(this, "Plan detenido", "El usuario detuvo la ejecución")
        statusText.text = "Plan detenido."
        setOrbIdle()
        scheduleListening(400)
    }

    private fun actionLabel(decision: MiaAgentDecision): String =
        when (decision.tool) {
            "open_app" -> "Abrir " + decision.app.ifBlank { decision.text }
            "waze" -> "Navegar a " + decision.target.ifBlank { decision.text }
            "spotify" -> "Spotify · " + decision.text.ifBlank { decision.target }
            "youtube" -> "YouTube · " + decision.text.ifBlank { decision.target }
            "chatgpt" -> "Consultar ChatGPT"
            "vision" -> "Abrir Vision"
            "tap_text" -> "Tocar " + decision.target.ifBlank { decision.text }
            "type_text" -> "Escribir texto"
            "back" -> "Volver"
            "home" -> "Ir al inicio"
            "set_volume" -> "Volumen " + decision.target + "%"
            "set_brightness" -> "Brillo " + decision.target + "%"
            "car_mode" -> if (decision.target.equals("off", true)) "Desactivar modo carro" else "Activar modo carro"
            "answer" -> "Responder"
            "clarify" -> "Pedir aclaración"
            else -> decision.tool
        }

    private fun renderPlan(plan: NexoAgentPlan, currentIndex: Int) {
        nowRunningCard.visibility = android.view.View.VISIBLE
        planTitleText.text = if (plan.actions.size == 1) "Ejecutando acción" else "Ejecutando " + plan.actions.size + " acciones"
        planProgressText.text = ((currentIndex + 1).coerceAtMost(plan.actions.size)).toString() + " / " + plan.actions.size
        planStepsText.text = buildString {
            plan.actions.forEachIndexed { index, action ->
                val marker = when {
                    index < currentIndex -> "✓"
                    index == currentIndex -> "→"
                    else -> "•"
                }
                append(marker).append("  ").append(actionLabel(action))
                if (index < plan.actions.lastIndex) append("\n")
            }
        }
    }

    private fun finishPlanSurface() {
        planActive = false
        setOrbSuccess()
        planProgressText.text = "Completado"
        planHandler.postDelayed({
            if (::nowRunningCard.isInitialized) nowRunningCard.visibility = android.view.View.GONE
        }, 1600)
    }

    private fun runAgentPlanner(raw: String) {
        val planner = MiaAgentPlanner(this)
        if (!planner.isConfigured()) {
            respond("Aún no tengo configurada mi inteligencia. Abre Inteligencia de NEXO y agrega tu clave de API.")
            return
        }

        statusText.text = "Pensando…"
        setOrbProcessing()
        stopListening()

        Thread {
            val result = planner.plan(raw)
            runOnUiThread {
                result.onSuccess {
                    NexoRuntimeState.clearIssue(this, "Agent Brain")
                    executeAgentPlan(it)
                }.onFailure {
                    val guidance = NexoRecoveryPolicy.fromThrowable("Agent Brain", it)
                    NexoRuntimeState.markIssue(this, "Agent Brain", guidance.logMessage)
                    NexoActionLog.add(this, "Plan IA", guidance.logMessage, false)
                    setOrbError()
                    refreshSystemOverview()
                    respond(guidance.userMessage)
                }
            }
        }.start()
    }

    private fun executeAgentPlan(plan: NexoAgentPlan) {
        if (plan.actions.isEmpty()) {
            respond("No encontré acciones para ejecutar.")
            return
        }

        NexoActionLog.add(this, "Plan IA", plan.actions.joinToString(" → ") { it.tool })
        planCancelled = false
        planActive = true
        val generation = ++planGeneration
        planHandler.removeCallbacksAndMessages(null)
        statusText.text = "Ejecutando plan…"
        renderPlan(plan, 0)
        executePlanStep(plan, 0, generation)
    }


    private fun requestSafetyConfirmation(
        decision: MiaAgentDecision,
        onApproved: () -> Unit,
        onRejected: () -> Unit = {}
    ) {
        val safety = NexoSafetyPolicy.evaluate(decision)
        when (safety.level) {
            NexoRiskLevel.SAFE -> onApproved()

            NexoRiskLevel.BLOCK -> {
                NexoActionLog.add(this, "Acción bloqueada", safety.reason, false)
                setOrbError()
                respond("No voy a ejecutar esa acción porque puede afectar de forma importante al dispositivo.")
                onRejected()
            }

            NexoRiskLevel.CONFIRM -> {
                NexoActionLog.add(this, "Confirmación requerida", actionLabel(decision))
                stopListening()
                AlertDialog.Builder(this)
                    .setTitle("Confirmar acción")
                    .setMessage(
                        actionLabel(decision) + "\n\n" +
                        safety.reason + "\n\n" +
                        "NEXO solo continuará si la autorizas."
                    )
                    .setNegativeButton("Cancelar") { _, _ ->
                        NexoActionLog.add(this, "Acción cancelada", actionLabel(decision), false)
                        setOrbIdle()
                        onRejected()
                        scheduleListening(350)
                    }
                    .setPositiveButton("Autorizar") { _, _ ->
                        NexoActionLog.add(this, "Acción autorizada", actionLabel(decision))
                        onApproved()
                    }
                    .setOnCancelListener {
                        onRejected()
                        scheduleListening(350)
                    }
                    .show()
            }
        }
    }

    private fun failPlanExecution(message: String, logDetail: String) {
        planCancelled = true
        planActive = false
        planGeneration++
        planHandler.removeCallbacksAndMessages(null)
        nowRunningCard.visibility = android.view.View.GONE
        NexoActionLog.add(this, "Plan interrumpido", logDetail, false)
        setOrbError()
        respond(message)
    }

    private fun awaitAccessibilityResult(
        actionId: String,
        generation: Int,
        timeoutMs: Long = 12_000L,
        onSuccess: () -> Unit
    ) {
        val startedAt = System.currentTimeMillis()

        fun poll() {
            if (planCancelled || generation != planGeneration) return

            val result = MiaAccessibilityService.consumeResult(this, actionId)
            if (result != null) {
                if (result.success) {
                    onSuccess()
                } else {
                    failPlanExecution(
                        "No pude completar una acción de pantalla. NEXO detuvo el plan para no continuar con un estado incierto.",
                        result.detail
                    )
                }
                return
            }

            if (System.currentTimeMillis() - startedAt >= timeoutMs) {
                failPlanExecution(
                    "La acción de pantalla tardó demasiado. Detuve el plan para evitar ejecutar pasos fuera de orden.",
                    "Timeout esperando Accessibility"
                )
                return
            }

            planHandler.postDelayed({ poll() }, 220)
        }

        poll()
    }

    private fun expectedPackageForPlanAction(
        plan: NexoAgentPlan,
        index: Int,
        decision: MiaAgentDecision
    ): String? {
        if (decision.app.isNotBlank()) {
            NexoSkillRegistry.resolvePackageName(this, decision.app)?.let { return it }
        }

        if (index <= 0) return null
        val previous = plan.actions[index - 1]

        return when (previous.tool) {
            "open_app" -> NexoSkillRegistry.resolvePackageName(
                this,
                previous.app.ifBlank { previous.text }
            )
            "chatgpt" -> "com.openai.chatgpt"
            "spotify" -> "com.spotify.music"
            "youtube" -> "com.google.android.youtube"
            else -> null
        }
    }

    private fun executePlanStep(plan: NexoAgentPlan, index: Int, generation: Int = planGeneration) {
        if (planCancelled || generation != planGeneration) return

        if (index >= plan.actions.size) {
            finishPlanSurface()
            respond(plan.speech.ifBlank { "Listo. Terminé el plan." })
            return
        }

        renderPlan(plan, index)
        val decision = plan.actions[index]

        if (!NexoSkillPolicy.isToolEnabled(this, decision.tool)) {
            val skill = NexoSkillPolicy.skillForTool(decision.tool)
            val skillName = skill?.name ?: decision.tool
            failPlanExecution(
                "La capacidad " + skillName + " está desactivada. Puedes habilitarla desde Skills.",
                "Skill bloqueada: " + skillName
            )
            return
        }
        val continuePlan = {
            if (!planCancelled && generation == planGeneration) {
                planHandler.postDelayed(
                    { executePlanStep(plan, index + 1, generation) },
                    650
                )
            }
        }

        when (decision.tool) {
            "open_app" -> {
                val target = decision.app.ifBlank { decision.text }
                val launch = NexoSkillRegistry.resolveLaunchIntent(this, target)

                if (launch != null) {
                    NexoActionLog.add(this, "Plan: abrir app", target)
                    startActivity(launch)
                } else {
                    NexoActionLog.add(this, "Plan: abrir app", "No encontré " + target, false)
                }
                continuePlan()
            }

            "waze" -> {
                val destination = resolveDestination(decision.target.ifBlank { decision.text })
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://waze.com/ul?q=" + Uri.encode(destination) + "&navigate=yes")
                ).apply { setPackage("com.waze") }
                if (intent.resolveActivity(packageManager) != null) {
                    NexoActionLog.add(this, "Plan: Waze", destination)
                    startActivity(intent)
                } else {
                    NexoActionLog.add(this, "Plan: Waze", "Waze no disponible", false)
                }
                continuePlan()
            }

            "spotify", "youtube" -> {
                val query = decision.text.ifBlank { decision.target }
                val packageName = if (decision.tool == "spotify") "com.spotify.music" else "com.google.android.youtube"
                val displayName = if (decision.tool == "spotify") "Spotify" else "YouTube"

                val direct = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                    setPackage(packageName)
                    putExtra(SearchManager.QUERY, query)
                    putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                val fallback = if (decision.tool == "spotify") {
                    Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:" + Uri.encode(query))).apply {
                        setPackage(packageName)
                    }
                } else {
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))
                    ).apply { setPackage(packageName) }
                }

                val chosen = if (direct.resolveActivity(packageManager) != null) direct else fallback
                if (chosen.resolveActivity(packageManager) != null) {
                    NexoActionLog.add(this, "Plan: " + displayName, query)
                    startActivity(chosen)
                } else {
                    NexoActionLog.add(this, "Plan: " + displayName, "Aplicación no disponible", false)
                }
                continuePlan()
            }

            "vision" -> {
                if (index != plan.actions.lastIndex) {
                    failPlanExecution(
                        "Vision debe cerrar este plan antes de usar su resultado en otra acción. Detuve los pasos siguientes para mantener el control.",
                        "Vision no es todavía una fuente encadenable dentro del mismo plan"
                    )
                    return
                }

                NexoActionLog.add(this, "Plan: Vision", "Capturar y analizar")
                finishPlanSurface()
                startActivity(
                    Intent(this, VisionActivity::class.java).apply {
                        putExtra(VisionActivity.EXTRA_AUTO_ANALYZE, true)
                    }
                )
            }

            "chatgpt" -> {
                if (!isAccessibilityServiceEnabled()) {
                    NexoActionLog.add(this, "Plan: ChatGPT", "Control de apps desactivado", false)
                    respondAndThen("Para continuar necesito que actives el control de aplicaciones de NEXO.") {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                    return
                }
                val launch = packageManager.getLaunchIntentForPackage("com.openai.chatgpt")
                if (launch != null) {
                    val actionId = MiaAccessibilityService.queueChatGptRequest(
                        this,
                        decision.text,
                        decision.newChat
                    )
                    NexoActionLog.add(this, "Plan: ChatGPT", decision.text)
                    startActivity(launch)
                    awaitAccessibilityResult(actionId, generation, 18_000L) {
                        continuePlan()
                    }
                } else {
                    failPlanExecution(
                        "ChatGPT no está instalado, así que detuve el plan.",
                        "ChatGPT no está instalado"
                    )
                }
            }

            "tap_text", "type_text", "back", "home" -> {
                if (!isAccessibilityServiceEnabled()) {
                    NexoActionLog.add(this, "Plan: " + decision.tool, "Control de apps desactivado", false)
                    respondAndThen("Para continuar necesito que actives el control de aplicaciones de NEXO.") {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                    return
                }

                requestSafetyConfirmation(
                    decision = decision,
                    onApproved = {
                        val expectedPackage = expectedPackageForPlanAction(
                            plan,
                            index,
                            decision
                        )
                        val actionId = MiaAccessibilityService.queueGenericAction(
                            this,
                            decision.tool,
                            decision.target.ifBlank { decision.text },
                            expectedPackage ?: "*"
                        )
                        NexoActionLog.add(
                            this,
                            "Plan: " + decision.tool,
                            decision.target.ifBlank { decision.text }
                        )
                        awaitAccessibilityResult(actionId, generation) {
                            continuePlan()
                        }
                    },
                    onRejected = {
                        planCancelled = true
                        planActive = false
                        nowRunningCard.visibility = android.view.View.GONE
                    }
                )
            }

            "set_volume" -> {
                val percent = decision.target.toIntOrNull()?.coerceIn(0, 100) ?: 50
                val audio = getSystemService(AUDIO_SERVICE) as AudioManager
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val value = ((percent / 100f) * max).toInt().coerceIn(0, max)
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
                NexoActionLog.add(this, "Plan: volumen", percent.toString() + "%")
                continuePlan()
            }

            "set_brightness" -> {
                val percent = decision.target.toIntOrNull()?.coerceIn(0, 100) ?: 50
                window.attributes = window.attributes.apply {
                    screenBrightness = (percent / 100f).coerceIn(0.01f, 1f)
                }
                NexoActionLog.add(this, "Plan: brillo", percent.toString() + "%")
                continuePlan()
            }

            "car_mode" -> {
                if (decision.target.equals("off", ignoreCase = true)) {
                    deactivateCarMode(false)
                } else {
                    activateCarMode(false)
                }
                continuePlan()
            }

            "answer" -> {
                planActive = false
                NexoActionLog.add(this, "Plan: respuesta", decision.text)
                respond(decision.text.ifBlank { plan.speech.ifBlank { "Listo." } })
            }

            "clarify" -> {
                planActive = false
                NexoActionLog.add(this, "Plan: aclaración", decision.text)
                respond(decision.text.ifBlank { "Necesito un dato más para continuar." })
            }

            else -> {
                NexoActionLog.add(this, "Plan", "Herramienta desconocida: " + decision.tool, false)
                continuePlan()
            }
        }
    }

    private fun containsAny(text: String, vararg options: String): Boolean =
        options.any { text.contains(it) }

    private fun requireSkill(skillId: String): Boolean {
        if (NexoSkillPolicy.isEnabled(this, skillId)) return true

        val skillName = NexoSkillPolicy.definitions
            .firstOrNull { it.id == skillId }
            ?.name ?: skillId

        NexoActionLog.add(this, "Skill bloqueada", skillName, false)
        setOrbError()
        respond("La capacidad " + skillName + " está desactivada. Puedes habilitarla desde Skills.")
        return false
    }

    private fun openPackage(packageName: String, displayName: String) {
        if (!requireSkill("apps")) return
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            NexoActionLog.add(this, "Abrir aplicación", displayName)
            respondAndThen("Abriendo $displayName") { startActivity(launchIntent) }
        } else {
            NexoActionLog.add(this, "Abrir aplicación", "$displayName no está instalado", false)
            respond("$displayName no está instalado.")
        }
    }

    private fun extractContactName(command: String): String? =
        valueAfterMarker(command, listOf("al contacto ", "contacto "))

    private fun extractDestination(command: String): String? = valueAfterMarker(
        command,
        listOf(
            "waze para ir a ", "waze para ir al ", "quiero ir a ", "quiero ir al ",
            "llevame a ", "llevame al ", "navega a ", "navega al ",
            "vamos a ", "vamos al ", "ruta hacia ", "ruta al ", "ir a ", "ir al "
        )
    )

    private fun extractYouTubeQuery(command: String): String? {
        val direct = valueAfterMarker(
            command,
            listOf(
                "reproduce en youtube ", "reproducir en youtube ", "pon en youtube ",
                "buscame en youtube ", "busca en youtube ", "buscar en youtube ",
                "youtube reproduce ", "youtube pon ", "youtube busca ", "youtube buscame "
            )
        )
        if (direct != null) return direct

        return valueBeforeSuffix(
            command,
            listOf(" en youtube"),
            listOf("reproduce ", "reproducir ", "pon ", "quiero escuchar ", "escuchar ")
        )
    }

    private fun extractSpotifyQuery(command: String): String? {
        val direct = valueAfterMarker(
            command,
            listOf(
                "reproduce en spotify ", "reproducir en spotify ", "pon en spotify ",
                "buscame en spotify ", "busca en spotify ", "buscar en spotify ",
                "spotify reproduce ", "spotify pon ", "spotify busca ", "spotify buscame "
            )
        )
        if (direct != null) return direct

        return valueBeforeSuffix(
            command,
            listOf(" en spotify"),
            listOf("reproduce ", "reproducir ", "pon ", "quiero escuchar ", "escuchar ")
        )
    }

    private fun extractSavedPlace(command: String): Pair<String, String>? {
        val commands = listOf(
            "guarda mi trabajo como " to "work",
            "guarda mi trabajo en " to "work",
            "mi trabajo es " to "work",
            "guarda mi casa como " to "home",
            "guarda mi casa en " to "home",
            "mi casa es " to "home"
        )
        return commands.firstNotNullOfOrNull { (marker, key) ->
            command.substringAfter(marker, "").trim().takeIf { it.isNotBlank() }?.let { key to it }
        }
    }

    private fun valueAfterMarker(command: String, markers: List<String>): String? =
        markers.firstNotNullOfOrNull { marker ->
            command.substringAfter(marker, "").trim().takeIf { it.isNotBlank() }
        }

    private fun valueBeforeSuffix(command: String, suffixes: List<String>, prefixes: List<String>): String? {
        for (suffix in suffixes) {
            if (!command.endsWith(suffix)) continue
            val base = command.removeSuffix(suffix).trim()
            for (prefix in prefixes) {
                if (base.startsWith(prefix)) {
                    return base.removePrefix(prefix).trim().takeIf { it.isNotBlank() }
                }
            }
        }
        return null
    }

    private fun savePlace(key: String, address: String) {
        preferences.edit().putString(key, address).apply()
        val name = if (key == "work") "trabajo" else "casa"
        respond("Listo. Guardé $address como tu $name.")
    }

    private fun resolveDestination(destination: String): String {
        val clean = destination.trim()
        return when (clean) {
            "mi trabajo", "trabajo" -> preferences.getString("work", null) ?: clean
            "mi casa", "casa" -> preferences.getString("home", null) ?: clean
            "aeropuerto", "el aeropuerto", "aeropuerto de tocumen", "tocumen",
            "aeropuerto internacional de tocumen" -> "Aeropuerto Internacional de Tocumen, Panamá"
            else -> clean
        }
    }

    private fun playMediaSearch(packageName: String, displayName: String, query: String) {
        if (!requireSkill("media")) return
        if (packageManager.getLaunchIntentForPackage(packageName) == null) {
            respond("$displayName no está instalado.")
            return
        }

        val playIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            setPackage(packageName)
            putExtra(SearchManager.QUERY, query)
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val canPlayDirectly = playIntent.resolveActivity(packageManager) != null
        if (canPlayDirectly) {
            respondAndThen("Reproduciendo $query en $displayName") {
                startActivity(playIntent)
            }
            return
        }

        val fallbackIntent = when (packageName) {
            "com.spotify.music" -> Intent(
                Intent.ACTION_VIEW,
                Uri.parse("spotify:search:${Uri.encode(query)}")
            ).apply { setPackage(packageName) }

            else -> Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
            ).apply { setPackage(packageName) }
        }

        respondAndThen("Buscando $query en $displayName") {
            startActivity(fallbackIntent)
        }
    }

    private fun requestContactAndOpen(contactName: String) {
        if (!requireSkill("messaging")) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            openWhatsAppContact(contactName)
        } else {
            pendingContactName = contactName
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun openWhatsAppContact(contactName: String) {
        if (packageManager.getLaunchIntentForPackage("com.whatsapp") == null) {
            respond("WhatsApp no está instalado.")
            return
        }
        val phoneNumber = findPhoneNumber(contactName)
        if (phoneNumber == null) {
            respond("No encontré el contacto $contactName.")
            return
        }
        val digits = phoneNumber.filter(Char::isDigit).let {
            if (it.length == 8) "507$it" else it
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")).apply {
            setPackage("com.whatsapp")
        }
        respondAndThen("Abriendo el chat de $contactName en WhatsApp") { startActivity(intent) }
    }

    private fun findPhoneNumber(contactName: String): String? {
        val target = normalize(contactName)
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        var bestMatch: String? = null
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val savedName = cursor.getString(nameIndex) ?: continue
                val normalizedName = normalize(savedName)
                if (normalizedName == target) return cursor.getString(numberIndex)
                if (bestMatch == null && normalizedName.contains(target)) {
                    bestMatch = cursor.getString(numberIndex)
                }
            }
        }
        return bestMatch
    }

    private fun openWazeDestination(destination: String) {
        if (!requireSkill("navigation")) return
        if ((destination == "mi trabajo" || destination == "trabajo") && !preferences.contains("work")) {
            respond("Todavía no sé dónde queda tu trabajo. Dime: guarda mi trabajo como, y luego la dirección.")
            return
        }
        if ((destination == "mi casa" || destination == "casa") && !preferences.contains("home")) {
            respond("Todavía no sé dónde queda tu casa. Dime: guarda mi casa como, y luego la dirección.")
            return
        }
        if (packageManager.getLaunchIntentForPackage("com.waze") == null) {
            respond("Waze no está instalado.")
            return
        }
        val uri = Uri.parse("https://waze.com/ul?q=${Uri.encode(destination)}&navigate=yes")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.waze") }
        respondAndThen("Abriendo Waze para ir a $destination") { startActivity(intent) }
    }

    private fun normalize(value: String): String = value.lowercase(Locale.getDefault())
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")

    private fun selectPreferredVoice() {
        val voices = textToSpeech.voices ?: return
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
        if (preferred != null) {
            textToSpeech.voice = preferred
        }
    }

    private fun respond(message: String, listenAgain: Boolean = true) {
        stopListening()
        statusText.text = message
        setOrbProcessing()
        if (!listenAgain) assistantActive = false

        if (speechReady) {
            textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "response_${System.currentTimeMillis()}")
        } else if (listenAgain) {
            scheduleListening()
        }
    }

    private fun respondAndThen(message: String, action: () -> Unit) {
        stopListening()
        statusText.text = message

        if (!speechReady) {
            action()
            scheduleListening(900)
            return
        }

        pendingAction = action
        textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "action_${System.currentTimeMillis()}")
    }

    private fun finishSpeechCycle() {
        val action = pendingAction
        pendingAction = null

        if (!assistantActive) {
            waitingForCommand = false
            statusText.text = "Escucha pausada."
            setOrbIdle()
            return
        }

        if (action != null) {
            action()
            waitingForCommand = false
            scheduleListening(900)
        } else {
            if (waitingForCommand) {
                statusText.text = "Te escucho…"
                scheduleListening(80)
            } else {
                statusText.text = "Escuchando…"
                setOrbIdle()
                scheduleListening(350)
            }
        }
    }

    private fun makeOrbDrawable(alpha: Int, colorRes: Int): GradientDrawable {
        val base = ContextCompat.getColor(this, colorRes)
        val fill = android.graphics.Color.argb(
            alpha,
            android.graphics.Color.red(base),
            android.graphics.Color.green(base),
            android.graphics.Color.blue(base)
        )
        val stroke = android.graphics.Color.argb(
            150,
            android.graphics.Color.red(base),
            android.graphics.Color.green(base),
            android.graphics.Color.blue(base)
        )
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
            setStroke(3, stroke)
        }
    }

    private fun setOrbIdle() {
        if (!::orbView.isInitialized) return
        (orbView.tag as? ObjectAnimator)?.cancel()
        orbView.animate().cancel()
        orbView.background = makeOrbDrawable(68, R.color.nexo_text_muted)
        orbView.scaleX = 0.72f
        orbView.scaleY = 0.72f
        orbView.alpha = 0.76f
    }

    private fun setOrbActivated() {
        if (!::orbView.isInitialized) return
        (orbView.tag as? ObjectAnimator)?.cancel()
        orbView.animate().cancel()
        orbView.background = makeOrbDrawable(160, R.color.nexo_accent)
        orbView.animate()
            .scaleX(1.22f)
            .scaleY(1.22f)
            .alpha(1f)
            .setDuration(180)
            .start()
    }

    private fun setOrbListening() {
        if (!::orbView.isInitialized) return
        (orbView.tag as? ObjectAnimator)?.cancel()
        orbView.animate().cancel()
        orbView.background = makeOrbDrawable(145, R.color.nexo_accent)
        orbView.scaleX = 1.0f
        orbView.scaleY = 1.0f
        orbView.alpha = 1f

        val pulse = ObjectAnimator.ofPropertyValuesHolder(
            orbView,
            PropertyValuesHolder.ofFloat(android.view.View.SCALE_X, 1.0f, 1.18f),
            PropertyValuesHolder.ofFloat(android.view.View.SCALE_Y, 1.0f, 1.18f),
            PropertyValuesHolder.ofFloat(android.view.View.ALPHA, 0.82f, 1.0f)
        ).apply {
            duration = 720
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        }
        orbView.tag = pulse
        pulse.start()
    }

    private fun setOrbProcessing() {
        if (!::orbView.isInitialized) return
        (orbView.tag as? ObjectAnimator)?.cancel()
        orbView.background = makeOrbDrawable(115, R.color.nexo_warning)
        orbView.scaleX = 0.95f
        orbView.scaleY = 0.95f
        orbView.alpha = 0.94f

        ObjectAnimator.ofFloat(orbView, android.view.View.ROTATION, 0f, 360f).apply {
            duration = 1200
            repeatCount = ObjectAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            orbView.tag = this
            start()
        }
    }

    private fun setOrbSuccess() {
        if (!::orbView.isInitialized) return
        (orbView.tag as? ObjectAnimator)?.cancel()
        orbView.animate().cancel()
        orbView.background = makeOrbDrawable(150, R.color.nexo_success)
        orbView.animate().scaleX(1.08f).scaleY(1.08f).alpha(1f).setDuration(220).start()
    }

    private fun setOrbError() {
        if (!::orbView.isInitialized) return
        (orbView.tag as? ObjectAnimator)?.cancel()
        orbView.animate().cancel()
        orbView.background = makeOrbDrawable(150, R.color.nexo_error)
        orbView.animate().scaleX(1.04f).scaleY(1.04f).alpha(1f).setDuration(180).start()
    }

    private fun activateCarMode(speak: Boolean = true) {
        if (!NexoSkillPolicy.isEnabled(this, "modes")) {
            if (speak) requireSkill("modes")
            return
        }
        NexoModeManager.set(this, NexoMode.CAR)
        NexoModeManager.applyWindowProfile(this, NexoMode.CAR)
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (max * 0.65f).toInt().coerceAtLeast(1)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        NexoActionLog.add(this, "Modo carro", "Pantalla activa, brillo alto y audio preparado")
        if (speak) respond("Modo carro activado. Mantendré la pantalla encendida y el audio preparado.")
    }

    private fun deactivateCarMode(speak: Boolean = true) {
        NexoModeManager.set(this, NexoMode.NORMAL)
        NexoModeManager.applyWindowProfile(this, NexoMode.NORMAL)
        NexoActionLog.add(this, "Modo carro", "Desactivado")
        if (speak) respond("Modo carro desactivado.")
    }

    private fun openCalculator() {
        val calculatorPackages = listOf(
            "com.google.android.calculator",
            "com.sec.android.app.popupcalculator",
            "com.android.calculator2",
            "com.huawei.calculator"
        )
        val intent = calculatorPackages.firstNotNullOfOrNull {
            packageManager.getLaunchIntentForPackage(it)
        }
        if (intent != null) {
            respondAndThen("Abriendo Calculadora") { startActivity(intent) }
        } else {
            respond("No encontré la calculadora instalada.")
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        planHandler.removeCallbacksAndMessages(null)
        speechRecognizer?.destroy()
        speechRecognizer = null

        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        super.onDestroy()
    }
}
