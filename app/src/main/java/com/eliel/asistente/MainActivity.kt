package com.eliel.asistente

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        findViewById<Button>(R.id.micButton).setOnClickListener {
            ensureMicPermissionAndListen()
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

        when {
            containsAny(command, "abre whatsapp", "abrir whatsapp", "whatsapp") -> openPackage("com.whatsapp", "WhatsApp")
            containsAny(command, "abre waze", "abrir waze", "waze") -> openPackage("com.waze", "Waze")
            containsAny(command, "abre youtube", "abrir youtube", "youtube") -> openPackage("com.google.android.youtube", "YouTube")
            containsAny(command, "abre spotify", "abrir spotify", "spotify") -> openPackage("com.spotify.music", "Spotify")
            containsAny(command, "abre maps", "abre mapas", "google maps", "mapas") -> openPackage("com.google.android.apps.maps", "Google Maps")
            containsAny(command, "abre gmail", "abrir gmail", "gmail") -> openPackage("com.google.android.gm", "Gmail")
            containsAny(command, "abre chrome", "abrir chrome", "chrome") -> openPackage("com.android.chrome", "Chrome")
            containsAny(command, "abre configuracion", "abre ajustes", "configuracion", "ajustes") -> {
                startActivity(Intent(Settings.ACTION_SETTINGS))
                statusText.text = "Abriendo Ajustes"
            }
            containsAny(command, "abre calculadora", "abrir calculadora", "calculadora") -> openCalculator()
            else -> statusText.text = "Todavía no conozco ese comando: $raw"
        }
    }

    private fun containsAny(text: String, vararg options: String): Boolean =
        options.any { text.contains(it) }

    private fun openPackage(packageName: String, displayName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            startActivity(launchIntent)
            statusText.text = "Abriendo $displayName"
        } else {
            statusText.text = "$displayName no está instalado."
        }
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
            startActivity(intent)
            statusText.text = "Abriendo Calculadora"
        } else {
            statusText.text = "No encontré la calculadora instalada."
        }
    }
}
