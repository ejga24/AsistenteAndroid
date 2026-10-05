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

class SecurityCenterActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security_center)

        statusText = findViewById(R.id.securityStatusText)

        findViewById<Button>(R.id.securityAccessibilityButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.securityPermissionsButton).setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + packageName)
                )
            )
        }
        findViewById<Button>(R.id.securityIntelligenceButton).setOnClickListener {
            startActivity(Intent(this, AgentSettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val mic = permissionGranted(Manifest.permission.RECORD_AUDIO)
        val contacts = permissionGranted(Manifest.permission.READ_CONTACTS)

        val accessibility = NexoAccessibilityStatus.isEnabled(this)

        val ai = MiaAgentPlanner(this).isConfigured()

        statusText.text = buildString {
            append("PRIVACIDAD Y PERMISOS\n\n")
            append(if (mic) "✓ Micrófono autorizado" else "• Micrófono pendiente")
            append("\n")
            append(if (contacts) "✓ Contactos autorizados" else "• Contactos solo cuando los necesites")
            append("\n")
            append(if (accessibility) "✓ Control de aplicaciones activo" else "• Control de aplicaciones desactivado")
            append("\n")
            append(if (ai) "✓ Motor de inteligencia configurado" else "• Motor de inteligencia pendiente")

            append("\n\nPOLÍTICA DE NEXO\n")
            append("• Las credenciales se cifran con Android Keystore y no se incluyen en GitHub.\n")
            append("• El historial local no conserva comandos de voz, texto escrito, consultas ni destinos sensibles.\n")
            append("• El backup de datos privados de NEXO está desactivado.\n")
            append("• NEXO exige la palabra de activación antes de ejecutar órdenes de voz.\n")
            append("• Los planes se pueden detener desde Now Running.\n")
            append("• Las acciones sensibles requieren confirmación explícita según su categoría.\n")
            append("• Vision y la automatización sensible de pantalla requieren que la tablet esté desbloqueada.\n")
            append("• Los permisos se solicitan solo cuando una capacidad los necesita.\n")
            append("• Vision envía únicamente la captura solicitada al motor configurado para analizarla.\n")
            append("• Las solicitudes de Intelligence y Vision usan almacenamiento remoto desactivado cuando la API lo permite.\n")
            append("• Las pantallas de credenciales, Vision e historial están protegidas contra capturas del sistema.\n")
            append("• La captura temporal de Vision se elimina después del análisis o de un error.")
        }
    }

    private fun permissionGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
