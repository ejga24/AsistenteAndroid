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
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var statusText: TextView
    private lateinit var textToSpeech: TextToSpeech
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var speechIntent: Intent

    private val handler = Handler(Looper.getMainLooper())
    private val preferences by lazy { getSharedPreferences("assistant_places", MODE_PRIVATE) }

    private var speechReady = false
    private var assistantActive = true
    private var isListening = false
    private var isSpeaking = false
    private var pendingAction: (() -> Unit)? = null
    private var pendingContactName: String? = null
    private var waitingForCommand = false
    private val wakeWord = "mia"

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceRecognition()
        } else {
            assistantActive = false
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
        setupSpeechRecognizer()
        textToSpeech = TextToSpeech(this, this)

        val serviceIntent = Intent(this, AssistantWakeService::class.java).apply {
            action = AssistantWakeService.ACTION_START
        }
        ContextCompat.startForegroundService(this, serviceIntent)

        intent.getStringExtra(AssistantWakeService.EXTRA_VOICE_COMMAND)?.let { command ->
            intent.removeExtra(AssistantWakeService.EXTRA_VOICE_COMMAND)
            handler.postDelayed({ handleCommand(command) }, 500)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return
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
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).also { recognizer ->
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    statusText.text = "Te escucho…"
                }

                override fun onBeginningOfSpeech() {
                    statusText.text = "Escuchando…"
                }

                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    statusText.text = "Procesando…"
                }

                override fun onError(error: Int) {
                    isListening = false
                    if (!assistantActive || isFinishing || isDestroyed) return

                    when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            assistantActive = false
                            statusText.text = "Necesito permiso de micrófono para escucharte."
                        }
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> scheduleListening(900)
                        else -> scheduleListening(500)
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val spoken = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()

                    if (spoken.isNullOrBlank()) {
                        statusText.text = if (waitingForCommand) "No escuché el comando. Di Mía para intentarlo de nuevo." else "Di “Mía” para activarme."
                        waitingForCommand = false
                        scheduleListening(450)
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

        statusText.text = "Listo. Di “Mía” para activarme."
        ensureMicPermissionAndListen()
    }

    override fun onResume() {
        super.onResume()
        val serviceIntent = Intent(this, AssistantWakeService::class.java).apply {
            action = AssistantWakeService.ACTION_PAUSE_LISTENING
        }
        startService(serviceIntent)

        if (assistantActive && !isListening && !isSpeaking && ::statusText.isInitialized) {
            scheduleListening(350)
        }
    }

    override fun onPause() {
        super.onPause()
        stopListening()
        val serviceIntent = Intent(this, AssistantWakeService::class.java).apply {
            action = AssistantWakeService.ACTION_RESUME_LISTENING
        }
        startService(serviceIntent)
    }

    private fun ensureMicPermissionAndListen() {
        if (!assistantActive || isListening || isSpeaking) return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startVoiceRecognition()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun scheduleListening(delayMs: Long = 450) {
        if (!assistantActive || isFinishing || isDestroyed) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ ensureMicPermissionAndListen() }, delayMs)
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
        val normalized = normalize(raw)

        if (waitingForCommand) {
            waitingForCommand = false
            statusText.text = "Escuché: $raw"
            handleCommand(raw)
            return
        }

        val wakeIndex = normalized.indexOf(wakeWord)
        if (wakeIndex < 0) {
            statusText.text = "Di “Mía” para activarme."
            scheduleListening(250)
            return
        }

        val afterWake = normalized.substring(wakeIndex + wakeWord.length)
            .trim()
            .trimStart(',', '.', ':', ';', '-', ' ')

        playWakeTone()

        if (afterWake.isNotBlank()) {
            waitingForCommand = false
            statusText.text = "Sí, dime."
            pendingAction = { handleCommand(afterWake) }
            speakWakeResponse()
        } else {
            waitingForCommand = true
            statusText.text = "Sí, dime."
            speakWakeResponse()
        }
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

    private fun speakWakeResponse() {
        stopListening()
        if (speechReady) {
            textToSpeech.speak(
                "Sí, dime.",
                TextToSpeech.QUEUE_FLUSH,
                null,
                "wake_${System.currentTimeMillis()}"
            )
        } else {
            scheduleListening(250)
        }
    }

    private fun handleCommand(raw: String) {
        val command = normalize(raw)
        val savedPlace = extractSavedPlace(command)
        val youtubeQuery = extractYouTubeQuery(command)
        val spotifyQuery = extractSpotifyQuery(command)
        val contactName = extractContactName(command)
        val destination = extractDestination(command)
        val chatGptRequest = extractChatGptRequest(command)

        when {
            containsAny(command, "deja de escuchar", "detente", "pausa asistente", "para de escuchar") -> {
                assistantActive = false
                stopListening()
                respond("De acuerdo. La escucha quedó pausada.", listenAgain = false)
            }
            savedPlace != null -> savePlace(savedPlace.first, savedPlace.second)
            chatGptRequest != null -> automateChatGpt(chatGptRequest.first, chatGptRequest.second)
            spotifyQuery != null -> playMediaSearch("com.spotify.music", "Spotify", spotifyQuery)
            youtubeQuery != null -> playMediaSearch("com.google.android.youtube", "YouTube", youtubeQuery)
            command.contains("whatsapp") && contactName != null -> requestContactAndOpen(contactName)
            containsAny(command, "abre whatsapp", "abrir whatsapp", "whatsapp") -> openPackage("com.whatsapp", "WhatsApp")
            destination != null -> openWazeDestination(resolveDestination(destination))
            containsAny(command, "abre waze", "abrir waze", "waze") -> openPackage("com.waze", "Waze")
            containsAny(command, "abre youtube", "abrir youtube", "youtube") -> openPackage("com.google.android.youtube", "YouTube")
            containsAny(command, "abre disney", "abrir disney", "disney plus", "disney+") -> openPackage("com.disney.disneyplus", "Disney Plus")
            containsAny(command, "abre spotify", "abrir spotify", "spotify") -> openPackage("com.spotify.music", "Spotify")
            containsAny(command, "abre maps", "abre mapas", "google maps", "mapas") -> openPackage("com.google.android.apps.maps", "Google Maps")
            containsAny(command, "abre gmail", "abrir gmail", "gmail") -> openPackage("com.google.android.gm", "Gmail")
            containsAny(command, "abre chrome", "abrir chrome", "chrome") -> openPackage("com.android.chrome", "Chrome")
            containsAny(command, "abre configuracion", "abre ajustes", "configuracion", "ajustes") -> {
                respondAndThen("Abriendo Ajustes") { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            }
            containsAny(command, "abre calculadora", "abrir calculadora", "calculadora") -> openCalculator()
            containsAny(command, "como estas", "como te va") -> respond("Muy bien, gracias. Estoy listo para ayudarte.")
            containsAny(command, "que puedes hacer", "ayuda", "comandos") -> respond(
                "Puedo abrir aplicaciones, buscar en YouTube, llevarte a un destino con Waze y guardar tu casa o tu trabajo."
            )
            else -> respond("Todavía no entendí esa solicitud. Intenta decirla de otra forma.")
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
        if (!isAccessibilityServiceEnabled()) {
            respondAndThen(
                "Para controlar aplicaciones necesito que actives el acceso de Mía una sola vez. Te llevo a la pantalla para habilitarlo."
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

    private fun containsAny(text: String, vararg options: String): Boolean =
        options.any { text.contains(it) }

    private fun openPackage(packageName: String, displayName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            respondAndThen("Abriendo $displayName") { startActivity(launchIntent) }
        } else {
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

        if (action != null) {
            action()
            waitingForCommand = false
            scheduleListening(900)
        } else {
            if (waitingForCommand) {
                statusText.text = "Te escucho…"
                scheduleListening(180)
            } else {
                statusText.text = "Di “Mía” para activarme."
                scheduleListening(350)
            }
        }
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
        speechRecognizer?.destroy()
        speechRecognizer = null

        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        super.onDestroy()
    }
}
