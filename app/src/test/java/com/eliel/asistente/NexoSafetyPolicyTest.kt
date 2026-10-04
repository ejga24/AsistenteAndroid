package com.eliel.asistente

import org.junit.Assert.assertEquals
import org.junit.Test

class NexoSafetyPolicyTest {

    @Test
    fun safeDirectActionRemainsSafe() {
        val decision = MiaAgentDecision(tool = "open_app", app = "Spotify")
        assertEquals(NexoRiskLevel.SAFE, NexoSafetyPolicy.evaluate(decision).level)
    }

    @Test
    fun paymentUiActionRequiresConfirmation() {
        val decision = MiaAgentDecision(
            tool = "tap_text",
            target = "Confirmar pago"
        )
        val result = NexoSafetyPolicy.evaluate(decision)
        assertEquals(NexoRiskLevel.CONFIRM, result.level)
        assertEquals(NexoRiskCategory.MONEY, result.category)
    }

    @Test
    fun communicationUiActionRequiresConfirmation() {
        val decision = MiaAgentDecision(
            tool = "tap_text",
            target = "Enviar"
        )
        val result = NexoSafetyPolicy.evaluate(decision)
        assertEquals(NexoRiskLevel.CONFIRM, result.level)
        assertEquals(NexoRiskCategory.COMMUNICATION, result.category)
    }

    @Test
    fun deviceResetIsBlocked() {
        val decision = MiaAgentDecision(
            tool = "tap_text",
            target = "Restablecer de fabrica"
        )
        val result = NexoSafetyPolicy.evaluate(decision)
        assertEquals(NexoRiskLevel.BLOCK, result.level)
        assertEquals(NexoRiskCategory.DEVICE_RESET, result.category)
    }
}
