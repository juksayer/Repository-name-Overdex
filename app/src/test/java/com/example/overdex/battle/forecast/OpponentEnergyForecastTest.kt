package com.example.overdex.battle.forecast

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.BattleResult
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import org.junit.Assert.*
import org.junit.Test

class OpponentEnergyForecastTest {
    private val opponent = ActivePokemonSide.OPPONENT
    private val water = Move("Water Gun", PokemonType.WATER, 3, 3, true, 1)
    private val snow = Move("Powder Snow", PokemonType.ICE, 5, 8, true, 2)
    private fun article(id: String, at: Double, payload: TestimonyPayload) = RealityArticle(
        ArticleId(id), 0L, 0L, SourceId("TEST"), payload, monotonicTimeNanos = nanos(at))
    private fun nanos(t: Double) = (t * 1_000_000_000L).toLong()
    private fun OpponentEnergyForecast.add(id: String, at: Double, payload: TestimonyPayload) = observe(article(id, at, payload))
    private fun OpponentEnergyForecast.visible(from: Double = 0.0, until: Double = 200.0) {
        var at = from
        while (at <= until) { observeBattlefield(nanos(at), true); at += .125 }
    }
    private fun engine(vararg available: Move = arrayOf(water, snow)) = OpponentEnergyForecast().apply {
        visible()
        registerMoves("Sealeo", available.toList())
        add("species", -1.0, ActivePokemonSpeciesWitnessed(opponent, "Sealeo"))
        add("go", 0.0, MatchStarted)
    }
    private fun hit(id: String, species: String? = "Sealeo", side: ActivePokemonSide = opponent) = FastMoveUseObserved(
        id, side, if (side == opponent) ActivePokemonSide.PLAYER else opponent,
        attackerSpeciesName = species, evidenceKinds = listOf("HP_BORDER_PULSE"))

    @Test fun `five active seconds accrue expected energy even with no captured hit`() {
        val state = engine().estimate(nanos(5.0))!!
        assertEquals("Powder Snow", state.assumedMoveName)
        assertEquals(40, state.energy); assertEquals(5, state.expectedUses)
        assertEquals(0, state.observedUses); assertEquals(10, state.activeTurns)
        assertFalse(state.moveIdentified)
    }

    @Test fun `choose energy per turn not biggest single gain`() {
        val quick = water.copy(name = "Quick", energy = 5)
        val slow = snow.copy(name = "Slow", energy = 20, turns = 5)
        assertEquals("Quick", engine(quick, slow).estimate(nanos(5.0))!!.assumedMoveName)
    }

    @Test fun `late identity revises projection without rewriting or double counting hits`() {
        val f = engine()
        val original = article("hit", 1.0, hit("use"))
        f.observe(original); f.add("duplicate", 1.0, hit("use"))
        assertEquals(40, f.estimate(nanos(5.0))!!.energy)
        f.add("actual", 2.0, FastMoveIdentified(opponent, "Sealeo", "Water Gun", nanos(.5),
            null, 0, "EFFECTIVENESS_TEXT"))
        val state = f.estimate(nanos(5.0))!!
        assertEquals(30, state.energy); assertEquals(10, state.expectedUses)
        assertEquals(1, state.observedUses); assertTrue(state.moveIdentified)
        assertEquals(original, article("hit", 1.0, hit("use")))
    }

    @Test fun `charge timer and QTE cannot earn idle wall clock energy`() {
        val f = engine(water)
        f.add("incoming", 2.0, AttackIncoming)
        f.add("timer", 4.0, BattleDecisionTimerObserved(5, "5", "OCR"))
        f.add("clear", 9.0, BattleDecisionTimerObserved(null, "", "OCR"))
        val idle = f.estimate(nanos(12.0))!!
        assertEquals(12, idle.energy); assertTrue(idle.paused)
        f.add("landed", 11.0, ChargeMoveUsedAnnounced)
        f.add("resumed", 12.5, hit("resumed"))
        val resumed = f.estimate(nanos(13.0))!!
        assertEquals(18, resumed.energy); assertFalse(resumed.paused)
    }

