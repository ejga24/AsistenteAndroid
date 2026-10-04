package com.eliel.asistente

import android.content.Context

enum class NexoMode(val id: String, val displayName: String) {
    NORMAL("normal", "Normal"),
    CAR("car", "Modo carro"),
    HOME("home", "Casa"),
    WORK("work", "Trabajo"),
    KIOSK("kiosk", "Kiosco")
}

object NexoModeManager {
    private const val PREFS = "nexo_modes"
    private const val KEY_CURRENT = "current_mode"

    fun current(context: Context): NexoMode {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CURRENT, NexoMode.NORMAL.id)
            ?: NexoMode.NORMAL.id
        return NexoMode.entries.firstOrNull { it.id == id } ?: NexoMode.NORMAL
    }

    fun set(context: Context, mode: NexoMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CURRENT, mode.id)
            .apply()

        NexoActionLog.add(context, "Modo", mode.displayName)
    }

    fun isProductionEnabled(mode: NexoMode): Boolean =
        mode == NexoMode.NORMAL || mode == NexoMode.CAR
}
