package com.eliel.asistente

import java.text.Normalizer
import java.util.Locale

object UberRateParser {

    private val standard = Regex(
        """(?:usd|b\s*/?\.?|\$)?\s*(\d{1,2}(?:[.,]\d{1,3})?)\s*[/／]\s*(?:km|kilometros?|kilómetros?)\s*\(?\s*estimado\s*\)?""",
        RegexOption.IGNORE_CASE
    )

    private val withoutSlash = Regex(
        """(?:usd|b\s*/?\.?|\$)?\s*(\d{1,2}(?:[.,]\d{1,3})?)\s*(?:km|kilometros?|kilómetros?)\s*\(?\s*estimado\s*\)?""",
        RegexOption.IGNORE_CASE
    )

    private val rateOnly = Regex(
        """(?:usd|b\s*/?\.?|\$)?\s*(\d{1,2}(?:[.,]\d{1,3})?)\s*[/／]?\s*(?:km|kilometros?|kilómetros?)""",
        RegexOption.IGNORE_CASE
    )

    fun extract(parts: List<String>): Double? {
        if (parts.isEmpty()) return null

        val normalizedParts = parts.map(::normalize).filter { it.isNotBlank() }
        val combined = normalize(normalizedParts.joinToString(" "))

        val hasActiveOfferContext =
            combined.contains("viaje:") ||
            combined.contains("viaje ") ||
            combined.contains("aceptar") ||
            combined.contains("me interesa") ||
            Regex("""\ba\s+\d{1,2}\s+min\b""").containsMatchIn(combined)

        if (!hasActiveOfferContext) return null

        for (part in normalizedParts) {
            parse(standard.find(part))?.let { return it }
            parse(withoutSlash.find(part))?.let { return it }
        }

        parse(standard.find(combined))?.let { return it }
        parse(withoutSlash.find(combined))?.let { return it }

        if (combined.contains("estimado")) {
            parse(rateOnly.find(combined))?.let { return it }
        }

        return null
    }

    private fun parse(match: MatchResult?): Double? {
        val value = match?.groupValues?.getOrNull(1)
            ?.replace(',', '.')
            ?.toDoubleOrNull()
            ?: return null
        return value.takeIf { it in 0.05..20.0 }
    }

    private fun normalize(input: String): String =
        Normalizer.normalize(input.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
}
