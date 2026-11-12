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

class RevisedLiveFaintExclusionTest {
    @Test fun `player count exclusion and recovery join a resolved form to its bare badge`() {
        val fainted = FaintedCombatants()
        val side = ActivePokemonSide.PLAYER
        fun identity(id: String, name: String, dex: Int, at: Long) =
            article(id, at, ActivePokemonSpeciesWitnessed(side, name, dex))
        fun positive(id: String, at: Long) =
            article(id, at, ActiveHpBarMeasured(side, 20, 30, 120, 40, .5f))
        listOf(identity("a-form", "Gourgeist (Average)", 711, 2),
            article("three", 3, PlayerPokeBallCountMeasured(3)),
            identity("b", "Camerupt", 323, 5), article("two", 6, PlayerPokeBallCountMeasured(2)),
            positive("b-positive", 7)).forEach { fainted.observe(it) }
        assertTrue(fainted.contains(side, "Gourgeist"))
        assertTrue(fainted.contains(side, "Gourgeist (Average)"))
        val returning = identity("bare-return", "Gourgeist", 711, 9)
        assertTrue(fainted.observe(returning).isEmpty())
        assertTrue(fainted.observe(identity("delayed-c", "Turtonator", 776, 4)).isEmpty())
        assertEquals(listOf(returning), fainted.observe(positive("return-positive", 10)))
        assertFalse(fainted.contains(side, "Gourgeist"))
        assertFalse(fainted.contains(side, "Gourgeist (Average)"))
        assertTrue(fainted.contains(side, "Turtonator"))
    }

    @Test fun `delayed identity revises exclusion and positive HP retries the original blocked return`() {
        val fainted = FaintedCombatants()
        prefix().forEach { fainted.observe(it) }
        assertTrue(fainted.contains(SIDE, "Carbink"))
        val returning = species("a-return", "Carbink", 703, 9)
        assertTrue(fainted.observe(returning).isEmpty())
        assertTrue("A lingering name alone does not revive", fainted.contains(SIDE, "Carbink"))
        assertTrue(fainted.observe(species("delayed-c", "Quagsire", 195, 4)).isEmpty())
        assertFalse(fainted.contains(SIDE, "Carbink"))
        val recovery = fainted.observe(hp("a-positive-return", 10)).single()
        assertEquals(returning, recovery)
        assertEquals(9 * SECOND, recovery.monotonicTimeNanos)
        assertTrue(fainted.contains(SIDE, "Quagsire"))
        assertFalse(fainted.contains(SIDE, "Carbink"))
        assertTrue(fainted.observe(hp("a-later-positive", 11)).isEmpty())
    }

    @Test fun `count conclusions cannot independently exclude but legacy non-count faints remain authoritative`() {
        val fainted = FaintedCombatants()
        val identity = species("a", "Carbink", 703, 2)
        fainted.observe(identity)
        val countConclusion = faint("unsupported-count", "Carbink", 3)
        fainted.observe(countConclusion)
        assertFalse(fainted.contains(SIDE, "Carbink"))
        assertFalse(fainted.supports(countConclusion.payload as ActivePokemonFaintedWitnessed, 3 * SECOND))
        fainted.observe(article("legacy", 4, ActivePokemonFaintedWitnessed(
            SIDE, "Carbink", 703, "LEGACY_EXPLICIT_FAINT")))
        fainted.observe(species("lingering", "Carbink", 703, 5))
        assertTrue(fainted.observe(hp("positive", 6)).isEmpty())
        assertTrue(fainted.contains(SIDE, "Carbink"))
    }

    @Test fun `positive HP in a conflicting return interval cannot recover an arbitrary identity`() {
        val fainted = FaintedCombatants()
        prefix().forEach { fainted.observe(it) }
        fainted.observe(species("conflicting-return", "Gourgeist", 711, 9))
        fainted.observe(species("a-return", "Carbink", 703, 9))
        fainted.observe(species("delayed-c", "Quagsire", 195, 4))
        assertTrue("The shared resolver cannot attribute this HP to either same-time identity",
            fainted.observe(hp("unknown-positive", 10)).isEmpty())
    }

    @Test fun `fast move use recovers original return identity without another badge reading`() {
        val engine = FastMoveUseInference()
        engine.accept(article("go", 1, MatchStarted))
        prefix().forEach { engine.accept(it) }
        engine.accept(species("a-return", "Carbink", 703, 9))
        engine.accept(species("delayed-c", "Quagsire", 195, 4))
        engine.accept(hp("a-positive-return", 10))
        val outputs = engine.accept(article("actual-hit", 11,
            HpBarBorderPulse(ActivePokemonSide.PLAYER, ApertureStatus.PRESENT, 100f, 10))) + engine.flush()
        val hit = outputs.single()
        assertEquals("Carbink", hit.payload.attackerSpeciesName)
        assertEquals("a-return", hit.payload.appearanceId)
        assertEquals(11 * SECOND, hit.observedArticle.monotonicTimeNanos)
        assertEquals("actual-hit", hit.observedArticle.id.value)
    }

