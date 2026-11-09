package com.example.overdex.battle.forecast

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.BattleResult
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class OpponentEnergyForecastCacheTest {
    private val opponent = ActivePokemonSide.OPPONENT
    private val water = Move("Water Gun", PokemonType.WATER, 3, 3, true, 1)
    private val snow = Move("Powder Snow", PokemonType.ICE, 5, 8, true, 2)
    private fun nanos(t: Double) = (t * 1_000_000_000L).toLong()
    private fun article(id: String, at: Double, payload: TestimonyPayload) = RealityArticle(
        ArticleId(id), 0L, 0L, SourceId("TEST"), payload, monotonicTimeNanos = nanos(at))
    private fun hit(id: String, species: String = "Sealeo") = FastMoveUseObserved(
        id, opponent, ActivePokemonSide.PLAYER, attackerSpeciesName = species,
        evidenceKinds = listOf("HP_BORDER_PULSE"))

    /** A cold projection replays the same original inputs for every query. */
    private class Comparison {
        val cached = OpponentEnergyForecast()
        private val inputs = mutableListOf<(OpponentEnergyForecast) -> Unit>()
        fun change(input: (OpponentEnergyForecast) -> Unit) {
            inputs += input
            input(cached)
        }
        fun check(at: Long): OpponentEnergyEstimate? {
            val fresh = OpponentEnergyForecast()
            inputs.forEach { it(fresh) }
            val expected = fresh.estimate(at)
            assertEquals("cached projection at $at", expected, cached.estimate(at))
            return expected
        }
    }

    private fun Comparison.setup() {
        change { it.registerMoves("Sealeo", listOf(water, snow)) }
        change { it.registerMoves("Vaporeon", listOf(water)) }
        change { it.observe(article("species", -1.0, ActivePokemonSpeciesWitnessed(opponent, "Sealeo"))) }
        change { it.observe(article("go", 0.0, MatchStarted)) }
    }

    @Test fun `unchanged ticks and normal frame extensions never replay semantic history`() {
        val comparison = Comparison().apply { setup() }
        comparison.change { it.observeBattlefield(nanos(0.0), true) }
        comparison.change { it.observeBattlefield(nanos(.125), true) }
        repeat(100) { index ->
            val event = article("hit-$index", .125, hit("use-$index"))
            comparison.change { it.observe(event) }
        }
        comparison.check(nanos(.125))
        val before = comparison.cached.projectionWork()
        assertEquals(102L, before.eventsApplied)
        repeat(1_000) { index ->
            val at = nanos(.25 + index * .125)
            comparison.cached.observeBattlefield(at, true)
            comparison.cached.estimate(at)
        }
        assertEquals(before, comparison.cached.projectionWork())

        // One appended semantic event is applied once, without replaying the 102.
        comparison.cached.observe(article("last-hit", 125.125, hit("last-use")))
        comparison.cached.estimate(nanos(125.125))
        assertEquals(before.copy(eventsApplied = before.eventsApplied + 1), comparison.cached.projectionWork())
    }

    @Test fun `future and duplicate evidence do not invalidate the processed prefix`() {
        val comparison = Comparison().apply { setup() }
        comparison.change { it.observeBattlefield(nanos(0.0), true) }
        comparison.change { it.observeBattlefield(nanos(.125), true) }
        comparison.check(nanos(.25))
        val before = comparison.cached.projectionWork()
        val future = article("future", 10.0, GetReadyWitnessed)
        comparison.change { it.observe(future) }
        comparison.change { it.observe(future) }
        comparison.change { it.registerMoves("Sealeo", listOf(water, snow)) }
        comparison.check(nanos(.5))
        assertEquals(before, comparison.cached.projectionWork())
        comparison.check(nanos(10.0))
        assertEquals(before.copy(eventsApplied = before.eventsApplied + 1), comparison.cached.projectionWork())
    }

    @Test fun `late cues equal-time insertion and backward queries match fresh replay`() {
        val comparison = Comparison().apply { setup() }
        for (index in 0..160) comparison.change { it.observeBattlefield(nanos(index * .125), true) }
        comparison.change { it.observe(article("hit", 15.0, hit("use"))) }
        comparison.check(nanos(18.0))
        comparison.change { it.observe(article("late-ready", 2.0, GetReadyWitnessed)) }
        comparison.change { it.observe(article("used", 12.0, ChargeMoveUsedAnnounced)) }
        comparison.change { it.observe(article("spend", 12.0, ChargedMoveEnergySpent(
            opponent, "Sealeo", "Body Slam", 35, "ANNOUNCEMENT"))) }
        comparison.check(nanos(18.0))
        comparison.change { it.observe(article("timer", 12.0, BattleDecisionTimerObserved(3, "3", "OCR"))) }
        for (at in listOf(18.0, 1.0, 12.0, 16.0, 15.0, 18.0)) comparison.check(nanos(at))
        comparison.change { it.observe(article("result", 19.0, MatchEnded(BattleResult.WIN))) }
        comparison.check(nanos(30.0))
        comparison.check(nanos(18.0))
    }

    @Test fun `late knowledge revises past projection but repeated candidate sets do no work`() {
        val comparison = Comparison().apply { setup() }
        for (index in 0..80) comparison.change { it.observeBattlefield(nanos(index * .125), true) }
        comparison.change { it.observe(article("hit", 1.0, hit("use"))) }
        assertEquals(40, comparison.check(nanos(5.0))!!.energy)
        comparison.change { it.restrictCandidates("Sealeo", listOf("Water Gun")) }
        assertEquals(30, comparison.check(nanos(5.0))!!.energy)
        val before = comparison.cached.projectionWork()
        repeat(50) { comparison.change { it.restrictCandidates("Sealeo", listOf("water-gun", "Water Gun")) } }
        comparison.check(nanos(6.0))
        assertEquals(before, comparison.cached.projectionWork())
        comparison.change { it.observe(article("identity", 2.0, FastMoveIdentified(
            opponent, "Sealeo", "Water Gun", nanos(.5), null, 0, "EFFECTIVENESS_TEXT"))) }
        assertTrue(comparison.check(nanos(5.0))!!.moveIdentified)
        comparison.change { it.registerMoves("Sealeo", listOf(water.copy(energy = 4), snow)) }
        assertEquals(40, comparison.check(nanos(5.0))!!.energy)
    }

    @Test fun `confirming an apparent pair reconciles a previously invisible hit`() {
        val comparison = Comparison()
        comparison.change { it.registerMoves("Sealeo", listOf(water)) }
        comparison.change { it.observe(article("species", 10.0, ActivePokemonSpeciesWitnessed(opponent, "Sealeo"))) }
        comparison.change { it.observeBattlefield(nanos(10.0), true) }
        comparison.change { it.observe(article("hit", 10.0, hit("first"))) }
        assertEquals(0, comparison.check(nanos(10.1))!!.observedUses)
        val before = comparison.cached.projectionWork()
        comparison.change { it.observeBattlefield(nanos(10.125), true) }
        assertEquals(1, comparison.check(nanos(10.25))!!.observedUses)
        assertEquals(before.builds + 1, comparison.cached.projectionWork().builds)
    }

    @Test fun `brief gap recovery and stale frame boundaries preserve source-time spending`() {
        val comparison = Comparison().apply { setup() }
        comparison.change { it.restrictCandidates("Sealeo", listOf("Water Gun")) }
        for (index in 0..7) comparison.change { it.observeBattlefield(nanos(index * .125), true) }
        comparison.change { it.observeBattlefield(nanos(.9), false) }
        comparison.change { it.observe(article("spend", 1.0, ChargedMoveEnergySpent(
            opponent, "Sealeo", "Body Slam", 35, "ANNOUNCEMENT"))) }
        assertEquals(32, comparison.check(nanos(1.0))!!.unexplainedSpend)
        comparison.change { it.observeBattlefield(nanos(1.125), true) }
        assertEquals(29, comparison.check(nanos(1.375))!!.unexplainedSpend)
        comparison.change { it.observeBattlefield(nanos(2.0), true) }
        comparison.change { it.observe(article("isolated-hit", 2.0, hit("isolated"))) }
        assertEquals(0, comparison.check(nanos(2.0))!!.observedUses)
        comparison.change { it.observeBattlefield(nanos(2.125), true) }
        assertEquals(1, comparison.check(nanos(2.25))!!.observedUses)
        comparison.change { it.observeBattlefield(nanos(2.05), false) } // reordered frame ignored
        comparison.check(nanos(2.25))
    }

    @Test fun `identity learned after result revises historical energy without resuming the clock`() {
        val comparison = Comparison().apply { setup() }
        for (index in 0..160) comparison.change { it.observeBattlefield(nanos(index * .125), true) }
        comparison.change { it.observe(article("result", 5.0, MatchEnded(BattleResult.WIN))) }
        assertEquals(40, comparison.check(nanos(10.0))!!.energy)
        comparison.change { it.observe(article("late-identity", 6.0, FastMoveIdentified(
            opponent, "Sealeo", "Water Gun", nanos(.5), null, 0, "EFFECTIVENESS_TEXT"))) }
        val reconciled = comparison.check(nanos(10.0))!!
        assertEquals(30, reconciled.energy)
        assertTrue(reconciled.moveIdentified)
        assertTrue(reconciled.paused)
        assertEquals(reconciled, comparison.check(nanos(20.0)))
        comparison.check(nanos(4.0))
        assertEquals(reconciled, comparison.check(nanos(20.0)))
    }

    @Test fun `mixed late inputs switches faints restarts and reset match cold projections`() {
        val comparison = Comparison().apply { setup() }
        val random = Random(1924)
        repeat(100) { index ->
            val at = index * .25
            comparison.change { it.observeBattlefield(nanos(at), index % 13 !in 8..10) }
            val sourceAt = (at - random.nextInt(0, 12) * .125).coerceAtLeast(0.0)
            val payload = when (index % 11) {
                0 -> ActivePokemonSpeciesWitnessed(opponent, if (index % 2 == 0) "Sealeo" else "Vaporeon")
                1 -> GetReadyWitnessed
                2 -> ChargeMoveUsedAnnounced
                3 -> ChargedMoveEnergySpent(opponent, "Sealeo", "Body Slam", 35, "ANNOUNCEMENT")
                4 -> ActivePokemonFaintedWitnessed(ActivePokemonSide.PLAYER, "Pikachu")
                5 -> BattleDecisionTimerObserved(1, "1", "OCR")
                6 -> ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Vaporeon")
                7 -> hit("use-$index")
                8 -> ActivePokemonFaintedWitnessed(opponent, "Vaporeon")
                9 -> ObservationRestartedByUser
                else -> MatchStarted
            }
            comparison.change { it.observe(article("mixed-$index", sourceAt, payload)) }
            comparison.check(nanos(at))
            comparison.check(nanos(random.nextDouble(0.0, at + .1)))
        }
        comparison.change { it.reset() }
        assertNull(comparison.check(nanos(100.0)))
        comparison.setup()
        comparison.check(nanos(1.0))
    }
}
