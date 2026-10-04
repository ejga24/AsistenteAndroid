package com.eliel.asistente

/**
 * Contract for NEXO wake-word engines.
 *
 * The current Android recognizer remains the compatibility fallback.
 * A dedicated on-device hotword engine can replace it without changing
 * MainActivity or the rest of the agent architecture.
 */
interface NexoWakeEngine {
    val engineName: String
    val isLocal: Boolean
    val wakeWord: String

    fun start()
    fun pause()
    fun stop()
}

object NexoWakeConfig {
    const val WAKE_WORD = "nexo"
    const val PREFS = "nexo_wake"
    const val KEY_ENGINE = "wake_engine"
    const val ENGINE_ANDROID_FALLBACK = "android_speech"
    const val ENGINE_LOCAL = "local_hotword"

    fun displayName(engine: String): String = when (engine) {
        ENGINE_LOCAL -> "Local Hotword"
        else -> "Android Speech (compatibilidad)"
    }
}
