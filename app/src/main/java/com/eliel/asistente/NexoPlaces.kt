package com.eliel.asistente

import android.content.Context

object NexoPlaces {
    private const val LEGACY_PREFS = "assistant_places"

    fun save(context: Context, key: String, value: String) {
        NexoPrivateStore.putString(context, key, value.trim())
        context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(key)
            .apply()
    }

    fun get(context: Context, key: String): String? {
        NexoPrivateStore.getString(context, key)?.let { return it }

        val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .getString(key, null)
            ?.trim()
            .takeUnless { it.isNullOrBlank() }
            ?: return null

        runCatching {
            NexoPrivateStore.putString(context, key, legacy)
            context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(key)
                .apply()
        }

        return legacy
    }

    fun contains(context: Context, key: String): Boolean =
        get(context, key) != null
}
