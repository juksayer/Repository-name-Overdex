package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.CountdownGlyphWitnessed
import com.example.overdex.battle.custody.GetReadyWitnessed
import com.example.overdex.battle.custody.InMemoryTestimonyCustody
import com.example.overdex.battle.custody.MatchStarted
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.custody.TeamSelectPartyWitnessed
import com.example.overdex.battle.custody.TestimonyPayload
import com.example.overdex.battle.custody.VsScreenWitnessed
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DroidballSessionVsCaptureTest {
    @Test
    fun `VS remains observable from navigation after manually arming HUD and expiry of countdown attention`() =
        withSession { session, _, _ ->
            assertTrue(session.vsScreenWitnessingActive())
            session.beginTeamSelectScan()
            assertTrue(session.vsScreenWitnessingActive())
            session.beginCalibration()
            assertTrue(session.vsScreenWitnessingActive())
            session.armCountdown()
            assertTrue(session.vsScreenWitnessingActive())
            assertFalse(session.countdownWitnessingActive(System.nanoTime() + 60_000_000_000L))
            assertTrue(session.vsScreenWitnessingActive())
        }

    @Test
    fun `accepted VS retires its capture without creating a match start`() =
        assertCaptureRetires(VsScreenWitnessed)

    @Test
    fun `late Team Select acceptance does not disarm a manually requested HUD`() =
        withSession { session, custody, timeline ->
            session.armCountdown()
            assertTrue(session.canObserveTeamSelect)
            val at = System.nanoTime()
            custody.submitTestimony(SourceId("TEAM_SELECT_TEST"), TeamSelectPartyWitnessed,
                1_000L, 1f, emptyList(), at)
            // This later article is handled by the same session collector. Wait
            // for it so the assertion cannot pass before Team Select is applied.
            custody.submitTestimony(SourceId("COUNTDOWN_TEST"), CountdownGlyphWitnessed("3", .99f, 1),
                1_001L, 1f, emptyList(), at + 1_000_000L)
            withTimeout(4_000L) {
                while (!session.battleSurfaceEstablished.value) delay(10L)
            }
            assertEquals(DroidballSessionPhase.COUNTDOWN, session.phase.value)
            assertTrue(session.countdownWitnessingActive())
            assertFalse(timeline.getArticles().any { it.payload == MatchStarted })
        }

    @Test
    fun `Team Select acceptance still advances an explicitly restarted scan`() =
        withSession { session, custody, timeline ->
            session.armCountdown()
            assertTrue(session.restartObservation())
            custody.submitTestimony(SourceId("TEAM_SELECT_TEST"), TeamSelectPartyWitnessed,
                1_000L, 1f, emptyList(), System.nanoTime())
            withTimeout(4_000L) {
                while (session.phase.value != DroidballSessionPhase.TEAM_SELECT_ACTIVE) delay(10L)
            }
            assertTrue(session.canObserveTeamSelect)
            assertFalse(session.countdownWitnessingActive())
            assertFalse(timeline.getArticles().any { it.payload == MatchStarted })
        }

    @Test
    fun `countdown retires VS capture without inventing GO`() =
        assertCaptureRetires(CountdownGlyphWitnessed("3", .99f, 1))

    @Test
    fun `late combat evidence retires VS capture when prebattle cues were missed`() =
        assertCaptureRetires(GetReadyWitnessed)

    @Test
    fun `ending session retires VS capture`() = withSession { session, _, _ ->
        session.end()
        assertFalse(session.vsScreenWitnessingActive())
    }

    private fun assertCaptureRetires(payload: TestimonyPayload) = withSession { session, custody, timeline ->
        session.armCountdown()
        custody.submitTestimony(SourceId("VS_CAPTURE_TEST"), payload, 1_000L, null, emptyList(), System.nanoTime())
        withTimeout(4_000L) {
            while (session.vsScreenWitnessingActive()) delay(10L)
        }
        assertFalse(timeline.getArticles().any { it.payload == MatchStarted })
    }

    private fun withSession(
        action: suspend (DroidballSession, InMemoryTestimonyCustody, InMemoryRealityTimeline) -> Unit,
    ) = runBlocking {
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match(
            matchId = "VS_CAPTURE_TEST",
            custody = custody,
            realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge(),
        )
        val session = DroidballSession(match)
        try {
            action(session, custody, timeline)
        } finally {
            session.end()
            match.release()
        }
    }
}
