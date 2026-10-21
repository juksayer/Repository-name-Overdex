package com.example.overdex.battle.observation

import android.graphics.BitmapFactory
import android.os.Bundle
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.data.GameMasterLoader
import com.example.overdex.data.LocalSpriteProvider
import com.example.overdex.data.PokemonRepository
import com.example.overdex.data.local.PokedexDatabase
import com.example.overdex.data.observation.SpeciesNameRecognizer
import com.example.overdex.model.AnchorRegion
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

/** Read-only check of the actual Moondrop frames; no calibration writes. */
class RecordedOctober15SpeciesDeviceTest {
    @Test fun recoveryStaysOffStatusIconsAndReadsNamesAfterEmptyMenus() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val knowledge = PokemonRepository(PokedexDatabase.getDatabase(context).pokemonDao(),
            LocalSpriteProvider(context.assets), GameMasterLoader(context))
        val names = knowledge.getAllSpeciesNames()
        SpeciesNameRecognizer.warmUp()
        val cases = listOf(
            "57df49e18a19b8d12263a0209003da717a794f9c6b480edad09e8f917b4c5123" to null,
            "93d482164ae02f8f3aefeb693e926616d919b3eb05cd6c05d01ef0ca8931d7be" to "Sneasel",
            "39e60b3b9713aadd758abfbe30f0ecf490715447139e0e6e39146e5d47de8547" to "Vaporeon",
        )
        for ((hash, expected) in cases) {
            val frame = requireNotNull(BitmapFactory.decodeFile(File(context.filesDir, "artifacts/frames/sha256/$hash.png").path))
            try {
                val assigned = AnchorRegion(493 / 672f, 96 / 1493f, 157 / 672f, 32 / 1493f)
                val located = SpeciesBadgeTextLocator.locate(frame, assigned)
                assertEquals("No complete name outside the assigned row in $expected frame", assigned, located)
                val crop = requireNotNull(BattleCropResolver.resolve("TestSpecies", located, frame, 32))
                try {
                    val start = System.nanoTime()
                    val readings = SpeciesNameRecognizer.recognizeCandidates(crop.bitmap) {
                        FastOverlaySpeciesResolver.resolve(it, names) != null
                    }
                    val found = readings.firstNotNullOfOrNull { FastOverlaySpeciesResolver.resolve(it.text, names) }
                    assertEquals(expected, found)
                    instrumentation.sendStatus(0, Bundle().apply {
                        putString("species_read", "expected=$expected found=$found millis=${(System.nanoTime() - start) / 1_000_000}")
                    })
                } finally { crop.bitmap.recycle() }
            } finally { frame.recycle() }
        }
    }

    @Test fun pendingVaporeonEntryUsesSealeoFaintAndPopulatesHudWithMoves() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val knowledge = PokemonRepository(PokedexDatabase.getDatabase(context).pokemonDao(),
            LocalSpriteProvider(context.assets), GameMasterLoader(context))
        val timeline = InMemoryRealityTimeline()
        val custody = InMemoryTestimonyCustody()
        val match = Match("OCT15_ENTRY_CHECK", custody = custody, realityTimeline = timeline, pokemonKnowledge = knowledge)
        val witness = PersistedPlayerEntrySpeciesWitness()
        val base = System.nanoTime()
        fun send(source: String, payload: TestimonyPayload, millis: Long) = custody.submitTestimony(
            SourceId(source), payload, System.currentTimeMillis(), null, emptyList(), base + millis * 1_000_000L)
        suspend fun await(check: () -> Boolean) = withTimeout(10_000) { while (!check()) delay(20) }
        try {
            DroidballOverlayPresentation.clearOpponentSpecies()
            witness.start(match)
            send("OPPONENT_SPECIES_OVERLAY_PIPELINE",
                ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364), 0)
            await { DroidballOverlayPresentation.activeOpponentMovePossibilities.value?.speciesName == "Sealeo" }
            send("ANNOUNCEMENT_WITNESS", RawTestimony("Go, Vaporeon!"), 1000)
            await { timeline.getArticles().any { it.payload is PokemonIdentified } }
            assertTrue(timeline.getArticles().none {
                (it.payload as? ActivePokemonSpeciesWitnessed)?.speciesName == "Vaporeon" })
            // Exercise the more difficult delivery order: the faint has an
            // earlier source time but reaches the pending-entry witness later.
            send("OPPONENT_FAINT_WITNESS",
                ActivePokemonFaintedWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364,
                    "OPPONENT_POKE_BALL_COUNT_DECREASE"), 900)
            await { DroidballOverlayPresentation.activeOpponentMovePossibilities.value?.speciesName == "Vaporeon" }
            val articles = timeline.getArticles()
            val identity = articles.single { (it.payload as? ActivePokemonSpeciesWitnessed)?.speciesName == "Vaporeon" }
            assertEquals(ActivePokemonSide.OPPONENT, (identity.payload as ActivePokemonSpeciesWitnessed).side)
            assertEquals(base + 1_000_000_000L, identity.monotonicTimeNanos)
            val cited = identity.evidenceReferences.orEmpty().map { id -> articles.single { it.id.value == id }.payload }
            assertTrue(cited.any { it is RawTestimony && it.data == "Go, Vaporeon!" })
            assertTrue(cited.any { it is ActivePokemonFaintedWitnessed && it.speciesName == "Sealeo" })
            val moves = requireNotNull(DroidballOverlayPresentation.activeOpponentMovePossibilities.value)
            assertTrue(moves.fastMoves.isNotEmpty())
            assertTrue(moves.chargedMoves.isNotEmpty())
            assertTrue(articles.none { (it.payload as? ActivePokemonSpeciesWitnessed)?.let { p ->
                p.side == ActivePokemonSide.PLAYER && p.speciesName == "Vaporeon" } == true })
        } finally {
            witness.stop(); match.release(); DroidballOverlayPresentation.clearOpponentSpecies()
        }
    }

}
