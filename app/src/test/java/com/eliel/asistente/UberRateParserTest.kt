package com.eliel.asistente

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UberRateParserTest {

    private fun offer(rateLine: String) = listOf(
        "UberX",
        rateLine,
        "A 3 min (0.9 km)",
        "Viaje: 5 min (1.7 km)",
        "Aceptar"
    )

    @Test fun screenshot_037() = assertEquals(0.37, UberRateParser.extract(offer("USD0.37/km (estimado)"))!!, 0.0001)
    @Test fun screenshot_055() = assertEquals(0.55, UberRateParser.extract(offer("USD0.55/km (estimado)"))!!, 0.0001)
    @Test fun screenshot_074() = assertEquals(0.74, UberRateParser.extract(offer("USD0.74/km (estimado)"))!!, 0.0001)
    @Test fun screenshot_075() = assertEquals(0.75, UberRateParser.extract(offer("USD0.75/km (estimado)"))!!, 0.0001)
    @Test fun screenshot_052() = assertEquals(0.52, UberRateParser.extract(offer("USD0.52/km (estimado)"))!!, 0.0001)
    @Test fun screenshot_054() = assertEquals(0.54, UberRateParser.extract(offer("USD0.54/km (estimado)"))!!, 0.0001)
    @Test fun screenshot_084() = assertEquals(0.84, UberRateParser.extract(offer("USD0.84/km (estimado)"))!!, 0.0001)
    @Test fun screenshot_108_priority() = assertEquals(
        1.08,
        UberRateParser.extract(listOf("Uber Priority", "USD1.08/km (estimado)", "A 3 min (1.0 km)", "Viaje: 4 min (1.2 km)", "Aceptar"))!!,
        0.0001
    )

    @Test fun fragmented_accessibility_nodes() = assertEquals(
        0.84,
        UberRateParser.extract(listOf("UberX", "USD0.84/km", "(estimado)", "Viaje: 7 min (2.3 km)", "Aceptar"))!!,
        0.0001
    )

    @Test fun slash_removed_by_accessibility() = assertEquals(
        0.74,
        UberRateParser.extract(listOf("UberX", "USD0.74 km (estimado)", "Viaje: 7 min (2.3 km)", "Aceptar"))!!,
        0.0001
    )

    @Test fun ignores_total_money_without_rate() {
        assertNull(UberRateParser.extract(listOf("UberX", "USD30.00", "Viaje: 10 min (4 km)", "Aceptar")))
    }

    @Test fun ignores_priority_bonus() {
        assertNull(UberRateParser.extract(listOf("Uber Priority", "+USD0.46 por inicio de viaje", "Viaje: 4 min (1.2 km)", "Aceptar")))
    }
}
