package com.eliel.asistente

object NexoRecoveryPolicy {

    data class Guidance(
        val userMessage: String,
        val logMessage: String
    )

    fun fromThrowable(source: String, throwable: Throwable): Guidance {
        val raw = throwable.message.orEmpty()
        val lower = raw.lowercase()

        return when {
            lower.contains("not_configured") || lower.contains("configured") -> Guidance(
                userMessage = "Esta capacidad necesita completar la configuración de NEXO.",
                logMessage = source + ": configuración pendiente"
            )
            lower.contains("timeout") || lower.contains("timed out") -> Guidance(
                userMessage = "La operación tardó demasiado. NEXO quedó operativo y puedes intentarlo de nuevo.",
                logMessage = source + ": timeout"
            )
            lower.contains("network") || lower.contains("unable to resolve") ||
                lower.contains("connection") -> Guidance(
                userMessage = "No pude completar la operación por conectividad. Las funciones locales siguen disponibles.",
                logMessage = source + ": conectividad"
            )
            lower.contains("401") || lower.contains("403") -> Guidance(
                userMessage = "La inteligencia necesita revisar sus credenciales.",
                logMessage = source + ": credenciales rechazadas"
            )
            else -> Guidance(
                userMessage = "No pude completar esa operación, pero NEXO sigue disponible.",
                logMessage = source + ": " + raw.take(140)
            )
        }
    }
}
