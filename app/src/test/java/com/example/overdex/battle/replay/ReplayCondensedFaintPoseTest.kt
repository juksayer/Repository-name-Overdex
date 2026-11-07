package com.example.overdex.battle.replay

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class ReplayCondensedFaintPoseTest {
    @Test fun `Blanche replacement budget gives the fall a visible span before a lying beat`() {
        val startedAt = 10 * SECOND
        val replacementAt = startedAt + 650 * MS
        val finishBy = replacementAt - 100 * MS
        for (player in listOf(false, true)) {
            val direction = if (player) -1f else 1f
            val early = pose(startedAt, 150 * MS, player, finishBy)
            val middle = pose(startedAt, 250 * MS, player, finishBy)
            val late = pose(startedAt, 400 * MS, player, finishBy)
            assertTrue(early.rotationDegrees * direction in 1f..89f)
            assertTrue(middle.rotationDegrees * direction > early.rotationDegrees * direction)
            assertTrue(late.rotationDegrees * direction > middle.rotationDegrees * direction)
            assertTrue(late.rotationDegrees * direction < 90f)
            assertEquals(1f, late.saturation, 0f)
            assertEquals(1f, late.opacity, 0f)
            val lying = pose(startedAt, 500 * MS, player, finishBy)
            assertEquals(90f * direction, lying.rotationDegrees, 0f)
            assertTrue(lying.saturation in 0f..0.999f)
            val finished = pose(startedAt, 550 * MS, player, finishBy)
            assertEquals(90f * direction, finished.rotationDegrees, 0f)
            assertEquals(0f, finished.saturation, 0f)
            assertEquals(finished, pose(startedAt, 649 * MS, player, finishBy))
        }
        // All pose samples fit the supplied interval; no replacement instant
        // or archived knockout time has to move to make the animation legible.
        assertEquals(650 * MS, replacementAt - startedAt)
    }

    @Test fun `condensed fall retains full colour until horizontal across available budgets`() {
        for (budgetMs in listOf(100L, 300L, 550L, 900L, 1_500L, 2_000L)) {
            val finish = budgetMs * MS
            var fallingSamples = 0
            var lyingSamples = 0
            for (step in 0..100) {
                val state = ReplayFaintPose.at(0, finish * step / 100, false, finish)!!
                assertTrue(state.rotationDegrees in 0f..90f)
                if (state.rotationDegrees in 0.001f..89.999f) {
                    fallingSamples++
                    assertEquals(1f, state.saturation, 0f)
                    assertEquals(1f, state.opacity, 0f)
                    assertEquals(0f, state.shakePixels, 0f)
                }
                if (state.saturation < 1f) {
                    lyingSamples++
                    assertEquals(90f, state.rotationDegrees, 0f)
                }
            }
            assertTrue("$budgetMs ms fall must occupy multiple samples", fallingSamples >= 20)
            assertTrue("$budgetMs ms ending must leave a lying beat", lyingSamples >= 15)
        }
    }

    @Test fun `ordinary deadline and no deadline preserve the original complete schedule`() {
        val startedAt = 20 * SECOND
        for (player in listOf(false, true)) {
            for (elapsed in listOf(0L, 50 * MS, 180 * MS, 360 * MS, 900 * MS,
                    1_360 * MS, 1_500 * MS, 1_780 * MS, 1_955 * MS, 2_130 * MS, 5 * SECOND)) {
                val expected = ordinaryPose(elapsed, player)
                assertEquals(expected, ReplayFaintPose.at(startedAt, startedAt + elapsed, player))
                assertEquals(expected, ReplayFaintPose.at(startedAt, startedAt + elapsed, player,
                    startedAt + ReplayFaintPose.DURATION_NANOS))
                assertEquals(expected, ReplayFaintPose.at(startedAt, startedAt + elapsed, player,
                    startedAt + 10 * SECOND))
            }
        }
    }

    @Test fun `condensed pose is deterministic when paused or scrubbed backwards`() {
        val at = 10 * SECOND
        val finish = at + 550 * MS
        val forward = listOf(100L, 200L, 300L, 400L, 500L, 550L).associateWith {
            pose(at, it * MS, false, finish)
        }
        for (ms in forward.keys.reversed()) {
            assertEquals(forward.getValue(ms), pose(at, ms * MS, false, finish))
            assertEquals(forward.getValue(ms), pose(at, ms * MS, false, finish))
        }
        assertNull(ReplayFaintPose.at(at, at - 1, false, finish))
    }

    @Test fun `tiny or already expired presentation deadlines stay finite and finish`() {
        for (finish in listOf(-10L, 0L, 1L, 2L, 10L)) {
            for (cursor in listOf(0L, 1L, 2L, 10L, 100L)) {
                val state = ReplayFaintPose.at(0, cursor, false, finish)!!
                assertTrue(state.shakePixels.isFinite())
                assertTrue(state.rotationDegrees.isFinite())
                assertTrue(state.dropPixels.isFinite())
                assertTrue(state.opacity.isFinite())
                assertTrue(state.saturation.isFinite())
                if (cursor >= finish.coerceAtLeast(1L)) {
                    assertEquals(90f, state.rotationDegrees, 0f)
                    assertEquals(0f, state.saturation, 0f)
                }
            }
        }
    }

    private fun pose(startedAt: Long, elapsed: Long, player: Boolean, finish: Long) =
        ReplayFaintPose.at(startedAt, startedAt + elapsed, player, finish)!!

    /** Frozen ordinary sequence protects the pre-existing uncompressed choreography. */
    private fun ordinaryPose(elapsed: Long, player: Boolean): ReplayFaintPose {
        val shakeProgress = (elapsed.toFloat() / (360 * MS)).coerceIn(0f, 1f)
        val progress = ((elapsed - 1_360 * MS).toFloat() / (420 * MS)).coerceIn(0f, 1f)
        val fall = progress * progress * (3f - 2f * progress)
        val fade = ((elapsed - 1_780 * MS).toFloat() / (350 * MS)).coerceIn(0f, 1f)
        return ReplayFaintPose(
            if (elapsed < 360 * MS) sin(shakeProgress * PI * 6).toFloat() * 7f * (1f - shakeProgress) else 0f,
            (if (player) -90f else 90f) * fall,
            10f * fall,
            1f - .4f * fade,
            1f - fade,
        )
    }

    private companion object { const val MS = 1_000_000L; const val SECOND = 1_000_000_000L }
}
