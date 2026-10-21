package com.example.overdex.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.archive.*
import com.example.overdex.battle.replay.MatchReplayModel
import com.example.overdex.data.GameMasterLoader
import com.example.overdex.data.LocalSpriteProvider
import com.example.overdex.data.PokemonRepository
import com.example.overdex.data.local.PokedexDatabase
import com.example.overdex.ui.screens.observatory.MatchReplayScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Reads existing October 14 phone archives without altering or importing them. */
class RecordedReplayOpenDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun opensNewArchivesAndRendersMissingStartMatchAfterCatalogueLoads(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = PokemonRepository(PokedexDatabase.getDatabase(context).pokemonDao(),
            LocalSpriteProvider(context.assets), GameMasterLoader(context))
        val names = listOf(
            "2026-10-14_13-13-40_b5cbd643-c6a3-4129-86df-01c87b4b2621.odxmatch.zip",
            "2026-10-14_13-15-38_3a72baf6-8c7e-43f6-b7f0-584a204123cb.odxmatch.zip",
            "2026-10-14_13-23-03_d99c6fbd-e2b8-49fb-9583-78c54403c2c0.odxmatch.zip",
            "2026-10-14_13-23-50_e25d5a4a-c39e-4acc-ae5a-f5e579b0ff48.odxmatch.zip",
            "2026-10-14_13-38-38_2216a735-7b79-4d40-bcda-645a55030dd7.odxmatch.zip",
        )
        val archives = names.map { name ->
            val file = File(context.filesDir, "match_archives/$name")
            check(file.isFile) { "Recorded phone fixture is missing: $name" }
            MatchArchivePackageReader.read(file.inputStream())
        }
        for (archive in archives) {
            val original = MatchArchiveSerializer.serialize(archive)
            val species = MatchReplayModel.referencedSpeciesNames(archive).mapNotNull { name ->
                repository.getPokemonByName(name)?.let { name to it.id }
            }.toMap()
            assertTrue("Catalogue must be loaded for this regression", species.isNotEmpty())
            val model = MatchReplayModel(archive, speciesIdsByName = species)
            assertTrue(model.endNanos >= model.startNanos)
            var cursor = model.startNanos
            while (cursor < model.endNanos) {
                model.sceneAt(cursor)
                cursor += 250_000_000L
            }
            model.sceneAt(model.endNanos)
            assertEquals(original, MatchArchiveSerializer.serialize(archive))
        }
        val archive = archives.last()
        val start = archive.articles.mapNotNull { it.monotonicTimeNanos }.min()
        val referenceReads = AtomicInteger()
        compose.setContent {
            MatchReplayScreen(archive, onBack = {}, initialCursorNanos = start + 60_000_000_000L,
                resolveSpeciesId = { name -> repository.getPokemonByName(name)?.id.also { referenceReads.incrementAndGet() } },
                resolveFastMoves = { name -> repository.getPokemonByName(name)?.fastMoves.orEmpty() },
                resolveChargedMoves = { name -> repository.getPokemonByName(name)?.chargedMoves.orEmpty() })
        }
        compose.waitUntil(20_000) { referenceReads.get() >= MatchReplayModel.referencedSpeciesNames(archive).size }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Camerupt").assertIsDisplayed()
        compose.onNodeWithContentDescription("Vaporeon").assertIsDisplayed()
        File(context.cacheDir, "recorded-replay-open.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        Unit
    }
}
