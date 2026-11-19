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

    @Test fun moondropReplayCanSeekWhileEndingEvidenceIsPending() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("recordedMoondropTwoPulses") == "true")
        val context = instrumentation.targetContext
        val file = File(context.filesDir, "match_archives/2026-11-19_12-27-58_457f590b-6d03-4469-b223-b45fcf0ac2b9.odxmatch.zip")
        val started = android.os.SystemClock.elapsedRealtime()
        val archive = file.inputStream().use(MatchArchivePackageReader::read)
        val go = archive.articles.first { it.payload is ArchivedMatchStarted }.monotonicTimeNanos!!
        val repository = PokemonRepository(PokedexDatabase.getDatabase(context).pokemonDao(),
            LocalSpriteProvider(context.assets), GameMasterLoader(context))
        val releaseAudio = kotlinx.coroutines.CompletableDeferred<Unit>()
        val enteredAudio = kotlinx.coroutines.CompletableDeferred<Unit>()
        var down: () -> Unit = {}
        val cursor = java.util.concurrent.atomic.AtomicLong(-1L)
        val lcd = androidx.compose.runtime.mutableStateOf<(@androidx.compose.runtime.Composable () -> Unit)?>(null)
        compose.setContent {
            androidx.compose.foundation.layout.Column {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1f)) {
                    MatchReplayScreen(archive, onBack = {}, onDown = { down = it },
                        initialCursorNanos = go + 65_000_000_000L,
                        resolveSpeciesId = { repository.getPokemonByName(it)?.id },
                        resolveFastMoves = { repository.getPokemonByName(it)?.fastMoves.orEmpty() },
                        resolveChargedMoves = { repository.getPokemonByName(it)?.chargedMoves.orEmpty() },
                        resolveArchivedAudio = { _, _, _ ->
                            enteredAudio.complete(Unit)
                            releaseAudio.await()
                            com.example.overdex.battle.replay.ReplayAudioRecovery()
                        },
                        pairedReplayScene = { scene, at -> cursor.set(at); scene },
                        onLcdContentUpdate = { lcd.value = it })
                }
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1f)) { lcd.value?.invoke() }
            }
        }
        try {
            compose.waitUntil(30_000) { enteredAudio.isCompleted && cursor.get() == go + 65_000_000_000L }
            compose.onNodeWithContentDescription("Turtonator").assertIsDisplayed()
            compose.onNodeWithContentDescription("Vaporeon").assertIsDisplayed()
            android.util.Log.i("ReplayOpenTrial", "Archive read to usable scene: ${android.os.SystemClock.elapsedRealtime() - started} ms")
            compose.runOnIdle { down() }
            val sought = compose.runOnIdle { cursor.get() }
            assertTrue(sought > go + 65_000_000_000L)
            releaseAudio.complete(Unit)
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("CHECKING ENDING AUDIO").fetchSemanticsNodes().isEmpty()
            }
            compose.runOnIdle { assertEquals(sought, cursor.get()) }
        } finally {
            releaseAudio.complete(Unit)
        }
    }

    @Test fun savedSecondDragonPulseShowsSelectableFaintWithOwnerOnLcd() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("recordedMoondropTwoPulses") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.filesDir, "match_archives/2026-11-19_12-27-58_457f590b-6d03-4469-b223-b45fcf0ac2b9.odxmatch.zip")
        val archive = file.inputStream().use(MatchArchivePackageReader::read)
        val go = archive.articles.first { it.payload is ArchivedMatchStarted }.monotonicTimeNanos!!
        val repo = PokemonRepository(PokedexDatabase.getDatabase(context).pokemonDao(),
            LocalSpriteProvider(context.assets), GameMasterLoader(context))
        val lcd = androidx.compose.runtime.mutableStateOf<(@androidx.compose.runtime.Composable () -> Unit)?>(null)
        compose.setContent {
            androidx.compose.foundation.layout.Column {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1f)) {
                    MatchReplayScreen(archive, onBack = {}, initialCursorNanos = go + 91_000_000_000L,
                        impactArchiveUri = android.net.Uri.fromFile(file), resolveSpeciesNames = repo::getAllSpeciesNames,
                        resolveSpeciesId = { repo.getPokemonByName(it)?.id },
                        resolveFastMoves = { repo.getPokemonByName(it)?.fastMoves.orEmpty() },
                        resolveChargedMoves = { repo.getPokemonByName(it)?.chargedMoves.orEmpty() },
                        onLcdContentUpdate = { lcd.value = it })
                }
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1f)) { lcd.value?.invoke() }
            }
        }
        val label = "Faint · Vaporeon · Dragon Pulse by Turtonator"
        compose.waitUntil(240_000) { compose.onAllNodesWithText(label, substring = true).fetchSemanticsNodes().isNotEmpty() }
        val marker = compose.onNodeWithText(label, substring = true)
        marker.assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous replay event").performClick()
        // From a cursor beyond the final marker, previous seeks to the faint itself.
        marker.assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous replay event").performClick()
        compose.onNodeWithContentDescription("Next replay event").performClick()
        marker.assertIsDisplayed()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        marker.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val last = layout.lineCount - 1
        val measured = "LCD ${layout.size}, last line ${layout.getLineRight(last)} x ${layout.getLineBottom(last)}"
        assertFalse(measured, layout.isLineEllipsized(last))
        assertEquals(measured, layout.layoutInput.text.length, layout.getLineEnd(last, visibleEnd = true))
        assertTrue(measured, layout.getLineRight(last) <= layout.size.width + 1)
        assertTrue(measured, layout.getLineBottom(last) <= layout.size.height + 1)
        File(context.cacheDir, "recorded-vaporeon-faint-lcd.png").outputStream().use {
            marker.captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

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
