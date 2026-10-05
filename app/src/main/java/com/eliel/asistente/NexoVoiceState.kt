package com.eliel.asistente

import android.content.Context

object NexoVoiceState {
    private const val PREFS = "nexo_voice"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        if (isEnabled(context) == enabled) return

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()

        NexoActionLog.add(
            context,
            "Voice Core",
            if (enabled) "Escucha activada" else "Escucha pausada"
        )
    }
}
