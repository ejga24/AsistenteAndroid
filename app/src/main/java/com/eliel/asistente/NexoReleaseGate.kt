package com.eliel.asistente

import android.content.Context

enum class NexoRcState {
    CODE_READY,
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
        val health = NexoSystemHealth.snapshot(context)
        val missingRequired = health.checks.filter { it.required && !it.ready }

        if (missingRequired.isNotEmpty()) {
            return NexoReleaseReadiness(
                state = NexoRcState.NEEDS_SETUP,
                headline = "Configuración pendiente",
                detail = missingRequired.joinToString(" · ") { it.label }
            )
        }

        return NexoReleaseReadiness(
            state = NexoRcState.DEVICE_VALIDATION,
            headline = "Código listo para validación física",
            detail = "Faltan pruebas reales de wake/background, MagicOS, orientación, instalación y batería."
        )
    }
}
