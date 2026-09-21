package com.eliel.asistente

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.provider.ContactsContract
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
    private lateinit var textToSpeech: TextToSpeech
    private var speechReady = false
    private var pendingAction: (() -> Unit)? = null
    private var pendingContactName: String? = null

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()

        if (spoken.isNullOrBlank()) {
            statusText.text = "No entendí. Intenta otra vez."
        } else {
            statusText.text = "Escuché: $spoken"
            handleCommand(spoken)
        }
    }

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startVoiceRecognition()
        else Toast.makeText(this, "Necesito permiso de micrófono.", Toast.LENGTH_LONG).show()
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
        textToSpeech = TextToSpeech(this, this)

        statusText = findViewById(R.id.statusText)
        findViewById<Button>(R.id.micButton).setOnClickListener {
            ensureMicPermissionAndListen()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
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
                    runOnUiThread { runPendingAction() }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    runOnUiThread { runPendingAction() }
                }
            })
        }
    }

    private fun ensureMicPermissionAndListen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startVoiceRecognition()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PA")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "¿Qué quieres abrir?")
        }

        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            statusText.text = "Este teléfono no tiene disponible el reconocimiento de voz."
        }
    }

    private fun handleCommand(raw: String) {
        val command = raw.lowercase(Locale.getDefault())
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")

        val contactName = extractContactName(command)
        val destination = extractDestination(command)

        when {
            command.contains("whatsapp") && contactName != null ->
                requestContactAndOpen(contactName)
            containsAny(command, "abre whatsapp", "abrir whatsapp", "whatsapp") -> openPackage("com.whatsapp", "WhatsApp")
            command.contains("waze") && destination != null ->
                openWazeDestination(destination)
            containsAny(command, "abre waze", "abrir waze", "waze") -> openPackage("com.waze", "Waze")
            containsAny(command, "abre youtube", "abrir youtube", "youtube") -> openPackage("com.google.android.youtube", "YouTube")
            containsAny(command, "abre spotify", "abrir spotify", "spotify") -> openPackage("com.spotify.music", "Spotify")
            containsAny(command, "abre maps", "abre mapas", "google maps", "mapas") -> openPackage("com.google.android.apps.maps", "Google Maps")
            containsAny(command, "abre gmail", "abrir gmail", "gmail") -> openPackage("com.google.android.gm", "Gmail")
            containsAny(command, "abre chrome", "abrir chrome", "chrome") -> openPackage("com.android.chrome", "Chrome")
            containsAny(command, "abre configuracion", "abre ajustes", "configuracion", "ajustes") -> {
                respondAndThen("Abriendo Ajustes") {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
            containsAny(command, "abre calculadora", "abrir calculadora", "calculadora") -> openCalculator()
            else -> respond("Todavía no conozco ese comando: $raw")
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

    private fun extractContactName(command: String): String? {
        val markers = listOf("al contacto ", "contacto ")
        return markers.firstNotNullOfOrNull { marker ->
            command.substringAfter(marker, "").trim().takeIf { it.isNotBlank() }
        }
    }

    private fun extractDestination(command: String): String? {
        val markers = listOf("para ir a ", "llevame a ", "ir a ")
        return markers.firstNotNullOfOrNull { marker ->
            command.substringAfter(marker, "").trim().takeIf { it.isNotBlank() }
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
        respondAndThen("Abriendo el chat de $contactName en WhatsApp") {
            startActivity(intent)
        }
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
        if (packageManager.getLaunchIntentForPackage("com.waze") == null) {
            respond("Waze no está instalado.")
            return
        }
        val uri = Uri.parse("https://waze.com/ul?q=${Uri.encode(destination)}&navigate=yes")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.waze") }
        respondAndThen("Abriendo Waze para ir a $destination") {
            startActivity(intent)
        }
    }

    private fun normalize(value: String): String = value.lowercase(Locale.getDefault())
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")

    private fun respond(message: String) {
        statusText.text = message
        if (speechReady) textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "response_${System.currentTimeMillis()}")
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

    private fun runPendingAction() {
        val action = pendingAction
        pendingAction = null
        action?.invoke()
    }

    private fun openCalculator() {
        val calculatorPackages = listOf(
            "com.google.android.calculator",
            "com.sec.android.app.popupcalculator",
            "com.android.calculator2",
            "com.huawei.calculator"
        )

        val intent = calculatorPackages
            .firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }

        if (intent != null) {
            respondAndThen("Abriendo Calculadora") { startActivity(intent) }
        } else {
            respond("No encontré la calculadora instalada.")
        }
    }

    override fun onDestroy() {
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        super.onDestroy()
    }
}
