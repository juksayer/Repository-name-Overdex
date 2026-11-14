package com.example.overdex.battle.observation

import javax.imageio.ImageIO
import org.junit.Assert.*
import org.junit.Test

class RecordedChargedImpactBoundaryTest {
    private fun measure(bottom: Int): CompleteHpBarSample? {
        val image = ImageIO.read(javaClass.getResource("/battle/hp/2026-11-13-dual-view/pixel-final-shadow-ball.png"))
        // The preserved fixture includes the complete bar. Its provenance records video resampling.
        // Intersect the calibrated territory with actual fixture pixels; never synthesize missing pixels.
        return ChargedImpactHpBarMeasurer.acquireComplete(672,1419,
            ActiveHpBarMeasurement(370,551,562,569,.7f,.95f),
            BattleCropBounds(228,850,447,bottom)) { x,y ->
                assertTrue(x in 228 until 447 && y in 850 until 915)
                image.getRGB(x-228,y-850)
            }
    }

    @Test fun `old charged territory cannot accept a clipped lower outline`() {
        assertNull(measure(887))
    }

    @Test fun `expanded territory recovers complete recorded outline without inventing empty HP`() {
        val sample = requireNotNull(measure(915))
        assertTrue(sample.bounds.bottom > 887)
        assertTrue(sample.damageColumns > 100)
        assertNotEquals(HpBarFillColor.EMPTY,sample.hpSample?.fillColor)
    }
}