    @Test fun `faint clears energy and repeated badge does not revive it`() {
        val f = engine()
        f.add("faint", 5.0, ActivePokemonFaintedWitnessed(opponent, "Sealeo"))
        f.add("stale", 6.0, ActivePokemonSpeciesWitnessed(opponent, "Sealeo"))
        f.add("stale-hit", 7.0, hit("stale-hit"))
        assertEquals(0, f.estimate(nanos(20.0))!!.energy)
        assertTrue(f.estimate(nanos(20.0))!!.paused)
    }

    @Test fun `switch retains banked energy and never awards bench time`() {
        val f = engine(water)
        f.registerMoves("Vaporeon", listOf(water))
        f.add("switch", 2.0, ActivePokemonSpeciesWitnessed(opponent, "Vaporeon"))
        f.add("back", 10.0, ActivePokemonSpeciesWitnessed(opponent, "Sealeo"))
        assertEquals(18, f.estimate(nanos(11.0))!!.energy)
    }

    @Test fun `cap and witnessed spend reconcile without inventing missing energy`() {
        val f = engine(water)
        assertEquals(100, f.estimate(nanos(30.0))!!.energy)
        f.add("charge", 30.0, ChargedMoveEnergySpent(opponent, "Sealeo", "Body Slam", 35, "ANNOUNCEMENT"))
        assertEquals(65, f.estimate(nanos(40.0))!!.energy)
        val early = engine(water)
        early.add("early-charge", 1.0, ChargedMoveEnergySpent(opponent, "Sealeo", "Body Slam", 35, "ANNOUNCEMENT"))
        assertEquals(29, early.estimate(nanos(2.0))!!.unexplainedSpend)
        assertEquals(0, early.estimate(nanos(2.0))!!.energy)
    }

    @Test fun `late phase events reconcile by capture time and result freezes estimate`() {
        val f = engine(water)
        f.add("hit", 15.0, hit("use"))
        f.add("result", 16.0, MatchEnded(BattleResult.WIN))
        f.add("delayed-pause", 2.0, GetReadyWitnessed)
        assertEquals(21, f.estimate(nanos(40.0))!!.energy)
        assertTrue(f.estimate(nanos(40.0))!!.paused)
    }

    @Test fun `countdown does not accrue energy and missing GO starts a partial estimate`() {
        val f = OpponentEnergyForecast().apply { visible() }
        f.registerMoves("Sealeo", listOf(water))
        f.add("species", 0.0, ActivePokemonSpeciesWitnessed(opponent, "Sealeo"))
        assertEquals(0, f.estimate(nanos(120.0))!!.energy)
        f.add("first-hit", 120.0, hit("first"))
        assertEquals(9, f.estimate(nanos(121.0))!!.energy)
        assertTrue(f.estimate(nanos(121.0))!!.partialStart)
    }

    @Test fun `late first species can use known GO but a replacement cannot inherit lead time`() {
        val f = OpponentEnergyForecast().apply { visible() }
        f.registerMoves("Sealeo", listOf(water))
        f.add("go", 0.0, MatchStarted)
        f.add("species", 2.0, ActivePokemonSpeciesWitnessed(opponent, "Sealeo"))
        assertEquals(30, f.estimate(nanos(5.0))!!.energy)
        f.registerMoves("Vaporeon", listOf(water))
        f.add("faint", 5.0, ActivePokemonFaintedWitnessed(opponent, "Sealeo"))
        f.add("replacement", 8.0, ActivePokemonSpeciesWitnessed(opponent, "Vaporeon"))
        assertEquals(0, f.estimate(nanos(10.0))!!.energy)
    }

    @Test fun `candidate restrictions revise the assumed move and body motion does not count as a hit`() {
        val f = engine()
        f.restrictCandidates("Sealeo", listOf("Water Gun"))
        f.add("motion", 1.0, hit("motion").copy(evidenceKinds = listOf("HP_VERTICAL_MOTION")))
        assertEquals(30, f.estimate(nanos(5.0))!!.energy)
        assertEquals(0, f.estimate(nanos(5.0))!!.observedUses)
    }

