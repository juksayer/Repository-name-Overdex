package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.FastMoveEnergyDerived
import com.example.overdex.battle.custody.FastMoveIdentified
import com.example.overdex.battle.custody.FastMoveUseObserved
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.custody.SpeciesCheckMeasured
import com.example.overdex.battle.custody.TestimonyPayload
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.Move
import com.example.overdex.model.Pokemon
import com.example.overdex.model.PokemonType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FastMoveEnergyInferenceTest {
    private val waterGun = Move("Water Gun", PokemonType.WATER, 3, 3, true, 1)
    private val powderSnow = Move("Powder Snow", PokemonType.ICE, 5, 8, true, 2)
    private val sealeo = Pokemon(
        364, "Sealeo", listOf(PokemonType.ICE, PokemonType.WATER), "Hoenn",
        fastMoves = listOf(waterGun, powderSnow), chargedMoves = emptyList()
    )

    @Test
    fun `one fused use generates energy once even when it cites several witnesses`() = runBlocking {
        val engine = FastMoveEnergyInference(Knowledge(sealeo))
        engine.accept(species("species", "Sealeo"))
        engine.accept(identity("identity", "Sealeo", "Water Gun"))

        val use = use("use-1", listOf("HP_BORDER_PULSE", "HP_BORDER_CADENCE", "RECIPIENT_VISUAL_ARTIFACT"))
        val result = engine.accept(use)

        val energy = result.single().payload
        assertEquals(1, energy.observedCompletedUses)
        assertEquals(3, energy.energyPerUse)
        assertEquals(3, energy.totalEnergyGenerated)
        assertEquals(setOf("use-1", "identity"), result.single().predecessorIds.map { it.value }.toSet())
        assertTrue(engine.accept(use).isEmpty())
    }

    @Test
    fun `uses observed before identity receive energy retroactively at their original times`() = runBlocking {
        val engine = FastMoveEnergyInference(Knowledge(sealeo))
        engine.accept(species("species", "Sealeo"))
        val first = use("use-1", monotonicNanos = 1_000_000_000L)
        val second = use("use-2", monotonicNanos = 1_500_000_000L)
        assertTrue(engine.accept(first).isEmpty())
        assertTrue(engine.accept(second).isEmpty())

        val resolved = engine.accept(identity("identity", "Sealeo", "Water Gun", 2_000_000_000L))

        assertEquals(listOf("use-1", "use-2"), resolved.map { it.observedArticle.id.value })
        assertEquals(listOf(1_000_000_000L, 1_500_000_000L), resolved.map { it.observedArticle.monotonicTimeNanos })
        assertEquals(listOf(3, 3), resolved.map { it.payload.totalEnergyGenerated })
    }

    @Test
    fun `entry clears the former combatant identity`() = runBlocking {
        val engine = FastMoveEnergyInference(Knowledge(sealeo))
        engine.accept(species("species", "Sealeo"))
        engine.accept(identity("identity", "Sealeo", "Water Gun"))
        engine.accept(article("entry", SpeciesCheckMeasured(
            ActivePokemonSide.OPPONENT, 2, "OPENED", "ENTRY",
            2_000_000_000L, 0, 1_500_000_000L
        ), 2_000_000_000L))

        assertTrue(engine.accept(use("new-use", monotonicNanos = 2_500_000_000L)).isEmpty())
        engine.accept(species("species-2", "Sealeo", 2_600_000_000L))
        val resolved = engine.accept(identity("identity-2", "Sealeo", "Powder Snow", 3_000_000_000L))

        assertEquals(8, resolved.single().payload.totalEnergyGenerated)
        assertEquals("new-use", resolved.single().observedArticle.id.value)
    }

    @Test
    fun `charge fill alone cannot count exact fast move energy`() = runBlocking {
        val engine = FastMoveEnergyInference(Knowledge(sealeo))
        engine.accept(species("species", "Sealeo"))
        engine.accept(identity("identity", "Sealeo", "Water Gun"))

        val result = engine.accept(use("shade", listOf("PLAYER_CHARGE_FILL_INCREASE")))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `higher energy tie break remains a hypothesis until corroborated`() = runBlocking {
        val engine = FastMoveEnergyInference(Knowledge(sealeo))
        engine.accept(species("species", "Sealeo"))
        engine.accept(identity(
            "assumption", "Sealeo", "Powder Snow", basis =
                "BORDER_PULSE_CADENCE_AND_REFERENCE_KNOWLEDGE_HIGHER_ENERGY_TIE_BREAK_ASSUMPTION"
        ))

        assertTrue(engine.accept(use("use-before-proof")).isEmpty())
        val confirmed = engine.accept(identity(
            "confirmed", "Sealeo", "Powder Snow",
            basis = "SIDE_LOCATED_EFFECTIVENESS_TEXT_AND_ACTIVE_SPECIES_REFERENCE_KNOWLEDGE"
        ))

        assertEquals(8, confirmed.single().payload.totalEnergyGenerated)
    }

    @Test
    fun `single uncorroborated cadence narrows move without starting exact energy`() = runBlocking {
        val engine = FastMoveEnergyInference(Knowledge(sealeo))
        engine.accept(species("species", "Sealeo"))
        engine.accept(identity(
            "cadence-only", "Sealeo", "Water Gun",
            basis = "BORDER_PULSE_CADENCE_AND_REFERENCE_KNOWLEDGE"
        ))

        assertTrue(engine.accept(use("use-before-agreement")).isEmpty())
        val confirmed = engine.accept(identity(
            "cadence-agreed", "Sealeo", "Water Gun",
            basis = "BORDER_PULSE_CADENCE_AND_REFERENCE_KNOWLEDGE_CORROBORATED_BY_AUDIO_PROFILE"
        ))

        assertEquals(3, confirmed.single().payload.totalEnergyGenerated)
    }

    private fun species(id: String, name: String, at: Long = 1L) = article(
        id, ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, name, 364), at
    )

    private fun identity(
        id: String,
        species: String,
        move: String,
        at: Long = 1L,
        basis: String = "SIDE_LOCATED_EFFECTIVENESS_TEXT_AND_ACTIVE_SPECIES_REFERENCE_KNOWLEDGE",
    ) = article(
        id,
        FastMoveIdentified(
            ActivePokemonSide.OPPONENT, species, move,
            moveDurationNanos = if (move == "Water Gun") 500_000_000L else 1_000_000_000L,
            observedMedianIntervalNanos = null,
            cadenceSampleCount = 0,
            basis = basis
        ),
        at,
        confidence = 0.8f
    )

    private fun use(
        id: String,
        evidence: List<String> = listOf("HP_BORDER_PULSE"),
        monotonicNanos: Long = 1L
    ) = article(
        id,
        FastMoveUseObserved(
            useId = id,
            attackingSide = ActivePokemonSide.OPPONENT,
            damagedSide = ActivePokemonSide.PLAYER,
            appearanceId = "species",
            attackerSpeciesName = "Sealeo",
            evidenceKinds = evidence
        ),
        monotonicNanos,
        confidence = 0.9f
    )

    private fun article(
        id: String,
        payload: TestimonyPayload,
        at: Long,
        confidence: Float? = null
    ) = RealityArticle(
        id = ArticleId(id),
        perceivedAt = at / 1_000_000L,
        recordedAt = 1L,
        sourceId = SourceId("test"),
        payload = payload,
        confidence = confidence,
        monotonicTimeNanos = at
    )

    private class Knowledge(vararg pokemon: Pokemon) : PokemonKnowledge {
        private val byName = pokemon.associateBy { it.name }
        private val byId = pokemon.associateBy { it.id }
        override suspend fun getPokemonByName(name: String): Pokemon? = byName[name]
        override suspend fun getPokemonById(id: Int): Pokemon? = byId[id]
        override suspend fun getAllSpeciesNames(): Set<String> = byName.keys
    }
}
