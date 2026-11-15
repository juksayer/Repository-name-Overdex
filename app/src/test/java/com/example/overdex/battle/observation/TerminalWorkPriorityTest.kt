package com.example.overdex.battle.observation

import org.junit.Assert.*
import org.junit.Test

class TerminalWorkPriorityTest {
    private var now = 100_000_000_000L
    private val priority = TerminalWorkPriority { now }

    @Test fun `fresh cue gives a fixed window and repeated observations cannot extend it`() {
        val start = now
        assertTrue(priority.request(start))
        assertTrue(priority.isActive())
        now += 2_000_000_000L
        assertFalse(priority.request(now))
        now = start + TerminalWorkPriority.WINDOW
        assertFalse(priority.isActive())
        assertFalse(priority.request(now))
        now += TerminalWorkPriority.COOLDOWN
        assertTrue(priority.request(now))
    }

    @Test fun `late and future cues never pause ordinary readers`() {
        assertFalse(priority.request(now - TerminalWorkPriority.MAX_CUE_AGE - 1))
        assertFalse(priority.request(now + 1))
        assertFalse(priority.isActive())
    }

    @Test fun `survival or a living switch is not needed to unblock routine work`() {
        // A hint has no KO meaning and a missing transition event cannot strand
        // routine readers: expiration alone restores them.
        assertTrue(priority.request(now))
        now += TerminalWorkPriority.WINDOW
        assertFalse(priority.isActive())
    }
}
