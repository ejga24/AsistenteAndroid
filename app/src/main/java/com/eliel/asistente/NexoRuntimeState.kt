package com.eliel.asistente

import android.content.Context

data class NexoRuntimeIssue(
    val source: String,
    val message: String,
    val timestamp: Long
)

object NexoRuntimeState {
    private const val PREFS = "nexo_runtime_state"
    private const val KEY_SOURCE = "issue_source"
    private const val KEY_MESSAGE = "issue_message"
    private const val KEY_TIME = "issue_time"
    private const val MAX_AGE_MS = 15 * 60 * 1000L

    fun markIssue(context: Context, source: String, message: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SOURCE, source)
            .putString(KEY_MESSAGE, message.take(220))
            .putLong(KEY_TIME, System.currentTimeMillis())
            .apply()
    }

    fun clearIssue(context: Context, source: String? = null) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val currentSource = prefs.getString(KEY_SOURCE, null)
        if (source == null || source == currentSource) {
            prefs.edit().clear().apply()
        }
    }

    fun currentIssue(context: Context): NexoRuntimeIssue? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val source = prefs.getString(KEY_SOURCE, null) ?: return null
        val message = prefs.getString(KEY_MESSAGE, null) ?: return null
        val time = prefs.getLong(KEY_TIME, 0L)
        if (time <= 0L || System.currentTimeMillis() - time > MAX_AGE_MS) {
            prefs.edit().clear().apply()
            return null
        }
        return NexoRuntimeIssue(source, message, time)
    }
}
