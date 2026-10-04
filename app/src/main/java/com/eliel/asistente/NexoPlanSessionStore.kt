package com.eliel.asistente

import android.content.Context

object NexoPlanSessionStore {
    private const val PREFS = "nexo_plan_session"
    private const val KEY_ACTIVE = "active"
    private const val KEY_SUMMARY = "summary"
    private const val KEY_STARTED = "started"

    @Volatile
    private var processExecutionActive = false

    fun recoverInterruptedIfNeeded(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ACTIVE, false) || processExecutionActive) return null

        val summary = prefs.getString(KEY_SUMMARY, null)
        prefs.edit().clear().apply()
        return summary
    }

    fun markStarted(context: Context, summary: String) {
        processExecutionActive = true
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_SUMMARY, summary.take(240))
            .putLong(KEY_STARTED, System.currentTimeMillis())
            .apply()
    }

    fun clear(context: Context) {
        processExecutionActive = false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
