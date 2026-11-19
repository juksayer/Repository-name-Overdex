package com.example.overdex.battle.replay

import org.junit.Assert.*
import org.junit.Test

class ReplayFastMoveRhythmTest {
    private val turn = 500_000_000L
    private fun hit(at: Int, turns: Int = 5, side: String = "PLAYER", move: String? = "Incinerate"): ReplayFastMoveCycle {
        val impact = at * turn
        val witness = ReplayFastMoveWitness(side, impact, move, null, turns * turn, 0, Long.MAX_VALUE,
            setOf("HP_DAMAGE_TICK"), setOf("$side-$at"), setOf(impact),
            listOf(ReplayFastMoveDamage(impact, .9f, .8f)), archivedUseWithDamage = true)
        return ReplayFastMoveCycle(witness, impact - turns * turn, impact)
    }

    private fun predict(uses: List<ReplayFastMoveCycle>, endTurn: Int,
                        resets: List<Long> = emptyList(), charges: List<LongRange> = emptyList(),
                        eligible: (String, Long, Long) -> Boolean = { _, _, _ -> true },
                        contradicts: (ReplayFastMoveCycle, Long, Long, Long) -> Boolean = { _, _, _, _ -> false }
    ) = ReplayFastMoveRhythm.present(uses, 0, endTurn * turn, resets, charges, eligible, contradicts)
        .filter { ReplayFastMoveRhythm.INFERRED in it.witness.evidenceKinds && it !in uses }

    @Test fun independentlyFillsBothSidesButKeepsRealHitsAndSourceClocksSeparate() {
        val uses = listOf(hit(5), hit(15), hit(12, 1, "OPPONENT", "Water Gun"))
        val inferred = predict(uses, 21)
        assertEquals(listOf(10, 20), inferred.filter { it.witness.side == "PLAYER" }.map { (it.impactAtNanos / turn).toInt() })
        assertEquals((13..20).toList(), inferred.filter { it.witness.side == "OPPONENT" }.map { (it.impactAtNanos / turn).toInt() })
        assertEquals(inferred, predict(uses.reversed(), 21))
        assertTrue(inferred.all { it.witness.evidenceKinds == setOf(ReplayFastMoveRhythm.INFERRED) &&
            it.witness.articleIds.isEmpty() && it.witness.sourceTimesNanos.isEmpty() && it.witness.damageTicks.isEmpty() &&
            !it.witness.archivedUseWithDamage })
        assertTrue(uses.all { it.witness.articleIds.isNotEmpty() && it.witness.damageTicks.size == 1 })
    }

    @Test fun offBeatUseKeepsItsWindupAndEstablishesANewRhythm() {
        // The projected landing at turn 15 would overlap the real attack at 13..18.
        val uses = listOf(hit(5), hit(18))
        assertEquals(listOf(10L, 23L), predict(uses, 24).map { it.impactAtNanos / turn })
    }

    @Test fun capturedJitterNeverAddsASecondLandingOrOverlapsTheConfirmedFlight() {
        val next = hit(20)
        val jitter = 80_000_000L
        val earlier = next.copy(startedAtNanos = next.startedAtNanos - jitter, impactAtNanos = next.impactAtNanos - jitter,
            witness = next.witness.copy(observedAtNanos = next.impactAtNanos - jitter, damageTicks = emptyList()))
        val cycles = predict(listOf(hit(5), earlier), 26)
        assertEquals(listOf(10 * turn, 15 * turn - jitter, 25 * turn - jitter), cycles.map { it.impactAtNanos })
        assertEquals(earlier.startedAtNanos, cycles[1].impactAtNanos)
        assertEquals(earlier.impactAtNanos, cycles.last().startedAtNanos)
    }

    @Test fun corruptEndClockCannotAllocateAnUnboundedNumberOfAnimations() {
        assertTrue(predict(listOf(hit(5, 1)), Int.MAX_VALUE).isEmpty())
    }

    @Test fun chargeResumesOnceButSwitchAndReturnEachNeedANewAnchor() {
        val charge = 12 * turn until 18 * turn
        val cycles = predict(listOf(hit(5), hit(38)), 60, resets = listOf(26 * turn, 47 * turn),
            charges = listOf(charge, 13 * turn until 15 * turn))
        assertEquals(listOf(10L, 23L, 43L), cycles.map { it.impactAtNanos / turn })
        assertEquals(18 * turn, cycles[1].startedAtNanos)
        assertTrue(cycles.none { it.startedAtNanos in charge || it.impactAtNanos in charge })
    }

