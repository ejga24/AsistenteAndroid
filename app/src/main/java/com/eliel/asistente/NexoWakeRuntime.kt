package com.eliel.asistente

import android.content.Context

object NexoWakeRuntime {

    data class Status(
        val configuredEngine: String,
        val displayName: String,
        val local: Boolean,
        val productionReady: Boolean
    )

    fun status(context: Context): Status {
        val prefs = context.getSharedPreferences(NexoWakeConfig.PREFS, Context.MODE_PRIVATE)
        val configured = prefs.getString(
            NexoWakeConfig.KEY_ENGINE,
            NexoWakeConfig.ENGINE_ANDROID_FALLBACK
        ) ?: NexoWakeConfig.ENGINE_ANDROID_FALLBACK

        val requestedLocal = configured == NexoWakeConfig.ENGINE_LOCAL

        // RC1 has no validated local engine implementation yet. Never report
        // a local engine as active until the runtime can actually instantiate it.
        return if (requestedLocal) {
            Status(
                configuredEngine = configured,
                displayName = "Android Speech (fallback · hotword local pendiente)",
                local = false,
                productionReady = true
            )
        } else {
            Status(
                configuredEngine = configured,
                displayName = NexoWakeConfig.displayName(configured),
                local = false,
                productionReady = true
            )
        }
    }

    fun setEngine(context: Context, engine: String) {
        require(
            engine == NexoWakeConfig.ENGINE_ANDROID_FALLBACK ||
                engine == NexoWakeConfig.ENGINE_LOCAL
        )
        context.getSharedPreferences(NexoWakeConfig.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(NexoWakeConfig.KEY_ENGINE, engine)
            .apply()
    }
}
