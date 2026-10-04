package com.eliel.asistente

import android.graphics.Bitmap

data class NexoVisionResult(
    val summary: String,
    val confidence: Float? = null
)

interface NexoVisionEngine {
    val engineName: String
    val requiresNetwork: Boolean

    fun analyze(bitmap: Bitmap): Result<NexoVisionResult>
}

object NexoVisionRuntime {
    const val ENGINE_NONE = "none"

    fun displayName(engine: String): String = when (engine) {
        ENGINE_NONE -> "Captura preparada · análisis pendiente"
        else -> engine
    }
}
