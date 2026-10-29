package com.example.overdex.battle.observation

import com.example.overdex.data.BattleCalibration
import org.junit.Assert.*
import org.junit.Test

class PointerCoordinatesTest {
    @Test fun `center tap maps to sole move only with witnessed single control layout`() {
        val controls = com.example.overdex.model.AnchorRegion(.1f, .8f, .8f, .15f)
        val center = PointerCoordinates(500f, 850f)
        assertNull(PointerCoordinateParser.chargedButtonSlot(center, 1000, 1000, controls))
        assertEquals(1, PointerCoordinateParser.chargedButtonSlot(center, 1000, 1000, controls, singleCentered = true))
        assertNull(PointerCoordinateParser.chargedButtonSlot(PointerCoordinates(250f, 850f),
            1000, 1000, controls, singleCentered = true))
        assertNull(PointerCoordinateParser.chargedButtonSlot(PointerCoordinates(500f, 750f),
            1000, 1000, controls, singleCentered = true))
    }
    @Test fun `pointer trace over separator does not discard otherwise exact coordinates`() {
        assertEquals(PointerCoordinates(328f, 2187f),
            PointerCoordinateParser.parse("P:1/1 X:328.0 Y} 2187.0", 1080, 2400))
    }
    @Test fun `real full-display coordinates are not scaled or shifted by system bars`() {
        val point = PointerCoordinateParser.parse("P: 1 / 1 X: 328.0 Y: 2187.0", 1080, 2400)!!
        assertEquals(328f, point.x)
        assertEquals(2187f, point.y)
        assertEquals(1, PointerCoordinateParser.chargedButtonSlot(point, 1080, 2400,
            BattleCalibration().trainerChargeMoveControlsRegion))
        // Same source fractions on a second physical display.
        assertEquals(1, PointerCoordinateParser.chargedButtonSlot(PointerCoordinates(656f, 4374f), 2160, 4800,
            BattleCalibration().trainerChargeMoveControlsRegion))
    }

    @Test fun `release deltas multi touch velocities and malformed coordinates are never taps`() {
        listOf("P: 0 / 1 dX: 2.0 dY: -1.0", "P: 1 / 1 dX: 2.0 dY: 1.0",
            "P: 2 / 2 X: 328.0 Y: 2187.0", "P: 1 / 2 X: 328.0 Y: 2187.0",
            "P: 1 / 1 Xv: 328.0 Yv: 2187.0", "P: 1 / 1 X: 328.O Y: 2187.0",
            "P: 1 / 1 X: 328.0 Y: 2401.0", "P: 1 / 1 X: -12 Y: 2187",
            "X: 328 Y: 2187", "P: 1 / 1 X: 328 Y: 2187 X: 748").forEach {
            assertNull(it, PointerCoordinateParser.parse(it, 1080, 2400))
        }
    }

    @Test fun `ordinary fast tapping and gap between buttons do not select a charge button`() {
        val controls = BattleCalibration().trainerChargeMoveControlsRegion
        assertNull(PointerCoordinateParser.chargedButtonSlot(PointerCoordinates(734f, 1534f), 1080, 2400, controls))
        assertNull(PointerCoordinateParser.chargedButtonSlot(PointerCoordinates(540f, 2180f), 1080, 2400, controls))
        assertEquals(2, PointerCoordinateParser.chargedButtonSlot(PointerCoordinates(750f, 2180f), 1080, 2400, controls))
    }
}
