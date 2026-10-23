package com.example.overdex.battle.observation

import org.junit.Assert.*
import org.junit.Test

class TeamSelectRosterWindowTest {
    @Test fun `old party acceptance cannot authorize the recorded VS crop`() {
        val window = TeamSelectRosterWindow()
        // October 23 Moondrop: accepted roster frame, then trainer torso matched as Delibird.
        window.confirm(16_007_749_586_724L)
        assertFalse(window.allows(16_018_272_833_800L))
    }
    @Test fun `slow readers can finish a nearby source frame after VS is published`() {
        val window = TeamSelectRosterWindow()
        window.confirm(10_000_000_000L)
        window.close(11_000_000_000L)
        assertTrue(window.allows(9_500_000_000L))
        assertTrue(window.allows(10_500_000_000L))
        assertFalse(window.allows(11_000_000_000L))
        assertFalse(window.allows(7_000_000_000L))
    }
    @Test fun `no party confirmation means no roster publication`() {
        assertFalse(TeamSelectRosterWindow().allows(1L))
    }
}
