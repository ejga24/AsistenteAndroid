package com.eliel.asistente

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UberRateParserTest {

    private fun offer(rateLine: String, action: String = "Aceptar") = listOf(
        "UberX",
        rateLine,
        "A 3 min (0.9 km)",
        "Viaje: 5 min (1.7 km)",
        action
    )

    @Test fun screenshot_034() = assertEquals(0.34, UberRateParser.extract(offer("USD0.34/km (estimado)"))!!, 0.0001)
    @Test fun screenshot_037() = assertEquals(0.37, UberRateParser.extract(offer("USD0.37/km (estimado)"))!!, 0.0001)
    @Test fun screenshot_039() = assertEquals(0.39, UberRateParser.extract(offer("USD0.39/km (estimado)"))!!, 0.0001)
    @Test fun screenshot_045() = assertEquals(0.45, UberRateParser.extract(offer("USD0.45/km (estimado)"))!!, 0.0001)
    @Test fun screenshot_052() = assertEquals(0.52, UberRateParser.extract(offer("USD0.52/km (estimado)"))!!, 0.0001)
    @Test fun screenshot_054() = assertEquals(0.54, UberRateParser.extract(offer("USD0.54/km (estimado)"))!!, 0.0001)
    @Test fun screenshot_081() = assertEquals(0.81, UberRateParser.extract(offer("USD0.81/km (estimado)"))!!, 0.0001)
    @Test fun screenshot_084() = assertEquals(0.84, UberRateParser.extract(offer("USD0.84/km (estimado)"))!!, 0.0001)

    @Test fun me_interesa_is_valid_action() = assertEquals(
        0.54,
        UberRateParser.extract(offer("USD0.54/km (estimado)", "Me interesa"))!!,
        0.0001
    )

    @Test fun priority_extra_is_ignored_and_estimated_rate_wins() = assertEquals(
        0.52,
        UberRateParser.extract(
            listOf(
                "Uber Priority",
                "USD0.52/km (estimado)",
                "+USD0.46 por inicio de viaje",
                "Viaje: 8 min (3.1 km)",
                "Aceptar"
            )
        )!!,
        0.0001
    )

    @Test fun ignores_priority_extra_without_estimated_rate() {
        assertNull(UberRateParser.extract(listOf("Uber Priority", "+USD0.46 por inicio de viaje", "Aceptar")))
    }

    @Test fun rejects_total_money() {
        assertNull(UberRateParser.extract(listOf("USD4.18", "Viaje: 16 min", "Me interesa")))
    }

    @Test fun rejects_rate_when_estimado_is_separate_line() {
        assertNull(UberRateParser.extract(listOf("USD0.84/km", "(estimado)", "Aceptar")))
    }

    @Test fun rejects_rate_without_active_action() {
        assertNull(UberRateParser.extract(listOf("USD0.84/km (estimado)", "Viaje: 5 min")))
    }

    @Test fun rejects_our_own_old_banner_text() {
        assertNull(UberRateParser.extract(listOf("✅ ACEPTAR", "USD 1.00/km", "(estimado de Uber)")))
    }

    @Test fun rejects_our_new_banner_text_even_with_action_word() {
        assertNull(UberRateParser.extract(listOf("✅ ACEPTAR", "0.45/km")))
    }
}
