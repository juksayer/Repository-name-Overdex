package com.example.overdex.battle.observation

import com.example.overdex.battle.observation.DroidballForegroundStartupGate.StopAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DroidballForegroundStartupGateTest {
    @Test fun `stop immediately after request waits for promotion then skips capture`() {
        val gate = DroidballForegroundStartupGate()
        assertTrue(gate.beginStart())
        assertEquals(StopAction.DEFER, gate.requestStop())
        assertEquals(StopAction.DEFER, gate.requestStop())
        assertFalse(gate.foregroundPromoted())
        assertEquals(StopAction.STOP_NOW, gate.requestStop())
    }

    @Test fun `normal promotion permits capture and active Stop is immediate`() {
        val gate = DroidballForegroundStartupGate()
        assertTrue(gate.beginStart())
        assertTrue(gate.foregroundPromoted())
        assertEquals(StopAction.STOP_NOW, gate.requestStop())
        assertEquals(StopAction.STOP_NOW, gate.requestStop())
    }

    @Test fun `duplicate start cannot erase cancellation awaiting promotion`() {
        val gate = DroidballForegroundStartupGate()
        assertTrue(gate.beginStart())
        assertEquals(StopAction.DEFER, gate.requestStop())
        assertFalse(gate.beginStart())
        assertFalse(gate.foregroundPromoted())
    }

    @Test fun `failed start clears its pending obligation and the next session starts normally`() {
        val gate = DroidballForegroundStartupGate()
        assertTrue(gate.beginStart())
        assertEquals(StopAction.DEFER, gate.requestStop())
        gate.markStopped()
        assertEquals(StopAction.NONE, gate.requestStop())
        assertTrue(gate.beginStart())
        assertTrue(gate.foregroundPromoted())
    }

    @Test fun `finished cancelled session does not cancel a later startup`() {
        val gate = DroidballForegroundStartupGate()
        assertTrue(gate.beginStart())
        assertEquals(StopAction.DEFER, gate.requestStop())
        assertFalse(gate.foregroundPromoted())
        gate.markStopped()
        assertTrue(gate.beginStart())
        assertTrue(gate.foregroundPromoted())
    }

    @Test fun `an unsolicited or repeated promotion never starts capture twice`() {
        val gate = DroidballForegroundStartupGate()
        assertEquals(StopAction.NONE, gate.requestStop())
        assertFalse(gate.foregroundPromoted())
        assertTrue(gate.beginStart())
        assertTrue(gate.foregroundPromoted())
        assertFalse(gate.foregroundPromoted())
    }
}
