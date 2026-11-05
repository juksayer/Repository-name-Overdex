package com.example.overdex.battle.replay

import org.junit.Assert.*
import org.junit.Test

class ReplayCombatantEndPoseTest {
    @Test fun `result-only defeat withdraws upright in full colour and fades smoothly on either side`() {
        val at = 1_000_000_000L
        // Match presentation can align a result without supplying a faint.
        val combatant = ReplayCombatant("Malamar", 687, 0,
            defeatedAtNanos = at, knockoutPresentationAtNanos = at)
        for (player in listOf(false, true)) {
            assertNull(ReplayCombatantEndPose.at(combatant, at - 1, player))
            val first = ReplayCombatantEndPose.at(combatant, at, player)!!
            assertEquals(1f, first.opacity, 0f)
            assertEquals(0f, first.withdrawalPixels, 0f)
            val halfway = at + ReplayCombatantEndPose.DEFEAT_DURATION_NANOS / 2
            val middle = ReplayCombatantEndPose.at(combatant, halfway, player)!!
            assertEquals(.5f, middle.opacity, .0001f)
            assertEquals(if (player) -12f else 12f, middle.withdrawalPixels, .0001f)
            val end = at + ReplayCombatantEndPose.DEFEAT_DURATION_NANOS
            for (cursor in listOf(at, at + 100_000_000L, halfway, end, end + 20_000_000_000L)) {
                val pose = ReplayCombatantEndPose.at(combatant, cursor, player)!!
                assertEquals(0f, pose.shakePixels, 0f)
                assertEquals(0f, pose.rotationDegrees, 0f)
                assertEquals(0f, pose.dropPixels, 0f)
                assertEquals(1f, pose.saturation, 0f)
            }
            assertEquals(0f, ReplayCombatantEndPose.at(combatant, end, player)!!.opacity, 0f)
            // Pause and reverse scrub restore the same pose without animation state.
            assertEquals(middle, ReplayCombatantEndPose.at(combatant, halfway, player))
            assertNull(ReplayCombatantEndPose.at(combatant, at - 1, player))
        }
        assertFalse(combatant.isFainted)
        assertNull(combatant.faintedAtNanos)
    }

    @Test fun `witnessed faint keeps the existing fall and colour drain including quick replacements`() {
        val at = 1_000_000_000L
        for (player in listOf(false, true)) for (finishBy in listOf(null, at + 550_000_000L)) {
            val combatant = ReplayCombatant("Malamar", 687, 0, isFainted = true,
                faintedAtNanos = at, defeatedAtNanos = at,
                knockoutPresentationAtNanos = at, knockoutFinishByNanos = finishBy)
            for (age in listOf(0L, 100_000_000L, 400_000_000L, 1_500_000_000L, 3_000_000_000L)) {
                val expected = ReplayFaintPose.at(at, at + age, player, finishBy)!!
                val actual = ReplayCombatantEndPose.at(combatant, at + age, player)!!
                assertEquals(ReplayCombatantEndPose(expected.opacity, expected.shakePixels,
                    expected.rotationDegrees, expected.dropPixels, expected.saturation), actual)
                assertEquals(0f, actual.withdrawalPixels, 0f)
            }
        }
    }

    @Test fun `result departure respects finishing impact alignment without becoming a faint`() {
        val combatant = ReplayCombatant("Malamar", 687, 0,
            defeatedAtNanos = 1_000_000_000L, knockoutPresentationAtNanos = 2_000_000_000L)
        assertNull(ReplayCombatantEndPose.at(combatant, 1_500_000_000L, false))
        assertEquals(1f, ReplayCombatantEndPose.at(combatant, 2_000_000_000L, false)!!.opacity, 0f)
        assertNull(ReplayCombatantEndPose.at(ReplayCombatant("Camerupt", 323, 0), 3_000_000_000L, true))
        assertNull(ReplayCombatantEndPose.at(null, 3_000_000_000L, true))
    }
}
