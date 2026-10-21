package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.overdex.battle.archive.*
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.data.BattleCalibration
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.AnchorRegion
import com.example.overdex.model.Pokemon
import com.example.overdex.model.observation.*
import java.io.File
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QteTypeIconCaptureDeviceTest {
    @Test fun draftSamplesKeepOriginalPixelsCoordinatesTimesAndCompactEvidence() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = File(context.cacheDir, "qte-sample-test-${System.nanoTime()}")
        val store = FileCropArtifactStore(root)
        val frame = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) for (x in 0 until width) setPixel(x, y, if (x < 50) Color.RED else Color.BLUE)
        }
        val frames = Channel<CapturedVisualFrame>()
        val completed = Channel<CropCaptured>(Channel.UNLIMITED)
        val input = object : ObservationInput {
            override val source = SessionSource.LIVE_CAPTURE
            override suspend fun supply(onVisualData: suspend (Bitmap) -> Unit) {
                for (item in frames) onVisualData(item.bitmap)
            }
            override suspend fun supplyFrames(onFrame: suspend (CapturedVisualFrame) -> Unit) {
                for (item in frames) onFrame(item)
            }
        }
        val knowledge = object : PokemonKnowledge {
            override suspend fun getPokemonByName(name: String): Pokemon? = null
            override suspend fun getPokemonById(id: Int): Pokemon? = null
            override suspend fun getAllSpeciesNames() = emptySet<String>()
        }
        val timeline = InMemoryRealityTimeline()
        val custody = InMemoryTestimonyCustody()
        val match = Match("QTE_SAMPLE_TEST", custody = custody, realityTimeline = timeline, pokemonKnowledge = knowledge)
        val calibration = BattleCalibration()
        var now = System.nanoTime()
        val gate = QteTypeIconSampling({ calibration.qteTypeIconsRegion }, { now })
        val witness = CropCaptureWitness(input, calibration, BattleWitnessContracts.qteTypeIconsCapture, store,
            isEnabled = gate::isEnabled, acceptsFrame = gate::acceptsFrame,
            calibrationProvider = { calibration.copy(qteTypeIconsRegion = gate.region!!) },
            observerId = ObserverId("QTE_TYPE_ICONS_CAPTURE", ObservationSource.SCREEN_CAPTURE), name = "QTE sample test",
            captureIntervalNanos = QteTypeIconSampling.CAPTURE_INTERVAL_NANOS,
            onCaptured = { crop, _, at -> gate.captured(at); completed.trySend(crop) })
        try {
            gate.beginSession()
            assertTrue(gate.sample(AnchorRegion(.6f, .4f, .2f, .2f)))
            witness.start(match)
            frames.send(CapturedVisualFrame(frame, 999, now - 1)) // Cached frame must be rejected.
            val firstAt = now
            frames.send(CapturedVisualFrame(frame, 1000, firstAt))
            val first = withTimeout(5_000) { completed.receive() }
            assertEquals(BattleCropBounds(60, 40, 80, 60), first.cropProvenance.bounds)
            val blue = store.loadVerifiedPng(first.artifact, first.cropProvenance)!!
            try { assertEquals(20, blue.width); assertEquals(Color.BLUE, blue.getPixel(10, 10)) }
            finally { blue.recycle() }

            now += 12_000_000_000L
            assertTrue(gate.sample(AnchorRegion(.1f, .2f, .3f, .2f)))
            frames.send(CapturedVisualFrame(frame, 13000, now))
            val second = withTimeout(5_000) { completed.receive() }
            assertEquals(BattleCropBounds(10, 20, 40, 40), second.cropProvenance.bounds)
            val red = store.loadVerifiedPng(second.artifact, second.cropProvenance)!!
            try { assertEquals(Color.RED, red.getPixel(10, 10)) } finally { red.recycle() }

            val captured = custody.getRecords().filterIsInstance<TestimonyRecord>().filter { it.payload is CropCaptured }
            assertEquals(listOf(1000L, 13000L), captured.map { it.timestamp })
            assertEquals(listOf(firstAt, now), captured.map { it.monotonicTimeNanos })
            withTimeout(5_000) { while (timeline.getArticles().count { it.payload is CropCaptured } < 2) delay(10) }
            val compact = MatchArchiveArticleSelector.select(timeline.getArticles().map(RealityArticleArchiveMapper::map),
                MatchArchiveExportMode.COMPACT_CITED_EVIDENCE)
            assertEquals(2, compact.count { it.payload is ArchivedCropCaptured })
            assertTrue(listOf(first, second).all { it.artifact.relativePath.startsWith("artifacts/crops/") })
        } finally {
            witness.stop(); gate.stop(); match.release()
            frames.close(); completed.close(); frame.recycle(); root.deleteRecursively()
        }
    }
}
