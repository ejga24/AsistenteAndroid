package com.eliel.asistente

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NexoActionLog {
    private const val LEGACY_PREFS = "nexo_action_log"
    private const val LEGACY_KEY = "entries"
    private const val PRIVATE_KEY = "action_log_entries"
    private const val MAX = 80

    private fun sanitizeDetail(action: String, value: String): String {
        if (value.isBlank()) return ""

        val normalizedAction = action.lowercase(Locale.ROOT)
        val sensitiveActionTokens = listOf(
            "type_text",
            "escribir texto",
            "credencial",
            "api key",
            "whatsapp",
            "mensaje",
            "message",
            "buscar",
            "search",
            "consulta",
            "query",
            "destino",
            "destination",
            "waze",
            "youtube",
            "spotify",
            "vision",
            "contacto",
            "contact"
        )
        if (sensitiveActionTokens.any(normalizedAction::contains)) {
            return "[contenido privado]"
        }

        return value
            .replace(Regex("""sk-[A-Za-z0-9_-]{12,}"""), "[credencial oculta]")
            .replace(Regex("""(?i)bearer\s+[A-Za-z0-9._-]{12,}"""), "Bearer [oculto]")
            .replace(
                Regex("""(?i)(password|contraseña|contrasena|pin)\s*[:=]\s*\S+"""),
                "$1=[oculto]"
            )
            .take(280)
    }

    private fun readEntries(context: Context): JSONArray {
        NexoPrivateStore.getString(context, PRIVATE_KEY)?.let { raw ->
            return runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        }

        val legacyPrefs = context.getSharedPreferences(
            LEGACY_PREFS,
            Context.MODE_PRIVATE
        )
        val legacyRaw = legacyPrefs.getString(LEGACY_KEY, null).orEmpty()
        if (legacyRaw.isBlank()) return JSONArray()

        val parsed = runCatching { JSONArray(legacyRaw) }.getOrElse { JSONArray() }
        if (NexoPrivateStore.putString(context, PRIVATE_KEY, parsed.toString())) {
            legacyPrefs.edit().remove(LEGACY_KEY).apply()
        }
        return parsed
    }

    private fun writeEntries(context: Context, entries: JSONArray): Boolean {
        val saved = NexoPrivateStore.putString(context, PRIVATE_KEY, entries.toString())
        if (saved) {
            context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(LEGACY_KEY)
                .apply()
        }
        return saved
    }

    fun add(context: Context, action: String, detail: String = "", success: Boolean = true) {
        val current = readEntries(context)
        val next = JSONArray()
        next.put(JSONObject().apply {
            put("time", System.currentTimeMillis())
            put("action", action)
            put("detail", sanitizeDetail(action, detail))
            put("success", success)
        })
        for (i in 0 until minOf(current.length(), MAX - 1)) next.put(current.get(i))
        writeEntries(context, next)
    }

    fun formatted(context: Context): String {
        val entries = readEntries(context)
        if (entries.length() == 0) return "Todavía no hay acciones registradas."
        val fmt = SimpleDateFormat("dd/MM/yyyy  hh:mm a", Locale("es", "PA"))
        return buildString {
            for (i in 0 until entries.length()) {
                val item = entries.getJSONObject(i)
                append(if (item.optBoolean("success", true)) "✓ " else "⚠ ")
                append(item.optString("action"))
                val detail = item.optString("detail")
                if (detail.isNotBlank()) append(" — ").append(detail)
                append("\n").append(fmt.format(Date(item.optLong("time"))))
                if (i < entries.length() - 1) append("\n\n")
            }
        }
    }

    fun clear(context: Context) {
        NexoPrivateStore.remove(context, PRIVATE_KEY)
        context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(LEGACY_KEY)
            .apply()
    }
}
