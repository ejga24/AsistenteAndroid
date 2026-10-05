package com.eliel.asistente

import android.content.Context

data class NexoSkillDefinition(
    val id: String,
    val name: String,
    val description: String,
    val tools: Set<String>
)

object NexoSkillPolicy {
    private const val PREFS = "nexo_skills"

    val definitions = listOf(
        NexoSkillDefinition(
            id = "apps",
            name = "Aplicaciones",
            description = "Abrir aplicaciones instaladas.",
            tools = setOf("open_app")
        ),
        NexoSkillDefinition(
            id = "navigation",
            name = "Navegación",
            description = "Abrir rutas y destinos en Waze.",
            tools = setOf("waze")
        ),
        NexoSkillDefinition(
            id = "media",
            name = "Media",
            description = "Spotify y YouTube.",
            tools = setOf("spotify", "youtube")
        ),
        NexoSkillDefinition(
            id = "messaging",
            name = "Mensajería",
            description = "WhatsApp y contactos asociados.",
            tools = emptySet()
        ),
        NexoSkillDefinition(
            id = "chatgpt",
            name = "ChatGPT",
            description = "Abrir y automatizar ChatGPT.",
            tools = setOf("chatgpt")
        ),
        NexoSkillDefinition(
            id = "vision",
            name = "Vision",
            description = "Cámara y análisis visual bajo demanda.",
            tools = setOf("vision")
        ),
        NexoSkillDefinition(
            id = "screen_control",
            name = "Control de pantalla",
            description = "Tocar, escribir, volver e ir al inicio.",
            tools = setOf("tap_text", "type_text", "back", "home")
        ),
        NexoSkillDefinition(
            id = "device",
            name = "Dispositivo",
            description = "Volumen y brillo.",
            tools = setOf("set_volume", "set_brightness")
        ),
        NexoSkillDefinition(
            id = "modes",
            name = "Modes",
            description = "Activar o desactivar perfiles como Modo carro.",
            tools = setOf("car_mode")
        )
    )

    fun isEnabled(context: Context, skillId: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(skillId, true)

    fun setEnabled(context: Context, skillId: String, enabled: Boolean) {
        if (isEnabled(context, skillId) == enabled) return

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(skillId, enabled)
            .apply()

        val skill = definitions.firstOrNull { it.id == skillId }
        NexoActionLog.add(
            context,
            "Skill",
            (skill?.name ?: skillId) + if (enabled) " activada" else " desactivada"
        )
    }

    fun skillForTool(tool: String): NexoSkillDefinition? =
        definitions.firstOrNull { tool in it.tools }

    fun isToolEnabled(context: Context, tool: String): Boolean {
        val skill = skillForTool(tool) ?: return true
        return isEnabled(context, skill.id)
    }

    fun enabledNames(context: Context): List<String> =
        definitions.filter { isEnabled(context, it.id) }.map { it.name }
}
