package com.example.overdex.ui

import android.os.Build
import android.os.SystemClock
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.archive.ArchivedActivePokemonFaintedWitnessed
import com.example.overdex.battle.archive.ArchivedActivePokemonSpeciesWitnessed
import com.example.overdex.battle.archive.ArchivedCropCaptured
import com.example.overdex.battle.archive.ArchivedMatchEnded
import com.example.overdex.battle.archive.ArchivedMatchStarted
import com.example.overdex.battle.archive.ArchivedMatchRecordStarted
import com.example.overdex.battle.archive.ArchivedRawText
import com.example.overdex.battle.archive.MatchArchivePackageReader
import com.example.overdex.battle.archive.MatchArchiveSerializer
import com.example.overdex.battle.replay.ArchivedSpeciesCropRecognizer
import com.example.overdex.battle.replay.ReplayIdentityObservation
import com.example.overdex.battle.replay.ReplayWindow
import com.example.overdex.data.GameMasterLoader
import com.example.overdex.data.LocalSpriteProvider
import com.example.overdex.data.PokemonRepository
import com.example.overdex.data.local.PokedexDatabase
import com.example.overdex.ui.screens.observatory.MatchReplayScreen
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * Production replay preparation and frame-clock playback of the full Blanche
 * archive. The host supplies the ZIP in a disposable emulator's cache; this
 * test neither imports a match nor starts Droidball or a phone recording.
 */
class ReplayBlanchePlaybackDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fullReplayPlaybackKeepsRecordedKnockoutsGradesAndResources(): Unit = runBlocking {
        assumeTrue("Run recorded replay instrumentation on a disposable emulator only",
            Build.HARDWARE in setOf("ranchu", "goldfish") || Build.FINGERPRINT.contains("generic") ||
                Build.MODEL.contains("Emulator"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val requireOcr = when (val value = InstrumentationRegistry.getArguments().getString("require_ocr", "true")) {
            "true" -> true
            "false" -> false
            else -> error("require_ocr must be true or false, received $value")
        }
        val source = File(context.cacheDir, "replay-blanche-review.odxmatch.zip")
        check(source.isFile) { "Supply the full Blanche ZIP at ${source.absolutePath}" }
        val sourceHash = sha256(source)
        val archive = source.inputStream().use { MatchArchivePackageReader.read(it) }
        assertEquals("a04cb9fd-a9f1-4709-9ce3-e1d5b5f29080", archive.matchId)
        assertTrue("The regression needs retained species pixels, not a reduced timeline", archive.mediaIncluded)
        val original = MatchArchiveSerializer.serialize(archive)
        val actualOcrModuleAvailable = ensureTextRecognitionModule(context, requireOcr)
        val repository = PokemonRepository(PokedexDatabase.getDatabase(context).pokemonDao(),
            LocalSpriteProvider(context.assets), GameMasterLoader(context))
        val catalogue = repository.getAllSpeciesNames()
        assertTrue("Use the current recognition catalogue", catalogue.containsAll(
            listOf("Sneasel", "Sealeo", "Vaporeon", "Turtonator", "Camerupt")))
        fun speciesAt(name: String) = archive.articles.first {
            (it.payload as? ArchivedActivePokemonSpeciesWitnessed)?.speciesName == name
        }.monotonicTimeNanos!!
        fun faintAt(name: String) = archive.articles.single {
            (it.payload as? ArchivedActivePokemonFaintedWitnessed)?.speciesName == name
        }.monotonicTimeNanos!!
        fun gradeAt(grade: String) = archive.articles.single {
            it.sourceId == "QTE_PROGRESS_TEXT_WITNESS" && (it.payload as? ArchivedRawText)?.value == "$grade!"
        }.monotonicTimeNanos!!
        val resultAt = archive.articles.single { it.payload is ArchivedMatchEnded }.monotonicTimeNanos!!
        // The complete original record extends past the ending pose. Explicit
        // whole-record transport bounds let the test drive the real LCD seek
        // callback without changing any model inputs or source timestamp.
        val bounds = ReplayWindow(archive.articles.mapNotNull { it.monotonicTimeNanos }.min(),
            archive.articles.mapNotNull { it.monotonicTimeNanos }.max())
        val initial = speciesAt("Sealeo") - SECOND
        val pressA = AtomicReference<() -> Unit>({})
        val dragLcd = AtomicReference<(Offset) -> Unit>({})
        val recovered = AtomicReference<List<ReplayIdentityObservation>?>(null)
        val cropFailure = AtomicReference<Throwable?>(null)
        val cropPasses = AtomicInteger()
        val speciesReads = AtomicInteger()
        val moveReads = AtomicInteger()
        var lcd by mutableStateOf<(@Composable () -> Unit)?>(null)
        compose.setContent {
            Column(Modifier.fillMaxSize().background(Color(0xFF163721))) {
                Box(Modifier.weight(1f)) {
                    MatchReplayScreen(
                        archive = archive,
                        onBack = {},
                        onA = pressA::set,
                        onLcdDrag = dragLcd::set,
                        onLcdContentUpdate = { lcd = it },
                        initialCursorNanos = initial,
                        playbackWindow = bounds,
                        initialPlaybackSpeed = 1f,
                        allowClipSave = false,
                        resolveSpeciesId = { name ->
                            repository.getPokemonByName(name)?.id.also { speciesReads.incrementAndGet() }
                        },
                        resolveSpeciesTypes = { name -> repository.getPokemonByName(name)?.types.orEmpty() },
                        resolveFastMoveType = { species, move ->
                            repository.getPokemonByName(species)?.fastMoves?.firstOrNull {
                                moveKey(it.name) == moveKey(move)
                            }?.type
                        },
                        resolveFastMoves = { species ->
                            repository.getPokemonByName(species)?.fastMoves.orEmpty().also { moveReads.incrementAndGet() }
                        },
                        resolveChargedMoveType = { species, move ->
                            repository.getPokemonByName(species)?.chargedMoves?.firstOrNull {
                                moveKey(it.name) == moveKey(move)
                            }?.type
                        },
                        resolveChargedMoves = { species ->
                            repository.getPokemonByName(species)?.chargedMoves.orEmpty().also { moveReads.incrementAndGet() }
                        },
                        resolveArchivedSpeciesCrops = { selected, progress ->
                            cropPasses.incrementAndGet()
                            try {
                                ArchivedSpeciesCropRecognizer.recognize(selected, { source.inputStream() }, catalogue,
                                    resolveSpeciesId = { name -> repository.getPokemonByName(name)?.id },
                                    onProgress = progress).also { actual ->
                                    recovered.set(actual)
                                    File(context.cacheDir, "replay-blanche-playback-crops.json").writeText(
                                        Json { prettyPrint = true }.encodeToString(actual))
                                    File(context.cacheDir, "replay-blanche-playback-crops.txt").writeText(
                                        "archiveSha256=$sourceHash\ncatalogueNames=${catalogue.size}\n" +
                                            "requireOcr=$requireOcr\nactualOcrModuleAvailable=$actualOcrModuleAvailable\n" +
                                            actual.joinToString("\n") { "${it.side}\t${it.speciesName}\t${it.atNanos}\t${it.basis}" })
                                }
                            } catch (error: Throwable) {
                                cropFailure.set(error)
                                throw error
                            }
                        },
                    )
                }
                Box(Modifier.height(190.dp)) { lcd?.invoke() }
            }
        }
        // OCR, catalogue lookup and Coil use real dispatchers during preparation.
        // Freeze only after the actual screen has installed its prepared model.
        compose.waitUntil(60_000) {
            cropFailure.get() != null || (recovered.get() != null &&
                compose.onAllNodesWithContentDescription("Sneasel").fetchSemanticsNodes().isNotEmpty())
        }
        cropFailure.get()?.let { throw AssertionError("Real archived crop recognition failed", it) }
        val actualCrops = checkNotNull(recovered.get())
        assertEquals(1, cropPasses.get())
        assertTrue(speciesReads.get() > 0)
        assertTrue(moveReads.get() > 0)
        // Both modes invoke the actual recognizer. An unavailable optional
        // module may honestly return no recovery; never fill that gap in tests.
        actualCrops.forEach { identity ->
            assertEquals("RECONSTRUCTED FROM ARCHIVED SPECIES CROP", identity.basis)
            assertTrue(identity.speciesName in catalogue)
            val cropName = when (identity.side) {
                "PLAYER" -> "PlayerActiveSpeciesTextCrop"
                "OPPONENT" -> "OpponentActiveSpeciesTextCrop"
                else -> error("Recovered identity has no battle side: ${identity.side}")
            }
            assertTrue("Recovered identity must come from a real same-side source crop", archive.articles.any {
                (it.payload as? ArchivedCropCaptured)?.cropName == cropName && it.monotonicTimeNanos == identity.atNanos
            })
        }
        if (requireOcr) {
            assertTrue(actualOcrModuleAvailable)
            assertEquals(listOf("Turtonator", "Camerupt"), actualCrops.filter { it.side == "PLAYER" }.map { it.speciesName })
            assertEquals(listOf("Sneasel", "Sealeo", "Vaporeon"), actualCrops.filter { it.side == "OPPONENT" }.map { it.speciesName })
            actualCrops.forEach { assertEquals(speciesAt(it.speciesName), it.atNanos) }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        var playing = false
        fun frame() {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        fun togglePlayback() {
            compose.runOnIdle { pressA.get().invoke() }
            playing = !playing
            // Commit the transport change and establish the playback clock's
            // first frame before advancing the short observed interval.
            frame()
            frame()
        }
        fun seek(at: Long) {
            if (playing) togglePlayback()
            val elapsed = displayedElapsedNanos()
            val from = bounds.startNanos + elapsed
            val deltaPixels = ((at - from).toDouble() / bounds.durationNanos * 600.0).toFloat()
            compose.runOnIdle { dragLcd.get().invoke(Offset(deltaPixels, 0f)) }
            frame()
            // LCD time is truncated to100ms. Stay well inside each tested phase
            // rather than assuming the seek created a nanosecond-exact cursor.
            assertTrue(kotlin.math.abs(bounds.startNanos + displayedElapsedNanos() - at) <= 200 * MS)
        }
        fun advance(ms: Long) {
            check(playing)
            compose.mainClock.advanceTimeBy(ms)
            compose.waitForIdle()
        }
        fun save(label: String) {
            File(context.cacheDir, "replay-blanche-playback-$label.txt").writeText(compose.onRoot().printToString())
            File(context.cacheDir, "replay-blanche-playback-$label.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap()
                    .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        fun resources(count: Int) {
            compose.onNodeWithContentDescription("Opponent: $count of 3 Pokémon remaining", substring = true)
                .assertIsDisplayed()
        }

        val goAt = archive.articles.single { it.payload is ArchivedMatchStarted }.monotonicTimeNanos!!
        seek(goAt - 500 * MS)
        compose.onNodeWithContentDescription("Turtonator").assertIsDisplayed()
        compose.onNodeWithContentDescription("Golbat").assertDoesNotExist()
        save("opening")
        seek(initial)
        compose.onNodeWithContentDescription("Sneasel").assertIsDisplayed()
        resources(3)
        togglePlayback()
        advance(600)
        compose.onNodeWithContentDescription("Sneasel, fainted").assertIsDisplayed()
        resources(2)
        save("sneasel-falling")
        advance(250)
        compose.onNodeWithContentDescription("Sneasel, fainted").assertIsDisplayed()
        save("sneasel-lying")
        advance(200)
        compose.onNodeWithContentDescription("Sealeo").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sneasel, fainted").assertDoesNotExist()
        resources(2) // Verified count evidence follows the fitted knockout pose.
        seek(faintAt("Sneasel") + 150 * MS)
        resources(2)

        seek(speciesAt("Vaporeon") - SECOND)
        togglePlayback()
        advance(600)
        compose.onNodeWithContentDescription("Sealeo, fainted").assertIsDisplayed()
        resources(1)
        save("sealeo-falling")
        advance(250)
        compose.onNodeWithContentDescription("Sealeo, fainted").assertIsDisplayed()
        save("sealeo-lying")
        advance(200)
        compose.onNodeWithContentDescription("Vaporeon").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sealeo, fainted").assertDoesNotExist()
        resources(1)
        seek(faintAt("Sealeo") + 150 * MS)
        resources(1)

        // The two accepted vibration patterns overlap the same Brutal Swing.
        // Walk through their restart rather than checking one frozen grade.
        val recordOrigin = archive.articles.single { it.payload is ArchivedMatchRecordStarted }.monotonicTimeNanos!!
        seek(recordOrigin + 73_682_574_158L - 200 * MS)
        togglePlayback()
        advance(300)
        compose.onNodeWithText("EXCELLENT").assertIsDisplayed()
        repeat(4) {
            advance(200)
            compose.onNodeWithText("NICE").assertDoesNotExist()
        }
        save("grade-continuity")

        for (grade in listOf("GREAT", "NICE")) {
            seek(gradeAt(grade) - 500 * MS)
            togglePlayback()
            advance(600)
            compose.onNodeWithText(grade).assertIsDisplayed()
            compose.onNodeWithContentDescription("Charging move").assertIsDisplayed().assert(
                SemanticsMatcher("A recorded grade keeps an unnamed player charge") { node ->
                    node.config.getOrNull(SemanticsProperties.StateDescription)?.startsWith("PLAYER: Move unconfirmed;") == true
                })
            compose.onNodeWithContentDescription("Charged move projectile").assertDoesNotExist()
            save(grade.lowercase())
        }
        seek(resultAt - 500 * MS)
        togglePlayback()
        advance(700)
        compose.onNodeWithContentDescription("Vaporeon, defeated").assertIsDisplayed()
        compose.onNodeWithContentDescription("Vaporeon, fainted").assertDoesNotExist()
        compose.onNodeWithContentDescription("Opponent Vaporeon HP", useUnmergedTree = true).assertDoesNotExist()
        resources(1) // A WIN does not manufacture a zero-ball measurement.
        save("result")
        togglePlayback()
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
        assertEquals(sourceHash, sha256(source))
        File(context.cacheDir, "replay-blanche-playback-verification.txt").writeText(
            "archiveSha256=$sourceHash\narchiveImmutable=true\ncropPasses=${cropPasses.get()}\n" +
                "requireOcr=$requireOcr\nactualOcrModuleAvailable=$actualOcrModuleAvailable\n" +
                "ocrAvailabilityCheckedAt=preflight\n" +
                "actualCropIdentities=${actualCrops.size}\nspeciesResolverCalls=${speciesReads.get()}\n" +
                "moveResolverCalls=${moveReads.get()}\nplaybackSpeed=1\n")
        Unit
    }

    private fun displayedElapsedNanos(): Long {
        val time = Regex("(?:PLAY|PAUSE)\\s+(\\d+):(\\d+)\\.(\\d)")
        val match = compose.onAllNodes(hasText("PAUSE", substring = true) or hasText("PLAY", substring = true),
            useUnmergedTree = true).fetchSemanticsNodes().firstNotNullOfOrNull { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.firstNotNullOfOrNull { time.find(it.text) }
            } ?: error("The real replay LCD transport time is not visible")
        return (match.groupValues[1].toLong() * 600 + match.groupValues[2].toLong() * 10 +
            match.groupValues[3].toLong()) * 100 * MS
    }

    /** Real emulator dependency setup; never substitute guessed crop identities for unavailable OCR. */
    private suspend fun ensureTextRecognitionModule(context: Context, requireOcr: Boolean): Boolean = withContext(Dispatchers.IO) {
        val startedAt = SystemClock.elapsedRealtime()
        val deadline = startedAt + 60_000L
        val report = File(context.cacheDir, "replay-blanche-playback-mlkit-preflight.txt")
        val messages = mutableListOf("Real Play services text-recognition module preflight; requireOcr=$requireOcr")
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val client = ModuleInstall.getClient(context)
        fun record(message: String) {
            messages += "${SystemClock.elapsedRealtime() - startedAt}ms $message"
            report.writeText(messages.joinToString("\n") + "\n")
        }
        fun <T> await(task: Task<T>): T {
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining <= 0) throw TimeoutException("Text-recognition module preflight exceeded 60s")
            return Tasks.await(task, minOf(10_000L, remaining), TimeUnit.MILLISECONDS)
        }
        try {
            if (await(client.areModulesAvailable(recognizer)).areModulesAvailable()) {
                record("available=true; no install needed")
                return@withContext true
            }
            if (!requireOcr) {
                record("available=false; explicit fallback checks once, does not install, and lets real OCR abstain")
                return@withContext false
            }
            record("available=false; requesting actual optional module")
            val request = ModuleInstallRequest.newBuilder().addApi(recognizer).build()
            val installed = await(client.installModules(request))
            record("request acknowledged; alreadyInstalled=${installed.areModulesAlreadyInstalled()}")
            // Acknowledgement does not establish that download/installation has
            // completed. Check the actual module before starting any crop OCR.
            while (SystemClock.elapsedRealtime() < deadline) {
                if (await(client.areModulesAvailable(recognizer)).areModulesAvailable()) {
                    record("available=true; installation verified")
                    return@withContext true
                }
                record("available=false; waiting for download")
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining > 0) delay(minOf(1_000L, remaining))
            }
            throw TimeoutException("Text-recognition module was still unavailable after 60s")
        } catch (error: Exception) {
            record("FAILED ${error.javaClass.simpleName}: ${error.message}")
            if (!requireOcr) return@withContext false
            throw AssertionError("Real ML Kit text recognition is unavailable on this emulator; " +
                "the download/preflight failed before replay OCR. See ${report.name}. No crop results were fabricated.", error)
        } finally {
            recognizer.close()
        }
    }

    private fun sha256(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                hash.update(buffer, 0, read)
            }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }

    private fun moveKey(value: String) = value.uppercase().filter(Char::isLetterOrDigit)
    private companion object { const val MS = 1_000_000L; const val SECOND = 1_000_000_000L }
}
