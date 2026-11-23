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

    @Test fun `matching badge reconciles a contradicted baseline only on a later settled complete bar`() {
        val later = row("fresh", 4*S, payload = hp.copy(filledFraction = .7f, fillColor = "GREEN"))
        val profile = ArchivedCompleteHpBarObserved("OPPONENT",
            540, 890, 845, 918, 1080, 2400, 297, 208, 0, "LIVE_COLOR", null, null,
            180f, 140f, unmaskedUncertainColumns = 0)
        val appearances = listOf(ReplayHpAppearance("swampert", 0), ReplayHpAppearance("", 2*S),
            ReplayHpAppearance("swampert", 3*S, reconfirmed = true))
        for (damage in listOf(0, 4)) {
            val rows = listOf(row(), later, row("whole", 4*S, payload = profile.copy(damageColumns = damage)))
            val track = ReplayHpTrack(rows, "OPPONENT", ReplayHpEvidence(rows))
            assertEquals(.002f, track.stateAt(3*S, appearances, false, 0).filledFraction!!, 0f)
            assertEquals(if (damage == 0) .7f else .002f,
                track.stateAt(4*S, appearances, false, 0).filledFraction!!, 0f)
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
    @Test fun `supported Fast Moves step through a shared observed drop at their displayed landings`() {
        val before = row("before", S, payload = hp.copy(filledFraction = .9f, fillColor = "GREEN"))
        val after = row("after", 4*S, payload = hp)
        val rows = listOf(before, after)
        val attacks = listOf(cycle(2*S), cycle(3*S))
        val track = ReplayHpTrack(rows.reversed(), "OPPONENT", ReplayHpEvidence(rows), attacks)
        val appearances = listOf(ReplayHpAppearance("houndoom", 0))
        fun at(time: Long) = track.stateAt(time, appearances, false, 0)

        assertEquals(.9f, at(2*S - 1).filledFraction!!, 0f)
        assertEquals(.451f, at(2*S).filledFraction!!, .00001f)
        assertEquals(.9f, at(2*S).damageTrailFraction!!, 0f)
        assertTrue(at(2*S).borderPulse)
        assertTrue(at(2*S).damageTimingReconstructed)
        assertFalse(at(2*S).currentHpConfirmed)
        assertEquals(.002f, at(3*S).filledFraction!!, 0f)
        assertEquals(.451f, at(3*S).damageTrailFraction!!, .00001f)
        assertEquals(.002f, at(4*S).lastSeenFraction!!, 0f)
        assertEquals(.451f, at(2*S).filledFraction!!, .00001f)
    }

    @Test fun `cadence-only hits flash without inventing damage on a living sliver`() {
        val rows = listOf(row("before", S), row("after", 4*S))
        val track = ReplayHpTrack(rows, "OPPONENT", ReplayHpEvidence(rows),
            listOf(cycle(2*S), cycle(3*S, inferred = true)))
        val appearance = listOf(ReplayHpAppearance("sealeo", 0))
        val second = track.stateAt(3*S, appearance, false, 0)
        assertTrue(second.borderPulse)
        assertNull(second.damageTrailFraction)
        assertEquals(.002f, second.filledFraction!!, 0f)
    }

    @Test fun `a captured individual tick a charge or a switch keeps the loss out of earlier attacks`() {
        val before = row("before", S, payload = hp.copy(filledFraction = .9f, fillColor = "GREEN"))
        val after = row("after", 4*S)
        val tick = row("tick", 3*S, payload = ArchivedActiveHpBarDamageTickMeasured("OPPONENT", .9f, .002f, .898f))
        val appearance = listOf(ReplayHpAppearance("houndoom", 0))
        for ((rows, continuous) in listOf(listOf(before, after, tick) to true, listOf(before, after) to false)) {
            val track = ReplayHpTrack(rows, "OPPONENT", ReplayHpEvidence(rows),
                listOf(cycle(2*S), cycle(3*S)), uninterrupted = { _, _ -> continuous })
            val early = track.stateAt(2*S, appearance, false, 0)
            assertEquals(.9f, early.filledFraction!!, 0f)
            assertNull(early.damageTrailFraction)
            assertEquals(.002f, track.stateAt(4*S, appearance, false, 0).filledFraction!!, 0f)
        }
    }

    @Test fun centeredLowBarNeedsConsistentOrdinaryReadingsFromTheSameAppearanceToDisregardIt() {
        val camera = row("center", 2*S, payload = hp.copy(barLeft = 390, barRight = 695))
            .copy(sourceId = "CHARGED_IMPACT_OPPONENT_HP_BAR_FRAME_WITNESS")
        val before = row("before", S, payload = hp.copy(filledFraction = .6f))
        val after = row("after", 3*S, payload = hp.copy(filledFraction = .601f))
        val again = row("again", 3*S + S/4, payload = hp.copy(filledFraction = .599f))
        fun display(rows: List<ArchivedRealityArticle>, continuous: Boolean): Float {
            val track = ReplayHpTrack(rows, "OPPONENT", ReplayHpEvidence(rows),
                sameAppearance = { _, _ -> continuous })
            return track.stateAt(2*S, listOf(ReplayHpAppearance("gallade", 0)), false, 0).filledFraction!!
        }
        val captured = listOf(before, camera, after, again)
        assertEquals(.6f, display(captured.reversed(), true), 0f)
        assertEquals(.002f, display(captured, false), 0f)
        assertEquals(.002f, display(listOf(before, camera, after), true), 0f)
        assertEquals(.002f, display(listOf(before, camera, row("still-low", 3*S), again), true), 0f)
    }

    @Test fun bracketedSettlingDipAlignsWithTheQualifiedLandingAndProtectsTinyOrCorroboratedLows() {
        fun bar(name: String, ms: Long, fill: Float, confidence: Float = .99f) =
            row(name, ms * 1_000_000, confidence, hp.copy(filledFraction = fill))
        val impact = 2*S
        val tick = row("tick", impact, payload = ArchivedActiveHpBarDamageTickMeasured("OPPONENT", .13f, .10f, .03f))
        val landing = cycle(impact).let { it.copy(witness = it.witness.copy(
            damageTicks = listOf(ReplayFastMoveDamage(impact, .13f, .10f)))) }
        fun display(dip: Float, extras: List<ArchivedRealityArticle> = emptyList(), continuous: Boolean = true): List<Float?> {
            val rows = listOf(bar("before", 1000, .13f), bar("early", 1400, .10f), bar("dip", 1650, dip, .85f),
                bar("settled", 2000, .10f), tick, bar("next", 2200, .10f), bar("later", 2300, .10f)) + extras
            val track = ReplayHpTrack(rows, "OPPONENT", ReplayHpEvidence(rows), listOf(landing),
                sameAppearance = { _, _ -> true }, uninterrupted = { _, _ -> continuous })
            val appearance = listOf(ReplayHpAppearance("cradily", 0))
            return listOf(impact-1, impact).map { track.stateAt(it, appearance, false, 0).filledFraction }
        }
        assertEquals(listOf(.13f, .10f), display(.08f))
        assertEquals(listOf(.002f, .002f), display(.002f))
        assertEquals(listOf(.08f, .08f), display(.08f, listOf(bar("independent", 1710, .08f))))
        assertEquals(listOf(.08f, .08f), display(.08f, continuous = false))
    }

    private fun cycle(impact: Long, inferred: Boolean = false) = ReplayFastMoveCycle(
        ReplayFastMoveWitness("PLAYER", impact, "Dragon Tail", null, S, 0, 10*S,
            if (inferred) setOf(ReplayFastMoveRhythm.INFERRED) else setOf("AUDIO"),
            if (inferred) emptySet() else setOf("move-$impact"),
            if (inferred) emptySet() else setOf(impact)),
        impact - S, impact,
    )

    private companion object { const val S = 1_000_000_000L }
}
