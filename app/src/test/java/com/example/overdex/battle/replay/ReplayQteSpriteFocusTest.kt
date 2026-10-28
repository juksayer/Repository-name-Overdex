package com.example.overdex.battle.replay

import org.junit.Assert.*
import org.junit.Test

class ReplayQteSpriteFocusTest {
    @Test fun `only player QTE changes sprite focus and charge release restores it`() {
        val cursor = 7 * SECOND
        val charging = actionAt(cursor)
        assertTrue(ReplayChargePose.qteSpriteScale(charging, cursor, player = true) > 1f)
        assertTrue(ReplayChargePose.qteSpriteScale(charging, cursor, player = false) < 1f)
        for (inactive in listOf(null, charging.copy(side = "OPPONENT"), charging.copy(side = "UNKNOWN"),
            charging.copy(phase = ReplayChargePhase.STRIKING))) {
            assertEquals(1f, ReplayChargePose.qteSpriteScale(inactive, cursor, player = true), 0f)
            assertEquals(1f, ReplayChargePose.qteSpriteScale(inactive, cursor, player = false), 0f)
        }
    }

    @Test fun `focus ramps at both QTE edges and restores the same pose after pause or reverse scrub`() {
        val early = 5 * SECOND + 125_000_000L
        val middle = 7 * SECOND
        val late = 10 * SECOND - 125_000_000L
        fun scales(cursor: Long) = listOf(true, false).map {
            ReplayChargePose.qteSpriteScale(actionAt(cursor), cursor, it)
        }
        assertEquals(listOf(1f, 1f), scales(5 * SECOND))
        val earlyPose = scales(early)
        val middlePose = scales(middle)
        assertTrue(earlyPose[0] in 1f..middlePose[0])
        assertTrue(earlyPose[1] in middlePose[1]..1f)
        assertEquals(earlyPose, scales(late))
        assertEquals(listOf(1f, 1f), scales(10 * SECOND))
        assertEquals(earlyPose, scales(early))
        assertEquals(earlyPose, scales(early))
    }

    private fun actionAt(cursor: Long) = ReplayChargedMoveAction(
        side = "PLAYER", moveName = "Foul Play", startedAtNanos = 10 * SECOND,
        progress = (cursor - 5 * SECOND).toFloat() / (5 * SECOND),
        phase = ReplayChargePhase.CHARGING, elapsedNanos = cursor - 5 * SECOND,
    )

    private companion object { const val SECOND = 1_000_000_000L }
}
