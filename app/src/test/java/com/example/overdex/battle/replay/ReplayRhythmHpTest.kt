package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Test

class ReplayRhythmHpTest {
    private fun ms(t: Long) = t * 1_000_000L
    private fun article(t: Long, payload: ArchivedTestimonyPayload, confidence: Float, source: String) =
        ArchivedRealityArticle("id-$t-$source", "match", 0, 0, source, payload, emptyList(),
            confidence, null, emptyList(), ms(t))
    private fun hp(t: Long, amount: Float, side: String = "OPPONENT", confidence: Float = .95f,
                   source: String = "normal", left: Int = 220, width: Int = 660) =
        article(t, ArchivedActiveHpBarMeasured(side, left, 300, left + 192, 317, amount,
            width, 1400, if (amount <= .2f) "RED" else "GREEN"), confidence, source)
    private val anchor = ReplayFastMoveCycle(
        ReplayFastMoveWitness("PLAYER", ms(2500), "Incinerate", null, ms(2500), 0, ms(10000),
            setOf("HP_DAMAGE_TICK"), setOf("tick"), setOf(ms(2500)),
            listOf(ReplayFastMoveDamage(ms(2500), .605f, .505f))), 0, ms(2500))
    private val evidenceOfLoss = listOf(hp(2350, .605f), hp(2500, .505f))
    private fun veto(samples: List<ArchivedRealityArticle>, cycle: ReplayFastMoveCycle = anchor,
                     start: Long = 0, end: Long = 10000, supported: List<ReplayFastMoveCycle> = emptyList()) =
        ReplayRhythmHp(samples, ReplayHpEvidence(samples), supported).contradicts(cycle, ms(5000), ms(start), ms(end))

    @Test fun visibleSameExchangeDamageAndUnchangedWholeBarSuspendOnlyTheAttackingSide() {
        val samples = evidenceOfLoss + listOf(hp(4480, .505f), hp(5900, .505f))
        assertTrue(veto(samples))
        assertTrue(veto(samples.reversed()))
        assertFalse(veto(samples, anchor.copy(witness = anchor.witness.copy(side = "OPPONENT"))))
        assertFalse(veto(samples, start = 2700)) // Damage was before the new appearance or charge.
        assertFalse(veto(samples, end = 5600)) // Later HP belongs to another window.
    }

    @Test fun protectedLiveSliverCanContradictAResolvableHitWithoutBecomingDamageOrZero() {
        val tiny = 1f / 188
        val samples = evidenceOfLoss + listOf(hp(4450, tiny), hp(5850, tiny))
        assertTrue(veto(samples))
        val quietAnchor = anchor.copy(impactAtNanos = ms(3000), witness = anchor.witness.copy(damageTicks = emptyList()))
        assertFalse(veto(samples, quietAnchor))
        assertTrue(veto(samples, quietAnchor, supported = listOf(anchor)))
        val changed = article(4000, ArchivedStatChangeObserved("OPPONENT", "DEFENSE", 1, 1, null, "sound", "test"), .9f, "stat")
        assertFalse(veto(samples + changed))
        assertFalse(veto(samples, quietAnchor, supported = listOf(anchor.copy(witness = anchor.witness.copy(moveName = "Ember")))))
    }

    @Test fun uncertainTailCanCoexistWithASettledLiveEdgeButAnOrangeTrailCannot() {
        fun profile(t: Long, live: Int, damage: Int, top: Int = 300) = article(t, ArchivedCompleteHpBarObserved(
            "OPPONENT", 220, top, 412, 317, 660, 1400, 188, live, damage, "LIVE_COLOR",
            null, null, 229f, 88f, unmaskedUncertainColumns = 47), .99f, "complete")
        // The 12:12 Vaporeon exchange shows 24 live columns, then 11 live plus 13 orange;
        // later whole bars retain the same live sliver against a noisy arena background.
        val from = .13297872f
        val to = .06382979f
        val cycle = anchor.copy(witness = anchor.witness.copy(damageTicks =
            listOf(ReplayFastMoveDamage(ms(2500), from, to))))
        val samples = listOf(hp(2200, from), hp(2500, to), profile(2500, 11, 13),
            hp(3750, to), profile(3750, 11, 0), hp(5980, to), profile(5980, 11, 0, top = 301))
        assertTrue(veto(samples, cycle))
        assertFalse(veto(samples + profile(5600, 11, 8), cycle))
        assertFalse(veto(samples + profile(5980, 40, 0), cycle))
        assertFalse(veto(samples + profile(5980, 11, 0, top = 303), cycle))
    }

