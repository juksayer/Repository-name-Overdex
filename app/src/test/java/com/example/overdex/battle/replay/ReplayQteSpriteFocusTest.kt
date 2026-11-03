package com.example.overdex.battle.replay

import org.junit.Assert.*
import org.junit.Test

class ReplayQteSpriteFocusTest {
    @Test fun `either QTE or shield choice changes sprite focus and charge release restores it`() {
        val cursor = 7 * SECOND
        val charging = actionAt(cursor)
        for (side in listOf("PLAYER", "OPPONENT")) {
            val preparation = charging.copy(side = side)
            assertEquals(1.2f, ReplayChargePose.qteSpriteScale(preparation, cursor, player = true), .00001f)
            assertEquals(.85f, ReplayChargePose.qteSpriteScale(preparation, cursor, player = false), .00001f)
            assertEquals(20f, ReplayChargePose.qteSpriteTranslationXPixels(preparation, cursor, player = true), 0f)
            assertEquals(-10f, ReplayChargePose.qteSpriteTranslationXPixels(preparation, cursor, player = false), 0f)
        }
        for (inactive in listOf(null, charging.copy(side = "UNKNOWN"),
            charging.copy(phase = ReplayChargePhase.STRIKING))) {
            assertEquals(1f, ReplayChargePose.qteSpriteScale(inactive, cursor, player = true), 0f)
            assertEquals(1f, ReplayChargePose.qteSpriteScale(inactive, cursor, player = false), 0f)
            assertEquals(0f, ReplayChargePose.qteSpriteTranslationXPixels(inactive, cursor, player = true), 0f)
            assertEquals(0f, ReplayChargePose.qteSpriteTranslationXPixels(inactive, cursor, player = false), 0f)
        }
    }

    @Test fun `focus ramps at both QTE edges and restores the same pose after pause or reverse scrub`() {
        val early = 5 * SECOND + 125_000_000L
        val middle = 7 * SECOND
        val late = 10 * SECOND - 125_000_000L
        fun scales(cursor: Long) = listOf(true, false).map {
            ReplayChargePose.qteSpriteScale(actionAt(cursor), cursor, it)
        }
        assertEquals(10f, ReplayChargePose.qteSpriteTranslationXPixels(actionAt(early), early, true), .0001f)
        assertEquals(-5f, ReplayChargePose.qteSpriteTranslationXPixels(actionAt(late), late, false), .0001f)
        assertEquals(0f, ReplayChargePose.qteSpriteTranslationXPixels(actionAt(10 * SECOND), 10 * SECOND, true), 0f)
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
