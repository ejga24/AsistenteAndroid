package com.eliel.asistente

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var statusText: TextView
    private lateinit var micButton: Button
    private lateinit var textToSpeech: TextToSpeech
    private val handler = Handler(Looper.getMainLooper())
    private val preferences by lazy { getSharedPreferences("assistant_places", MODE_PRIVATE) }

    private var speechReady = false
    private var assistantActive = true
    private var isListening = false
    private var pendingAction: (() -> Unit)? = null
    private var pendingContactName: String? = null

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()

        if (spoken.isNullOrBlank()) {
            assistantActive = false
            statusText.text = "Escucha pausada. Pulsa Hablar para continuar."
            micButton.text = "🎤 Hablar"
        } else {
            statusText.text = "Escuché: $spoken"
            handleCommand(spoken)
        }
    }

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
        micButton = findViewById(R.id.micButton)
        micButton.setOnClickListener {
            assistantActive = true
            ensureMicPermissionAndListen()
        }

        textToSpeech = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            statusText.text = "No pude iniciar la voz. Pulsa Hablar para continuar."
            ensureMicPermissionAndListen()
            return
        }

        var result = textToSpeech.setLanguage(Locale("es", "PA"))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            result = textToSpeech.setLanguage(Locale("es"))
        }
        speechReady = result != TextToSpeech.LANG_MISSING_DATA &&
            result != TextToSpeech.LANG_NOT_SUPPORTED
        textToSpeech.setSpeechRate(1.0f)
        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                runOnUiThread { finishSpeechCycle() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                runOnUiThread { finishSpeechCycle() }
            }
        })

        respond("Hola, ¿cómo estás? Dime, ¿en qué te puedo ayudar?")
    }

    private fun ensureMicPermissionAndListen() {
        if (!assistantActive || isListening) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startVoiceRecognition()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun scheduleListening() {
        if (!assistantActive || isFinishing || isDestroyed) return
        handler.postDelayed({ ensureMicPermissionAndListen() }, 450)
    }

    private fun startVoiceRecognition() {
        if (!assistantActive || isListening) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PA")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Te escucho…")
        }

        try {
            isListening = true
            statusText.text = "Te escucho…"
            micButton.text = "Escuchando…"
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            isListening = false
            assistantActive = false
            micButton.text = "🎤 Hablar"
            statusText.text = "Este teléfono no tiene disponible el reconocimiento de voz."
        }
    }

    private fun handleCommand(raw: String) {
        val command = normalize(raw)
        val savedPlace = extractSavedPlace(command)
        val youtubeQuery = extractYouTubeQuery(command)
        val contactName = extractContactName(command)
        val destination = extractDestination(command)

        when {
            containsAny(command, "deja de escuchar", "detente", "pausa asistente", "para de escuchar") -> {
                assistantActive = false
                micButton.text = "🎤 Hablar"
                respond("De acuerdo. La escucha quedó pausada.", listenAgain = false)
            }
            savedPlace != null -> savePlace(savedPlace.first, savedPlace.second)
            youtubeQuery != null -> searchYouTube(youtubeQuery)
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
            command == "asistente" || command.endsWith(" asistente") -> respond("Sí, dime. ¿En qué te ayudo?")
            containsAny(command, "que puedes hacer", "ayuda", "comandos") -> respond(
                "Puedo abrir aplicaciones, buscar en YouTube, llevarte a un destino con Waze y guardar tu casa o tu trabajo."
            )
            else -> respond("Todavía no entendí esa solicitud. Intenta decirla de otra forma.")
        }
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

    private fun extractYouTubeQuery(command: String): String? = valueAfterMarker(
        command,
        listOf(
            "buscame en youtube ", "busca en youtube ", "buscar en youtube ",
            "pon en youtube ", "youtube busca ", "youtube buscame "
        )
    )

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

    private fun searchYouTube(query: String) {
        if (packageManager.getLaunchIntentForPackage("com.google.android.youtube") == null) {
            respond("YouTube no está instalado.")
            return
        }
        val uri = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.youtube") }
        respondAndThen("Buscando $query en YouTube") { startActivity(intent) }
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

    private fun respond(message: String, listenAgain: Boolean = true) {
        statusText.text = message
        if (!listenAgain) assistantActive = false
        if (speechReady) {
            textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "response_${System.currentTimeMillis()}")
        } else if (listenAgain) {
            scheduleListening()
        }
    }

    private fun respondAndThen(message: String, action: () -> Unit) {
        statusText.text = message
        if (!speechReady) {
            action()
            return
        }
        pendingAction = action
        textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "action_${System.currentTimeMillis()}")
    }

    private fun finishSpeechCycle() {
        val action = pendingAction
        pendingAction = null
        if (action != null) action() else scheduleListening()
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
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        super.onDestroy()
    }
}