    @Test fun staleLowConfidenceObscuredAndRelocatedReadingsCannotEstablishAPause() {
        val pre = hp(4450, .505f)
        val post = hp(5880, .505f)
        val profile = ArchivedCompleteHpBarObserved("OPPONENT", 220, 300, 412, 317, 660, 1400,
            188, 0, 0, "UNRESOLVED_COLOR", null, null, .9f, null)
        assertFalse(veto(evidenceOfLoss + pre))
        assertFalse(veto(evidenceOfLoss + listOf(hp(3280, .505f), post)))
        assertFalse(veto(evidenceOfLoss + listOf(pre, hp(7400, .505f))))
        assertFalse(veto(evidenceOfLoss + listOf(hp(4450, .505f, confidence = .7f), post)))
        assertFalse(veto(evidenceOfLoss + listOf(pre, hp(5880, .505f, source = "CHARGED_IMPACT_OPPONENT_HP_BAR_FRAME_WITNESS"))))
        assertFalse(veto(evidenceOfLoss + listOf(pre, hp(5880, .505f, left = 330))))
        assertFalse(veto(evidenceOfLoss + listOf(pre, hp(5880, .505f, width = 400))))
        assertFalse(veto(evidenceOfLoss + listOf(pre, post, article(5000, profile, .95f, "complete"))))
    }

    @Test fun realOrUnresolvableLossCannotEstablishAPause() {
        val pre = hp(4450, .505f)
        assertFalse(veto(evidenceOfLoss + listOf(pre, hp(5850, .405f))))
        assertFalse(veto(evidenceOfLoss + listOf(pre, hp(5000, .46f), hp(5850, .505f))))
        val small = anchor.copy(witness = anchor.witness.copy(damageTicks =
            listOf(ReplayFastMoveDamage(ms(2500), .515f, .505f))))
        assertFalse(veto(listOf(hp(2350, .515f), hp(2500, .505f), pre, hp(5850, .505f)), small))
        assertFalse(veto(listOf(hp(2350, .95f), hp(2500, .505f), pre, hp(5850, .505f))))
    }

    @Test fun twoUnwitnessedBeatsCanResolveToOneMeasuredNonlethalEdge() {
        fun profile(t: Long, live: Int, damage: Int, columns: Int = 188) = article(t, ArchivedCompleteHpBarObserved(
            "OPPONENT", 220, 300, 412, 317, 660, 1400, columns, live, damage, "LIVE_COLOR",
            null, null, 100f, 25f), .98f, "complete")
        // A five-turn attack previously took 10 columns. Two more projected hits
        // cannot leave eight live. The first new edge includes all 10 lost columns.
        val frames = listOf(hp(2200, 30f / 188), hp(2500, 20f / 188),
            hp(4000, 20f / 188), profile(4000, 18, 7, columns = 189),
            hp(6700, 9f / 188), profile(6700, 8, 10),
            hp(6950, 11f / 188, confidence = .65f),
            hp(8500, 10f / 188), profile(8500, 8, 0))
        val hit = anchor.copy(witness = anchor.witness.copy(damageTicks =
            listOf(ReplayFastMoveDamage(ms(2500), 30f / 188, 20f / 188))))
        fun adapter(data: List<ArchivedRealityArticle>) = ReplayRhythmHp(data, ReplayHpEvidence(data), listOf(hit))
        fun check(data: List<ArchivedRealityArticle>, supported: ReplayFastMoveCycle = hit, from: Long = 0) =
            ReplayRhythmHp(data, ReplayHpEvidence(data), listOf(supported))
                .oneReception(supported, ms(5000), ms(7500), ms(from), ms(10000))
        assertEquals(ms(6700), check(frames))
        assertEquals(ms(6700), check(frames.reversed()))
        val reader = adapter(frames)
        val presented = ReplayFastMoveRhythm.present(listOf(hit), 0, ms(15000), emptyList(), emptyList(),
            { _, _, _ -> true }, reader::contradicts, oneReception = reader::oneReception)
        assertEquals(listOf(ms(2500), ms(6700)), presented.map { it.impactAtNanos })
        val decor = presented.last()
        assertTrue(ReplayFastMoveRhythm.HP_RECEPTION in decor.witness.evidenceKinds)
        assertTrue(decor.witness.articleIds.isEmpty() && decor.witness.damageTicks.isEmpty())
        assertEquals(ms(4200), decor.startedAtNanos)
        assertSame(hit, presented.first())

        assertNull(check(frames.filter { it.monotonicTimeNanos != ms(8500) }))
        assertNull(check(frames + profile(6700, 8, 0))) // Conflicting profiles at one time.
        assertNull(check(frames + hp(7250, 13f / 188))) // A trusted intermediate live edge disagrees.
        assertNull(check(frames + profile(4200, 0, 18))) // Complete-bar ambiguity or a possible faint.
        assertNull(check(frames, from = 2900)) // Switch/charge since measured loss.
        assertNull(check(frames + article(6000, ArchivedStatChangeObserved(
            "PLAYER", "ATTACK", 1, 1, null, "sound", "test"), .95f, "stat")))
        val noComparableHit = hit.copy(witness = hit.witness.copy(damageTicks = emptyList()))
        assertNull(check(frames, noComparableHit))
        val halfHit = hit.copy(witness = hit.witness.copy(damageTicks =
            listOf(ReplayFastMoveDamage(ms(2500), 25f / 188, 20f / 188))))
        assertNull(check(frames.map { if (it.monotonicTimeNanos == ms(2200)) hp(2200, 25f / 188) else it }, halfHit))
        assertNull(check(frames.map { if (it.monotonicTimeNanos == ms(8500) && it.payload is ArchivedCompleteHpBarObserved)
            profile(8500, 8, 1) else it })) // Orange has not yet settled.
    }
}
