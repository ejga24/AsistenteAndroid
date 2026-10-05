package com.eliel.asistente

data class NexoWakeMatch(
    val found: Boolean,
    val command: String = ""
)

object NexoWakePhrase {
    private val pattern = Regex(
        """(^|[\s,.:;!?¿¡-])nexo(?=$|[\s,.:;!?¿¡-])""",
        setOf(RegexOption.IGNORE_CASE)
    )

    fun extract(normalizedSpeech: String): NexoWakeMatch {
        val match = pattern.find(normalizedSpeech) ?: return NexoWakeMatch(false)
        val command = normalizedSpeech
            .substring(match.range.last + 1)
            .trim()
            .trimStart(',', '.', ':', ';', '!', '?', '¿', '¡', '-', ' ')
        return NexoWakeMatch(true, command)
    }
}
