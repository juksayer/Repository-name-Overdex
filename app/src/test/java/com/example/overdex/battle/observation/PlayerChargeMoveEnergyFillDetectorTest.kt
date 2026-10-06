package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerChargeMoveEnergyFillDetectorTest {
    @Test fun `persistent upward fill step emits once at the first changed frame`() {
        val detector = PlayerChargeMoveEnergyFillDetector()
        assertNull(detector.accept(listOf(0.10f, 0.20f), 0L))
        assertNull(detector.accept(listOf(0.17f, 0.28f), 500_000_000L))

        val measured = detector.accept(listOf(0.17f, 0.28f), 550_000_000L)

        requireNotNull(measured)
        assertEquals(500_000_000L, measured.observedAtNanos)
        assertEquals(listOf(0, 1), measured.changedSlots)
        assertEquals(listOf(0.10f, 0.20f), measured.beforeBySlot)
        assertEquals(listOf(0.17f, 0.28f), measured.afterBySlot)
        assertNull(detector.accept(listOf(0.17f, 0.28f), 600_000_000L))
    }

    @Test fun `one frame flash and large screen transition do not emit energy testimony`() {
        val detector = PlayerChargeMoveEnergyFillDetector()
        assertNull(detector.accept(listOf(0.10f, 0.10f), 0L))
        assertNull(detector.accept(listOf(0.18f, 0.18f), 500_000_000L))
        assertNull(detector.accept(listOf(0.10f, 0.10f), 550_000_000L))
        assertNull(detector.accept(listOf(0.80f, 0.80f), 1_000_000_000L))
        assertNull(detector.accept(listOf(0.80f, 0.80f), 1_050_000_000L))
    }

    @Test fun `filled slot may stay fixed while the other slot gains energy`() {
        val detector = PlayerChargeMoveEnergyFillDetector()
        assertNull(detector.accept(listOf(1.0f, 0.20f), 0L))
        assertNull(detector.accept(listOf(1.0f, 0.28f), 500_000_000L))
        val measured = detector.accept(listOf(1.0f, 0.28f), 550_000_000L)
        requireNotNull(measured)
        assertEquals(listOf(1), measured.changedSlots)
    }
}
