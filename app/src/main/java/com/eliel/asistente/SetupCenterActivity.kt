package com.eliel.asistente

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SetupCenterActivity : AppCompatActivity() {

    private lateinit var progressText: TextView
    private lateinit var checklistText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup_center)

        progressText = findViewById(R.id.setupProgressText)
        checklistText = findViewById(R.id.setupChecklistText)

        findViewById<Button>(R.id.setupMicButton).setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + packageName)
                )
            )
        }

        findViewById<Button>(R.id.setupAccessibilityButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.setupIntelligenceButton).setOnClickListener {
            startActivity(Intent(this, AgentSettingsActivity::class.java))
        }

        findViewById<Button>(R.id.setupVisionButton).setOnClickListener {
            startActivity(Intent(this, VisionActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val mic = permissionGranted(Manifest.permission.RECORD_AUDIO)
        val camera = permissionGranted(Manifest.permission.CAMERA)
        val contacts = permissionGranted(Manifest.permission.READ_CONTACTS)
        val accessibility = isAccessibilityEnabled()
        val ai = MiaAgentPlanner(this).isConfigured()

        val checks = listOf(mic, accessibility, ai, camera)
        val completed = checks.count { it }
        val percent = (completed * 100) / checks.size

        progressText.text = percent.toString() + "% listo"

        checklistText.text = buildString {
            append(if (mic) "✓ " else "• ")
            append("Micrófono para voz y wake word")
            append("\n\n")

            append(if (accessibility) "✓ " else "• ")
            append("Control de aplicaciones")
            append("\n\n")

            append(if (ai) "✓ " else "• ")
            append("Inteligencia de NEXO")
            append("\n\n")

            append(if (camera) "✓ " else "• ")
            append("Vision / cámara")
            append("\n\n")

            append(if (contacts) "✓ " else "• ")
            append("Contactos · opcional para WhatsApp")
        }
    }

    private fun permissionGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun isAccessibilityEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val component = packageName + "/" + MiaAccessibilityService::class.java.name
        return enabledServices.split(':').any { it.equals(component, ignoreCase = true) }
    }
}
