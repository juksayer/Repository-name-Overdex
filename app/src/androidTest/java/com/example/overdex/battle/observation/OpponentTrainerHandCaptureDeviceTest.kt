package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.overdex.battle.archive.ArchivedCropCaptured
import com.example.overdex.battle.archive.MatchArchiveArticleSelector
import com.example.overdex.battle.archive.MatchArchiveExportMode
import com.example.overdex.battle.archive.RealityArticleArchiveMapper
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.GetReadyWitnessed
import com.example.overdex.battle.custody.InMemoryTestimonyCustody
import com.example.overdex.battle.custody.TestimonyRecord
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.data.BattleCalibration
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.AnchorRegion
import com.example.overdex.model.Pokemon
import com.example.overdex.model.observation.CapturedVisualFrame
import com.example.overdex.model.observation.ObservationInput
import com.example.overdex.model.observation.SessionSource
import java.io.File
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises pixel custody only. These deterministic colors do not establish arm detection. */
@RunWith(AndroidJUnit4::class)
class OpponentTrainerHandCaptureDeviceTest {
    @Test
    fun manualSamplesKeepTinyDefaultAndMovedDraftWithSourceTimesAndCompactEvidence() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = File(context.cacheDir, "opponent-hand-sample-test-${System.nanoTime()}")
        val store = FileCropArtifactStore(root)
        val frame = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.DKGRAY)
            val canvas = Canvas(this)
            canvas.drawRect(1045f, 600f, 1070f, 680f, Paint().apply { color = Color.RED })
            canvas.drawRect(540f, 1200f, 675f, 1440f, Paint().apply { color = Color.GREEN })
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
        val match = Match("OPPONENT_HAND_SAMPLE_TEST", custody = custody, realityTimeline = timeline,
            pokemonKnowledge = knowledge)
        val calibration = BattleCalibration()
        var now = 1_000_000_000L
        val gate = QteTypeIconSampling(
            savedRegion = { calibration.opponentTrainerHandRegion }, now = { now },
            followsQte = false, sampleLimit = 100,
            observerId = ObserverId("OPPONENT_TRAINER_HAND_SAMPLE_GATE", ObservationSource.SCREEN_CAPTURE),
            name = "Opponent trainer hand sample test gate"
        )
        val witness = CropCaptureWitness(input, calibration, BattleWitnessContracts.opponentTrainerHandCapture, store,
            isEnabled = gate::isEnabled, acceptsFrame = gate::acceptsFrame,
            calibrationProvider = { calibration.copy(opponentTrainerHandRegion = requireNotNull(gate.region)) },
            observerId = ObserverId("OPPONENT_TRAINER_HAND_CAPTURE", ObservationSource.SCREEN_CAPTURE),
            name = "Opponent trainer hand sample test", captureIntervalNanos = 100_000_000L,
            onCaptured = { crop, _, at -> gate.captured(at); completed.trySend(crop) })
        try {
            gate.beginSession()
            gate.observe(GetReadyWitnessed, now)
            assertFalse("QTE must not start a hand experiment", gate.isEnabled())
            assertTrue(gate.sample(calibration.opponentTrainerHandRegion))
            witness.start(match)

            val firstAt = now
            frames.send(CapturedVisualFrame(frame, 999, firstAt - 1)) // A cached frame is not a new sample.
            frames.send(CapturedVisualFrame(frame, 1000, firstAt))
            val first = withTimeout(5_000) { completed.receive() }
            assertEquals(BattleCropBounds(1045, 600, 1070, 680), first.cropProvenance.bounds)
            assertEquals(1080, first.cropProvenance.sourceWidth)
            assertEquals(2400, first.cropProvenance.sourceHeight)
            val tiny = requireNotNull(store.loadVerifiedPng(first.artifact, first.cropProvenance))
            try {
                assertEquals(25, tiny.width)
                assertEquals(80, tiny.height)
                assertEquals(Color.RED, tiny.getPixel(12, 40))
            } finally { tiny.recycle() }

            // Source frames within the 100 ms cadence do not add a second raw record.
            now = firstAt + 99_999_999L
            frames.send(CapturedVisualFrame(frame, 1099, now))
            for (sample in 1 until 100) {
                now = firstAt + sample * 100_000_000L
                frames.send(CapturedVisualFrame(frame, 1000L + sample * 100L, now))
                withTimeout(5_000) { completed.receive() }
            }
            assertEquals(100, gate.status.value.saved)
            assertFalse("The manual experiment stops at 100 samples", gate.isEnabled())

            // Moving the draft starts a separate request; it does not rewrite the saved calibration.
            now = firstAt + 12_000_000_000L
            val movedDraft = AnchorRegion(.5f, .5f, .125f, .1f)
            assertTrue(gate.sample(movedDraft))
            frames.send(CapturedVisualFrame(frame, 13000, now))
            val moved = withTimeout(5_000) { completed.receive() }
            assertEquals(BattleCropBounds(540, 1200, 675, 1440), moved.cropProvenance.bounds)
            assertEquals(BattleCropBounds(1045, 600, 1070, 680),
                BattleCropContracts.opponentTrainerHand.resolve(calibration, frame)!!.let { crop ->
                    try { crop.provenance.bounds } finally { crop.bitmap.recycle() }
                })
            val movedPixels = requireNotNull(store.loadVerifiedPng(moved.artifact, moved.cropProvenance))
            try { assertEquals(Color.GREEN, movedPixels.getPixel(60, 100)) } finally { movedPixels.recycle() }

            val captured = custody.getRecords().filterIsInstance<TestimonyRecord>().filter { it.payload is CropCaptured }
            assertEquals(101, captured.size)
            assertEquals((0 until 100).map { 1000L + it * 100L } + 13000L, captured.map { it.timestamp })
            assertEquals((0 until 100).map { firstAt + it * 100_000_000L } + now,
                captured.map { it.monotonicTimeNanos })
            withTimeout(5_000) {
                while (timeline.getArticles().count { it.payload is CropCaptured } < 101) delay(10)
            }
            val compact = MatchArchiveArticleSelector.select(timeline.getArticles().map(RealityArticleArchiveMapper::map),
                MatchArchiveExportMode.COMPACT_CITED_EVIDENCE)
            val raw = compact.filter { it.payload is ArchivedCropCaptured }
            assertEquals(101, raw.size)
            assertTrue(raw.all { it.sourceId == "OPPONENT_TRAINER_HAND_CAPTURE" &&
                (it.payload as ArchivedCropCaptured).cropName == "OpponentTrainerHandCrop" })
        } finally {
            witness.stop(); gate.stop(); match.release()
            frames.close(); completed.close(); frame.recycle(); root.deleteRecursively()
        }
    }
}
