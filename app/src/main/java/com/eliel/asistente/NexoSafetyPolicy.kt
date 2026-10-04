package com.eliel.asistente

enum class NexoRiskLevel {
    SAFE,
    CONFIRM,
    BLOCK
}

data class NexoSafetyDecision(
    val level: NexoRiskLevel,
    val reason: String = ""
)

object NexoSafetyPolicy {

    private val sensitiveWords = listOf(
        "pagar", "pago", "comprar", "compra", "transferir", "transferencia",
        "enviar dinero", "borrar", "eliminar", "desinstalar", "instalar",
        "aceptar", "confirmar", "publicar", "enviar", "reservar", "cancelar",
        "cerrar sesion", "cambiar contraseña", "cambiar contrasena"
    )

    private val blockedWords = listOf(
        "formatear dispositivo", "restablecer fabrica", "restablecer de fabrica"
    )

    fun evaluate(decision: MiaAgentDecision): NexoSafetyDecision {
        val combined = listOf(decision.tool, decision.app, decision.text, decision.target)
            .joinToString(" ")
            .lowercase()

        if (blockedWords.any { combined.contains(it) }) {
            return NexoSafetyDecision(
                NexoRiskLevel.BLOCK,
                "Esta acción puede afectar de forma importante al dispositivo."
            )
        }

        val genericScreenControl = decision.tool in setOf("tap_text", "type_text")
        val sensitiveIntent = sensitiveWords.any { combined.contains(it) }

        return when {
            genericScreenControl && sensitiveIntent -> NexoSafetyDecision(
                NexoRiskLevel.CONFIRM,
                "La acción podría confirmar, enviar, comprar, borrar o cambiar información."
            )
            else -> NexoSafetyDecision(NexoRiskLevel.SAFE)
        }
    }
}
