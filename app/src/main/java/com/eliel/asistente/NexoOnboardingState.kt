package com.eliel.asistente

import android.content.Context

object NexoOnboardingState {
    private const val PREFS = "nexo_onboarding"
    private const val KEY_PRESENTED = "setup_presented"

    fun shouldPresent(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_PRESENTED, false)) return false
        return !NexoSystemHealth.snapshot(context).requiredReady
    }

    fun markPresented(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PRESENTED, true)
            .apply()
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PRESENTED)
            .apply()
    }
}