    @Test fun hardCutInsideChargeOrCoincidentWithOutgoingImpactClearsAnchor() {
        val charge = 12 * turn until 22 * turn
        val before = predict(listOf(hit(5)), 40, listOf(18 * turn), listOf(charge))
        assertEquals(listOf(10 * turn), before.map { it.impactAtNanos })
        assertTrue(predict(listOf(hit(5)), 40, listOf(5 * turn)).isEmpty())
        assertTrue(predict(listOf(hit(5)), 10).isEmpty()) // Presentation never awards the final beat.
    }

    @Test fun survivingHPSuppressesUntilTheNextIndependentUse() {
        val uses = listOf(hit(5), hit(30), hit(5, 1, "OPPONENT", "Water Gun"))
        val inferred = predict(uses, 36, charges = listOf(17 * turn until 20 * turn)) { anchor, at, from, until ->
            anchor.witness.side == "PLAYER" && at == 10 * turn && from == 0L && until == 17 * turn
        }
        assertEquals(listOf(35 * turn), inferred.filter { it.witness.side == "PLAYER" }.map { it.impactAtNanos })
        assertTrue(inferred.any { it.witness.side == "OPPONENT" && it.impactAtNanos == 11 * turn })
    }

    @Test fun unknownOrIneligibleUseCannotSeedAChain() {
        val unknown = hit(15, move = null)
        assertEquals(listOf(10 * turn), predict(listOf(hit(5), unknown), 40).map { it.impactAtNanos })
        val noDuration = unknown.copy(witness = unknown.witness.copy(moveName = "Incinerate", durationNanos = null))
        assertTrue(predict(listOf(noDuration), 50).isEmpty())
        assertTrue(predict(listOf(hit(5)), 50, eligible = { _, _, _ -> false }).isEmpty())
        assertTrue(predict(predict(listOf(hit(5)), 20), 30).isEmpty())
    }

    @Test fun supportedLaunchAndSupportedImpactAnchorTheSameFullCycle() {
        val landed = hit(10)
        val launched = landed.copy(witness = landed.witness.copy(observedAtNanos = landed.startedAtNanos,
            sourceTimesNanos = setOf(landed.startedAtNanos), damageTicks = emptyList(),
            archivedUseWithDamage = false, evidenceKinds = setOf("SUPPORTED_ATTACK_START")))
        val inferred = predict(listOf(launched), 21)
        assertEquals(listOf(10L, 15L), inferred.map { it.startedAtNanos / turn })
        assertEquals(listOf(15L, 20L), inferred.map { it.impactAtNanos / turn })
        assertEquals(predict(listOf(landed), 21).map { it.impactAtNanos }, inferred.map { it.impactAtNanos })
    }

    @Test fun aWitnessAfterFourMissedBeatsConfirmsOneOccurrenceAndKeepsItsSourceClock() {
        val first = hit(5)
        val raw = hit(30)
        val received = raw.impactAtNanos + 80_000_000L
        val later = raw.copy(startedAtNanos = received - 5 * turn, impactAtNanos = received,
            witness = raw.witness.copy(observedAtNanos = received, sourceTimesNanos = setOf(received),
                damageTicks = emptyList(), archivedUseWithDamage = false, evidenceKinds = setOf("FAST_MOVE_USE")))
        fun schedule(use: ReplayFastMoveCycle = later, reset: List<Long> = emptyList(),
                     charges: List<LongRange> = emptyList(), paused: Boolean = false, fixed: Boolean = false) =
            ReplayFastMoveRhythm.present(listOf(use, first), 0, 36 * turn, reset, charges,
                { _, _, _ -> true }, { _, at, _, _ -> paused && at == 15 * turn }, { fixed })
        val result = schedule()
        val confirmed = result.single { it.witness.articleIds == later.witness.articleIds }
        assertEquals(later.witness, confirmed.witness)
        assertEquals(30 * turn, confirmed.impactAtNanos)
        assertEquals((5..35 step 5).map { it * turn }, result.map { it.impactAtNanos })
        assertEquals(received, later.impactAtNanos)
        val measured = later.copy(witness = later.witness.copy(damageTicks =
            listOf(ReplayFastMoveDamage(received, .5f, .4f))))
        val offBeat = later.copy(impactAtNanos = received + turn, startedAtNanos = later.startedAtNanos + turn,
            witness = later.witness.copy(observedAtNanos = received + turn))
        for (blocked in listOf(schedule(reset = listOf(14 * turn)), schedule(charges = listOf(12 * turn..16 * turn)),
            schedule(paused = true), schedule(use = measured), schedule(fixed = true))) {
            assertEquals(received, blocked.single { it.witness.articleIds == later.witness.articleIds }.impactAtNanos)
        }
        assertEquals(offBeat, schedule(use = offBeat).single { it.witness.articleIds == later.witness.articleIds })
    }
}
