package com.example.overdex.battle.observation

import android.graphics.Bitmap
import com.example.overdex.battle.artifact.CropArtifactReference
import com.example.overdex.battle.artifact.CropArtifactStore
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.observation.ObservationInput
import com.example.overdex.model.observation.SessionSource
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Test

/** Real witness wiring and custody; no Android pixels or recognition accuracy are simulated. */
class TerminalVisualEarlyPreparationWitnessTest {
    @Test fun `terminal subscriber captures early QTE without replacing HP or waiting for HP geometry`() {
        val f = Fixture("fanout")
        val hp = mutableListOf<AcceptedChargeCaptureCue>()
        f.match.acceptedChargeCapturePreparation.attach(hp::add)
        try {
            f.witness.start(f.match)
            val crop = f.grade("grade")
            val window = requireNotNull(f.gate.captureWindow(ActivePokemonSide.OPPONENT))
            assertEquals(1, hp.size)
            assertEquals(crop.monotonicTimeNanos, window.from)
            assertEquals(listOf(crop.id.value), window.evidenceReferences)
            assertFalse(f.custody.getRecords().filterIsInstance<TestimonyRecord>().any {
                it.payload is ActivePokemonFaintedWitnessed || it.payload is ActiveHpBarMeasured ||
                    it.payload is ActiveHpBarDamageTickMeasured
            })
            f.witness.stop()
            assertFalse(f.gate.isCapturing(ActivePokemonSide.OPPONENT))
            f.grade("after-stop")
            assertEquals("Stopping terminal capture must not detach HP", 2, hp.size)
            assertFalse(f.gate.isCapturing(ActivePokemonSide.OPPONENT))
            val summary = f.custody.getRecords().filterIsInstance<TestimonyRecord>().single {
                it.sourceId.id == "TERMINAL_VISUAL_CAPTURE_DIAGNOSTICS"
            }
            val text = (summary.payload as RawTestimony).data.toString()
            assertTrue(text.contains("received=0"))
            assertTrue(text.contains("retainedPngs=0"))
            assertEquals(crop.monotonicTimeNanos, summary.monotonicTimeNanos)
            assertEquals(listOf(crop.id.value), summary.evidenceReferences)
        } finally { f.close() }
    }

    @Test fun `restarting witness for another match cannot receive old match cues`() {
        val first = Fixture("first")
        val second = Fixture("second")
        try {
            first.witness.start(first.match)
            first.grade("first-grade")
            first.witness.stop()
            first.witness.start(second.match)
            first.grade("obsolete-first-match")
            assertFalse(first.gate.isCapturing(ActivePokemonSide.OPPONENT))
            second.grade("second-grade")
            val current = requireNotNull(first.gate.captureWindow(ActivePokemonSide.OPPONENT))
            assertEquals(listOf("second-grade"), current.evidenceReferences)
        } finally { first.close(); second.close() }
    }

    private class Fixture(val id: String) {
        val custody = InMemoryTestimonyCustody()
        val match = Match(id, custody = custody, realityTimeline = InMemoryRealityTimeline(),
            pokemonKnowledge = FakePokemonKnowledge())
        val gate = TerminalVisualCaptureGate()
        val witness = TerminalVisualSequenceWitness(object : ObservationInput {
            override val source = SessionSource.DEBUG
            override suspend fun supply(onVisualData: suspend (Bitmap) -> Unit) { awaitCancellation() }
        }, BattleCalibration(), object : CropArtifactStore {
            override fun preservePng(bitmap: Bitmap): CropArtifactReference? = error("No image supplied")
        }, gate)

        fun grade(id: String): RealityArticle {
            val at = System.nanoTime()
            val contract = BattleCropContracts.qteProgressText
            val crop = RealityArticle(ArticleId(id), System.currentTimeMillis(), System.currentTimeMillis(),
                SourceId("QTE_PROGRESS_TEXT_CAPTURE"), CropCaptured(
                    CropArtifactReference("artifacts/crops/$id.png", "a".repeat(64), 1),
                    BattleCropProvenance(contract.cropName, 672, 1493, BattleCropBounds(100, 300, 570, 450))),
                matchId = MatchId(this.id), monotonicTimeNanos = at)
            match.acceptedChargeCapturePreparation.offer("QTE_PROGRESS_TEXT_WITNESS", contract, crop, "GREAT!", System.nanoTime())
            return crop
        }

        fun close() { witness.stop(); match.release() }
    }
}
