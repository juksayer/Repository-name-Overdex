package com.example.overdex.battle.replay

import org.junit.Assert.*
import org.junit.Test

class ReplayChargeThrowTimingTest {
    @Test fun `both sides release at the apex and descend while the icon travels independently`() {
        for (player in listOf(true, false)) {
            val side = if (player) "PLAYER" else "OPPONENT"
            fun pose(progress: Float) = ReplayChargePose.at(
                ReplayChargedMoveAction(side, "Rock Tomb", 0, progress, phase = ReplayChargePhase.STRIKING), player)
            val peak = pose(.65f)
            assertEquals(if (player) -48f else -24f, peak.risePixels, .0001f)
            assertEquals(0f, pose(.64f).projectileProgress, 0f)
            assertEquals(0f, peak.projectileProgress, 0f)
            val descending = (66..100).map { pose(it / 100f) }
            assertTrue(descending.first().projectileProgress > 0f)
            assertTrue(descending.first().risePixels > peak.risePixels)
            assertTrue(descending.zipWithNext().all { (a, b) ->
                b.risePixels >= a.risePixels && b.projectileProgress > a.projectileProgress
            })
            assertTrue(descending.all { it.projectileRisePixels == peak.risePixels })
            assertEquals(0f, pose(1f).risePixels, 0f)
            assertEquals(1f, pose(1f).projectileProgress, 0f)
            assertEquals(peak, pose(.65f)) // reverse seeking restores the same apex
        }
    }

    @Test fun `descent eases away from the apex instead of snapping down`() {
        fun height(p: Float) = ReplayChargePose.at(
            ReplayChargedMoveAction("PLAYER", "Rock Tomb", 0, p, phase = ReplayChargePhase.STRIKING), true).risePixels
        assertTrue(height(.66f) - height(.65f) < .2f)
        // About halfway through the descent, the body has not already snapped home.
        assertTrue(height(.82f) in -28f..-20f)
        assertTrue(height(1f) - height(.99f) < .2f)
    }

    @Test fun `sprite growth and shrink each take three quarters of a second`() {
        val start = 1_000_000_000L
        val impact = 7_800_000_000L
        fun focus(cursor: Long): Float {
            return ReplayChargePose.qteFocusAmount(ReplayChargeFocus(start, impact), cursor)
        }
        assertTrue(focus(start + 250_000_000L) in .2f.. .3f)
        assertEquals(1f, focus(start + 750_000_000L), 0f)
        assertEquals(focus(start + 250_000_000L), focus(impact + 500_000_000L), 0f)
        assertEquals(1f, focus(impact), 0f)
        assertEquals(0f, focus(impact + 750_000_000L), 0f)
        assertEquals(0f, focus(start), 0f)
    }
}
