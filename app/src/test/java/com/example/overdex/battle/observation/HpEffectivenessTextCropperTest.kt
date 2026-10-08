package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HpEffectivenessTextCropperTest {
    @Test
    fun `places a wide OCR strip immediately above the tracked bar`() {
        val rect = HpEffectivenessTextCropper.resolveBounds(
            sourceWidth = 675,
            sourceHeight = 1500,
            hpRegionBounds = BattleCropBounds(200, 300, 655, 1146),
            measurement = ActiveHpBarMeasurement(
                left = 80,
                top = 420,
                right = 330,
                bottom = 440,
                filledFraction = 0.6f,
                confidence = 0.9f,
            ),
        )

        requireNotNull(rect)
        assertEquals(192, rect.left)
        assertEquals(645, rect.top)
        assertEquals(618, rect.right)
        assertEquals(725, rect.bottom)
    }

    @Test
    fun `lets text strip cross the HP search region edge before clamping to the screen`() {
        val rect = HpEffectivenessTextCropper.resolveBounds(
            sourceWidth = 675,
            sourceHeight = 1500,
            hpRegionBounds = BattleCropBounds(337, 219, 659, 1213),
            measurement = ActiveHpBarMeasurement(
                left = 8,
                top = 200,
                right = 208,
                bottom = 212,
                filledFraction = 0.8f,
                confidence = 0.9f,
            ),
        )

        requireNotNull(rect)
        assertEquals(275, rect.left)
        assertEquals(374, rect.top)
        assertEquals(615, rect.right)
        assertEquals(422, rect.bottom)
        // The opponent HP search region begins at x=337. The effectiveness
        // phrase is deliberately allowed to extend left of it, so the opening
        // word in "NOT VERY EFFECTIVE" is not discarded.
        assertTrue(rect.left < 337)
    }

    @Test
    fun `rejects an invalid bar measurement`() {
        assertNull(
            HpEffectivenessTextCropper.resolveBounds(
                sourceWidth = 300,
                sourceHeight = 500,
                hpRegionBounds = BattleCropBounds(0, 0, 300, 500),
                measurement = ActiveHpBarMeasurement(30, 40, 30, 50, 0.5f, 0.5f),
            )
        )
    }
}
