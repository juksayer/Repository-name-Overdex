package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Test

class TrainerInactiveTimerOverlayClearanceMeasurerTest {
    @Test fun `measures colorful fractions without relying on Android color utilities`() {
        val teal = 0xff1edcb4.toInt()
        val gray = 0xff777777.toInt()
        assertEquals(0.5f, TrainerInactiveTimerOverlayClearanceMeasurer.colorfulFraction(intArrayOf(teal, gray)), 0.001f)
    }
}
