package com.example.overdex.battle.observation

import android.graphics.BitmapFactory
import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.InMemoryTestimonyCustody
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.data.observation.OcrField
import com.example.overdex.data.observation.OcrHeightProfile
import com.example.overdex.data.observation.OcrPriority
import com.example.overdex.data.observation.OcrSourceContext
import com.example.overdex.data.observation.SharedLatinTextRecognizer
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
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

/**
 * Private, unmodified GO pixels supplied by the host; no screenshots are bundled.
 * The isolated Match and all capture clocks below are synthetic test input, not
 * a reconstruction of the original match's unknown full-frame capture times.
 */
class TransientAnnouncementRetentionDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun savedGetReadyRockTombAndNiceAreReadableByActualMlKit(): Unit = runBlocking {
        val directory = privateFixtureDirectory()
        ensureTextRecognitionModule()
        SharedLatinTextRecognizer.warmUp()
        val report = JSONObject().put("clockPolicy", CLOCK_POLICY).put("actualMlKit", true)
        val results = JSONArray()
        report.put("crops", results)
        val output = File(context.cacheDir, "transient-announcement-native-ocr.json")
        try {
            for (spec in SPECS) {
                val bitmap = BitmapFactory.decodeFile(verifiedFixture(directory, spec).absolutePath)
                    ?: error("Cannot decode ${spec.filename}")
                try {
                    assertEquals(spec.bounds.right - spec.bounds.left, bitmap.width)
                    assertEquals(spec.bounds.bottom - spec.bounds.top, bitmap.height)
                    val startedAt = System.nanoTime()
                    val readings = AnnouncementRecognizer.recognizeEvidence(bitmap,
                        OcrSourceContext(672), OcrHeightProfile(OcrField.ANNOUNCEMENT))
                    results.put(JSONObject().put("file", spec.filename).put("sha256", spec.sha256)
                        .put("sourceWidth", 672).put("sourceHeight", 1493)
                        .put("elapsedMillis", (System.nanoTime() - startedAt) / MS)
                        .put("readings", readingsJson(readings)))
                    output.writeText(report.toString(2))
                    assertTrue("No real OCR treatment returned for ${spec.filename}", readings.isNotEmpty())
                    spec.expectedText?.let { expected ->
                        assertTrue("Expected $expected in actual OCR of ${spec.filename}: $readings",
                            readings.any { normalize(it.acceptedText).contains(normalize(expected)) })
                    }
                    // Companion controls need not be perfectly empty. Preserve what
                    // the native reader returns instead of prescribing its output.
                } finally { bitmap.recycle() }
            }
            report.put("status", "passed")
        } catch (error: Throwable) {
            report.put("status", "failed").put("error", error.toString())
            throw error
        } finally { output.writeText(report.toString(2)) }
    }

    @Test fun productionWitnessRetainsBriefTextAcrossBlockedSharedOcrAdmission(): Unit = runBlocking {
        val directory = privateFixtureDirectory()
        ensureTextRecognitionModule()
        SharedLatinTextRecognizer.warmUp()
        val temporary = File(context.cacheDir, "transient-announcement-witness-${System.nanoTime()}")
        check(temporary.mkdirs())
        val store = FileCropArtifactStore(temporary)
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match("synthetic-transient-retention", custody = custody, realityTimeline = timeline,
            pokemonKnowledge = object : PokemonKnowledge {
                override suspend fun getAllSpeciesNames() = setOf("Camerupt")
                override suspend fun getPokemonByName(name: String): Pokemon? = null
                override suspend fun getPokemonById(id: Int): Pokemon? = null
            })
        val announcement = PersistedAnnouncementWitness(store)
        val qte = PersistedAnnouncementWitness(store,
            ObserverId("QTE_PROGRESS_TEXT_WITNESS", ObservationSource.SCREEN_CAPTURE),
            "QTE progress", BattleCropContracts.qteProgressText)
        val releaseBackend = CompletableDeferred<Unit>()
        val backendEntered = CompletableDeferred<Unit>()
        val schedulerEvents = CopyOnWriteArrayList<String>()
        val blocker = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            SharedLatinTextRecognizer.withReadingTurn(OcrPriority.NORMAL) {
                schedulerEvents += "blocker-entered"
                backendEntered.complete(Unit)
                releaseBackend.await()
                schedulerEvents += "blocker-released"
            }
        }
        var normalWaiter: kotlinx.coroutines.Job? = null
        val report = JSONObject().put("clockPolicy", CLOCK_POLICY).put("actualMlKit", true)
        val output = File(context.cacheDir, "transient-announcement-retention.json")
        val captures = mutableListOf<RealityArticle>()
        try {
            withTimeout(5_000) { backendEntered.await() }
            announcement.start(match)
            qte.start(match)
            // These source clocks are deliberately recent, distinct synthetic
            // test captures, generated only after the real model is warmed.
            val sourceBase = System.nanoTime() - 100 * MS
            suspend fun capture(filename: String, offsetMillis: Long): RealityArticle {
                val spec = SPECS.single { it.filename == filename }
                val bytes = verifiedFixture(directory, spec).readBytes()
                val reference = requireNotNull(store.preserveEncodedPng(bytes))
                val capturedAt = sourceBase + offsetMillis * MS
                val receipt = custody.submitTestimony(SourceId("SYNTHETIC_RETAINED_CROP"),
                    CropCaptured(reference, BattleCropProvenance(spec.cropName, 672, 1493, spec.bounds)),
                    1_000_000L + offsetMillis, null, emptyList(), capturedAt)
                match.awaitAcceptedEvidencePublished()
                return timeline.getArticles().single { it.sequenceNumber == receipt.sequenceNumber }
                    .also { captures += it }
            }

            val ready = capture("get-ready-announcement-exact.png", 0)
            await { SharedLatinTextRecognizer.waitingCount(OcrPriority.TRANSIENT) == 1 }
            val rockTomb = capture("rock-tomb-announcement-exact.png", 10)
            capture("nice-announcement-exact.png", 20)
            val nice = capture("nice-qte-exact.png", 30)
            capture("rock-tomb-qte-exact.png", 40)
            await { SharedLatinTextRecognizer.waitingCount(OcrPriority.TRANSIENT) == 2 }
            normalWaiter = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                SharedLatinTextRecognizer.withReadingTurn(OcrPriority.NORMAL) {
                    schedulerEvents += "normal-fairness-turn"
                }
            }
            await { SharedLatinTextRecognizer.waitingCount(OcrPriority.NORMAL) == 1 }
            // Native admission stays blocked for two seconds, allowing the
            // production asynchronous artifact probe to inspect this tiny burst.
            // The pure queue tests separately use deterministic inspection gates.
            delay(2_000)
            assertTrue(timeline.getArticles().none { it.sourceId.id.endsWith("_WITNESS_OCR") })
            releaseBackend.complete(Unit)
            blocker.join()

            val expected = listOf(
                Triple(ready, "ANNOUNCEMENT_WITNESS", "Get Ready!"),
                Triple(rockTomb, "ANNOUNCEMENT_WITNESS", "Camerupt used Rock Tomb!"),
                Triple(nice, "QTE_PROGRESS_TEXT_WITNESS", "NICE!"),
            )
            val accepted = expected.map { (source, witnessId, expectedText) ->
                await(30_000) { timeline.getArticles().any {
                    it.sourceId.id == witnessId && it.evidenceReferences?.contains(source.id.value) == true
                } }
                match.awaitAcceptedEvidencePublished()
                val article = timeline.getArticles().single {
                    it.sourceId.id == witnessId && it.evidenceReferences?.contains(source.id.value) == true
                }
                assertTrue("$witnessId did not read $expectedText: ${article.payload}",
                    normalize((article.payload as RawTestimony).data.toString()).contains(normalize(expectedText)))
                assertEquals(source.perceivedAt, article.perceivedAt)
                assertEquals(source.monotonicTimeNanos, article.monotonicTimeNanos)
                val originals = timeline.getArticles().filter {
                    it.sourceId.id == "${witnessId}_OCR" &&
                        it.evidenceReferences == listOf(source.id.value)
                }
                assertTrue("No native source readings for ${source.id.value}", originals.isNotEmpty())
                originals.forEach { raw ->
                    assertEquals(source.perceivedAt, raw.perceivedAt)
                    assertEquals(source.monotonicTimeNanos, raw.monotonicTimeNanos)
                    assertNotNull((raw.payload as RawTestimony).ocr)
                    assertTrue(article.evidenceReferences!!.contains(raw.id.value))
                }
                val latency = timeline.getArticles().single {
                    it.sourceId.id == "${witnessId}_LATENCY" && it.monotonicTimeNanos == source.monotonicTimeNanos
                }
                assertTrue((latency.payload as RawTestimony).data.toString().contains("selection=TEXT_KEYFRAME"))
                article
            }
            withTimeout(5_000) { requireNotNull(normalWaiter).join() }
            val orderedAnnouncement = accepted.filter { it.sourceId.id == "ANNOUNCEMENT_WITNESS" }
                .sortedBy { it.sequenceNumber }
            assertEquals(listOf(ready.monotonicTimeNanos, rockTomb.monotonicTimeNanos),
                orderedAnnouncement.map { it.monotonicTimeNanos })
            assertTrue(schedulerEvents.contains("normal-fairness-turn"))
            report.put("status", "passed").put("selected", JSONArray(accepted.map(::articleJson)))
        } catch (error: Throwable) {
            report.put("status", "failed").put("error", error.toString())
            throw error
        } finally {
            releaseBackend.complete(Unit)
            blocker.cancelAndJoin()
            normalWaiter?.cancelAndJoin()
            announcement.stop()
            qte.stop()
            match.awaitAcceptedEvidencePublished()
            report.put("schedulerEvents", JSONArray(schedulerEvents))
            report.put("captures", JSONArray(captures.map(::articleJson)))
            report.put("witnessArticles", JSONArray(timeline.getArticles().filter {
                it.sourceId.id.startsWith("ANNOUNCEMENT_WITNESS") || it.sourceId.id.startsWith("QTE_PROGRESS_TEXT_WITNESS")
            }.map(::articleJson)))
            output.writeText(report.toString(2))
            match.release()
            temporary.deleteRecursively()
        }
    }

    private fun privateFixtureDirectory(): File {
        check(Build.HARDWARE in setOf("ranchu", "goldfish") || Build.FINGERPRINT.contains("generic") ||
            Build.MODEL.contains("Emulator")) {
            "This isolated recognition test must run on a disposable emulator, never a user phone"
        }
        val argument = InstrumentationRegistry.getArguments().getString("fixtureDir")
            ?: error("Supply fixtureDir pointing to the private recognition-gap-fixture directory")
        val directory = File(argument).canonicalFile
        val privateRoot = File(context.applicationInfo.dataDir).canonicalFile
        check(directory.path.startsWith(privateRoot.path + File.separator) && directory.isDirectory) {
            "fixtureDir must be inside this emulator app's private data directory: $directory"
        }
        return directory
    }

    private fun verifiedFixture(directory: File, spec: Spec): File = File(directory, spec.filename).also { file ->
        check(file.isFile && file.canonicalFile.parentFile == directory) { "Missing private fixture ${spec.filename}" }
        check(sha256(file.readBytes()) == spec.sha256) { "Changed source pixels in ${spec.filename}" }
    }

    /** Missing real ML Kit is a failure, never a skip or a fabricated OCR result. */
    private suspend fun ensureTextRecognitionModule() = withContext(Dispatchers.IO) {
        val startedAt = SystemClock.elapsedRealtime()
        val deadline = startedAt + 60_000L
        val report = File(context.cacheDir, "transient-announcement-mlkit-preflight.txt")
        val messages = mutableListOf("Actual emulator ML Kit module preflight; required=true")
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val client = ModuleInstall.getClient(context)
        fun record(message: String) {
            messages += "${SystemClock.elapsedRealtime() - startedAt}ms $message"
            report.writeText(messages.joinToString("\n") + "\n")
        }
        fun <T> awaitTask(task: Task<T>): T {
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining <= 0) throw TimeoutException("ML Kit preflight exceeded 60 seconds")
            return Tasks.await(task, minOf(10_000L, remaining), TimeUnit.MILLISECONDS)
        }
        try {
            if (!awaitTask(client.areModulesAvailable(recognizer)).areModulesAvailable()) {
                record("available=false; requesting actual optional module")
                val installed = awaitTask(client.installModules(ModuleInstallRequest.newBuilder().addApi(recognizer).build()))
                record("request acknowledged; alreadyInstalled=${installed.areModulesAlreadyInstalled()}")
                while (!awaitTask(client.areModulesAvailable(recognizer)).areModulesAvailable()) {
                    record("available=false; awaiting download")
                    delay(1_000)
                }
            }
            record("available=true; actual module verified")
        } catch (error: Exception) {
            record("FAILED ${error.javaClass.simpleName}: ${error.message}")
            throw AssertionError("Actual ML Kit is unavailable on this emulator. See ${report.name}; " +
                "no crop readings were fabricated.", error)
        } finally { recognizer.close() }
    }

    private fun readingsJson(readings: List<OcrTextEvidence>) = JSONArray(readings.map { reading ->
        JSONObject().put("treatment", reading.treatment).put("rawText", reading.rawText)
            .put("acceptedText", reading.acceptedText)
            .put("sourceFrameWidth", reading.sourceFrameWidth)
            .put("words", JSONArray(reading.words.map { word ->
                JSONObject().put("text", word.text).put("decision", word.decision)
                    .put("bounds", word.bounds?.let {
                        JSONArray(listOf(it.left, it.top, it.right, it.bottom))
                    })
            }))
    })

    private fun articleJson(article: RealityArticle): JSONObject = JSONObject()
        .put("id", article.id.value).put("sourceId", article.sourceId.id)
        .put("perceivedAt", article.perceivedAt).put("monotonicTimeNanos", article.monotonicTimeNanos)
        .put("sequenceNumber", article.sequenceNumber).put("evidenceReferences", JSONArray(article.evidenceReferences.orEmpty()))
        .put("data", (article.payload as? RawTestimony)?.data?.toString())
        .put("ocr", (article.payload as? RawTestimony)?.ocr?.let { readingsJson(listOf(it)).getJSONObject(0) })
        .put("cropName", (article.payload as? CropCaptured)?.cropProvenance?.cropName)

    private suspend fun await(timeoutMillis: Long = 5_000, condition: () -> Boolean) = withTimeout(timeoutMillis) {
        while (!condition()) delay(10)
    }

    private data class Spec(val filename: String, val sha256: String, val cropName: String,
        val bounds: BattleCropBounds, val expectedText: String? = null)

    private companion object {
        const val MS = 1_000_000L
        const val CLOCK_POLICY = "All submitted captures and clocks are synthetic; actual source pixels have no known canonical full-frame timestamp"
        fun normalize(text: String) = text.uppercase().replace(Regex("[^A-Z0-9]"), "")
        fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val ANNOUNCEMENT = BattleCropBounds(50, 326, 622, 375)
        val QTE = BattleCropBounds(80, 388, 591, 500)
        val SPECS = listOf(
            Spec("get-ready-announcement-exact.png", "9d73028ca5cbc91d450f8be62998fddf64e99926a40612d20d3fa9b41fa950d7",
                "AnnouncementCrop", ANNOUNCEMENT, "Get Ready!"),
            Spec("get-ready-qte-exact.png", "22a0ce11d8ace6363a5ee9816fd099b1fc0444d78a4bafdab191e96b3dfba541",
                "QteProgressTextCrop", QTE),
            Spec("nice-announcement-exact.png", "bb952659750e350c97ac232d316cc0bac67431544847072a882180865615213d",
                "AnnouncementCrop", ANNOUNCEMENT),
            Spec("nice-qte-exact.png", "b7c9660fb25d255e32f69939df830cb2f512797fef56ef557d9558d9f603a7ac",
                "QteProgressTextCrop", QTE, "NICE!"),
            Spec("rock-tomb-announcement-exact.png", "34a0da79dc7e38cd47d5f3569cd01b8257d84c7a8c914b058a49146d3a275ab2",
                "AnnouncementCrop", ANNOUNCEMENT, "Camerupt used Rock Tomb!"),
            Spec("rock-tomb-qte-exact.png", "d0e3f025f24a5a0a61a78c4dca189841cef2f627832ead02a76fbab319202a5a",
                "QteProgressTextCrop", QTE),
        )
    }
}
