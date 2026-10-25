package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.Move
import com.example.overdex.model.Pokemon
import com.example.overdex.model.PokemonType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Incoming hit reactions must not identify an idle recipient's equipped move. */
class FastMoveMotionOwnershipTest {
    @Test fun `initial late species read recovers unnamed attacks before recognition`() = runBlocking {
        val engine = FastMoveCadenceInference(Knowledge)
        val hits = listOf(use("early1", 10.0), use("early2", 11.45), use("early3", 12.95))
        hits.forEach { assertTrue(engine.accept(it).isEmpty()) }
        val result = engine.accept(article("late-name", ActivePokemonSpeciesWitnessed(
            ActivePokemonSide.OPPONENT, "Sneasel"), 14.0)).single()
        assertEquals("Ice Shard", (result.payload as FastMoveIdentified).moveName)
        assertEquals(nanos(12.95), result.observedArticle.monotonicTimeNanos)
        assertTrue(result.predecessorIds.containsAll((hits.map { it.id } + ArticleId("late-name"))))
    }

    @Test fun `real species replacement does not recover unnamed departed attacks`() = runBlocking {
        val engine = engine()
        listOf(use("early1", 10.0), use("early2", 11.45), use("early3", 12.95)).forEach { engine.accept(it) }
        assertTrue(engine.accept(article("replacement", ActivePokemonSpeciesWitnessed(
            ActivePokemonSide.OPPONENT, "Sneasel"), 14.0)).isEmpty())
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)!!.identifiedMoveName)
    }

    @Test fun `faint boundary rejects old unnamed attacks published afterward`() = runBlocking {
        val engine = engine()
        engine.accept(article("faint", ActivePokemonFaintedWitnessed(ActivePokemonSide.OPPONENT, "Sealeo"), 13.0))
        listOf(use("late-old1", 10.0), use("late-old2", 11.45), use("late-old3", 12.95)).forEach { engine.accept(it) }
        assertTrue(engine.accept(article("replacement", ActivePokemonSpeciesWitnessed(
            ActivePokemonSide.OPPONENT, "Sneasel"), 14.0)).isEmpty())
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)!!.identifiedMoveName)
    }

    @Test fun `explicit first appearance boundary still rejects earlier unnamed attacks`() = runBlocking {
        val engine = FastMoveCadenceInference(Knowledge)
        listOf(use("early1", 10.0), use("early2", 11.45), use("early3", 12.95)).forEach { engine.accept(it) }
        assertTrue(engine.accept(article("known-entry", ActivePokemonSpeciesWitnessed(
            ActivePokemonSide.OPPONENT, "Sneasel", appearanceStartedAtNanos = nanos(13.0)), 14.0)).isEmpty())
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)!!.identifiedMoveName)
    }

    @Test fun `reaction only motion cannot identify a move or narrow the forecast pool`() = runBlocking {
        val engine = engine()
        val samples = listOf(motion("reaction1", 10.0), motion("reaction2", 10.5))
        val before = samples.map { it.copy() }
        samples.forEach { assertTrue(engine.accept(it).isEmpty()) }
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)!!.identifiedMoveName)
        assertEquals(setOf("Water Gun", "Powder Snow"), engine.candidateSnapshot(ActivePokemonSide.OPPONENT)!!.remainingMoveNames.toSet())
        assertEquals(listOf("CADENCE"), engine.candidateSnapshot(ActivePokemonSide.OPPONENT)!!.evidenceKinds)
        assertEquals(before, samples)
    }

    @Test fun `incoming impacts cannot license the victim motion`() = runBlocking {
        val engine = engine()
        for ((index, time) in listOf(9.5, 10.0, 10.5).withIndex()) {
            engine.accept(use("incoming$index", time, ActivePokemonSide.PLAYER))
            assertTrue(engine.accept(motion("reaction$index", time)).isEmpty())
        }
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)!!.identifiedMoveName)
    }

    @Test fun `one historical attack cannot license later idle reactions`() = runBlocking {
        val engine = engine()
        engine.accept(use("old-hit", 2.0))
        assertTrue(engine.accept(motion("reaction1", 10.0)).isEmpty())
        assertTrue(engine.accept(motion("reaction2", 10.5)).isEmpty())
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)!!.identifiedMoveName)
    }

    @Test fun `one hit cannot support both ends of a motion cycle`() = runBlocking {
        val engine = engine()
        engine.accept(use("only-hit", 10.25))
        assertTrue(engine.accept(motion("reaction1", 10.0)).isEmpty())
        assertTrue(engine.accept(motion("reaction2", 10.5)).isEmpty())
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)!!.identifiedMoveName)
        assertEquals(2, engine.candidateSnapshot(ActivePokemonSide.OPPONENT)!!.remainingMoveNames.size)
    }

    @Test fun `delayed impacts recover retained motion using source times and predecessor lineage`() = runBlocking {
        val engine = engine()
        engine.accept(motion("m1", 10.0))
        engine.accept(motion("m2", 10.5))
        assertTrue(engine.accept(use("hit1", 9.5).copy(recordedAt = 90_000)).isEmpty())
        val result = engine.accept(use("hit2", 10.0).copy(recordedAt = 91_000)).single()
        assertEquals("Water Gun", (result.payload as FastMoveIdentified).moveName)
        assertTrue(result.predecessorIds.containsAll(listOf("m1", "hit1", "hit2").map(::ArticleId)))
        assertEquals(nanos(10.0), result.observedArticle.monotonicTimeNanos)
        // A new conclusion uses retained testimony; it never manufactures an attack.
        assertTrue(result.payload is FastMoveIdentified)
    }

    @Test fun `impact arrival rechecks a first-use stream after motion arrived first`() = runBlocking {
        val engine = engine()
        engine.accept(motion("m1", 10.0))
        engine.accept(motion("m2", 10.5))
        // Motion endpoints were observed late but their times belong to this cycle.
        engine.accept(use("hit3", 10.5))
        assertTrue(engine.accept(use("hit2", 10.0)).isEmpty())
        val result = engine.accept(use("hit1", 9.5)).single()
        assertEquals("Water Gun", (result.payload as FastMoveIdentified).moveName)
        assertTrue(result.predecessorIds.containsAll(listOf("m2", "hit2", "hit3").map(::ArticleId)))
        assertEquals(nanos(10.5), result.observedArticle.monotonicTimeNanos)
    }

    @Test fun `half second motion cannot borrow one second impacts through endpoint tolerance`() = runBlocking {
        val engine = engine()
        for (index in 0..3) engine.accept(use("slow$index", 10.0 + index))
        for (index in 0..2) assertTrue(engine.accept(motion("reaction$index", 10.95 + index)).isEmpty())
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)!!.identifiedMoveName)
        assertEquals(setOf("Water Gun", "Powder Snow"), engine.candidateSnapshot(ActivePokemonSide.OPPONENT)!!.remainingMoveNames.toSet())
    }

    @Test fun `common phase lag still supports real motion and hits`() = runBlocking {
        val engine = engine()
        engine.accept(use("hit1", 10.0))
        engine.accept(use("hit2", 10.5))
        val result = engine.accept(motion("motion", 10.9)).single()
        assertEquals("Water Gun", (result.payload as FastMoveIdentified).moveName)
        assertTrue(result.predecessorIds.containsAll(listOf("hit1", "hit2", "motion").map(::ArticleId)))
    }

    @Test fun `late use naming a departed species cannot license replacement motion`() = runBlocking {
        val engine = engine()
        engine.accept(article("replacement", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Test Sealeo"), 9.0))
        for (index in 0..2) {
            val hit = use("wrong$index", 10.0 + index * 0.5)
            engine.accept(hit.copy(payload = (hit.payload as FastMoveUseObserved).copy(attackerSpeciesName = "Sealeo")))
        }
        assertTrue(engine.accept(motion("m1", 10.5)).isEmpty())
        assertTrue(engine.accept(motion("m2", 11.0)).isEmpty())
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)!!.identifiedMoveName)
    }

    @Test fun `already delivered late phase impacts keep their later source time in identity`() = runBlocking {
        val engine = engine()
        // Reverse publication order deliberately prevents a use-interval vote.
        for ((index, time) in listOf(10.4, 9.9, 9.4).withIndex()) engine.accept(use("hit$index", time))
        assertTrue(engine.accept(motion("m1", 9.85)).isEmpty())
        val result = engine.accept(motion("m2", 10.35)).single()
        assertEquals("Water Gun", (result.payload as FastMoveIdentified).moveName)
        assertEquals(nanos(10.4), result.observedArticle.monotonicTimeNanos)
        assertTrue(result.predecessorIds.containsAll(listOf("hit0", "hit1", "hit2").map(::ArticleId)))
    }

    @Test fun `two motion cycles cannot reuse a single observed impact pair`() = runBlocking {
        val engine = engine()
        engine.accept(use("last", 10.4))
        engine.accept(use("first", 9.9))
        assertTrue(engine.accept(motion("m1", 10.0)).isEmpty())
        assertTrue(engine.accept(motion("m2", 10.5)).isEmpty())
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)!!.identifiedMoveName)
    }

    @Test fun `a replacement cannot borrow the departed pokemon impacts`() = runBlocking {
        val engine = engine()
        engine.accept(use("old-hit1", 10.0))
        engine.accept(article("faint", ActivePokemonFaintedWitnessed(ActivePokemonSide.OPPONENT, "Sealeo"), 10.1))
        engine.accept(article("replacement", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Test Sealeo"), 10.2))
        engine.accept(use("new-hit", 10.5))
        assertTrue(engine.accept(motion("reaction1", 10.5)).isEmpty())
        assertTrue(engine.accept(motion("reaction2", 11.0)).isEmpty())
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)!!.identifiedMoveName)
    }

    @Test fun `actual impact stream identifies without any motion`() = runBlocking {
        val engine = engine()
        engine.accept(use("hit1", 10.0))
        engine.accept(use("hit2", 10.5))
        val result = engine.accept(use("hit3", 11.0)).single()
        assertEquals("Water Gun", (result.payload as FastMoveIdentified).moveName)
        assertTrue(result.payload.basis.contains("CONSISTENT_IMPACT_STREAM"))
    }

    @Test fun `configured idle move stays configured without motion promotion or energy`() = runBlocking {
        val engine = FastMoveCadenceInference(Knowledge)
        val energy = FastMoveEnergyInference(Knowledge)
        engine.accept(article("team", PlayerTeamSlotConfigured(1, "Sealeo", 364, "Powder Snow", listOf("Surf")), 0.0))
        val initial = engine.accept(article("species", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Sealeo"), 0.0)).single()
        assertEquals("CONFIGURED_PLAYER_TEAM_AND_ACTIVE_SPECIES", (initial.payload as FastMoveIdentified).basis)
        assertTrue(energy.accept(initial.observedArticle.copy(payload = initial.payload)).isEmpty())
        for (index in 0..3) {
            val raw = motion("idle$index", 10.0 + index, 1.0, ActivePokemonSide.PLAYER)
            assertTrue(engine.accept(raw).isEmpty())
            assertTrue(energy.accept(raw).isEmpty())
        }
        assertEquals("Powder Snow", engine.candidateSnapshot(ActivePokemonSide.PLAYER)!!.identifiedMoveName)
    }

    private suspend fun engine() = FastMoveCadenceInference(Knowledge).also {
        it.accept(article("species", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo"), 0.0))
    }
    private fun nanos(seconds: Double) = (seconds * 1_000_000_000).toLong()
    private fun motion(id: String, at: Double, interval: Double = 0.5, side: ActivePokemonSide = ActivePokemonSide.OPPONENT) =
        article(id, ActiveHpBarMotionCadenceMeasured(side, nanos(interval), 25f, 6), at)
    private fun use(id: String, at: Double, side: ActivePokemonSide = ActivePokemonSide.OPPONENT) = article(id,
        FastMoveUseObserved(id, side, if (side == ActivePokemonSide.PLAYER) ActivePokemonSide.OPPONENT else ActivePokemonSide.PLAYER,
            "appearance", null, listOf("HP_DAMAGE_TICK")), at)
    private fun article(id: String, payload: TestimonyPayload, at: Double) = RealityArticle(ArticleId(id),
        perceivedAt = (at * 1000).toLong(), recordedAt = (at * 1000).toLong(), sourceId = SourceId("motion-regression"),
        payload = payload, monotonicTimeNanos = nanos(at))
    private object Knowledge : PokemonKnowledge {
        private val pokemon = Pokemon(364, "Sealeo", listOf(PokemonType.WATER, PokemonType.ICE), "Hoenn", fastMoves = listOf(
            Move("Water Gun", PokemonType.WATER, 3, 3, isFast = true, turns = 1),
            Move("Powder Snow", PokemonType.ICE, 5, 8, isFast = true, turns = 2)), chargedMoves = emptyList())
        private val sneasel = Pokemon(215, "Sneasel", listOf(PokemonType.DARK, PokemonType.ICE), "Johto", fastMoves = listOf(
            Move("Feint Attack", PokemonType.DARK, 6, 6, isFast = true, turns = 2),
            Move("Ice Shard", PokemonType.ICE, 9, 10, isFast = true, turns = 3)), chargedMoves = emptyList())
        override suspend fun getPokemonByName(name: String) = when (name) {
            "Sneasel" -> sneasel
            "Sealeo" -> pokemon
            "Test Sealeo" -> pokemon.copy(name = name)
            else -> null
        }
        override suspend fun getPokemonById(id: Int) = pokemon.takeIf { it.id == id }
        override suspend fun getAllSpeciesNames() = setOf("Sealeo", "Test Sealeo", "Sneasel")
    }
}
