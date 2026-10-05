package com.eliel.asistente

import java.text.Normalizer
import java.util.Locale

enum class NexoRiskLevel {
    SAFE,
    CONFIRM,
    BLOCK
}

enum class NexoRiskCategory {
    NONE,
    MONEY,
    DESTRUCTIVE,
    COMMUNICATION,
    ACCOUNT,
    INSTALLATION,
    BOOKING,
    DEVICE_RESET
}

data class NexoSafetyDecision(
    val level: NexoRiskLevel,
    val category: NexoRiskCategory = NexoRiskCategory.NONE,
    val reason: String = ""
)

object NexoSafetyPolicy {

    private val moneyWords = listOf(
        "pagar", "pago", "comprar", "compra", "transferir", "transferencia",
        "enviar dinero", "abonar", "cobrar"
    )

    private val destructiveWords = listOf(
        "borrar", "eliminar", "vaciar", "desinstalar", "cancelar cuenta"
    )

    private val communicationWords = listOf(
        "enviar", "publicar", "responder", "mandar mensaje", "enviar correo",
        "compartir", "postear"
    )

    private val accountWords = listOf(
        "cerrar sesion", "cambiar contraseña", "cambiar contrasena",
        "cambiar correo", "cambiar telefono", "aceptar terminos"
    )

    private val installationWords = listOf(
        "instalar", "desinstalar", "permitir origen desconocido"
    )

    private val bookingWords = listOf(
        "reservar", "confirmar reserva", "cancelar reserva", "comprar boleto"
    )

    private val blockedWords = listOf(
        "formatear dispositivo", "restablecer fabrica", "restablecer de fabrica",
        "borrar todos los datos"
    )

    fun evaluate(decision: MiaAgentDecision): NexoSafetyDecision {
        val combined = normalizeRiskText(
            listOf(decision.tool, decision.app, decision.text, decision.target)
                .joinToString(" ")
        )

        if (blockedWords.any { combined.contains(it) }) {
            return NexoSafetyDecision(
                level = NexoRiskLevel.BLOCK,
                category = NexoRiskCategory.DEVICE_RESET,
                reason = "Esta acción puede borrar datos o restablecer el dispositivo."
            )
        }

        val category = when {
            moneyWords.any { combined.contains(it) } -> NexoRiskCategory.MONEY
            destructiveWords.any { combined.contains(it) } -> NexoRiskCategory.DESTRUCTIVE
            accountWords.any { combined.contains(it) } -> NexoRiskCategory.ACCOUNT
            installationWords.any { combined.contains(it) } -> NexoRiskCategory.INSTALLATION
            bookingWords.any { combined.contains(it) } -> NexoRiskCategory.BOOKING
            communicationWords.any { combined.contains(it) } -> NexoRiskCategory.COMMUNICATION
            else -> NexoRiskCategory.NONE
        }

        val uiAutomation = decision.tool in setOf("tap_text", "type_text")
        val confirmable = category != NexoRiskCategory.NONE

        return when {
            uiAutomation && confirmable -> NexoSafetyDecision(
                level = NexoRiskLevel.CONFIRM,
                category = category,
                reason = reasonFor(category)
            )
            else -> NexoSafetyDecision(NexoRiskLevel.SAFE)
        }
    }

    private fun normalizeRiskText(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)

    private fun reasonFor(category: NexoRiskCategory): String = when (category) {
        NexoRiskCategory.MONEY ->
            "Esta acción puede producir un pago, compra o transferencia."
        NexoRiskCategory.DESTRUCTIVE ->
            "Esta acción puede borrar, eliminar o desinstalar información."
        NexoRiskCategory.COMMUNICATION ->
            "Esta acción puede enviar o publicar información en tu nombre."
        NexoRiskCategory.ACCOUNT ->
            "Esta acción puede modificar el estado o la seguridad de una cuenta."
        NexoRiskCategory.INSTALLATION ->
            "Esta acción puede instalar o desinstalar software."
        NexoRiskCategory.BOOKING ->
            "Esta acción puede crear, confirmar o cancelar una reserva."
        NexoRiskCategory.DEVICE_RESET ->
            "Esta acción puede borrar datos o restablecer el dispositivo."
        NexoRiskCategory.NONE -> ""
    }
}
