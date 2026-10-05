package com.eliel.asistente

import android.content.Context

object NexoPlanSessionStore {
    private const val LEGACY_PREFS = "nexo_plan_session"
    private const val PRIVATE_KEY = "plan_session"
    private const val MAX_SESSION_AGE_MS = 30 * 60 * 1000L

    @Volatile
    private var processExecutionActive = false

    private data class StoredSession(
        val summary: String,
        val started: Long
    )

    private fun read(context: Context): StoredSession? {
        NexoPrivateStore.getString(context, PRIVATE_KEY)?.let { raw ->
            val parts = raw.split("|", limit = 2)
            val started = parts.firstOrNull()?.toLongOrNull() ?: return null
            val summary = parts.getOrNull(1).orEmpty()
            return StoredSession(summary, started)
        }

        val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        if (!legacy.getBoolean("active", false)) return null

        val summary = legacy.getString("summary", null).orEmpty()
        val started = legacy.getLong("started", 0L)
        val payload = "${started}|${summary.take(240)}"
        if (NexoPrivateStore.putString(context, PRIVATE_KEY, payload)) {
            legacy.edit().clear().apply()
        }
        return StoredSession(summary, started)
    }

    fun recoverInterruptedIfNeeded(context: Context): String? {
        if (processExecutionActive) return null
        val session = read(context) ?: return null
        clear(context)

        if (session.started <= 0L) return session.summary.ifBlank { null }
        val age = System.currentTimeMillis() - session.started
        if (age < 0L || age > MAX_SESSION_AGE_MS) return null
        return session.summary.ifBlank { null }
    }

    fun markStarted(context: Context, summary: String) {
        processExecutionActive = true
        val payload = "${System.currentTimeMillis()}|${summary.take(240)}"
        if (!NexoPrivateStore.putString(context, PRIVATE_KEY, payload)) {
            processExecutionActive = false
            NexoRuntimeState.markIssue(
                context,
                "Plan Engine",
                "No pude proteger el estado de ejecución del plan"
            )
        }
        context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    fun clear(context: Context) {
        processExecutionActive = false
        NexoPrivateStore.remove(context, PRIVATE_KEY)
        context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
