package com.eliel.asistente

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

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
        val health = NexoSystemHealth.snapshot(this)
        fun ready(id: String): Boolean =
            health.checks.firstOrNull { it.id == id }?.ready == true

        statusText.text = buildString {
            append("PRIVACIDAD Y PERMISOS\n\n")
            append(if (ready("microphone")) "✓ Micrófono autorizado" else "• Micrófono pendiente")
            append("\n")
            append(if (ready("contacts")) "✓ Contactos autorizados" else "• Contactos solo cuando los necesites")
            append("\n")
            append(if (ready("accessibility")) "✓ Control de aplicaciones activo" else "• Control de aplicaciones desactivado")
            append("\n")
            append(if (ready("intelligence")) "✓ Motor de inteligencia configurado" else "• Motor de inteligencia pendiente")
            append("\n")
            append(if (ready("notifications")) "✓ Notificaciones disponibles" else "• Notificaciones pendientes")
            append("\n")
            append(if (ready("battery")) "✓ Optimización de batería revisada" else "• Revisar batería si MagicOS suspende NEXO")

            append("\n\nPOLÍTICA DE NEXO\n")
            append("• Las credenciales se cifran con Android Keystore y no se incluyen en GitHub.\n")
            append("• El historial local está cifrado y no conserva comandos, texto escrito, consultas ni destinos sensibles.\n")
            append("• El backup de datos privados de NEXO está desactivado.\n")
            append("• NEXO exige la palabra de activación antes de ejecutar órdenes de voz.\n")
            append("• Los planes se validan antes de ejecutarse y se detienen ante pasos inciertos.\n")
            append("• Las acciones sensibles requieren confirmación explícita según su categoría.\n")
            append("• Vision y la automatización sensible de pantalla requieren que la tablet esté desbloqueada.\n")
            append("• Los permisos se solicitan solo cuando una capacidad los necesita.\n")
            append("• Vision envía únicamente la captura solicitada al motor configurado para analizarla.\n")
            append("• Intelligence y Vision solicitan no almacenar la respuesta remota.\n")
            append("• Las pantallas de credenciales, Vision e historial están protegidas contra capturas del sistema.\n")
            append("• La captura temporal de Vision se elimina después del análisis o de un error.")
        }
    }

}