package com.eliel.asistente

import android.content.Context

object NexoPlaces {
    private const val LEGACY_PREFS = "assistant_places"

    fun save(context: Context, key: String, value: String): Boolean {
        val saved = NexoPrivateStore.putString(context, key, value.trim())
        if (saved) {
            context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(key)
                .apply()
        }
        return saved
    }

    fun get(context: Context, key: String): String? {
        NexoPrivateStore.getString(context, key)?.let { return it }

        val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .getString(key, null)
            ?.trim()
            .takeUnless { it.isNullOrBlank() }
            ?: return null

        if (NexoPrivateStore.putString(context, key, legacy)) {
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