    @Test fun `missed Get Ready cannot generate energy during an invisible battlefield`() {
        val f = OpponentEnergyForecast()
        f.registerMoves("Sealeo", listOf(water))
        f.add("species", 0.0, ActivePokemonSpeciesWitnessed(opponent, "Sealeo"))
        f.add("go", 0.0, MatchStarted)
        f.visible(0.0, 2.0)
        f.observeBattlefield(nanos(2.1), false)
        f.add("charge-artifact", 5.0, hit("not-a-fast-move"))
        val paused = f.estimate(nanos(10.0))!!
        assertEquals(12, paused.energy)
        assertEquals(0, paused.observedUses)
        assertTrue(paused.paused)
        f.visible(10.0, 11.0)
        assertEquals(18, f.estimate(nanos(11.0))!!.energy)
    }

    @Test fun `capture stopping does not let the last visible frame authorize unlimited energy`() {
        val f = OpponentEnergyForecast()
        f.registerMoves("Sealeo", listOf(water))
        f.add("species", 0.0, ActivePokemonSpeciesWitnessed(opponent, "Sealeo"))
        f.add("go", 0.0, MatchStarted)
        assertEquals(0, f.estimate(nanos(20.0))!!.energy)
        f.visible(0.0, 2.0)
        assertEquals(15, f.estimate(nanos(20.0))!!.energy)
        assertTrue(f.estimate(nanos(20.0))!!.paused)
    }

    @Test fun `a stray hit cannot unlock an ongoing QTE even with two apparent bars`() {
        val f = engine(water)
        f.add("ready", 2.0, GetReadyWitnessed)
        f.add("qte-artifact", 4.0, hit("stray"))
        assertEquals(12, f.estimate(nanos(6.0))!!.energy)
        assertTrue(f.estimate(nanos(6.0))!!.paused)
        f.add("landed", 8.0, ChargeMoveUsedAnnounced)
        f.add("resumed", 10.0, hit("actual"))
        assertEquals(21, f.estimate(nanos(11.0))!!.energy)
    }

    @Test fun `brief tracker dropout preserves a move but a whole missing phase discards incomplete turns`() {
        val f = OpponentEnergyForecast()
        f.registerMoves("Sealeo", listOf(snow))
        f.add("species", 0.0, ActivePokemonSpeciesWitnessed(opponent, "Sealeo"))
        f.add("go", 0.0, MatchStarted)
        f.visible(0.0, .5)
        f.observeBattlefield(nanos(.6), false)
        f.visible(.75, 1.5)
        assertEquals(8, f.estimate(nanos(1.5))!!.energy)
        f.observeBattlefield(nanos(1.6), false)
        f.visible(10.0, 10.5)
        assertEquals(8, f.estimate(nanos(10.5))!!.energy)
    }

    @Test fun `one isolated apparent pair in a charge effect cannot restart energy`() {
        val f = OpponentEnergyForecast()
        f.registerMoves("Sealeo", listOf(water))
        f.add("species", 0.0, ActivePokemonSpeciesWitnessed(opponent, "Sealeo"))
        f.add("go", 0.0, MatchStarted)
        f.visible(0.0, 2.0)
        f.observeBattlefield(nanos(2.1), false)
        f.observeBattlefield(nanos(6.0), true)
        f.add("artifact", 6.0, hit("artifact"))
        assertEquals(12, f.estimate(nanos(8.0))!!.energy)
        assertEquals(0, f.estimate(nanos(8.0))!!.observedUses)
        assertTrue(f.estimate(nanos(8.0))!!.paused)
    }

    @Test fun `distinct duplicate impacts cannot exceed completed move cycles`() {
        val f = engine(snow)
        repeat(8) { index -> f.add("burst-$index", .1 + index * .05, hit("burst-$index")) }
        val early = f.estimate(nanos(.5))!!
        assertEquals(8, early.observedUses)
        assertEquals(0, early.expectedUses)
        assertEquals(0, early.energy)
        assertEquals(8, f.estimate(nanos(1.0))!!.energy)
        assertEquals(16, f.estimate(nanos(2.0))!!.energy)
    }

