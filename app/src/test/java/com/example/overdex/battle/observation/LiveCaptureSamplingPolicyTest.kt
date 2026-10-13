package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveCaptureSamplingPolicyTest {
    @Test
    fun `sixty hertz callbacks publish every other display frame`() {
        var lastPublished: Long? = null
        val published = (0..6).map { it * 16_666_667L }.filter { receivedAt ->
            LiveCaptureSamplingPolicy.shouldPublish(lastPublished, receivedAt).also { accepted ->
                if (accepted) lastPublished = receivedAt
            }
        }

        assertEquals(listOf(0L, 33_333_334L, 66_666_668L, 100_000_002L), published)
    }

    @Test
    fun `capture jitter near thirty hertz does not discard the intended frame`() {
        assertFalse(LiveCaptureSamplingPolicy.shouldPublish(1_000_000_000L, 1_029_999_999L))
        assertTrue(LiveCaptureSamplingPolicy.shouldPublish(1_000_000_000L, 1_030_000_000L))
    }

    @Test
    fun `instrument and queued transition frames never become battle evidence`() {
        val gate = LiveCaptureVisibilityGate()
        assertFalse(gate.permits(0L))
        gate.instrumentVisible(false, 1_000_000_000L)
        assertFalse(gate.permits(1_249_999_999L))
        assertTrue(gate.permits(1_250_000_000L))
        gate.instrumentVisible(true, 2_000_000_000L)
        assertFalse(gate.permits(1_999_999_999L))
        assertFalse(gate.permits(2_100_000_000L))
        gate.instrumentVisible(false, 3_000_000_000L)
        assertFalse(gate.permits(2_100_000_000L))
        assertTrue(gate.permits(3_250_000_000L))
    }
}