    @Test fun `cadence and energy resume after supported return without clearing the current actor for late faint`() = runBlocking {
        val knowledge = Knowledge()
        val cadence = FastMoveCadenceInference(knowledge)
        val energy = FastMoveEnergyInference(knowledge)
        suspend fun deliver(input: RealityArticle): List<FastMoveCadenceDerivation> {
            energy.accept(input)
            val derivations = cadence.accept(input)
            derivations.filter { it.payload is FastMoveIdentified }.forEach { derived ->
                energy.accept(derived.observedArticle.copy(
                    id = ArticleId("move-${derived.observedArticle.id.value}"), payload = derived.payload,
                    predecessorIds = derived.predecessorIds,
                ))
            }
            return derivations
        }
        deliver(article("go", 1, MatchStarted))
        prefix().forEach { deliver(it) }
        assertEquals("Gourgeist", cadence.candidateSnapshot(SIDE)?.speciesName)
        deliver(species("a-return", "Carbink", 703, 9))
        assertEquals("Gourgeist", cadence.candidateSnapshot(SIDE)?.speciesName)
        deliver(species("delayed-c", "Quagsire", 195, 4))
        val recovered = deliver(hp("a-positive-return", 10))
        assertEquals("Carbink", cadence.candidateSnapshot(SIDE)?.speciesName)
        assertEquals("Rock Throw", cadence.candidateSnapshot(SIDE)?.identifiedMoveName)
        assertEquals("a-return", recovered.single { it.payload is FastMoveIdentified }.observedArticle.id.value)
        assertEquals(9 * SECOND, recovered.single { it.payload is FastMoveIdentified }.observedArticle.monotonicTimeNanos)

        // The original wrong count conclusion can arrive again after the correction.
        deliver(faint("late-old-a-faint", "Carbink", 6))
        assertEquals("Carbink", cadence.candidateSnapshot(SIDE)?.speciesName)
        val use = article("real-use", 12, FastMoveUseObserved("real-use", SIDE,
            ActivePokemonSide.PLAYER, "a-return", "Carbink", listOf("HP_BORDER_PULSE")))
        val credited = energy.accept(use).single()
        assertEquals(SIDE, credited.payload.side)
        assertEquals("Rock Throw", credited.payload.moveName)
        assertEquals(5, credited.payload.totalEnergyGenerated)
        assertEquals(use, credited.observedArticle)
    }

    private fun prefix() = listOf(
        species("a", "Carbink", 703, 2),
        article("three", 3, OpponentBattleResourceCountMeasured(OpponentBattleResource.POKE_BALLS, 3, 3)),
        species("b", "Gourgeist", 711, 5),
        article("two", 6, OpponentBattleResourceCountMeasured(OpponentBattleResource.POKE_BALLS, 2, 3)),
        hp("b-positive", 7), faint("initial-a-faint", "Carbink", 6),
    )

    private fun faint(id: String, name: String, at: Long) = article(id, at,
        ActivePokemonFaintedWitnessed(SIDE, name, basis = "OPPONENT_POKE_BALL_COUNT_DECREASE"))
    private fun species(id: String, name: String, dex: Int, at: Long) = article(id, at,
        ActivePokemonSpeciesWitnessed(SIDE, name, dex))
    private fun hp(id: String, at: Long) = article(id, at,
        ActiveHpBarMeasured(SIDE, 20, 30, 120, 40, .5f))
    private fun article(id: String, at: Long, payload: TestimonyPayload) = RealityArticle(
        ArticleId(id), at * 1_000, at * 1_000, SourceId("test"), payload,
        monotonicTimeNanos = at * SECOND,
    )

    private class Knowledge : PokemonKnowledge {
        private val species = listOf(
            Pokemon(703, "Carbink", listOf(PokemonType.ROCK), "", fastMoves = listOf(
                Move("Rock Throw", PokemonType.ROCK, 8, 5, true, 2)), chargedMoves = emptyList()),
            Pokemon(711, "Gourgeist", listOf(PokemonType.GHOST), "", fastMoves = listOf(
                Move("Hex", PokemonType.GHOST, 6, 12, true, 3)), chargedMoves = emptyList()),
            Pokemon(195, "Quagsire", listOf(PokemonType.GROUND), "", fastMoves = listOf(
                Move("Mud Shot", PokemonType.GROUND, 3, 9, true, 2)), chargedMoves = emptyList()),
        )
        override suspend fun getPokemonByName(name: String) = species.find { it.name == name }
        override suspend fun getPokemonById(id: Int) = species.find { it.id == id }
        override suspend fun getAllSpeciesNames() = species.map { it.name }.toSet()
    }

    companion object {
        private val SIDE = ActivePokemonSide.OPPONENT
        private const val SECOND = 1_000_000_000L
    }
}
