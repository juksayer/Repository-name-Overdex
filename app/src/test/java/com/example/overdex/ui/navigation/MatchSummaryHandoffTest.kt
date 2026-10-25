package com.example.overdex.ui.navigation

import com.example.overdex.battle.archive.MatchArchiveSource
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import org.junit.Assert.*
import org.junit.Test

class MatchSummaryHandoffTest {
    private fun source(id: String) = MatchArchiveSource(MatchId(id), InMemoryRealityTimeline())

    @Test fun `a stopped or completed recording remains queued until displayed`() {
        val handoff = MatchSummaryHandoff()
        val match = source("first")
        handoff.offer(match)
        assertSame(match, handoff.pending.value)
        assertTrue(handoff.acknowledge(match.matchId))
        assertNull(handoff.pending.value)
        handoff.offer(match)
        assertNull("Repeated completion must not steal deliberate navigation", handoff.pending.value)
    }

    @Test fun `acknowledging an older match cannot discard a newer result`() {
        val handoff = MatchSummaryHandoff()
        handoff.offer(source("first"))
        val next = source("second")
        handoff.offer(next)
        assertFalse(handoff.acknowledge(MatchId("first")))
        assertSame(next, handoff.pending.value)
    }

    @Test fun `an explicit next match clears the previous return destination`() {
        val handoff = MatchSummaryHandoff()
        handoff.offer(source("first"))
        handoff.clearForNewMatch()
        assertNull(handoff.pending.value)
        handoff.offer(source("second"))
        assertEquals(MatchId("second"), handoff.pending.value?.matchId)
    }
}
