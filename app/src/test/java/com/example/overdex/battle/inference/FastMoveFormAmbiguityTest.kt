package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.FastMoveEffectiveness
import com.example.overdex.battle.custody.FastMoveEffectivenessWitnessed
import com.example.overdex.battle.custody.FastMoveIdentified
import com.example.overdex.battle.custody.FastMoveUseObserved
import com.example.overdex.battle.custody.MatchStarted
import com.example.overdex.battle.forecast.OpponentEnergyForecast
import com.example.overdex.battle.observation.DroidballOverlayPresentation
import com.example.overdex.battle.custody.PlayerTeamSlotConfigured
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.custody.TestimonyPayload
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.data.PokemonFormKnowledge
import com.example.overdex.model.GameMaster
import com.example.overdex.model.Move
import com.example.overdex.model.Pokemon
import com.example.overdex.model.PokemonType
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/** Reference-backed regressions for the Mawile / bare Stunfisk recording. */
class FastMoveFormAmbiguityTest {
    @Test fun `bare opponent Stunfisk retains both regional legal pools`() = runBlocking {
        val engine = FastMoveCadenceInference(knowledge)
        engine.accept(species("opponent", ActivePokemonSide.OPPONENT, "Stunfisk"))
        assertEquals(setOf("Thunder Shock", "Mud Shot", "Metal Claw"),
            engine.candidateSnapshot(ActivePokemonSide.OPPONENT)!!.remainingMoveNames.toSet())
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)!!.identifiedMoveName)
    }

    @Test fun `recorded super effective text cannot choose Ice Fang from unresolved Stunfisk form`() = runBlocking {
        val engine = FastMoveCadenceInference(knowledge)
        engine.accept(species("player", ActivePokemonSide.PLAYER, "Mawile"))
        engine.accept(species("opponent", ActivePokemonSide.OPPONENT, "Stunfisk"))
        val text = effectiveness("super-1", ActivePokemonSide.OPPONENT)
        val original = text.copy()
        assertTrue(engine.accept(text).none { it.payload is FastMoveIdentified })
        assertTrue(engine.accept(effectiveness("super-2", ActivePokemonSide.OPPONENT, 3L)).isEmpty())
        val candidates = engine.candidateSnapshot(ActivePokemonSide.PLAYER)!!
        assertEquals(setOf("Fire Fang", "Ice Fang"), candidates.remainingMoveNames.toSet())
        assertNull(candidates.identifiedMoveName)
        assertEquals(listOf("EFFECTIVENESS"), candidates.evidenceKinds)
        assertEquals(original, text) // Raw text/timestamp/source are never rewritten.
    }

    @Test fun `explicit Galarian form lets super effective text identify Fire Fang with source lineage`() = runBlocking {
        val engine = FastMoveCadenceInference(knowledge)
        engine.accept(species("player", ActivePokemonSide.PLAYER, "Mawile"))
        engine.accept(species("opponent-form", ActivePokemonSide.OPPONENT, "Stunfisk (Galarian)"))
        val result = engine.accept(effectiveness("super", ActivePokemonSide.OPPONENT)).single()
        assertEquals("Fire Fang", (result.payload as FastMoveIdentified).moveName)
        assertEquals(setOf("player", "opponent-form", "super"), result.predecessorIds.map { it.value }.toSet())
        assertEquals(2L, result.observedArticle.monotonicTimeNanos)
        assertEquals(setOf("Mud Shot", "Metal Claw"),
            engine.candidateSnapshot(ActivePokemonSide.OPPONENT)!!.remainingMoveNames.toSet())
    }

    @Test fun `fresh form evidence narrows retained uncertainty on next effectiveness read`() = runBlocking {
        val engine = FastMoveCadenceInference(knowledge)
        engine.accept(species("player", ActivePokemonSide.PLAYER, "Mawile"))
        engine.accept(species("opponent-bare", ActivePokemonSide.OPPONENT, "Stunfisk"))
        engine.accept(effectiveness("super-bare", ActivePokemonSide.OPPONENT))
        engine.accept(species("opponent-form", ActivePokemonSide.OPPONENT, "Stunfisk (Galarian)", 3L))
        val result = engine.accept(effectiveness("super-form", ActivePokemonSide.OPPONENT, 4L)).single()
        assertEquals("Fire Fang", (result.payload as FastMoveIdentified).moveName)
        assertTrue(result.predecessorIds.containsAll(listOf(ArticleId("opponent-form"), ArticleId("super-form"))))
    }

    @Test fun `configured ordinary player typing stays exact rather than admitting regional typing`() = runBlocking {
        val engine = FastMoveCadenceInference(knowledge)
        engine.accept(article("config", PlayerTeamSlotConfigured(1, "Stunfisk", 618, "Mud Shot", listOf("Mud Bomb"))))
        engine.accept(species("player", ActivePokemonSide.PLAYER, "Stunfisk"))
        engine.accept(species("opponent", ActivePokemonSide.OPPONENT, "Mawile"))
        val result = engine.accept(effectiveness("super", ActivePokemonSide.PLAYER)).single()
        assertEquals("Ice Fang", (result.payload as FastMoveIdentified).moveName)
        assertEquals("Mud Shot", engine.candidateSnapshot(ActivePokemonSide.PLAYER)!!.identifiedMoveName)
    }

    @Test fun `regional form candidates cannot borrow player roster lineage`() = runBlocking {
        val engine = FastMoveCadenceInference(knowledge)
        engine.accept(article("unrelated-config", PlayerTeamSlotConfigured(2, "Stunfisk (Galarian)", 618,
            "Mud Shot", listOf("Rock Slide"))))
        engine.accept(species("player", ActivePokemonSide.PLAYER, "Mawile"))
        engine.accept(species("opponent-form", ActivePokemonSide.OPPONENT, "Stunfisk (Galarian)"))
        val result = engine.accept(effectiveness("super", ActivePokemonSide.OPPONENT)).single()
        assertFalse(ArticleId("unrelated-config") in result.predecessorIds)
    }

    @Test fun `opponent Raichu pool remains broad until Alolan form is explicit`() = runBlocking {
        val engine = FastMoveCadenceInference(knowledge)
        engine.accept(species("bare", ActivePokemonSide.OPPONENT, "Raichu"))
        assertTrue("Charm" in engine.candidateSnapshot(ActivePokemonSide.OPPONENT)!!.remainingMoveNames)
        engine.accept(species("alolan", ActivePokemonSide.OPPONENT, "Raichu (Alolan)", 2L))
        assertFalse("Charm" in engine.candidateSnapshot(ActivePokemonSide.OPPONENT)!!.remainingMoveNames)
    }

    @Test fun `battle equivalent shadow alternatives preserve learned move across a switch`() = runBlocking {
        val engine = FastMoveCadenceInference(knowledge)
        engine.accept(species("player", ActivePokemonSide.PLAYER, "Stunfisk (Galarian)"))
        engine.accept(species("opponent", ActivePokemonSide.OPPONENT, "Mawile"))
        engine.accept(effectiveness("super", ActivePokemonSide.PLAYER))
        assertEquals("Fire Fang", engine.candidateSnapshot(ActivePokemonSide.OPPONENT)!!.identifiedMoveName)
        engine.accept(species("switch", ActivePokemonSide.OPPONENT, "Raichu", 3L))
        engine.accept(species("return", ActivePokemonSide.OPPONENT, "Mawile", 4L))
        assertEquals("Fire Fang", engine.candidateSnapshot(ActivePokemonSide.OPPONENT)!!.identifiedMoveName)
    }

    @Test fun `missing typing in a possible form cannot support effectiveness inference`() = runBlocking {
        val incomplete = object : PokemonKnowledge by knowledge {
            override suspend fun getPokemonByName(name: String): Pokemon? =
                knowledge.getPokemonByName(name)?.let { pokemon ->
                    if (name == "Stunfisk (Galarian)") pokemon.copy(types = emptyList()) else pokemon
                }
        }
        val engine = FastMoveCadenceInference(incomplete)
        engine.accept(species("player", ActivePokemonSide.PLAYER, "Mawile"))
        engine.accept(species("bare", ActivePokemonSide.OPPONENT, "Stunfisk"))
        assertTrue(engine.accept(effectiveness("super", ActivePokemonSide.OPPONENT)).isEmpty())
        val snapshot = engine.candidateSnapshot(ActivePokemonSide.PLAYER)!!
        assertNull(snapshot.identifiedMoveName)
        assertTrue(snapshot.evidenceKinds.isEmpty())
        assertTrue(snapshot.remainingMoveNames.containsAll(listOf("Fire Fang", "Ice Fang")))
    }

    @Test fun `unavailable form does not make the other form's move pool exhaustive`() = runBlocking {
        val incomplete = object : PokemonKnowledge by knowledge {
            override suspend fun getPokemonByName(name: String): Pokemon? =
                if (name == "Stunfisk (Galarian)") null else knowledge.getPokemonByName(name)
        }
        val engine = FastMoveCadenceInference(incomplete)
        assertTrue(engine.accept(species("bare", ActivePokemonSide.OPPONENT, "Stunfisk")).isEmpty())
        val snapshot = engine.candidateSnapshot(ActivePokemonSide.OPPONENT)!!
        assertNull(snapshot.identifiedMoveName)
        assertTrue(snapshot.remainingMoveNames.isEmpty())
    }

    @Test fun `catalogue lookups are cached and do not run on every witness`() = runBlocking {
        var reads = 0
        val counted = object : PokemonKnowledge by knowledge {
            override suspend fun getAllSpeciesNames(): Set<String> {
                reads++
                return knowledge.getAllSpeciesNames()
            }
        }
        val engine = FastMoveCadenceInference(counted)
        engine.accept(species("player", ActivePokemonSide.PLAYER, "Mawile"))
        engine.accept(species("bare", ActivePokemonSide.OPPONENT, "Stunfisk"))
        engine.accept(effectiveness("super", ActivePokemonSide.OPPONENT))
        engine.accept(species("other", ActivePokemonSide.OPPONENT, "Raichu", 3L))
        assertEquals(1, reads)
    }

    @Test fun `regional only move reaches HUD forecast and exact energy despite bare badge`() = runBlocking {
        val references = PokemonFormKnowledge(knowledge)
        val forms = references.forObservedName("Stunfisk")
        val move = forms.fastMoves.single { it.name == "Metal Claw" }
        assertTrue(forms.chargedMoves.any { it.name == "Rock Slide" })
        assertTrue(forms.chargedMoves.any { it.name == "Discharge" })
        val observed = species("bare", ActivePokemonSide.OPPONENT, "Stunfisk")
        val identity = article("sound-proof", FastMoveIdentified(ActivePokemonSide.OPPONENT, "Stunfisk",
            move.name, move.turns!! * 500_000_000L, null, 0, "AUDIO_REFERENCE_REPEATED_UNIQUE_LEGAL_SIDE"), 2L)
        DroidballOverlayPresentation.reset()
        try {
            DroidballOverlayPresentation.recordOpponentSpecies("Stunfisk", 618,
                forms.fastMoves.map { it.name to it.type }, forms.chargedMoves.map { it.name to it.type })
            DroidballOverlayPresentation.recordOpponentFastMoveCandidates("Stunfisk", listOf("Metal Claw"))
            DroidballOverlayPresentation.recordOpponentFastMove(identity.payload as FastMoveIdentified)
            val shown = DroidballOverlayPresentation.activeOpponentMovePossibilities.value!!.fastMoves.single()
            assertEquals("Metal Claw", shown.name)
            assertTrue(shown.isConfirmed)
        } finally {
            DroidballOverlayPresentation.reset()
        }
        val forecast = OpponentEnergyForecast()
        forecast.registerMoves("Stunfisk", forms.fastMoves)
        forecast.restrictCandidates("Stunfisk", listOf("Metal Claw"))
        forecast.observe(article("go", MatchStarted))
        forecast.observe(observed)
        forecast.observe(identity)
        assertEquals("Metal Claw", forecast.estimate(3_000_000_000L)!!.assumedMoveName)
        assertTrue(forecast.estimate(3_000_000_000L)!!.moveIdentified)
        val energy = FastMoveEnergyInference(knowledge, references)
        energy.accept(observed)
        assertTrue(energy.accept(identity).isEmpty()) // Identity alone never manufactures energy.
        val result = energy.accept(article("hit", FastMoveUseObserved("hit", ActivePokemonSide.OPPONENT,
            ActivePokemonSide.PLAYER, "appearance", "Stunfisk", listOf("HP_BORDER_PULSE")), 3L)).single()
        assertEquals(move.energy, result.payload.energyPerUse)
        assertEquals(1, result.payload.observedCompletedUses)
        assertTrue(result.predecessorIds.containsAll(listOf(ArticleId("hit"), ArticleId("sound-proof"))))
    }

    private fun species(id: String, side: ActivePokemonSide, name: String, at: Long = 1L) =
        article(id, ActivePokemonSpeciesWitnessed(side, name), at)

    private fun effectiveness(id: String, damaged: ActivePokemonSide, at: Long = 2L) =
        article(id, FastMoveEffectivenessWitnessed(FastMoveEffectiveness.SUPER_EFFECTIVE,
            damaged, "SUPER EFFECTIVE!"), at)

    private fun article(id: String, payload: TestimonyPayload, at: Long = 1L) = RealityArticle(
        ArticleId(id), perceivedAt = at, recordedAt = at, sourceId = SourceId("form-regression"),
        payload = payload, monotonicTimeNanos = at,
    )

    companion object {
        private val knowledge: PokemonKnowledge by lazy {
            val master = Json { ignoreUnknownKeys = true }.decodeFromString<GameMaster>(
                File("src/main/assets/gamemaster.json").readText())
            val moves = master.moves.associateBy { it.moveId }
            val byName = master.pokemon.associate { source ->
                source.speciesName to Pokemon(source.dex, source.speciesName,
                    source.types.mapNotNull { runCatching { PokemonType.valueOf(it.uppercase()) }.getOrNull() }, "",
                    fastMoves = source.fastMoves.mapNotNull { id -> moves[id]?.let { move ->
                        Move(move.name, PokemonType.valueOf(move.type.uppercase()), move.power, move.energyGain,
                            isFast = true, turns = move.turns)
                    } }, chargedMoves = source.chargedMoves.mapNotNull { id -> moves[id]?.let { move ->
                        Move(move.name, PokemonType.valueOf(move.type.uppercase()), move.power,
                            kotlin.math.abs(move.energy), isFast = false)
                    } })
            }
            object : PokemonKnowledge {
                override suspend fun getPokemonByName(name: String) = byName[name]
                override suspend fun getPokemonById(id: Int) = byName.values.firstOrNull { it.id == id }
                override suspend fun getAllSpeciesNames() = byName.keys
            }
        }
    }
}
