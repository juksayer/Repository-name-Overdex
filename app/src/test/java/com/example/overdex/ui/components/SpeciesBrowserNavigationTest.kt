package com.example.overdex.ui.components

import org.junit.Assert.*
import org.junit.Test

class SpeciesBrowserNavigationTest {
    @Test fun `preview rows remain ahead in either direction at slow and fast speeds`() {
        for (rows in 4..15) for (fast in listOf(false, true)) for (direction in listOf(1, -1)) {
            val indices = if (direction == 1) (0..1024).toList() else (1024 downTo 0).toList()
            var first = if (direction == 1) 0 else 1025 - rows
            for (selected in indices) {
                first = SpeciesBrowserNavigation.firstVisible(selected, first, rows, 1025, direction, fast)
                assertTrue(selected in first until first + rows)
                val ahead = if (fast) 3 else 1
                if (direction == 1) assertTrue(first + rows - 1 >= (selected + ahead).coerceAtMost(1024))
                else assertTrue(first <= (selected - ahead).coerceAtLeast(0))
            }
        }
    }

    @Test fun `large jumps and short or empty result lists stay in bounds`() {
        assertEquals(0, SpeciesBrowserNavigation.firstVisible(0, 45, 8, 0, 1, true))
        assertEquals(0, SpeciesBrowserNavigation.firstVisible(1, 45, 8, 2, 1, true))
        val first = SpeciesBrowserNavigation.firstVisible(950, 0, 8, 1025, 1, true)
        assertTrue(950 in first until first + 8)
        assertTrue(953 in first until first + 8)
        assertEquals(0, SpeciesBrowserNavigation.firstVisible(-1, 150, 8, 1025, 1, false))
    }

    @Test fun `holding longer increases speed with a bounded maximum`() {
        fun speed(ms: Long) = 1000.0 * SpeciesBrowserNavigation.repeatStep(ms) / SpeciesBrowserNavigation.repeatDelayMillis(ms)
        assertTrue(speed(0) < speed(1000))
        assertTrue(speed(1000) < speed(4000))
        assertTrue(speed(4000) < speed(10000))
        assertEquals(40L, SpeciesBrowserNavigation.repeatDelayMillis(60000))
        assertEquals(8, SpeciesBrowserNavigation.repeatStep(60000))
    }

    @Test fun `region tabs cover every species once including Hisui`() {
        val ids = RegistrationRegion.entries.drop(1).flatMap { it.dexRange.toList() }
        assertEquals((1..1025).toList(), ids)
        assertTrue(899 in RegistrationRegion.HISUI.dexRange)
        assertFalse(899 in RegistrationRegion.PALDEA.dexRange)
    }
}
