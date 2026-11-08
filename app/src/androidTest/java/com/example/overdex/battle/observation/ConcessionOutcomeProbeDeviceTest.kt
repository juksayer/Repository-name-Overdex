package com.example.overdex.battle.observation

import android.graphics.BitmapFactory
import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.InMemoryTestimonyCustody
import com.example.overdex.battle.custody.MatchEnded
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.data.BattleCalibration
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.data.observation.OcrPriority
import com.example.overdex.data.observation.OutcomeTextRecognizer
import com.example.overdex.data.observation.SharedLatinTextRecognizer
import com.example.overdex.model.AnchorRegion
import com.example.overdex.model.BattleResult
import com.example.overdex.model.Pokemon
import com.example.overdex.model.observation.OcrTextEvidence
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Actual private concession pixels; synthetic gate state and capture clocks only. */
class ConcessionOutcomeProbeDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun savedCalibrationContainsTheCompleteConcessionResultAndActualOcrReadsIt(): Unit = runBlocking {
        val fixtures = privateFixtures()
        ensureTextRecognitionModule()
        OutcomeTextRecognizer.warmUp()
        val source = requireNotNull(BitmapFactory.decodeFile(verified(fixtures, "source-you-win.png", SOURCE_HASH).path))
        val expected = requireNotNull(BitmapFactory.decodeFile(verified(fixtures, "you-win-exact.png", WIN_HASH).path))
        val resolved = requireNotNull(BattleCropContracts.matchOutcome.resolve(BattleCalibration(
            matchOutcomeRegion = AnchorRegion(.15000005f, .42f, .69499993f, .17500003f)), source))
        val output = File(context.cacheDir, "result-concession-crop-ocr.json")
        val report = JSONObject().put("sourceFrameSha256", SOURCE_HASH).put("clockPolicy", CLOCK_POLICY)
        try {
            assertEquals(672, source.width)
            assertEquals(1493, source.height)
            assertEquals(PROVENANCE, resolved.provenance)
            assertEquals(468, expected.width)
            assertEquals(261, expected.height)
            val expectedPixels = IntArray(expected.width * expected.height)
            val actualPixels = IntArray(expectedPixels.size)
            expected.getPixels(expectedPixels, 0, expected.width, 0, 0, expected.width, expected.height)
            resolved.bitmap.getPixels(actualPixels, 0, expected.width, 0, 0, expected.width, expected.height)
            assertArrayEquals("The saved normalized calibration must select these exact actual source pixels",
                expectedPixels, actualPixels)
            val readings = OutcomeTextRecognizer.withReadingTurn(OcrPriority.NORMAL) {
                recognizeCandidates(resolved.bitmap) { MatchOutcomeTextResolver.resolve(it) != null }
            }
            report.put("actualMlKit", true).put("readings", readingsJson(readings))
            assertTrue("Actual native readings must contain the exact YOU WIN letters: $readings",
                readings.any { letters(it.rawText) == "YOUWIN" })
            report.put("status", "passed").put("cropPixelsMatch", true)
        } catch (error: Throwable) {
            report.put("status", "failed").put("error", error.toString())
            throw error
        } finally {
            output.writeText(report.toString(2))
            resolved.bitmap.recycle(); source.recycle(); expected.recycle()
        }
    }

    @Test fun routineProbeWithReservesRetainsTheWinBeforeBlankAndProducesOneSourceTimedMatchEnd(): Unit = runBlocking {
        val fixtures = privateFixtures()
        ensureTextRecognitionModule()
        OutcomeTextRecognizer.warmUp()
        val temporary = File(context.cacheDir, "result-concession-probe-${System.nanoTime()}")
        check(temporary.mkdirs())
        val store = FileCropArtifactStore(temporary)
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match("SYNTHETIC_CONCESSION_PROBE", custody = custody, realityTimeline = timeline,
            pokemonKnowledge = object : PokemonKnowledge {
                override suspend fun getPokemonByName(name: String): Pokemon? = null
                override suspend fun getPokemonById(id: Int): Pokemon? = null
                override suspend fun getAllSpeciesNames() = emptySet<String>()
            })
        val session = DroidballSession(match)
        val text = PersistedOutcomeTextWitness(store)
        val phrase = PersistedOutcomePhraseWitness(
            { MatchOutcomeTextResolver.resolve(it) == MatchOutcomePhrase.WIN },
            ObserverId("YOU_WIN_WITNESS", ObservationSource.SCREEN_CAPTURE), "You Win")
        // Both capture clocks are deliberately synthetic, recent and two seconds
        // apart. They do not claim the retained loose frame's original event time.
        val sourceBase = System.nanoTime() - 2_100 * MS
        val gateNow = AtomicLong(sourceBase)
        val gate = MatchOutcomeCaptureGate(gateNow::get)
        gate.start(match)
        gate.observePlayerBallCount(sourceBase, 3)
        gate.observeOpponentBallCount(sourceBase, 3)
        assertFalse("Team Select reserves alone cannot start result observation", gate.isProbeEnabled())
        gate.observeMatchStarted(sourceBase - 5_000 * MS)
        gate.observeActiveHp(ActivePokemonSide.PLAYER, sourceBase, .9f)
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, sourceBase, .8f)
        assertFalse("Healthy teams with reserves must not use urgent result OCR", gate.isEnabled())
        assertTrue("Concessions still require the independent routine check", gate.isProbeEnabled())
        assertEquals(2_000 * MS, MatchOutcomeCaptureGate.PROBE_CAPTURE_INTERVAL_NANOS)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val blocker = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            SharedLatinTextRecognizer.withReadingTurn(OcrPriority.NORMAL) {
                entered.complete(Unit)
                release.await()
            }
        }
        val report = JSONObject().put("clockPolicy", CLOCK_POLICY)
            .put("playerBalls", 3).put("opponentBalls", 3).put("urgentGateEnabled", false)
            .put("syntheticCaptureIntervalNanos", 2_000 * MS)
        val output = File(context.cacheDir, "result-concession-probe.json")
        try {
            withTimeout(5_000) { entered.await() }
            phrase.start(match); text.start(match)
            suspend fun capture(filename: String, hash: String, offsetMillis: Long): RealityArticle {
                check(gate.isProbeEnabled())
                val artifact = requireNotNull(store.preserveEncodedPng(verified(fixtures, filename, hash).readBytes()))
                val receipt = custody.submitTestimony(SourceId(BattleWitnessContracts.matchOutcomeProbeCapture.witnessId),
                    CropCaptured(artifact, PROVENANCE), 100_000L + offsetMillis,
                    null, emptyList(), sourceBase + offsetMillis * MS)
                match.awaitAcceptedEvidencePublished()
                return timeline.getArticles().single { it.sequenceNumber == receipt.sequenceNumber }
            }
            val win = capture("you-win-exact.png", WIN_HASH, 0)
            await { SharedLatinTextRecognizer.waitingCount(OcrPriority.NORMAL) == 1 }
            assertEquals("The routine source must not occupy the urgent OCR lane", 0,
                SharedLatinTextRecognizer.waitingCount(OcrPriority.URGENT))
            gateNow.addAndGet(2_000 * MS)
            val blank = capture("battle-control-exact.png", CONTROL_HASH, 2_000)
            // Allow the production asynchronous artifact inspection to finish
            // before admission. Unit tests separately gate this deterministically.
            delay(500)
            assertTrue(timeline.getArticles().none { it.payload is MatchEnded })
            release.complete(Unit)
            blocker.join()
            await(30_000) { session.phase.value == DroidballSessionPhase.RESULT }
            match.awaitAcceptedEvidencePublished()
            val articles = timeline.getArticles()
            val end = articles.single { it.payload is MatchEnded }
            assertEquals(MatchEnded(BattleResult.WIN), end.payload)
            val accepted = articles.single { it.id == end.predecessorIds.single() }
            assertEquals("YOU_WIN_WITNESS", accepted.sourceId.id)
            val native = articles.single { it.id.value == accepted.evidenceReferences!!.single() }
            assertEquals("MATCH_OUTCOME_TEXT_WITNESS", native.sourceId.id)
            assertEquals(listOf(win.id.value), native.evidenceReferences)
            val nativePayload = native.payload as RawTestimony
            assertNotNull(nativePayload.ocr)
            assertEquals(MatchOutcomePhrase.WIN, MatchOutcomeTextResolver.resolve(nativePayload.data.toString()))
            for (article in listOf(native, accepted, end)) {
                assertEquals(win.monotonicTimeNanos, article.monotonicTimeNanos)
                assertEquals(win.perceivedAt, article.perceivedAt)
            }
            assertNotEquals(blank.monotonicTimeNanos, end.monotonicTimeNanos)
            val diagnostics = articles.filter { it.sourceId.id == "MATCH_OUTCOME_TEXT_WITNESS_LATENCY" }
            assertEquals("The decisive first probe should cancel the blank and every reader lane", 1, diagnostics.size)
            val diagnostic = (diagnostics.single().payload as RawTestimony).data.toString()
            assertTrue(diagnostic, diagnostic.contains("lane=PROBE priority=NORMAL"))
            assertTrue(diagnostic, diagnostic.contains("selection=TEXT_KEYFRAME"))
            await { !gate.isProbeEnabled() && !gate.isEnabled() }
            report.put("status", "passed").put("actualMlKit", true)
                .put("originalSourceClocksPreserved", true).put("diagnostic", diagnostic)
        } catch (error: Throwable) {
            report.put("status", "failed").put("error", error.toString())
            throw error
        } finally {
            release.complete(Unit)
            blocker.cancelAndJoin()
            text.stop(); phrase.stop(); gate.stop()
            match.awaitAcceptedEvidencePublished()
            report.put("articles", JSONArray(timeline.getArticles().map(::articleJson)))
            output.writeText(report.toString(2))
            session.end(); match.release(); temporary.deleteRecursively()
        }
    }

    private fun privateFixtures(): File {
        check(Build.HARDWARE in setOf("ranchu", "goldfish") || Build.FINGERPRINT.contains("generic") ||
            Build.MODEL.contains("Emulator")) { "Run this isolated result test only on a disposable emulator" }
        val argument = InstrumentationRegistry.getArguments().getString("fixtureDir")
            ?: error("Supply fixtureDir for the private result-probe-fixture directory")
        val directory = File(argument).canonicalFile
        check(directory.isDirectory && directory.path.startsWith(File(context.applicationInfo.dataDir).canonicalPath + File.separator))
        return directory
    }

    private fun verified(directory: File, name: String, hash: String) = File(directory, name).also {
        check(it.isFile && it.canonicalFile.parentFile == directory && sha256(it.readBytes()) == hash) {
            "Missing or changed actual source fixture $name"
        }
    }

    /** Verify the actual optional model; an unavailable download is a test failure. */
    private suspend fun ensureTextRecognitionModule() = withContext(Dispatchers.IO) {
        val started = SystemClock.elapsedRealtime()
        val deadline = started + 60_000
        val output = File(context.cacheDir, "result-concession-mlkit-preflight.txt")
        val lines = mutableListOf("Required actual ML Kit module; no bundled or fabricated substitute")
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val client = ModuleInstall.getClient(context)
        fun record(message: String) {
            lines += "${SystemClock.elapsedRealtime() - started}ms $message"
            output.writeText(lines.joinToString("\n") + "\n")
        }
        fun <T> awaitTask(task: Task<T>): T {
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining <= 0) throw TimeoutException("Actual text module unavailable after 60 seconds")
            return Tasks.await(task, minOf(10_000L, remaining), TimeUnit.MILLISECONDS)
        }
        try {
            if (!awaitTask(client.areModulesAvailable(recognizer)).areModulesAvailable()) {
                record("available=false; requesting actual optional module")
                awaitTask(client.installModules(ModuleInstallRequest.newBuilder().addApi(recognizer).build()))
                while (!awaitTask(client.areModulesAvailable(recognizer)).areModulesAvailable()) delay(1_000)
            }
            record("available=true; actual module verified")
        } catch (error: Exception) {
            record("FAILED ${error.javaClass.simpleName}: ${error.message}")
            throw AssertionError("Actual ML Kit preflight failed; see ${output.name}. No OCR results were substituted.", error)
        } finally { recognizer.close() }
    }

    private fun readingsJson(readings: List<OcrTextEvidence>) = JSONArray(readings.map {
        JSONObject().put("treatment", it.treatment).put("rawText", it.rawText).put("acceptedText", it.acceptedText)
    })

    private fun articleJson(article: RealityArticle) = JSONObject().put("id", article.id.value)
        .put("sourceId", article.sourceId.id).put("perceivedAt", article.perceivedAt)
        .put("monotonicTimeNanos", article.monotonicTimeNanos).put("sequenceNumber", article.sequenceNumber)
        .put("predecessorIds", JSONArray(article.predecessorIds.map { it.value }))
        .put("evidenceReferences", JSONArray(article.evidenceReferences.orEmpty()))
        .put("payload", article.payload.toString())

    private suspend fun await(timeoutMillis: Long = 5_000, condition: () -> Boolean) = withTimeout(timeoutMillis) {
        while (!condition()) delay(10)
    }

    private companion object {
        const val MS = 1_000_000L
        const val CLOCK_POLICY = "Gate state and capture clocks are synthetic; original loose YOU WIN frame has no known canonical capture time"
        const val SOURCE_HASH = "c6076aafdb5a11ebbc3a1bd8c70bbfc43c68a691af4689fea9b4271f5d59956b"
        const val WIN_HASH = "3246fc83b57ac4b507250d5ad3c1e1e3136f423fe5ce68a7da78f5e3709f418e"
        const val CONTROL_HASH = "182afff7044988b57bf5080594654da37b0217ed6e3f86084fa07185c98f78ae"
        val PROVENANCE = BattleCropProvenance("MatchOutcomeCrop", 672, 1493, BattleCropBounds(100, 627, 568, 888))
        fun letters(text: String) = text.uppercase().filter(Char::isLetter)
        fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
