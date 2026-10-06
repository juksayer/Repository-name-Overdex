package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HpEffectivenessTextCropperTest {
    @Test
    fun `places a wide OCR strip immediately above the tracked bar`() {
        val rect = HpEffectivenessTextCropper.resolveBounds(
            cropWidth = 455,
            cropHeight = 846,
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
        assertEquals(0, rect.left)
        assertEquals(345, rect.top)
        assertEquals(418, rect.right)
        assertEquals(425, rect.bottom)
    }

    @Test
    fun `clamps the strip at the crop edges`() {
        val rect = HpEffectivenessTextCropper.resolveBounds(
            cropWidth = 300,
            cropHeight = 500,
            measurement = ActiveHpBarMeasurement(
                left = 175,
                top = 20,
                right = 295,
                bottom = 32,
                filledFraction = 0.8f,
                confidence = 0.9f,
            ),
        )

        requireNotNull(rect)
        assertEquals(133, rect.left)
        assertEquals(0, rect.top)
        assertEquals(300, rect.right)
        assertEquals(23, rect.bottom)
    }

    @Test
    fun `rejects an invalid bar measurement`() {
        assertNull(
            HpEffectivenessTextCropper.resolveBounds(
                cropWidth = 300,
                cropHeight = 500,
                measurement = ActiveHpBarMeasurement(30, 40, 30, 50, 0.5f, 0.5f),
            )
        )
    }
}
