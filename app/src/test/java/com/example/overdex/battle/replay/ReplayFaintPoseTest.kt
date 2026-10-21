package com.example.overdex.battle.replay

import org.junit.Assert.*
import org.junit.Test

class ReplayFaintPoseTest {
    @Test fun `a quick replacement fits the full fall and colour drain before entry`() {
        val at = 1_000_000_000L
        val finish = at + 550_000_000L
        val falling = ReplayFaintPose.at(at, at + 400_000_000L, false, finish)!!
        assertTrue(falling.rotationDegrees in 1f..89f)
        assertEquals(1f, falling.saturation, 0f)
        val complete = ReplayFaintPose.at(at, finish, false, finish)!!
        assertEquals(90f, complete.rotationDegrees, 0f)
        assertEquals(0f, complete.saturation, 0f)
        assertEquals(falling, ReplayFaintPose.at(at, at + 400_000_000L, false, finish))
    }

    @Test fun `KO pose is reversible and finishes face upwards on both sides`() {
        val at = 1_000_000_000L
        assertNull(ReplayFaintPose.at(null, at, player = true))
        assertNull(ReplayFaintPose.at(at, at - 1, player = true))
        assertEquals(0f, ReplayFaintPose.at(at, at, true)!!.rotationDegrees, 0f)
        val mid = ReplayFaintPose.at(at, at + ReplayFaintPose.FALL_START_NANOS + 200_000_000, true)!!
        assertTrue(mid.rotationDegrees in -90f..-1f)
        val player = ReplayFaintPose.at(at, at + ReplayFaintPose.DURATION_NANOS, true)!!
        val opponent = ReplayFaintPose.at(at, at + ReplayFaintPose.DURATION_NANOS, false)!!
        assertEquals(-90f, player.rotationDegrees, 0f)
        assertEquals(90f, opponent.rotationDegrees, 0f)
        assertEquals(0f, player.shakePixels, 0f)
        assertEquals(player, ReplayFaintPose.at(at, at + 20_000_000_000L, true))
        // Pause and reverse scrub produce the same pose without replaying a side effect.
        assertEquals(mid, ReplayFaintPose.at(at, at + ReplayFaintPose.FALL_START_NANOS + 200_000_000, true))
    }

    @Test fun `colour and opacity remain intact until the sprite is lying down`() {
        val first = ReplayFaintPose.at(0, 50_000_000, true)!!
        assertNotEquals(0f, first.shakePixels)
        assertEquals(1f, first.saturation, 0f)
        assertEquals(0f, first.rotationDegrees, 0f)
        for (at in listOf(360_000_000L, 900_000_000L, ReplayFaintPose.FALL_START_NANOS)) {
            val held = ReplayFaintPose.at(0, at, false)!!
            assertEquals(0f, held.rotationDegrees, 0f)
            assertEquals(0f, held.shakePixels, 0f)
            assertEquals(1f, held.saturation, 0f)
        }
        for (at in listOf(ReplayFaintPose.FALL_START_NANOS + 200_000_000L, ReplayFaintPose.FALL_END_NANOS)) {
            val falling = ReplayFaintPose.at(0, at, true)!!
            assertEquals(1f, falling.saturation, 0f)
            assertEquals(1f, falling.opacity, 0f)
        }
        val fading = ReplayFaintPose.at(0, ReplayFaintPose.FALL_END_NANOS + 175_000_000L, true)!!
        assertEquals(-90f, fading.rotationDegrees, 0f)
        assertEquals(.5f, fading.saturation, .0001f)
        assertEquals(0f, ReplayFaintPose.at(0, ReplayFaintPose.DURATION_NANOS, true)!!.saturation, 0f)
    }
}
