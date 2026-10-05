package com.eliel.asistente

data class NexoPlanValidation(
    val valid: Boolean,
    val reason: String = ""
)

object NexoPlanValidator {
    private const val MAX_ACTIONS = 6

    fun validate(plan: NexoAgentPlan): NexoPlanValidation {
        if (plan.actions.isEmpty()) {
            return NexoPlanValidation(false, "El plan no contiene acciones.")
        }
        if (plan.actions.size > MAX_ACTIONS) {
            return NexoPlanValidation(false, "El plan supera el máximo de acciones.")
        }

        plan.actions.forEachIndexed { index, action ->
            val terminal = action.tool in setOf("answer", "clarify", "vision")
            if (terminal && index != plan.actions.lastIndex) {
                return NexoPlanValidation(
                    false,
                    "La acción " + action.tool + " debe cerrar el plan."
                )
            }

            val hasTextOrTarget = action.text.isNotBlank() || action.target.isNotBlank()
            val validAction = when (action.tool) {
                "open_app" -> action.app.isNotBlank() || action.text.isNotBlank()
                "waze" -> hasTextOrTarget
                "spotify", "youtube" -> hasTextOrTarget
                "chatgpt" -> action.text.isNotBlank()
                "vision", "back", "home", "answer", "clarify" -> true
                "tap_text" -> hasTextOrTarget
                "type_text" -> action.text.isNotBlank()
                "set_volume", "set_brightness" -> {
                    val value = action.target.toIntOrNull()
                    value != null && value in 0..100
                }
                "car_mode" -> action.target.equals("on", true) ||
                    action.target.equals("off", true)
                else -> false
            }

            if (!validAction) {
                return NexoPlanValidation(
                    false,
                    "La acción " + action.tool + " no tiene parámetros válidos."
                )
            }
        }

        return NexoPlanValidation(true)
    }
}
