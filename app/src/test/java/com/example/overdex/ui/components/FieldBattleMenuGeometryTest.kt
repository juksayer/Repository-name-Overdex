package com.example.overdex.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FieldBattleMenuGeometryTest {
    @Test fun pixelReferenceLeavesNativePartySpritesVisible() {
        assertEquals(75, FieldBattleMenuGeometry.leftPx(1080))
        assertEquals(930, FieldBattleMenuGeometry.widthPx(1080))
        assertEquals(1001, FieldBattleMenuGeometry.topPx(2280))
        assertEquals(1485, FieldBattleMenuGeometry.bottomPx(2280))
        assertEquals(484, FieldBattleMenuGeometry.heightPx(2280))
    }

    @Test fun actualWindowInsetDoesNotMoveTheMenuOnTheScreenshot() {
        for (inset in listOf(0, 84, 132, -24)) {
            val windowTop = FieldBattleMenuGeometry.windowTopPx(2280, inset)
            assertEquals(1001, windowTop + inset)
        }
    }

    @Test fun expandedNativeWindowStartsBelowLeagueAndCpRestrictions() {
        val restriction = com.example.overdex.battle.observation.TeamSelectCalibration.measured1080x2400.restrictions
        for (height in listOf(2280, 2400, 4560)) {
            val restrictionBottom = ((restriction.y + restriction.height) * height).toInt()
            for (inset in listOf(0, 84, 132, -24)) {
                val screenTop = FieldBattleMenuGeometry.windowTopPx(height, inset) + inset
                org.junit.Assert.assertTrue(screenTop > restrictionBottom)
                assertEquals(FieldBattleMenuGeometry.bottomPx(height), screenTop + FieldBattleMenuGeometry.heightPx(height))
            }
        }
    }

    @Test fun physicalCoordinatesScaleWithoutSubtractingSystemBars() {
        assertEquals(150, FieldBattleMenuGeometry.leftPx(2160))
        assertEquals(1860, FieldBattleMenuGeometry.widthPx(2160))
        assertEquals(2002, FieldBattleMenuGeometry.topPx(4560))
        assertEquals(2970, FieldBattleMenuGeometry.bottomPx(4560))
        assertEquals(1054, FieldBattleMenuGeometry.topPx(2400))
        assertEquals(1563, FieldBattleMenuGeometry.bottomPx(2400))
    }
}
