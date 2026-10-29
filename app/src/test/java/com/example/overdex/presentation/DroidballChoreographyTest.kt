package com.example.overdex.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DroidballChoreographyTest {
    @Test fun `departure gives a visible expression and eject before leaving the dock`() {
        val first = pose(DroidballJourneyPhase.DEPARTING, 0)
        assertSamePhysicalPose(DroidballPose(), first)
        val eager = pose(DroidballJourneyPhase.DEPARTING, 220)
        assertEquals(1f, eager.focus, .001f)
        assertEquals(0f, eager.xBodies, 0f)
        val pressed = pose(DroidballJourneyPhase.DEPARTING, 688)
        assertEquals(1f, pressed.press, .001f)
        assertTrue(pressed.shadow > 0f)
        assertEquals(0f, pressed.xBodies, 0f)
        val rolling = pose(DroidballJourneyPhase.DEPARTING, 1280)
        assertTrue(rolling.xBodies < 0f)
        assertTrue(rolling.rollDegrees < 0f)
        assertEquals(1f, rolling.detached, 0f)
    }

    @Test fun `departure has two distinct blinks rather than blinking throughout travel`() {
        val values = (0..1600 step 5).map { pose(DroidballJourneyPhase.DEPARTING, it.toLong()).blink > .5f }
        val groups = values.zipWithNext().count { (before, after) -> !before && after }
        assertEquals(2, groups)
        assertEquals(0f, pose(DroidballJourneyPhase.DEPARTING, 1450).blink, .001f)
    }

    @Test fun `arrival crosses two lengths skids scans and finally docks at the right`() {
        val first = pose(DroidballJourneyPhase.ARRIVING, 0)
        assertTrue(first.xBodies > 1f)
        assertEquals(1f, first.focus, 0f)
        val stopped = pose(DroidballJourneyPhase.ARRIVING, 1100)
        assertTrue(stopped.xBodies < -1.9f)
        val scan = pose(DroidballJourneyPhase.ARRIVING, 1565)
        assertTrue(scan.scan > .9f)
        assertEquals(0f, scan.focus, .001f)
        assertTrue(scan.xBodies < -1.9f)
        val settled = pose(DroidballJourneyPhase.ARRIVING, DroidballChoreography.ARRIVAL_MILLIS)
        assertSamePhysicalPose(pose(DroidballJourneyPhase.FIELD, 0), settled)
    }

    @Test fun `return leaves sadly then changes back into the shell material only while docking`() {
        val exit = pose(DroidballJourneyPhase.RETURNING, DroidballChoreography.EXIT_MILLIS)
        assertTrue(exit.xBodies > 2f)
        assertEquals(1f, exit.sadness, 0f)
        assertEquals(1f, exit.detached, 0f)
        val approach = pose(DroidballJourneyPhase.DOCKING, 400)
        assertTrue(approach.xBodies < 0f)
        assertEquals(1f, approach.detached, 0f)
        assertEquals(1f, approach.sadness, 0f)
        assertSamePhysicalPose(pose(DroidballJourneyPhase.DOCKED, 0),
            pose(DroidballJourneyPhase.DOCKING, DroidballChoreography.DOCKING_MILLIS))
    }

    @Test fun `poses are reproducible bounded and hold their endpoints after a delayed frame`() {
        for (phase in DroidballJourneyPhase.entries) {
            assertEquals(pose(phase, 0), pose(phase, -100))
            val duration = DroidballChoreography.durationMillis(phase)
            assertEquals(pose(phase, duration), pose(phase, Long.MAX_VALUE))
            for (time in 0L..2500L step 17) {
                val value = pose(phase, time)
                assertEquals(value, pose(phase, time))
                for (channel in listOf(value.focus, value.blink, value.detached, value.press,
                    value.shadow, value.scan, value.sadness)) {
                    assertTrue("$phase at $time has channel $channel", channel in 0f..1f)
                }
            }
        }
    }

    @Test fun `arrival roll finishes at an equivalent upright angle without a visible discontinuity`() {
        val before = pose(DroidballJourneyPhase.ARRIVING, 1049)
        val after = pose(DroidballJourneyPhase.ARRIVING, 1050)
        assertEquals(-360f, before.rollDegrees, .001f)
        assertEquals(0f, after.rollDegrees, 0f)
        assertEquals(before.xBodies, after.xBodies, .003f)
    }

    private fun pose(phase: DroidballJourneyPhase, time: Long) = DroidballChoreography.pose(phase, time)

    private fun assertSamePhysicalPose(expected: DroidballPose, actual: DroidballPose) {
        // A leftward journey can finish at -0.0; it occupies the same physical point as 0.0.
        val first = listOf(expected.xBodies, expected.rollDegrees, expected.focus, expected.blink,
            expected.detached, expected.press, expected.shadow, expected.scan, expected.sadness)
        val second = listOf(actual.xBodies, actual.rollDegrees, actual.focus, actual.blink,
            actual.detached, actual.press, actual.shadow, actual.scan, actual.sadness)
        first.zip(second).forEach { (a, b) -> assertEquals(a, b, .0001f) }
    }
}