    @Test fun `Charm burst cannot earn more than eight energy per three turns`() {
        val charm = Move("Charm", PokemonType.FAIRY, 12, 8, true, 3)
        val f = engine(charm)
        repeat(10) { index -> f.add("charm-$index", .1 + index * .14, hit("charm-$index")) }
        assertEquals(0, f.estimate(nanos(1.499))!!.energy)
        val firstCycle = f.estimate(nanos(1.5))!!
        assertEquals(8, firstCycle.energy)
        assertEquals(1, firstCycle.expectedUses)
        assertEquals(10, firstCycle.observedUses)
        val secondCycle = f.estimate(nanos(3.0))!!
        assertEquals(16, secondCycle.energy)
        assertEquals(2, secondCycle.expectedUses)
        assertEquals(10, secondCycle.observedUses)
    }

    @Test fun `a burst after elapsed cycles cannot spend unused time as extra energy`() {
        val f = engine(snow)
        repeat(12) { index -> f.add("burst-$index", 5.01 + index * .01, hit("burst-$index")) }
        val state = f.estimate(nanos(5.2))!!
        assertEquals(12, state.observedUses)
        assertEquals(5, state.expectedUses)
        assertEquals(40, state.energy)
    }

    @Test fun `late identity and reversed use publication still obey source time ceiling`() {
        val f = engine(water, snow)
        repeat(8) { index ->
            val at = .1 + (7 - index) * .05
            f.add("late-$index", at, hit("late-$index"))
        }
        f.add("actual", 2.0, FastMoveIdentified(opponent, "Sealeo", "Water Gun", nanos(.5),
            null, 0, "EFFECTIVENESS_TEXT"))
        val state = f.estimate(nanos(2.0))!!
        assertEquals(8, state.observedUses)
        assertEquals(4, state.expectedUses)
        assertEquals(12, state.energy)
    }

    @Test fun `partial capture gets one in progress completion but no burst credits`() {
        val f = OpponentEnergyForecast()
        f.registerMoves("Sealeo", listOf(water))
        f.add("species", 10.0, ActivePokemonSpeciesWitnessed(opponent, "Sealeo"))
        f.visible(10.0, 12.0)
        repeat(5) { index -> f.add("partial-$index", 10.125 + index * .025, hit("partial-$index")) }
        val first = f.estimate(nanos(10.3))!!
        assertTrue(first.partialStart)
        assertEquals(5, first.observedUses)
        assertEquals(1, first.expectedUses)
        assertEquals(3, first.energy)
        assertEquals(3, f.estimate(nanos(10.624))!!.energy)
        assertEquals(6, f.estimate(nanos(10.625))!!.energy)
    }

    @Test fun `post charge burst cannot borrow time from the QTE`() {
        val f = engine(water)
        f.add("ready", 2.0, GetReadyWitnessed)
        f.add("landed", 8.0, ChargeMoveUsedAnnounced)
        repeat(5) { index -> f.add("resume-$index", 8.8 + index * .02, hit("resume-$index")) }
        val resumed = f.estimate(nanos(9.0))!!
        assertEquals(5, resumed.observedUses)
        assertEquals(12, resumed.energy)
        assertEquals(15, f.estimate(nanos(9.25))!!.energy)
    }

    @Test fun `partial recording after known charge cannot claim unknown initial phase`() {
        val f = OpponentEnergyForecast()
        f.registerMoves("Sealeo", listOf(water))
        f.visible(0.0, 12.0)
        f.add("species", 0.0, ActivePokemonSpeciesWitnessed(opponent, "Sealeo"))
        f.add("ready", 2.0, GetReadyWitnessed)
        f.add("landed", 8.0, ChargeMoveUsedAnnounced)
        f.add("resume", 8.8, hit("resume"))
        assertEquals(0, f.estimate(nanos(9.0))!!.energy)
        assertEquals(3, f.estimate(nanos(9.25))!!.energy)
    }

    @Test fun `resume after replacement cannot include vacant field time`() {
        val f = engine(water)
        f.add("player", -1.0, ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Pikachu"))
        f.add("player-faint", 2.0, ActivePokemonFaintedWitnessed(ActivePokemonSide.PLAYER, "Pikachu"))
        f.add("replacement", 4.0, ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Vaporeon"))
        f.add("resume", 4.125, hit("resume"))
        assertEquals(12, f.estimate(nanos(4.125))!!.energy)
        assertEquals(15, f.estimate(nanos(4.5))!!.energy)
    }
}
