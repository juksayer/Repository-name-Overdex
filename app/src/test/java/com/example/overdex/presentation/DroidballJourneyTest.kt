package com.example.overdex.presentation

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DroidballJourneyTest {
    @Before fun setUp() = DroidballJourney.reset()
    @After fun tearDown() = DroidballJourney.reset()

    @Test fun `body ownership never overlaps throughout full trip`() {
        fun assertOwner(shell: Boolean, field: Boolean) {
            val state = DroidballJourney.state.value
            assertEquals(shell, state.shellOwnsBody)
            assertEquals(field, state.fieldOwnsBody)
            assertFalse(state.shellOwnsBody && state.fieldOwnsBody)
        }
        assertOwner(true, false)
        val departure = DroidballJourney.depart(10)
        assertOwner(true, false)
        assertTrue(DroidballJourney.departureFinished(departure, 20))
        assertOwner(false, false)
        DroidballJourney.enterField(30)
        assertOwner(false, true)
        DroidballJourney.arrivalFinished(DroidballJourney.state.value.generation, 40)
        assertEquals(DroidballJourneyPhase.FIELD, DroidballJourney.state.value.phase)
        val returning = DroidballJourney.returnToShell(50)
        assertOwner(false, true)
        DroidballJourney.fieldExitFinished(returning, 60)
        assertOwner(true, false)
        DroidballJourney.dockingFinished(DroidballJourney.state.value.generation, 70)
        assertEquals(DroidballJourneyPhase.DOCKED, DroidballJourney.state.value.phase)
    }

    @Test fun `stop cancels departure token without waiting for motion`() {
        val departure = DroidballJourney.depart(10)
        DroidballJourney.stopped(shellVisible = true, now = 11)
        assertFalse(DroidballJourney.departureFinished(departure, 12))
        assertEquals(DroidballJourneyPhase.DOCKING, DroidballJourney.state.value.phase)
    }

    @Test fun `stop during arrival cannot be undone by stale completion`() {
        DroidballJourney.departureFinished(DroidballJourney.depart(10), 20)
        DroidballJourney.enterField(30)
        val arrival = DroidballJourney.state.value.generation
        DroidballJourney.stopped(shellVisible = false, now = 31)
        DroidballJourney.arrivalFinished(arrival, 32)
        assertEquals(DroidballJourneyPhase.TRANSIT, DroidballJourney.state.value.phase)
        DroidballJourney.returnToShell(40)
        assertEquals(DroidballJourneyPhase.DOCKING, DroidballJourney.state.value.phase)
    }

    @Test fun `return during arrival has single completion and repeated start is harmless`() {
        DroidballJourney.departureFinished(DroidballJourney.depart(10), 20)
        DroidballJourney.enterField(30)
        val arrival = DroidballJourney.state.value.generation
        val returning = DroidballJourney.returnToShell(40)
        assertEquals(returning, DroidballJourney.returnToShell(41))
        DroidballJourney.arrivalFinished(arrival, 42)
        assertEquals(DroidballJourneyPhase.RETURNING, DroidballJourney.state.value.phase)
        DroidballJourney.fieldExitFinished(returning, 50)
        val docking = DroidballJourney.state.value
        DroidballJourney.fieldExitFinished(returning, 51)
        assertEquals(docking, DroidballJourney.state.value)
    }

    @Test fun `reset invalidates callbacks from an older session`() {
        val departure = DroidballJourney.depart(10)
        DroidballJourney.reset()
        assertFalse(DroidballJourney.departureFinished(departure, 20))
        DroidballJourney.stopped(true, 30)
        assertEquals(DroidballJourneyPhase.DOCKED, DroidballJourney.state.value.phase)
    }
}
