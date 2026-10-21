package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.data.observation.OutcomeTextRecognizer
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.data.BattleCalibration
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.AnchorRegion
import com.example.overdex.model.BattleResult
import com.example.overdex.model.Pokemon
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.Assert.assertTrue
import java.io.File

/** Recorded result pixels and the actual saved crop, without modifying calibration. */
class October12OutcomeDeviceTest {
    @Test fun preservedWinAfterBlankFramesProducesOneTimedMatchEndAndResultPhase() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = File(instrumentation.targetContext.cacheDir, "outcome-reader-test-${System.nanoTime()}")
        val store = FileCropArtifactStore(root)
        val knowledge = object : PokemonKnowledge {
            override suspend fun getPokemonByName(name: String): Pokemon? = null
            override suspend fun getPokemonById(id: Int): Pokemon? = null
            override suspend fun getAllSpeciesNames() = emptySet<String>()
        }
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match("RECORDED_RESULT", custody = custody, realityTimeline = timeline, pokemonKnowledge = knowledge)
        val session = DroidballSession(match)
        val reader = PersistedOutcomeTextWitness(store)
        val phrase = PersistedOutcomePhraseWitness(
            { MatchOutcomeTextResolver.resolve(it) == MatchOutcomePhrase.WIN },
            ObserverId("YOU_WIN_WITNESS", ObservationSource.SCREEN_CAPTURE), "You Win",
        )
        val frame = instrumentation.context.assets.open("battle/oct12/result-win.png")
            .use(BitmapFactory::decodeStream)!!
        val resolved = BattleCropContracts.matchOutcome.resolve(BattleCalibration(
            matchOutcomeRegion = AnchorRegion(.15000005f, .42f, .69499993f, .17500003f),
        ), frame)!!
        val blank = Bitmap.createBitmap(468, 261, Bitmap.Config.ARGB_8888)
        blank.eraseColor(android.graphics.Color.BLACK)
        val win = instrumentation.context.assets.open("battle/oct12/result-win-preserved-crop.png")
            .use(BitmapFactory::decodeStream)!!
        val at = System.nanoTime()
        try {
            phrase.start(match)
            reader.start(match)
            val blankArtifact = store.preservePng(blank)!!
            repeat(4) { index ->
                custody.submitTestimony(SourceId("MATCH_OUTCOME_CROP_CAPTURE"),
                    CropCaptured(blankArtifact, resolved.provenance), 1_000L + index * 250L,
                    null, emptyList(), at + index * 250_000_000L)
            }
            val winArtifact = store.preservePng(win)!!
            repeat(2) { index ->
                custody.submitTestimony(SourceId("MATCH_OUTCOME_CROP_CAPTURE"),
                    CropCaptured(winArtifact, resolved.provenance), 2_000L + index * 250L,
                    null, emptyList(), at + 1_000_000_000L + index * 250_000_000L)
            }
            withTimeout(5_000) {
                while (session.phase.value != DroidballSessionPhase.RESULT || timeline.getArticles().count {
                    it.sourceId.id == "MATCH_OUTCOME_TEXT_WITNESS" && it.payload is RawTestimony
                } < 1) delay(10)
            }
            val articles = timeline.getArticles()
            val end = articles.single { it.payload is MatchEnded }
            assertEquals(MatchEnded(BattleResult.WIN), end.payload)
            val acceptedPhrase = articles.single { it.id == end.predecessorIds.single() }
            val rawOcr = articles.single { it.id.value == requireNotNull(acceptedPhrase.evidenceReferences).single() }
            val crop = articles.single { it.id.value == requireNotNull(rawOcr.evidenceReferences).single() }
            // The newest candidate may be read before the first one. Every
            // derived link must still retain the frame actually read, not publication time.
            assertTrue(end.perceivedAt in setOf(2_000L, 2_250L))
            assertEquals(crop.perceivedAt, end.perceivedAt)
            assertEquals(crop.monotonicTimeNanos, end.monotonicTimeNanos)
            assertNotNull(crop.payload as? CropCaptured)
            assertEquals(winArtifact, (crop.payload as CropCaptured).artifact)
            instrumentation.sendStatus(0, Bundle().apply {
                putString("outcome_chain", "blank frames -> preserved YOU WIN crop -> OCR -> phrase -> one WIN -> RESULT")
                putString("outcome_chain_elapsed_ms", "${(System.nanoTime() - at) / 1_000_000}")
            })
        } finally {
            reader.stop(); phrase.stop(); session.end(); match.release()
            blank.recycle(); win.recycle(); resolved.bitmap.recycle(); frame.recycle()
            root.deleteRecursively()
        }
    }

    @Test fun readsWinFromTheRecordedResultCrop() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val frame = instrumentation.context.assets.open("battle/oct12/result-win.png")
            .use(BitmapFactory::decodeStream)!!
        val crop = Bitmap.createBitmap(frame, 100, 627, 468, 261)
        try {
            val text = OutcomeTextRecognizer.readText(crop)
            instrumentation.sendStatus(0, Bundle().apply { putString("outcome_text", text) })
            assertEquals("OCR: $text", MatchOutcomePhrase.WIN, MatchOutcomeTextResolver.resolve(text))
        } finally {
            crop.recycle()
            frame.recycle()
        }
    }
}
