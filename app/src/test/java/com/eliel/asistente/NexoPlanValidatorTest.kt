package com.eliel.asistente

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NexoPlanValidatorTest {

    @Test
    fun acceptsValidCompoundPlan() {
        val plan = NexoAgentPlan(
            actions = listOf(
                MiaAgentDecision(tool = "open_app", app = "Spotify"),
                MiaAgentDecision(tool = "spotify", text = "música relajante")
            )
        )
        assertTrue(NexoPlanValidator.validate(plan).valid)
    }

    @Test
    fun rejectsVisionBeforeAnotherStep() {
        val plan = NexoAgentPlan(
            actions = listOf(
                MiaAgentDecision(tool = "vision"),
                MiaAgentDecision(tool = "open_app", app = "Spotify")
            )
        )
        assertFalse(NexoPlanValidator.validate(plan).valid)
    }

    @Test
    fun rejectsInvalidPercentage() {
        val plan = NexoAgentPlan(
            actions = listOf(
                MiaAgentDecision(tool = "set_volume", target = "140")
            )
        )
        assertFalse(NexoPlanValidator.validate(plan).valid)
    }

    @Test
    fun rejectsBlankChatGptRequest() {
        val plan = NexoAgentPlan(
            actions = listOf(
                MiaAgentDecision(tool = "chatgpt", text = "")
            )
        )
        assertFalse(NexoPlanValidator.validate(plan).valid)
    }

    @Test
    fun rejectsClarificationFollowedByAction() {
        val plan = NexoAgentPlan(
            actions = listOf(
                MiaAgentDecision(tool = "clarify", text = "¿Qué aplicación?"),
                MiaAgentDecision(tool = "open_app", app = "Chrome")
            )
        )
        assertFalse(NexoPlanValidator.validate(plan).valid)
    }
    @Test
    fun rejectsMoreThanSixActions() {
        val plan = NexoAgentPlan(
            actions = List(7) {
                MiaAgentDecision(tool = "open_app", app = "Chrome")
            }
        )
        assertFalse(NexoPlanValidator.validate(plan).valid)
    }
}

