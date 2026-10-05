package com.eliel.asistente

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NexoWakePhraseTest {

    @Test
    fun detectsStandaloneWakeWord() {
        val result = NexoWakePhrase.extract("nexo abre spotify")
        assertTrue(result.found)
        assertEquals("abre spotify", result.command)
    }

    @Test
    fun detectsWakeWordAfterPunctuation() {
        val result = NexoWakePhrase.extract("hola, nexo: abre waze")
        assertTrue(result.found)
        assertEquals("abre waze", result.command)
    }

    @Test
    fun rejectsWakeInsideAnotherWord() {
        assertFalse(NexoWakePhrase.extract("conexionexo prueba").found)
        assertFalse(NexoWakePhrase.extract("anexo documento").found)
    }

    @Test
    fun wakeOnlyHasBlankCommand() {
        val result = NexoWakePhrase.extract("nexo")
        assertTrue(result.found)
        assertEquals("", result.command)
    }
    @Test
    fun detectsWakeWordWithSpanishOpeningPunctuation() {
        val question = NexoWakePhrase.extract("¿NEXO, abre Spotify?")
        assertTrue(question.found)
        assertEquals("abre Spotify?", question.command)

        val exclamation = NexoWakePhrase.extract("¡NEXO! abre Waze")
        assertTrue(exclamation.found)
        assertEquals("abre Waze", exclamation.command)
    }

}
