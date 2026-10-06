package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceMotionPulseDetectorTest {
    @Test fun `short acceleration burst becomes one measured pulse after quiet`() {
        val detector = DeviceMotionPulseDetector()

        assertNull(detector.accept(0L, 0.8f, 0f, 0f, 100L))
        assertNull(detector.accept(10_000_000L, 1.2f, 0f, 0f, 110L))
        assertNull(detector.accept(20_000_000L, 0.7f, 0f, 0f, 120L))
        assertNull(detector.accept(30_000_000L, 0.05f, 0f, 0f, 130L))
        val pulse = detector.accept(80_000_000L, 0.05f, 0f, 0f, 180L)

        requireNotNull(pulse)
        assertEquals(0L, pulse.startedAtMonotonicTimeNanos)
        assertEquals(80_000_000L, pulse.durationNanos)
        assertEquals(5, pulse.sampleCount)
        assertEquals(1.2f, pulse.peakLinearAccelerationMetersPerSecondSquared)
        assertTrue(pulse.rmsLinearAccelerationMetersPerSecondSquared > 0f)
    }

    @Test fun `ordinary low motion never creates a pulse`() {
        val detector = DeviceMotionPulseDetector()

        repeat(20) { index ->
            assertNull(detector.accept(index * 10_000_000L, 0.1f, 0.1f, 0.1f, index.toLong()))
        }
    }
}
