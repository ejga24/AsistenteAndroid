package com.eliel.asistente

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat

enum class NexoHealthState {
    READY,
    DEGRADED,
    BLOCKED
}

data class NexoHealthCheck(
    val id: String,
    val label: String,
    val ready: Boolean,
    val required: Boolean,
    val detail: String
)

data class NexoHealthSnapshot(
    val state: NexoHealthState,
    val checks: List<NexoHealthCheck>,
    val summary: String,
    val recommendation: String
) {
    val requiredReady: Boolean
        get() = checks.filter { it.required }.all { it.ready }

    val readyCount: Int
        get() = checks.count { it.ready }

    val totalCount: Int
        get() = checks.size
}

object NexoSystemHealth {

    fun snapshot(context: Context): NexoHealthSnapshot {
        val mic = permission(context, Manifest.permission.RECORD_AUDIO)
        val camera = permission(context, Manifest.permission.CAMERA)
        val contacts = permission(context, Manifest.permission.READ_CONTACTS)
        val notificationReady = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            permission(context, Manifest.permission.POST_NOTIFICATIONS)
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryExempt = powerManager.isIgnoringBatteryOptimizations(context.packageName)
        val access = accessibilityEnabled(context)
        val ai = MiaAgentPlanner(context).isConfigured()
        val wake = NexoWakeRuntime.status(context)
        val recognitionAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        val voiceEnabled = NexoVoiceState.isEnabled(context)
        val runtimeIssue = NexoRuntimeState.currentIssue(context)

        val checks = listOf(
            NexoHealthCheck(
                id = "voice",
                label = "Escucha",
                ready = voiceEnabled,
                required = true,
                detail = if (voiceEnabled) "Voice Core activo" else "Pausada por el usuario"
            ),
            NexoHealthCheck(
                id = "microphone",
                label = "Micrófono",
                ready = mic,
                required = true,
                detail = if (mic) "Listo para voz" else "Necesario para escuchar"
            ),
            NexoHealthCheck(
                id = "accessibility",
                label = "Control de aplicaciones",
                ready = access,
                required = true,
                detail = if (access) "Control activo" else "Control de apps limitado"
            ),
            NexoHealthCheck(
                id = "intelligence",
                label = "Inteligencia",
                ready = ai,
                required = true,
                detail = if (ai) "Motor configurado" else "Planificación IA no disponible"
            ),
            NexoHealthCheck(
                id = "wake",
                label = "Wake engine",
                ready = mic && (wake.local || recognitionAvailable),
                required = true,
                detail = when {
                    wake.local -> "Hotword local"
                    recognitionAvailable -> "Fallback Android activo"
                    else -> "Reconocimiento de voz no disponible"
                }
            ),
            NexoHealthCheck(
                id = "vision",
                label = "Vision",
                ready = camera,
                required = false,
                detail = if (camera) "Cámara disponible" else "Se habilita bajo demanda"
            ),
            NexoHealthCheck(
                id = "contacts",
                label = "Contactos",
                ready = contacts,
                required = false,
                detail = if (contacts) "WhatsApp por contacto disponible" else "Opcional"
            ),
            NexoHealthCheck(
                id = "notifications",
                label = "Notificaciones",
                ready = notificationReady,
                required = false,
                detail = if (notificationReady) {
                    "Estado de segundo plano visible"
                } else {
                    "Recomendado para mostrar el servicio de voz"
                }
            ),
            NexoHealthCheck(
                id = "battery",
                label = "Batería / MagicOS",
                ready = batteryExempt,
                required = false,
                detail = if (batteryExempt) {
                    "NEXO está exento de optimización"
                } else {
                    "Revisar optimización si MagicOS detiene NEXO"
                }
            )
        )

        val missingRequired = checks.filter { it.required && !it.ready }
        val state = when {
            !voiceEnabled -> NexoHealthState.DEGRADED
            !mic -> NexoHealthState.BLOCKED
            missingRequired.isNotEmpty() || runtimeIssue != null -> NexoHealthState.DEGRADED
            else -> NexoHealthState.READY
        }

        val summary = when (state) {
            NexoHealthState.READY -> "Operativo"
            NexoHealthState.DEGRADED -> "Operativo con funciones limitadas"
            NexoHealthState.BLOCKED -> "Requiere configuración"
        }

        val recommendation = when {
            !voiceEnabled -> "La escucha está pausada. Puedes reanudarla desde el dashboard."
            !mic -> "Activa el micrófono para usar NEXO por voz."
            !access -> "Activa Control de aplicaciones para automatizaciones de pantalla."
            !ai -> "Configura Inteligencia para planes complejos y Vision."
            runtimeIssue != null -> "Último incidente: " + runtimeIssue.source + ". " + runtimeIssue.message
            !notificationReady -> "Autoriza notificaciones para ver claramente el estado de escucha en segundo plano."
            !camera -> "Vision está disponible cuando autorices la cámara."
            else -> "Todos los núcleos principales están listos."
        }

        return NexoHealthSnapshot(
            state = state,
            checks = checks,
            summary = summary,
            recommendation = recommendation
        )
    }

    private fun permission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun accessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()

        val component = context.packageName + "/" + MiaAccessibilityService::class.java.name
        return enabled.split(':').any { it.equals(component, ignoreCase = true) }
    }
}
