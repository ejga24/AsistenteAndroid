package com.eliel.asistente

import java.text.Normalizer
import java.util.Locale

object UberRateParser {

    // Única regla válida:
    // el precio por km y "(estimado)" deben estar en la MISMA línea.
    // Ejemplos válidos:
    // USD0.54/km (estimado)
    // USD 0.54 / km (estimado)
    // B/.0,54/km (estimado)
    private val strictEstimatedRate = Regex(
        """(?:usd|b\s*/?\.?|\$)?\s*(\d{1,2}(?:[.,]\d{1,3})?)\s*[/／]\s*(?:km|kilometros?|kilómetros?)\s*\(\s*estimado\s*\)""",
        RegexOption.IGNORE_CASE
    )

    fun extract(parts: List<String>): Double? {
        if (parts.isEmpty()) return null

        // No combinar líneas. Esto evita agarrar bonos, extras de Priority,
        // montos totales o texto viejo donde "estimado" aparezca en otra zona.
        for (part in parts) {
            val normalized = normalize(part)
            val match = strictEstimatedRate.find(normalized) ?: continue
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
