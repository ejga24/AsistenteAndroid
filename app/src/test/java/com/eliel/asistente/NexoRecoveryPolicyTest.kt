package com.eliel.asistente

import org.junit.Assert.assertTrue
import org.junit.Test

class NexoRecoveryPolicyTest {

    @Test
    fun mapsCredentialErrorsWithoutExposingDetails() {
        val guidance = NexoRecoveryPolicy.fromThrowable(
            "Agent Brain",
            IllegalStateException("OpenAI HTTP 401")
        )

        assertTrue(guidance.userMessage.contains("credenciales"))
        assertTrue(guidance.logMessage.contains("credenciales rechazadas"))
    }

    @Test
    fun mapsRateLimitToLocalDegradedMode() {
        val guidance = NexoRecoveryPolicy.fromThrowable(
            "Agent Brain",
            IllegalStateException("OpenAI HTTP 429")
        )

        assertTrue(guidance.userMessage.contains("límite"))
        assertTrue(guidance.userMessage.contains("funciones locales"))
    }

    @Test
    fun mapsServerFailureToTemporaryServiceIssue() {
        val guidance = NexoRecoveryPolicy.fromThrowable(
            "Vision",
            IllegalStateException("Vision HTTP 503")
        )

        assertTrue(guidance.userMessage.contains("temporalmente"))
        assertTrue(guidance.logMessage.contains("servicio remoto"))
    }
}
