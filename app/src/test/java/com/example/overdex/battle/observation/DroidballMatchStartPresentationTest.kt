package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActiveHpBarDamageTickMeasured
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.CountdownGlyphWitnessed
import com.example.overdex.battle.custody.InMemoryTestimonyCustody
import com.example.overdex.battle.custody.MatchEnded
import com.example.overdex.battle.custody.MatchStarted
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.custody.TeamSelectPartyWitnessed
import com.example.overdex.battle.custody.TestimonyPayload
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.model.BattleResult
import com.example.overdex.presentation.DroidballJourney
import com.example.overdex.presentation.DroidballJourneyPhase
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Uses the live phase adapter and real custody/Match/session publication, not a synthetic phase. */
class DroidballMatchStartPresentationTest {
    @Before fun reset() {
        DroidballOverlayPresentation.reset()
        DroidballJourney.reset()
    }

    @After fun clear() = reset()

    @Test fun `accepted GO already occupies the start cache when its phase opens the field`() = runBlocking {
        val f = Fixture()
        val at = System.nanoTime()
        DroidballJourney.hideInTransit(at - 1)
        var cachePresentAtDelivery = false
        val delivery = launch(start = CoroutineStart.UNDISPATCHED) {
            f.session.phase.collect { phase ->
                if (phase == DroidballSessionPhase.BATTLE_ACTIVE) {
                    cachePresentAtDelivery = f.match.matchStarted.replayCache.singleOrNull()?.payload is MatchStarted
                }
                presentDroidballSessionPhase(f.session, f.session, phase, captureActive = true, shellVisible = false)
            }
        }
        try {
            f.go(at)
            await { DroidballOverlayPresentation.expanded.value }
            assertTrue(cachePresentAtDelivery)
            assertEquals(DroidballSessionPhase.BATTLE_ACTIVE, f.session.phase.value)
            assertFalse(DroidballOverlayPresentation.battleBadgeSeen.value)
            assertEquals(DroidballJourneyPhase.ARRIVING, DroidballJourney.state.value.phase)
            assertTrue(DroidballJourney.state.value.fieldOwnsBody)
            assertFalse(DroidballJourney.state.value.shellOwnsBody)
            assertEquals(1, f.timeline.getArticles().count { it.payload is MatchStarted })
            assertEquals(at, f.match.matchStarted.replayCache.single().monotonicTimeNanos)
        } finally { delivery.cancelAndJoin(); f.close() }
    }

    @Test fun `accepted countdown fallback also opens without inventing another Match Start`() = runBlocking {
        val f = Fixture()
        val at = System.nanoTime()
        DroidballJourney.hideInTransit(at - 1)
        val delivery = launch(start = CoroutineStart.UNDISPATCHED) {
            f.session.phase.collect { phase ->
                presentDroidballSessionPhase(f.session, f.session, phase, captureActive = true, shellVisible = false)
            }
        }
        try {
            f.submit(CountdownGlyphWitnessed("3", .99f, 1), at,
                BattleWitnessContracts.countdownGlyph.witnessId)
            f.submit(ActiveHpBarDamageTickMeasured(ActivePokemonSide.PLAYER, 1f, .95f, .05f),
                at + 3_100_000_000L, "PLAYER_ACTIVE_HP_DAMAGE_WITNESS")
            await { DroidballOverlayPresentation.expanded.value }
            val started = f.match.matchStarted.replayCache.single()
            assertEquals(at + 3_000_000_000L, started.monotonicTimeNanos)
            assertEquals(2, started.predecessorIds.size)
            assertTrue(DroidballJourney.state.value.fieldOwnsBody)
            assertEquals(1, f.timeline.getArticles().count { it.payload is MatchStarted })
        } finally { delivery.cancelAndJoin(); f.close() }
    }

    @Test fun `manual collapse survives late prebattle testimony and repeated GO in a real session`() = runBlocking {
        val f = Fixture()
        val at = System.nanoTime()
        val delivery = launch(start = CoroutineStart.UNDISPATCHED) {
            f.session.phase.collect { phase ->
                presentDroidballSessionPhase(f.session, f.session, phase, captureActive = true, shellVisible = false)
            }
        }
        try {
            f.go(at)
            await { DroidballOverlayPresentation.expanded.value }
            DroidballOverlayPresentation.toggleExpanded()
            assertFalse(f.session.canEditTeam)
            f.submit(TeamSelectPartyWitnessed, at - 1_000_000L, "TEAM_SELECT_TEST")
            f.go(at + 1_000_000L)
            await { f.timeline.getArticles().count { it.payload is CountdownGlyphWitnessed } == 2 }
            // The live Team Select handler is gated by canEditTeam. Even a stale
            // direct presentation request must not undo the current user's Hide.
            DroidballOverlayPresentation.showAcceptedTeamSelect()
            presentDroidballSessionPhase(f.session, f.session, f.session.phase.value,
                captureActive = true, shellVisible = false)
            assertFalse(f.session.canEditTeam)
            assertFalse(DroidballOverlayPresentation.expanded.value)
            assertEquals(DroidballOverlayMode.BATTLE_HUD, DroidballOverlayPresentation.mode.value)
            assertEquals(1, f.timeline.getArticles().count { it.payload is MatchStarted })
        } finally { delivery.cancelAndJoin(); f.close() }
    }

