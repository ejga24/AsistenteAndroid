package com.eliel.asistente

import android.app.Activity
import android.content.Context
import android.view.WindowManager

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
        if (current(context) == mode) return

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CURRENT, mode.id)
            .apply()

        NexoActionLog.add(context, "Modo", mode.displayName)
    }

    fun applyWindowProfile(activity: Activity, mode: NexoMode = current(activity)) {
        when (mode) {
            NexoMode.CAR -> {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                activity.window.attributes = activity.window.attributes.apply {
                    screenBrightness = 0.85f
                }
            }

            else -> {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                activity.window.attributes = activity.window.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }

    fun isProductionEnabled(mode: NexoMode): Boolean =
        mode == NexoMode.NORMAL || mode == NexoMode.CAR
}
