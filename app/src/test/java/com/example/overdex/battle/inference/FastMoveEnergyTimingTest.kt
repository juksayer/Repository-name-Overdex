package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.BattleResult
import com.example.overdex.model.Move
import com.example.overdex.model.Pokemon
import com.example.overdex.model.PokemonType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FastMoveEnergyTimingTest {
    private val side = ActivePokemonSide.PLAYER
    private val incinerate = Move("Incinerate", PokemonType.FIRE, 20, 20, true, 5)
    private val charm = Move("Charm", PokemonType.FAIRY, 12, 8, true, 3)
    private val waterGun = Move("Water Gun", PokemonType.WATER, 3, 3, true, 1)

    @Test fun `known GO requires a full reference cycle despite an incorrect measured duration`() = runBlocking {
        val engine = engine(incinerate)
        engine.accept(article("go", MatchStarted, 0.0))
        engine.accept(identity(incinerate)) // Deliberately claims a 1 ns duration.
        assertTrue(engine.accept(use("too-early", .1)).isEmpty())
        assertTrue(engine.accept(use("still-early", 2.499)).isEmpty())
        val observed = use("complete", 2.5)
        val award = engine.accept(observed).single()
        assertEquals(20, award.payload.totalEnergyGenerated)
        assertTrue(award.payload.basis.endsWith("DURATION_BOUNDED"))
        assertSame(observed, award.observedArticle)
    }

    @Test fun `distinct burst IDs cannot spend banked idle time`() = runBlocking {
        val engine = engine(incinerate)
        engine.accept(article("go", MatchStarted, 0.0))
        engine.accept(identity(incinerate))
        val source = listOf(100.0, 100.1, 100.2, 100.3, 102.5).mapIndexed { index, at -> use("burst-$index", at) }
        val awards = source.flatMap { engine.accept(it) }
        assertEquals(listOf(source.first(), source.last()), awards.map { it.observedArticle })
        assertEquals(40, awards.sumOf { it.payload.totalEnergyGenerated })
        assertEquals(5, source.size) // All raw observations are still available.
    }

    @Test fun `past only capture lag can reconcile jitter but cannot accumulate shorter cycles`() = runBlocking {
        val engine = engine(charm)
        engine.accept(article("go", MatchStarted, 0.0))
        engine.accept(identity(charm))
        assertEquals(1, engine.accept(use("delayed-first", 1.7, "Gardevoir")).size)
        assertEquals(1, engine.accept(use("on-time-second", 3.0, "Gardevoir")).size)
        assertTrue(engine.accept(use("physically-early-third", 4.3, "Gardevoir")).isEmpty())
        assertEquals(1, engine.accept(use("complete-third", 4.5, "Gardevoir")).size)
    }

    @Test fun `three turn Charm gives eight energy per physically possible completed cycle`() = runBlocking {
        val engine = engine(charm)
        engine.accept(article("go", MatchStarted, 0.0))
        engine.accept(identity(charm))
        val awards = mutableListOf<FastMoveEnergyDerivation>()
        listOf(.1, .5, 1.0, 1.5, 1.6, 1.7, 2.0, 2.5, 2.9, 3.0).forEachIndexed { index, at ->
            awards += engine.accept(use("charm-$index", at, "Gardevoir"))
            assertTrue(awards.sumOf { it.payload.totalEnergyGenerated } <= (at / 1.5).toInt() * 8)
        }
        assertEquals(listOf(8, 8), awards.map { it.payload.totalEnergyGenerated })
        assertEquals(listOf(nanos(1.5), nanos(3.0)), awards.map { it.observedArticle.monotonicTimeNanos })
    }

    @Test fun `a sustained early cadence cannot spend capture lag again every cycle`() = runBlocking {
        val engine = engine(charm)
        engine.accept(article("go", MatchStarted, 0.0))
        engine.accept(identity(charm))
        val awards = mutableListOf<FastMoveEnergyDerivation>()
        val source = (0 until 32).map { use("early-$it", 1.7 + it * 1.3, "Gardevoir") }
        source.forEach { observed ->
            awards += engine.accept(observed)
            val at = requireNotNull(observed.monotonicTimeNanos)
            assertTrue(awards.size <= at / nanos(1.5))
        }
        assertTrue(awards.size < source.size)
        assertTrue(awards.all { it.payload.energyPerUse == 8 })
    }

    @Test fun `late identity sorts original source times before reserving credits`() = runBlocking {
        val engine = engine(incinerate)
        engine.accept(article("go", MatchStarted, 0.0))
        val first = use("first", 2.5)
        val second = use("second", 5.0)
        val burst = use("burst", 5.1)
        listOf(burst, second, first).forEach { assertTrue(engine.accept(it).isEmpty()) }
        val awards = engine.accept(identity(incinerate, at = 20.0))
        assertEquals(listOf(first, second), awards.map { it.observedArticle })
        assertEquals(listOf(nanos(2.5), nanos(5.0)), awards.map { it.observedArticle.monotonicTimeNanos })
        assertTrue(engine.accept(identity(incinerate, at = 40.0)).isEmpty())
        assertTrue(engine.accept(burst.copy(id = ArticleId("republished-burst"), recordedAt = 50L)).isEmpty())
    }

    @Test fun `late valid uses fit between existing reservations without resetting the ceiling`() = runBlocking {
        val engine = engine(incinerate)
        engine.accept(article("go", MatchStarted, 0.0))
        engine.accept(identity(incinerate))
        assertEquals(1, engine.accept(use("first", 2.5)).size)
        assertEquals(1, engine.accept(use("third", 7.5)).size)
        assertEquals(1, engine.accept(use("late-second", 5.0)).size)
        assertTrue(engine.accept(use("extra-late", 5.2)).isEmpty())
    }

    @Test fun `unknown match start permits an in flight use but no rapid second credit`() = runBlocking {
        val engine = engine(incinerate)
        engine.accept(identity(incinerate))
        assertEquals(1, engine.accept(use("partial-start", .1)).size)
        assertTrue(engine.accept(use("burst", .2)).isEmpty())
        assertEquals(1, engine.accept(use("next-cycle", 2.6)).size)
    }

    @Test fun `charge pause and impact tail exclude entire overlapping cycles`() = runBlocking {
        val engine = engine(incinerate)
        engine.accept(article("go", MatchStarted, 0.0))
        engine.accept(identity(incinerate))
        assertEquals(1, engine.accept(use("before-charge", 2.5)).size)
        engine.accept(article("qte", GetReadyWitnessed, 3.0))
        assertTrue(engine.accept(use("just-inside-qte", 3.05)).isEmpty())
        assertTrue(engine.accept(use("during-qte", 5.0)).isEmpty())
        engine.accept(article("announced", ChargeMoveUsedAnnounced, 7.0))
        assertTrue(engine.accept(use("cycle-crosses-tail", 10.0)).isEmpty())
        assertEquals(1, engine.accept(use("complete-after-tail", 10.25)).size)
        assertTrue(engine.accept(identity(incinerate, at = 20.0)).isEmpty())
    }

    @Test fun `decision timer bounds cycles for both sides without using the clock to create uses`() = runBlocking {
        val engine = engine(waterGun)
        engine.accept(article("go", MatchStarted, 0.0))
        engine.accept(identity(waterGun))
        engine.accept(article("choose", BattleDecisionTimerObserved(5, "5", "OCR"), 2.0))
        assertTrue(engine.accept(use("during-decision", 3.0)).isEmpty())
        engine.accept(article("clear", BattleDecisionTimerObserved(null, "", "CLEAR"), 4.0))
        assertTrue(engine.accept(use("crosses-clear", 4.1)).isEmpty())
        assertEquals(1, engine.accept(use("complete-after-clear", 4.5)).size)
        assertTrue(engine.accept(article("clock-only", RawTestimony("clock"), 100.0)).isEmpty())
    }

    @Test fun `charge and decision cues reconcile by source time when delivery is reversed`() = runBlocking {
        for (reverse in listOf(false, true)) {
            val engine = engine(waterGun)
            engine.accept(article("go", MatchStarted, 0.0))
            engine.accept(identity(waterGun))
            val cues = listOf(article("start", GetReadyWitnessed, 3.0),
                article("end", ChargeMoveUsedAnnounced, 10.0),
                article("timer", BattleDecisionTimerObserved(5, "5", "OCR"), 20.0),
                article("clear", BattleDecisionTimerObserved(null, "", "CLEAR"), 22.0))
            (if (reverse) cues.reversed() else cues).forEach { engine.accept(it) }
            assertTrue(engine.accept(use("qte-hit", 5.0)).isEmpty())
            assertEquals(1, engine.accept(use("after-charge", 11.25)).size)
            assertTrue(engine.accept(use("timer-hit", 21.0)).isEmpty())
            assertEquals(1, engine.accept(use("after-decision", 22.5)).size)
        }
    }

    @Test fun `switch does not carry old cycle time into a different pokemon`() = runBlocking {
        val engine = engine(incinerate, charm)
        engine.accept(article("go", MatchStarted, 0.0))
        engine.accept(article("first-species", ActivePokemonSpeciesWitnessed(side, "Turtonator"), 0.0))
        engine.accept(identity(incinerate))
        assertEquals(1, engine.accept(use("old", 2.5)).size)
        engine.accept(article("replacement", ActivePokemonSpeciesWitnessed(side, "Gardevoir",
            appearanceStartedAtNanos = nanos(3.0)), 3.2))
        engine.accept(identity(charm, at = 3.3))
        assertTrue(engine.accept(use("too-soon", 3.5, "Gardevoir")).isEmpty())
        assertEquals(1, engine.accept(use("new-complete", 4.5, "Gardevoir")).size)
    }

    @Test fun `a faint blocks both sides until a genuine replacement and full cycle`() = runBlocking {
        val engine = engine(waterGun, charm)
        engine.accept(article("go", MatchStarted, 0.0))
        val opponentIdentity = identity(waterGun).let {
            it.copy(payload = (it.payload as FastMoveIdentified).copy(side = ActivePokemonSide.OPPONENT))
        }
        fun opponentUse(id: String, at: Double) = use(id, at).let {
            it.copy(payload = (it.payload as FastMoveUseObserved).copy(
                attackingSide = ActivePokemonSide.OPPONENT, damagedSide = ActivePokemonSide.PLAYER))
        }
        engine.accept(opponentIdentity)
        engine.accept(article("former", ActivePokemonSpeciesWitnessed(side, "Former"), 0.0))
        // Deliver replacement before the old faint to exercise independent
        // historical vacancy without replacing the active combatant again.
        engine.accept(article("replacement", ActivePokemonSpeciesWitnessed(side, "Gardevoir"), 5.0))
        engine.accept(article("late-faint", ActivePokemonFaintedWitnessed(side, "Former", null, "BALL_COUNT"), 2.0))
        assertEquals(1, engine.accept(opponentUse("killing-hit", 2.0)).size)
        assertTrue(engine.accept(opponentUse("vacant-field", 3.0)).isEmpty())
        assertTrue(engine.accept(opponentUse("crosses-entry", 5.1)).isEmpty())
        assertEquals(1, engine.accept(opponentUse("full-cycle", 5.5)).size)
    }

    @Test fun `late historical appearance still bounds that pokemon's earliest completed move`() = runBlocking {
        val engine = engine(incinerate, charm)
        fun appearance(id: String, species: String, slot: Int, at: Double) = article(id,
            ActivePokemonSpeciesWitnessed(side, species, playerTeamSlot = slot,
                appearanceStartedAtNanos = nanos(at)), at)
        engine.accept(article("go", MatchStarted, 0.0))
        engine.accept(appearance("first", "Gardevoir", 1, 0.0))
        engine.accept(appearance("current", "Gardevoir", 3, 30.0))
        engine.accept(appearance("late-middle", "Turtonator", 2, 8.0))
        engine.accept(identity(incinerate, at = 9.0))
        assertTrue(engine.accept(use("crosses-historical-entry", 9.0)).isEmpty())
        assertEquals(1, engine.accept(use("historical-complete", 10.5)).size)
    }

    @Test fun `a result rejects later uses while permitting valid delayed pre result evidence`() = runBlocking {
        val engine = engine(waterGun)
        engine.accept(article("go", MatchStarted, 0.0))
        engine.accept(identity(waterGun))
        engine.accept(article("result", MatchEnded(BattleResult.WIN), 10.0))
        assertTrue(engine.accept(use("after-result", 11.0)).isEmpty())
        assertEquals(1, engine.accept(use("late-before-result", 9.0)).size)
    }

    @Test fun `late phase evidence cannot rewrite accepted energy or jam future credits`() = runBlocking {
        val engine = engine(waterGun)
        engine.accept(article("go", MatchStarted, 0.0))
        engine.accept(identity(waterGun))
        val award = engine.accept(use("already-published", 2.0)).single()
        engine.accept(article("late-qte", AttackIncoming, 1.0))
        engine.accept(article("late-end", ChargeMoveUsedAnnounced, 3.0))
        assertTrue(engine.accept(use("new-inside-known-pause", 2.5)).isEmpty())
        assertEquals(1, engine.accept(use("after-known-pause", 4.25)).size)
        assertEquals(3, award.payload.totalEnergyGenerated)
        assertEquals(nanos(2.0), award.observedArticle.monotonicTimeNanos)
    }

    @Test fun `nonpositive or missing reference timing and energy never produce exact credits`() = runBlocking {
        for (invalid in listOf(incinerate.copy(turns = null), incinerate.copy(turns = 0),
            incinerate.copy(turns = -1), incinerate.copy(energy = 0), incinerate.copy(energy = -20), incinerate.copy(isFast = false))) {
            val engine = engine(invalid)
            engine.accept(identity(invalid))
            assertTrue(engine.accept(use("unqualified", 10.0)).isEmpty())
        }
    }

    @Test fun `separate sides have independent full duration reservations`() {
        val timing = FastMoveEnergyTiming()
        timing.observe(article("go", MatchStarted, 0.0))
        assertTrue(timing.reserve(ActivePokemonSide.PLAYER, nanos(2.5), nanos(2.5)))
        assertTrue(timing.reserve(ActivePokemonSide.OPPONENT, nanos(2.5), nanos(2.5)))
        assertTrue(!timing.reserve(ActivePokemonSide.PLAYER, nanos(2.6), nanos(2.5)))
    }

    private fun engine(vararg moves: Move) = FastMoveEnergyInference(object : PokemonKnowledge {
        private val pokemon = moves.groupBy { if (it.name == "Charm") "Gardevoir" else "Turtonator" }.mapValues { (name, pool) ->
            Pokemon(if (name == "Gardevoir") 282 else 776, name, listOf(pool.first().type), "", fastMoves = pool, chargedMoves = emptyList())
        }
        override suspend fun getPokemonByName(name: String) = pokemon[name]
        override suspend fun getPokemonById(id: Int) = pokemon.values.firstOrNull { it.id == id }
        override suspend fun getAllSpeciesNames() = pokemon.keys
    })

    private fun identity(move: Move, at: Double = 0.0) = article("identity-${move.name}-$at",
        FastMoveIdentified(side, if (move.name == "Charm") "Gardevoir" else "Turtonator", move.name,
            moveDurationNanos = 1L, observedMedianIntervalNanos = 1L, cadenceSampleCount = 10,
            basis = "CONFIGURED_PLAYER_TEAM_AND_ACTIVE_SPECIES"), at)

    private fun use(id: String, at: Double, species: String = "Turtonator") = article(id,
        FastMoveUseObserved(id, side, ActivePokemonSide.OPPONENT, "appearance", species, listOf("HP_DAMAGE_TICK")), at)

    private fun article(id: String, payload: TestimonyPayload, at: Double) = RealityArticle(
        ArticleId(id), perceivedAt = nanos(at) / 1_000_000L, recordedAt = 1L,
        sourceId = SourceId("timing-cap-test"), payload = payload, monotonicTimeNanos = nanos(at))

    private fun nanos(seconds: Double) = (seconds * 1_000_000_000L).toLong()
}
