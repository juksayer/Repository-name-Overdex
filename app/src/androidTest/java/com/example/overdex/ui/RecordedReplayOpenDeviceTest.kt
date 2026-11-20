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
import com.example.overdex.ui.ODXFi.ODXFiShell
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

    @Test fun pendingReviewHoldsPlaybackAtTheEndingAndResumesOnce() {
        fun event(id: String, time: Long, payload: ArchivedTestimonyPayload) =
            ArchivedRealityArticle(id, "ending", 0, 0, "TEST", payload, emptyList(), null, null, emptyList(), time)
        val archive = MatchArchive(matchId = "ending", articles = listOf(
            event("start", 1_000_000_000L, ArchivedMatchStarted),
            event("end", 41_000_000_000L, ArchivedMatchEnded("WIN"))))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("replay-ending", ".zip", context.cacheDir)
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val cursor = java.util.concurrent.atomic.AtomicLong()
        val completed = AtomicInteger()
        try {
            java.util.zip.ZipOutputStream(file.outputStream()).close()
            compose.setContent {
                MatchReplayScreen(archive, onBack = {}, initialCursorNanos = 20_900_000_000L,
                    playbackWindow = com.example.overdex.battle.replay.ReplayWindow(20_900_000_000L, 21_200_000_000L),
                    impactArchiveUri = android.net.Uri.fromFile(file), autoplay = true,
                    resolveArchivedAudio = { _, _, _ ->
                        release.await()
                        com.example.overdex.battle.replay.ReplayAudioRecovery()
                    },
                    pairedReplayScene = { scene, at -> cursor.set(at); scene },
                    onPlaybackComplete = { completed.incrementAndGet() })
            }
            compose.waitUntil(30_000) { cursor.get() == 21_000_000_000L }
            compose.onNodeWithText("Checking the finish…").assertIsDisplayed()
            compose.mainClock.advanceTimeBy(1_000)
            assertEquals(21_000_000_000L, cursor.get())
            assertEquals(0, completed.get())
            release.complete(Unit)
            compose.waitUntil(30_000) { completed.get() > 0 && cursor.get() == 21_200_000_000L }
            assertEquals(21_200_000_000L, cursor.get())
            assertEquals(1, completed.get())
            compose.onNodeWithText("Checking the finish…").assertDoesNotExist()
        } finally {
            release.complete(Unit)
            file.delete()
        }
    }

    @Test fun savedSecondDragonPulseShowsSelectableFaintWithOwnerOnLcd() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("recordedMoondropTwoPulses") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.filesDir, "match_archives/2026-11-19_12-27-58_457f590b-6d03-4469-b223-b45fcf0ac2b9.odxmatch.zip")
        val openedAt = android.os.SystemClock.elapsedRealtime()
        val archive = file.inputStream().use(MatchArchivePackageReader::read)
        val go = archive.articles.first { it.payload is ArchivedMatchStarted }.monotonicTimeNanos!!
        val repo = PokemonRepository(PokedexDatabase.getDatabase(context).pokemonDao(),
            LocalSpriteProvider(context.assets), GameMasterLoader(context))
        val model = MatchReplayModel(archive)
        val duration = model.endNanos - go
        var drag: (androidx.compose.ui.geometry.Offset) -> Unit = {}
        var cursor = 0L
        var rendered: com.example.overdex.battle.replay.ReplayScene? = null
        val lcd = androidx.compose.runtime.mutableStateOf<(@androidx.compose.runtime.Composable () -> Unit)?>(null)
        compose.setContent {
            ODXFiShell(showBattleOverlay = false, lcdContent = lcd.value, lcdEdgeToEdgeContent = true) {
                MatchReplayScreen(archive, onBack = {}, initialCursorNanos = go + 91_000_000_000L,
                    impactArchiveUri = android.net.Uri.fromFile(file), resolveSpeciesNames = repo::getAllSpeciesNames,
                    resolveSpeciesId = { repo.getPokemonByName(it)?.id },
                    resolveFastMoves = { repo.getPokemonByName(it)?.fastMoves.orEmpty() },
                    resolveChargedMoves = { repo.getPokemonByName(it)?.chargedMoves.orEmpty() },
                    resolveChargedMoveType = { species, move ->
                        repo.getPokemonByName(species)?.chargedMoves?.firstOrNull { it.name.equals(move, true) }?.type
                    },
                    pairedReplayScene = { scene, at -> rendered = scene; cursor = at; scene },
                    onLcdDrag = { drag = it },
                    onLcdContentUpdate = { lcd.value = it })
            }
        }
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Events").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Events").assertIsDisplayed().performClick()
        val playerLane = hasAnyAncestor(hasTestTag("replay-events-player"))
        val heading = compose.onNode(hasText("Vaporeon fainted") and playerLane)
        val credit = compose.onNode(hasText("Dragon Pulse by Turtonator") and playerLane)
        val faintTime = compose.onNodeWithText("Faint cry at 01:30.5 from GO")
        compose.waitUntil(480_000) { compose.onAllNodesWithText("Dragon Pulse by Turtonator").fetchSemanticsNodes().isNotEmpty() }
        heading.performScrollTo().assertIsDisplayed()
        credit.performScrollTo().assertIsDisplayed().assertReplayTextFits()
        faintTime.performScrollTo().assertIsDisplayed().assertReplayTextFits()
        android.util.Log.i("ReplayOpenTrial", "Archive open to credited LCD faint: ${android.os.SystemClock.elapsedRealtime() - openedAt} ms")
        val previous = compose.onNodeWithContentDescription("Previous replay event")
        val next = compose.onNodeWithContentDescription("Next replay event")
        // From a cursor beyond the final marker, Previous seeks to the faint itself.
        previous.performScrollTo().performClick()
        heading.performScrollTo().assertIsDisplayed()
        previous.performScrollTo().performClick()
        next.performScrollTo().performClick()
        credit.performScrollTo().assertIsDisplayed()
        File(context.cacheDir, "recorded-vaporeon-faint-lcd.png").outputStream().use {
            compose.onNodeWithTag("instrument-lcd-surface").captureToImage()
                .asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        val finish = compose.runOnIdle { requireNotNull(rendered?.opponent?.finishingChargeAssociation) }
        assertEquals("Turtonator", finish.attackerSpeciesName)
        assertEquals("Dragon Pulse", finish.moveName)
        fun seek(target: Long) {
            compose.runOnIdle { drag(androidx.compose.ui.geometry.Offset((600.0 * (target - cursor) / duration).toFloat(), 0f)) }
            compose.runOnIdle { assertTrue("seek error ${cursor - target} ns", kotlin.math.abs(cursor - target) < 1_000_000L) }
        }
        fun photograph(name: String) {
            File(context.cacheDir, "recorded-vaporeon-$name.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        seek(go + 78_500_000_000L)
        compose.onNodeWithContentDescription("Vaporeon").assertIsDisplayed()
        seek(finish.landingAtNanos + 1_000_000L)
        val projectile = compose.onNodeWithContentDescription("Charged move projectile").assertIsDisplayed()
        val target = compose.onNode(hasContentDescription("Vaporeon") or hasContentDescription("Vaporeon, fainted")).assertIsDisplayed()
        assertTrue("Dragon Pulse should overlap Vaporeon's sprite on impact",
            projectile.fetchSemanticsNode().boundsInRoot.overlaps(target.fetchSemanticsNode().boundsInRoot))
        compose.runOnIdle { assertEquals(finish, rendered!!.chargedMoveActions.single { it.side == "PLAYER" }.finishingAssociation) }
        photograph("impact")
        seek(finish.faintSourceAtNanos + com.example.overdex.battle.replay.ReplayFaintPose.DURATION_NANOS + 10_000_000L)
        compose.onNodeWithContentDescription("Vaporeon, fainted").assertIsDisplayed()
        credit.performScrollTo().assertIsDisplayed()
        photograph("faint")

        seek(go)
        val expected = compose.onAllNodesWithText("Expected from ", substring = true)
        var visited = 0
        while (expected.fetchSemanticsNodes().isEmpty() && visited < 200) {
            next.performScrollTo().assertIsEnabled().performClick()
            visited++
        }
        expected.onFirst().performScrollTo().assertIsDisplayed().assertReplayTextFits()
        val inferred = compose.runOnIdle { rendered!!.fastMoveActions.single {
            it.progress == 1f && it.sourceArticleIds.isEmpty()
        } }
        assertNull(inferred.finishingAssociation)
        val glyph = compose.onNodeWithContentDescription("${inferred.side} ${inferred.moveName} projectile")
        glyph.assertIsDisplayed()
        photograph("inferred-fast")
        previous.performScrollTo().performClick()
        next.performScrollTo().performClick()
        expected.onFirst().performScrollTo().assertIsDisplayed().assertReplayTextFits()
        glyph.assertIsDisplayed()
        val inferredAt = compose.runOnIdle { cursor }
        compose.onNodeWithText("Playback").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(inferredAt, cursor)
            assertTrue(inferred in rendered!!.fastMoveActions)
        }
        compose.onNodeWithText("Events").performScrollTo().performClick()
        expected.onFirst().performScrollTo().assertIsDisplayed()
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
