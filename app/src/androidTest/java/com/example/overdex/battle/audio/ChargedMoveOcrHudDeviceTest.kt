package com.example.overdex.battle.audio

import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.archive.*
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.DroidballOverlayPresentation
import com.example.overdex.battle.observation.Match
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.replay.MatchReplayModel
import com.example.overdex.data.GameMasterLoader
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ChargedMoveOcrHudDeviceTest {
    @Test fun recordedIcePunchMisreadConfirmsHudAndNamedReplayMove() = runBlocking {
        val loader = GameMasterLoader(InstrumentationRegistry.getInstrumentation().targetContext)
        val knowledge = object : PokemonKnowledge {
            override suspend fun getPokemonByName(name: String): Pokemon? {
                val species = loader.allPokemon().firstOrNull { it.speciesName == name } ?: return null
                return Pokemon(species.dex, species.speciesName, emptyList(), "", fastMoves = emptyList(),
                    chargedMoves = species.chargedMoves.mapNotNull { id -> loader.getMove(id)?.let {
                        Move(it.name, PokemonType.valueOf(it.type.uppercase()), it.power, kotlin.math.abs(it.energy), false)
                    } })
            }
            override suspend fun getPokemonById(id: Int) = loader.getPokemonByDex(id)?.let { getPokemonByName(it.speciesName) }
            override suspend fun getAllSpeciesNames() = loader.allPokemonNames()
        }
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match("OCR_HUD_TEST", custody = custody, realityTimeline = timeline, pokemonKnowledge = knowledge)
        val at = System.nanoTime()
        DroidballOverlayPresentation.reset()
        try {
            custody.submitTestimony(SourceId("SPECIES"), ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Galvantula", 596), 1_000, null, emptyList(), at)
            custody.submitTestimony(SourceId("SPECIES"), ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sneasel", 215), 1_000, null, emptyList(), at)
            // Original OCR from match 950f6ad9-3649-4216-b846-ad515c7f8c72, +34.105 s.
            val raw = RawTestimony("Sneasel used lce Punch!")
            custody.submitTestimony(SourceId("ANNOUNCEMENT_WITNESS"), raw, 2_000, null, emptyList(), at + 1_000_000_000)
            withTimeout(5_000) {
                while (DroidballOverlayPresentation.activeOpponentMovePossibilities.value?.chargedMoves
                        ?.none { it.name == "Ice Punch" && it.isConfirmed } != false) delay(10)
            }
            val articles = timeline.getArticles()
            val move = articles.single { it.payload is ChargedMoveEnergySpent }
            assertEquals("Ice Punch", (move.payload as ChargedMoveEnergySpent).moveName)
            assertEquals(ActivePokemonSide.OPPONENT, move.payload.side)
            assertEquals(raw, articles.single { it.id in move.predecessorIds }.payload)
            val replay = MatchReplayModel(MatchArchive(matchId = match.matchId,
                articles = articles.map(RealityArticleArchiveMapper::map)))
            val action = replay.sceneAt(at + 1_100_000_000).chargedMoveActions.single()
            assertEquals("Ice Punch", action.moveName)
            assertEquals("OPPONENT", action.side)
            assertTrue(articles.none { it.payload is StatChangeObserved })
        } finally { match.release(); DroidballOverlayPresentation.reset() }
    }
}
