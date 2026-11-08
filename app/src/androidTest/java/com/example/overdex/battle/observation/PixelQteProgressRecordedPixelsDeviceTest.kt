package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.archive.RealityArticleArchiveMapper
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.InMemoryTestimonyCustody
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.replay.replayQteProgressGrade
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.data.BattleCalibration
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.data.observation.SharedLatinTextRecognizer
import com.example.overdex.model.Pokemon
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Actual Pixel 4 pixels, not generated lettering. Both native screenshots and a
 * uniformly downscaled capture pass through the live crop and retained-artifact
 * OCR witness, then the same strict grade parser that replay uses.
 *
 * The supplied Excellent image was taken in the video viewer. Its viewer
 * controls remain in the test asset, outside the production QTE crop; it is not
 * an editable calibration reference in the application screenshot library.
 * These isolated capture clocks exercise custody only. They do not reconstruct
 * the original recording's unknown frame times or claim a measured shake range.
 */
class PixelQteProgressRecordedPixelsDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun niceIsReadAtNativeAndCaptureSize() = verify(Spec(
        "battle_samples/pixel4-20261108-152147-qte-nice.jpg", "NICE"))

    @Test fun greatIsReadAtNativeAndCaptureSize() = verify(Spec(
        "battle_samples/pixel4-20261108-152206-qte-great.jpg", "GREAT"))

    @Test fun excellentIsReadWithoutTheSurroundingVideoViewerControls() = verify(Spec(
        "pixel4_qte_progress/excellent-review.jpg", "EXCELLENT", testAsset = true))

    private fun verify(spec: Spec): Unit = runBlocking {
        check(Build.HARDWARE in setOf("ranchu", "goldfish") ||
            Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator")) {
            "Recorded-pixel OCR checks must run on a disposable emulator, never a user phone"
        }
        ensureTextRecognitionModule()
        SharedLatinTextRecognizer.warmUp()
        val bytes = (if (spec.testAsset) instrumentation.context else context)
            .assets.open(spec.path).use { it.readBytes() }
        val native = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) {
            "Cannot decode supplied Pixel screenshot ${spec.path}"
        }
        val report = JSONObject()
            .put("source", spec.path)
            .put("sourceSha256", sha256(bytes))
            .put("actualMlKit", true)
            .put("clockPolicy", "Isolated test captures; original frame times are unknown")
        val results = JSONArray()
        report.put("readings", results)
        val output = File(context.cacheDir, "pixel-qte-progress-${spec.grade.lowercase()}-ocr.json")
        try {
            assertEquals(1080, native.width)
            assertEquals(2280, native.height)
            for ((width, height) in listOf(1080 to 2280, 672 to 1419)) {
                val frame = if (width == native.width) native
                    else Bitmap.createScaledBitmap(native, width, height, true)
                try {
                    verifyFrame(spec.grade, frame, results, output, report)
                } finally {
                    if (frame !== native) frame.recycle()
                }
            }
            report.put("status", "passed")
        } catch (error: Throwable) {
            report.put("status", "failed").put("error", error.toString())
            throw error
        } finally {
            native.recycle()
            output.writeText(report.toString(2))
        }
    }

    private suspend fun verifyFrame(
        expectedGrade: String,
        frame: Bitmap,
        results: JSONArray,
        output: File,
        report: JSONObject,
    ) {
        val crop = requireNotNull(BattleCropContracts.qteProgressText.resolve(BattleCalibration(), frame))
        val temporary = File(context.cacheDir, "pixel-qte-artifacts-${System.nanoTime()}")
        check(temporary.mkdirs())
        val store = FileCropArtifactStore(temporary)
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match("pixel-qte-${expectedGrade.lowercase()}-${frame.width}", custody = custody,
            realityTimeline = timeline, pokemonKnowledge = object : PokemonKnowledge {
                override suspend fun getAllSpeciesNames() = emptySet<String>()
                override suspend fun getPokemonByName(name: String): Pokemon? = null
                override suspend fun getPokemonById(id: Int): Pokemon? = null
            })
        val witness = PersistedAnnouncementWitness(store,
            ObserverId(QTE_SOURCE, ObservationSource.SCREEN_CAPTURE),
            "QTE progress", BattleCropContracts.qteProgressText)
        try {
            val artifact = requireNotNull(store.preservePng(crop.bitmap))
            witness.start(match)
            val capturedAt = System.nanoTime()
            val perceivedAt = System.currentTimeMillis()
            val captureReceipt = custody.submitTestimony(SourceId("PIXEL_RECORDED_SAMPLE_CAPTURE"),
                CropCaptured(artifact, crop.provenance), perceivedAt, null, emptyList(), capturedAt)
            match.awaitAcceptedEvidencePublished()
            val captured = timeline.getArticles().single { it.sequenceNumber == captureReceipt.sequenceNumber }
            val accepted = withTimeout(30_000) {
                match.articles.first { it.sourceId.id == QTE_SOURCE &&
                    it.evidenceReferences?.contains(captured.id.value) == true }
            }
            match.awaitAcceptedEvidencePublished()
            val nativeReadings = timeline.getArticles().filter {
                it.sourceId.id == "${QTE_SOURCE}_OCR" &&
                    it.evidenceReferences == listOf(captured.id.value)
            }
            val grade = replayQteProgressGrade(RealityArticleArchiveMapper.map(accepted))
            results.put(JSONObject()
                .put("sourceWidth", frame.width).put("sourceHeight", frame.height)
                .put("crop", JSONObject().put("left", crop.provenance.bounds.left)
                    .put("top", crop.provenance.bounds.top).put("right", crop.provenance.bounds.right)
                    .put("bottom", crop.provenance.bounds.bottom))
                .put("acceptedText", (accepted.payload as RawTestimony).data.toString())
                .put("replayGrade", grade)
                .put("nativeReadings", JSONArray(nativeReadings.map { article ->
                    val reading = requireNotNull((article.payload as RawTestimony).ocr)
                    JSONObject().put("treatment", reading.treatment).put("rawText", reading.rawText)
                        .put("acceptedText", reading.acceptedText)
                        .put("wordDecisions", JSONArray(reading.words.map { word ->
                            JSONObject().put("text", word.text).put("decision", word.decision)
                        }))
                })))
            output.writeText(report.toString(2))

            assertEquals("Actual crop OCR did not reach the strict replay grade parser: ${results}",
                expectedGrade, grade)
            assertEquals(perceivedAt, accepted.perceivedAt)
            assertEquals(capturedAt, accepted.monotonicTimeNanos)
            assertTrue("The accepted grade must retain its native OCR evidence", nativeReadings.isNotEmpty())
            nativeReadings.forEach { reading ->
                assertNotNull((reading.payload as RawTestimony).ocr)
                assertEquals(capturedAt, reading.monotonicTimeNanos)
                assertTrue(accepted.evidenceReferences!!.contains(reading.id.value))
            }
        } finally {
            witness.stop()
            match.awaitAcceptedEvidencePublished()
            match.release()
            crop.bitmap.recycle()
            temporary.deleteRecursively()
        }
    }

    /** A missing native model is a failure, never an ignored or invented reading. */
    private suspend fun ensureTextRecognitionModule() = withContext(Dispatchers.IO) {
        val deadline = SystemClock.elapsedRealtime() + 60_000L
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val installer = ModuleInstall.getClient(context)
        fun <T> await(task: Task<T>): T {
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining <= 0) throw TimeoutException("Actual ML Kit module unavailable after 60 seconds")
            return Tasks.await(task, minOf(10_000L, remaining), TimeUnit.MILLISECONDS)
        }
        try {
            if (!await(installer.areModulesAvailable(recognizer)).areModulesAvailable()) {
                await(installer.installModules(ModuleInstallRequest.newBuilder().addApi(recognizer).build()))
                while (!await(installer.areModulesAvailable(recognizer)).areModulesAvailable()) delay(1_000)
            }
        } catch (error: Exception) {
            throw AssertionError("Actual ML Kit text module is required for the Pixel QTE pixel checks", error)
        } finally {
            recognizer.close()
        }
    }

    private data class Spec(val path: String, val grade: String, val testAsset: Boolean = false)

    private companion object {
        const val QTE_SOURCE = "QTE_PROGRESS_TEXT_WITNESS"
        fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
