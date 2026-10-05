package com.eliel.asistente

import android.content.Context
import android.content.pm.ApplicationInfo

enum class NexoRcState {
    DEBUG_BUILD,
    NEEDS_SETUP,
    DEVICE_VALIDATION
}

data class NexoReleaseReadiness(
    val state: NexoRcState,
    val headline: String,
    val detail: String
)

object NexoReleaseGate {

    fun evaluate(context: Context): NexoReleaseReadiness {
        val debugBuild = context.applicationInfo.flags and
            ApplicationInfo.FLAG_DEBUGGABLE != 0
        val health = NexoSystemHealth.snapshot(context)
        val missingRequired = health.checks.filter { it.required && !it.ready }

        if (missingRequired.isNotEmpty()) {
            return NexoReleaseReadiness(
                state = NexoRcState.NEEDS_SETUP,
                headline = "Configuración pendiente",
                detail = missingRequired.joinToString(" · ") { it.label }
            )
        }

        if (debugBuild) {
            return NexoReleaseReadiness(
                state = NexoRcState.DEBUG_BUILD,
                headline = "Build de validación",
                detail = "La configuración está lista. El APK final RC debe usar firma estable de release."
            )
        }

        return NexoReleaseReadiness(
            state = NexoRcState.DEVICE_VALIDATION,
            headline = "Código listo para validación física",
            detail = "Faltan pruebas reales de wake/background, MagicOS, orientación, instalación y batería."
        )
    }
}
