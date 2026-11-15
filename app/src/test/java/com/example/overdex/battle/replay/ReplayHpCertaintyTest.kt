package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Test

class ReplayHpCertaintyTest {
    private val hp = ArchivedActiveHpBarMeasured("OPPONENT", 540, 890, 845, 918, .002f, 1080, 2400, "RED")
    private fun row(id: String = "hp", at: Long = S, confidence: Float? = .99f,
                    payload: ArchivedTestimonyPayload = hp) = ArchivedRealityArticle(
        id, "match", 0, 0, "OPPONENT_ACTIVE_HP_BAR_WITNESS", payload, emptyList(), confidence, null, emptyList(), at)

    @Test fun `tiny live sliver survives without rounding but expires on source clock`() {
        val a = row()
        val evidence = ReplayHpEvidence(listOf(a))
        assertTrue(evidence.confirmsLiveHp(a))
        assertTrue(evidence.confirmsLiveHp(a, S + ReplayHpEvidence.FRESH_HP_NANOS))
        assertFalse(evidence.confirmsLiveHp(a, S + ReplayHpEvidence.FRESH_HP_NANOS + 1))
        assertFalse(evidence.confirmsLiveHp(a, S - 1))
        assertTrue(evidence.confirmsLiveHp(a.copy(recordedAt = Long.MAX_VALUE)))
    }

    @Test fun `weak missing and malformed supporting evidence is unknown`() {
        for (confidence in listOf(null, .79f, Float.NaN, Float.POSITIVE_INFINITY, 1.1f)) {
            val a = row(confidence = confidence)
            assertFalse(ReplayHpEvidence(listOf(a)).confirmsLiveHp(a))
        }
        for (p in listOf(hp.copy(fillColor = null), hp.copy(fillColor = "ORANGE"),
            hp.copy(sourceWidth = null), hp.copy(barRight = 1200), hp.copy(barBottom = 2401),
            hp.copy(barRight = 560), hp.copy(filledFraction = Float.NaN))) {
            val a = row(payload = p)
            assertFalse(ReplayHpEvidence(listOf(a)).confirmsLiveHp(a))
        }
    }

    @Test fun `whole bar orange or uncertain profile overrides numeric survival without becoming zero`() {
        val a = row()
        for (status in listOf("DAMAGE_COLOR", "SUSTAINED_DAMAGE_COLOR", "UNRESOLVED_COLOR")) {
            val profile = row("color", S + S/10, payload = ArchivedCompleteHpBarObserved("OPPONENT",
                540, 890, 845, 918, 1080, 2400, 297, 0, 5, status, S, 0, 170f, 140f))
            val evidence = ReplayHpEvidence(listOf(a, profile))
            assertTrue(evidence.confirmsLiveHp(a, S))
            assertFalse(evidence.confirmsLiveHp(a, S + S/10))
            assertEquals(.002f, (a.payload as ArchivedActiveHpBarMeasured).filledFraction, 0f)
        }
    }

    @Test fun `weak HP cannot erase an independent hit or establish fresh damage`() {
        val weak = row(confidence = .2f)
        val pulse = row("pulse", payload = ArchivedActiveHpBarBorderPulseObserved("OPPONENT", 10f, 2))
        val evidence = ReplayHpEvidence(listOf(weak, pulse))
        assertFalse(evidence.accepts(weak))
        assertTrue(evidence.accepts(pulse))
        val track = ReplayHpTrack(listOf(weak), "OPPONENT", evidence)
        assertFalse(track.stateAt(S, listOf(ReplayHpAppearance("swampert", 0)), false, 0).currentHpConfirmed)
    }

    @Test fun `display retains historical amount but does not label it current after freshness expires`() {
        val a = row()
        val track = ReplayHpTrack(listOf(a), "OPPONENT", ReplayHpEvidence(listOf(a)))
        val appearances = listOf(ReplayHpAppearance("swampert", 0))
        assertTrue(track.stateAt(S, appearances, false, 0).currentHpConfirmed)
        val later = track.stateAt(5*S, appearances, false, 0)
        assertFalse(later.currentHpConfirmed)
        assertEquals(.002f, later.filledFraction!!, 0f)
        assertEquals(.002f, later.lastSeenFraction!!, 0f)
        assertNull(track.stateAt(S - 1, appearances, false, 0).lastSeenFraction)
        assertEquals(.002f, track.stateAt(5*S, appearances, false, 0).lastSeenFraction!!, 0f)
    }

    @Test fun `last seen display rejects weak camera and other appearance observations`() {
        val a = row()
        val weak = row("weak", confidence = .2f)
        val camera = a.copy(sourceId = "CHARGED_IMPACT_OPPONENT_HP_BAR_FRAME_WITNESS")
        for (sample in listOf(weak, camera)) {
            val track = ReplayHpTrack(listOf(sample), "OPPONENT", ReplayHpEvidence(listOf(sample)))
            assertNull(track.stateAt(5*S, listOf(ReplayHpAppearance("swampert", 0)), false, 0).lastSeenFraction)
        }
        val track = ReplayHpTrack(listOf(a), "OPPONENT", ReplayHpEvidence(listOf(a)), allowLastSeen = { false })
        assertNull(track.stateAt(5*S, listOf(ReplayHpAppearance("swampert", 0)), false, 0).lastSeenFraction)
        val switched = ReplayHpTrack(listOf(a), "OPPONENT", ReplayHpEvidence(listOf(a)))
        assertNull(switched.stateAt(5*S, listOf(ReplayHpAppearance("swampert", 0),
            ReplayHpAppearance("vaporeon", 2*S)), false, 0).lastSeenFraction)
        val fainted = switched.stateAt(5*S, listOf(ReplayHpAppearance("swampert", 0)), true, 0)
        assertEquals(0f, fainted.filledFraction!!, 0f)
        assertNull(fainted.lastSeenFraction)
    }
    private companion object { const val S = 1_000_000_000L }
}