    @Test fun `inactive capture stale session and absent accepted start cannot authorize opening`() = runBlocking {
        val f = Fixture()
        val at = System.nanoTime()
        DroidballJourney.hideInTransit(at - 1)
        try {
            presentDroidballSessionPhase(f.session, f.session, DroidballSessionPhase.BATTLE_ACTIVE,
                captureActive = true, shellVisible = false)
            assertFalse(DroidballOverlayPresentation.panelAllowed.value)
            f.go(at)
            await { f.session.phase.value == DroidballSessionPhase.BATTLE_ACTIVE }

            assertFalse(presentDroidballSessionPhase(f.session, null, f.session.phase.value,
                captureActive = true, shellVisible = false))
            assertFalse(DroidballOverlayPresentation.panelAllowed.value)
            presentDroidballSessionPhase(f.session, f.session, f.session.phase.value,
                captureActive = false, shellVisible = false)
            assertFalse(DroidballOverlayPresentation.panelAllowed.value)
            assertEquals(DroidballJourneyPhase.TRANSIT, DroidballJourney.state.value.phase)

            f.submit(MatchEnded(BattleResult.WIN), at + 1_000_000L, "RESULT_TEST")
            await { f.session.hasCompletedMatch }
            presentDroidballSessionPhase(f.session, f.session, DroidballSessionPhase.BATTLE_ACTIVE,
                captureActive = true, shellVisible = false)
            assertFalse(DroidballOverlayPresentation.panelAllowed.value)
        } finally { f.close() }
    }

    @Test fun `late start never steals a visible shell or a newer return journey`() = runBlocking {
        val f = Fixture()
        val at = System.nanoTime()
        try {
            f.go(at)
            await { f.session.phase.value == DroidballSessionPhase.BATTLE_ACTIVE }
            for (shellVisible in listOf(true, false)) {
                DroidballOverlayPresentation.reset()
                DroidballJourney.reset()
                DroidballJourney.hideInTransit(if (shellVisible) at - 1 else at + 1)
                val before = DroidballJourney.state.value
                presentDroidballSessionPhase(f.session, f.session, f.session.phase.value,
                    captureActive = true, shellVisible = shellVisible)
                assertEquals(before, DroidballJourney.state.value)
            }

            DroidballOverlayPresentation.reset()
            DroidballJourney.enterField()
            DroidballJourney.returnToShell()
            val returning = DroidballJourney.state.value
            presentDroidballSessionPhase(f.session, f.session, f.session.phase.value,
                captureActive = true, shellVisible = false)
            assertEquals(DroidballJourneyPhase.RETURNING, returning.phase)
            assertEquals(returning, DroidballJourney.state.value)
        } finally { f.close() }
    }

    @Test fun `ending an unfinished session rejects an already queued active phase`() = runBlocking {
        val f = Fixture()
        val at = System.nanoTime()
        DroidballJourney.hideInTransit(at - 1)
        try {
            f.go(at)
            await { f.session.phase.value == DroidballSessionPhase.BATTLE_ACTIVE }
            f.session.end()
            assertFalse(presentDroidballSessionPhase(f.session, f.session, DroidballSessionPhase.BATTLE_ACTIVE,
                captureActive = true, shellVisible = false))
            assertFalse(DroidballOverlayPresentation.panelAllowed.value)
            assertEquals(DroidballJourneyPhase.TRANSIT, DroidballJourney.state.value.phase)
        } finally { f.close() }
    }

    private suspend fun await(condition: () -> Boolean) = withTimeout(4_000L) {
        while (!condition()) delay(10L)
    }

    private class Fixture {
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match(matchId = "MATCH_START_PRESENTATION", custody = custody,
            realityTimeline = timeline, pokemonKnowledge = FakePokemonKnowledge())
        val session = DroidballSession(match)
        fun go(at: Long) = submit(CountdownGlyphWitnessed("GO", .99f, 1), at,
            BattleWitnessContracts.countdownGlyph.witnessId)
        fun submit(payload: TestimonyPayload, at: Long, source: String) {
            custody.submitTestimony(SourceId(source), payload, System.currentTimeMillis(), 1f,
                emptyList(), at)
        }
        fun close() { session.end(); match.release() }
    }
}
