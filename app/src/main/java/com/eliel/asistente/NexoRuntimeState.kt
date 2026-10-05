package com.eliel.asistente

import android.content.Context
import org.json.JSONObject

data class NexoRuntimeIssue(
    val source: String,
    val message: String,
    val timestamp: Long
)

object NexoRuntimeState {
    private const val LEGACY_PREFS = "nexo_runtime_state"
    private const val PRIVATE_KEY = "runtime_issue"
    private const val MAX_AGE_MS = 15 * 60 * 1000L

    private fun payload(source: String, message: String, timestamp: Long): String =
        JSONObject().apply {
            put("source", source.take(80))
            put("message", message.take(220))
            put("timestamp", timestamp)
        }.toString()

    fun markIssue(context: Context, source: String, message: String) {
        val saved = NexoPrivateStore.putString(
            context,
            PRIVATE_KEY,
            payload(source, message, System.currentTimeMillis())
        )
        if (saved) {
            context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
    }

    fun clearIssue(context: Context, source: String? = null) {
        val current = currentIssue(context)
        if (source == null || current?.source == source) {
            NexoPrivateStore.remove(context, PRIVATE_KEY)
            context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
    }

    fun currentIssue(context: Context): NexoRuntimeIssue? {
        val encrypted = NexoPrivateStore.getString(context, PRIVATE_KEY)
        val issue = encrypted?.let(::parse) ?: migrateLegacy(context) ?: return null

        if (issue.timestamp <= 0L ||
            System.currentTimeMillis() - issue.timestamp !in 0..MAX_AGE_MS
        ) {
            NexoPrivateStore.remove(context, PRIVATE_KEY)
            return null
        }
        return issue
    }

    private fun parse(raw: String): NexoRuntimeIssue? = runCatching {
        val item = JSONObject(raw)
        NexoRuntimeIssue(
            source = item.optString("source"),
            message = item.optString("message"),
            timestamp = item.optLong("timestamp")
        )
    }.getOrNull()?.takeIf { it.source.isNotBlank() && it.message.isNotBlank() }

    private fun migrateLegacy(context: Context): NexoRuntimeIssue? {
        val prefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val source = prefs.getString("issue_source", null) ?: return null
        val message = prefs.getString("issue_message", null) ?: return null
        val time = prefs.getLong("issue_time", 0L)
        val issue = NexoRuntimeIssue(source, message, time)

        if (NexoPrivateStore.putString(context, PRIVATE_KEY, payload(source, message, time))) {
            prefs.edit().clear().apply()
        }
        return issue
    }
}
