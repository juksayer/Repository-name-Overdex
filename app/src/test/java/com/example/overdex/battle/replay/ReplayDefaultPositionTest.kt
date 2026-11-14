package com.example.overdex.battle.replay

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplayDefaultPositionTest {
    @Test fun `normal replay skips setup while retaining original seek origin`() {
        val recordingStart = 10L
        assertEquals(70L, replayDefaultPosition(recordingStart, 200, 70, false))
    }
    @Test fun `missing start falls back to recording beginning`() {
        assertEquals(10L, replayDefaultPosition(10, 200, null, false))
    }
    @Test fun `explicit clips retain their selected start`() {
        assertEquals(100L, replayDefaultPosition(100, 200, 70, true))
        assertEquals(10L, replayDefaultPosition(10, 60, 70, true))
    }
    @Test fun `start outside window is bounded`() {
        assertEquals(10L, replayDefaultPosition(10, 200, 0, false))
        assertEquals(200L, replayDefaultPosition(10, 200, 300, false))
    }
}
