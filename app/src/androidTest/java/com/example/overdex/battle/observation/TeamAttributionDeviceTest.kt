package com.example.overdex.battle.observation

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.archive.*
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.replay.MatchReplayModel
import com.example.overdex.data.*
import com.example.overdex.data.local.PokedexDatabase
import com.example.overdex.ui.screens.observatory.MatchReplayScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Post-OCR routing and completed-archive rendering on the Moondrop; no profile writes. */
class TeamAttributionDeviceTest {
    @get:Rule val compose = createComposeRule()
    private fun repository(): PokemonRepository {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return PokemonRepository(PokedexDatabase.getDatabase(context).pokemonDao(),
            LocalSpriteProvider(context.assets), GameMasterLoader(context))
    }

    @Test fun staleSavedTeamCannotPutPlayerEntryOrChargeOnOpponentHud() = runBlocking<Unit> {
        val timeline = InMemoryRealityTimeline()
        val custody = InMemoryTestimonyCustody()
        val match = Match("ATTRIBUTION_CHECK", custody = custody, realityTimeline = timeline, pokemonKnowledge = repository())
        val witness = PersistedPlayerEntrySpeciesWitness()
        val base = System.nanoTime()
        fun send(source: String, payload: TestimonyPayload, millis: Long) = custody.submitTestimony(
            SourceId(source), payload, System.currentTimeMillis(), null, emptyList(), base + millis * 1_000_000L)
        suspend fun await(check: () -> Boolean) = withTimeout(10_000) { while (!check()) delay(20) }
        try {
            DroidballOverlayPresentation.clearOpponentSpecies()
            witness.start(match)
            listOf(Triple("Samurott", 503, "Fury Cutter"), Triple("Ceruledge", 937, "Incinerate"),
                Triple("Machamp", 68, "Counter")).forEachIndexed { i, (name, id, move) ->
                val chargedMove = listOf("Hydro Cannon", "Shadow Ball", "Cross Chop")[i]
                send("CURRENT_TEAM_CONFIGURATION", PlayerTeamSlotConfigured(i + 1, name, id, move, listOf(chargedMove)), 0)
            }
            send("ANNOUNCEMENT_WITNESS", RawTestimony("Go, Gourgeist!"), 100)
            await { timeline.getArticles().any { it.payload is PokemonIdentified } }
            assertTrue(timeline.getArticles().none { it.payload is ActivePokemonSpeciesWitnessed })
            send(BattleWitnessContracts.countdownGlyph.witnessId, CountdownGlyphWitnessed("GO", 1f, 1), 300)
            await { timeline.getArticles().any { it.payload is MatchStarted } }
            assertTrue("Configured Samurott must not become an observed lead", timeline.getArticles().none {
                (it.payload as? ActivePokemonSpeciesWitnessed)?.speciesName == "Samurott" })
            // Captured before GO, published afterward: the entry is resolved at its source time.
            send("PLAYER_SPECIES_OVERLAY_PIPELINE", ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Gourgeist", 711), 200)
            send("OPPONENT_SPECIES_OVERLAY_PIPELINE", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364), 200)
            await { timeline.getArticles().any { it.sourceId.id == "ENTRY_ANNOUNCEMENT_SPECIES_WITNESS" &&
                (it.payload as? ActivePokemonSpeciesWitnessed)?.side == ActivePokemonSide.PLAYER } }
            await { DroidballOverlayPresentation.activeOpponentMovePossibilities.value?.speciesName == "Sealeo" }
            val entry = timeline.getArticles().single { it.sourceId.id == "ENTRY_ANNOUNCEMENT_SPECIES_WITNESS" &&
                it.payload is ActivePokemonSpeciesWitnessed }
            assertEquals(base + 100_000_000L, entry.monotonicTimeNanos)
            assertEquals(2, entry.evidenceReferences.orEmpty().size)
            assertEquals(listOf("Sealeo"), DroidballOverlayPresentation.opponentSpecies.value.map { it.speciesName })
            send("ANNOUNCEMENT_WITNESS", RawTestimony("Gourgeist used Seed Bomb!"), 700)
            await { timeline.getArticles().any { it.payload is ChargedMoveEnergySpent } }
            val charge = timeline.getArticles().mapNotNull { it.payload as? ChargedMoveEnergySpent }.single()
            assertEquals(ActivePokemonSide.PLAYER, charge.side)
            assertEquals("Seed Bomb", charge.moveName)
            assertTrue(timeline.getArticles().none {
                (it.payload as? ActivePokemonSpeciesWitnessed)?.let { p -> p.side == ActivePokemonSide.OPPONENT && p.speciesName == "Gourgeist" } == true })
        } finally {
            witness.stop(); match.release(); DroidballOverlayPresentation.clearOpponentSpecies()
        }
    }

    @Test fun recordedEveningBattlesKeepTheirTeamsAndRenderGourgeistAgainstSealeo() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = repository()
        val names = listOf(
            "2026-10-14_19-25-43_9b0a299f-44bb-44b8-a0f6-f3af4e62c013.odxmatch.zip",
            "2026-10-14_19-27-20_696b62f9-f6c0-4db4-9b57-c01f10597c60.odxmatch.zip",
        )
        val archives = names.map { MatchArchivePackageReader.read(File(context.filesDir, "match_archives/$it").inputStream()) }
        for (archive in archives) {
            val original = MatchArchiveSerializer.serialize(archive)
            val species = MatchReplayModel.referencedSpeciesNames(archive).mapNotNull { name ->
                repository.getPokemonByName(name)?.let { name to it }
            }.toMap()
            val model = MatchReplayModel(archive, speciesIdsByName = species.mapValues { it.value.id },
                fastMovesBySpeciesName = species.mapValues { it.value.fastMoves },
                chargedMovesBySpeciesName = species.mapValues { it.value.chargedMoves })
            val start = archive.articles.first().monotonicTimeNanos!!
            val playerNames = mutableSetOf<String>()
            for (second in 25L..90L) {
                val scene = model.sceneAt(start + second * 1_000_000_000L)
                scene.player?.speciesName?.let(playerNames::add)
                assertFalse("$second s: ${scene.opponent}", scene.opponent?.speciesName in setOf("Abomasnow", "Gourgeist", "Camerupt", "Samurott"))
            }
            assertTrue("Abomasnow" in playerNames)
            assertTrue("Camerupt" in playerNames)
            assertFalse("Samurott" in playerNames)
            assertEquals(original, MatchArchiveSerializer.serialize(archive))
        }
        val archive = archives.last()
        val at = archive.articles.first().monotonicTimeNanos!! + 50_000_000_000L
        compose.setContent {
            MatchReplayScreen(archive, onBack = {}, initialCursorNanos = at,
                resolveSpeciesId = { repository.getPokemonByName(it)?.id },
                resolveFastMoves = { repository.getPokemonByName(it)?.fastMoves.orEmpty() },
                resolveChargedMoves = { repository.getPokemonByName(it)?.chargedMoves.orEmpty() })
        }
        compose.waitUntil(20_000) {
            compose.onAllNodesWithContentDescription("Gourgeist").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithContentDescription("Sealeo").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Gourgeist").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sealeo").assertIsDisplayed()
        File(context.cacheDir, "team-attribution-replay.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
