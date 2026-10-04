package com.eliel.asistente

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SystemDiagnosticsActivity : AppCompatActivity() {

    private lateinit var reportText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_system_diagnostics)

        reportText = findViewById(R.id.diagnosticsText)

        findViewById<Button>(R.id.openAccessibilityButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.openAppSettingsButton).setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + packageName)
                )
            )
        }
        findViewById<Button>(R.id.refreshDiagnosticsButton).setOnClickListener {
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val mic = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()

        val component = packageName + "/" + MiaAccessibilityService::class.java.name
        val accessibility = enabledServices.split(':')
            .any { it.equals(component, ignoreCase = true) }

        val ai = MiaAgentPlanner(this).isConfigured()
        val battery = (getSystemService(BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val checks = listOf(mic, accessibility, ai)
        val ready = checks.count { it }

        reportText.text = buildString {
            append("ESTADO GENERAL\n")
            append(
                when (ready) {
                    3 -> "Operativo"
                    2 -> "Casi listo"
                    else -> "Requiere configuración"
                }
            )
            append("\n\nPERMISOS Y SERVICIOS\n")
            append(if (mic) "✓ Micrófono autorizado" else "• Micrófono pendiente")
            append("\n")
            append(if (accessibility) "✓ Control de aplicaciones activo" else "• Control de aplicaciones pendiente")
            append("\n")
            append(if (ai) "✓ Inteligencia configurada" else "• Inteligencia pendiente")
            append("\n\nDISPOSITIVO\n")
            append(Build.MANUFACTURER).append(" ").append(Build.MODEL)
            append("\nAndroid ").append(Build.VERSION.RELEASE)
            append(" · API ").append(Build.VERSION.SDK_INT)
            append("\nBatería ").append(battery).append("%")
            append("\n\nNEXO\n")
            append("3.0 alpha · ").append(NexoSkillRegistry.knownSkills().size).append(" skills base")
        }
    }
}
