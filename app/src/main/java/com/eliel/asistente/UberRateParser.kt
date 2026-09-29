package com.eliel.asistente

import java.text.Normalizer
import java.util.Locale

object UberRateParser {

    private val strictEstimatedRate = Regex(
        """(?:usd|b\s*/?\.?|\$)?\s*(\d{1,2}(?:[.,]\d{1,3})?)\s*[/／]\s*(?:km|kilometros?|kilómetros?)\s*\(\s*estimado\s*\)""",
        RegexOption.IGNORE_CASE
    )

    fun extract(parts: List<String>): Double? {
        if (parts.isEmpty()) return null

        val normalized = parts.map(::normalize).filter { it.isNotBlank() }

        // Una oferta válida debe tener una acción visible del popup actual.
        val hasCurrentOfferAction = normalized.any { line ->
            line.contains("aceptar") || line.contains("me interesa")
        }
        if (!hasCurrentOfferAction) return null

        // La tarifa solo vale si "/km (estimado)" está en ESA MISMA línea.
        for (line in normalized) {
            val match = strictEstimatedRate.find(line) ?: continue
            val value = match.groupValues.getOrNull(1)
                ?.replace(',', '.')
                ?.toDoubleOrNull()
                ?: continue
            if (value in 0.05..20.0) return value
        }

        return null
    }

    private fun normalize(input: String): String =
        Normalizer.normalize(input.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
}
