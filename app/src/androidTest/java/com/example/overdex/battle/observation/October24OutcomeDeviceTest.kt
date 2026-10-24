package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.data.observation.OutcomeTextRecognizer
import com.example.overdex.data.observation.SharedLatinTextRecognizer
import com.example.overdex.data.observation.OcrPriority
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.BattleResult
import com.example.overdex.model.Pokemon
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class October24OutcomeDeviceTest {
    @Test fun nativeAndWhiteLetterReadingsResolveResultsButRejectRewardScreens() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets
        OutcomeTextRecognizer.warmUp()
        for (name in assets.list("battle/oct24/outcomes")!!.sorted()) {
            val bitmap = assets.open("battle/oct24/outcomes/$name").use(BitmapFactory::decodeStream)!!
            try {
                val before = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(before, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val start = System.nanoTime()
                val readings = OutcomeTextRecognizer.withReadingTurn {
                    recognizeCandidates(bitmap) { MatchOutcomeTextResolver.resolve(it) != null }
                }
                val expected = when {
                    name.startsWith("143.980") || name.startsWith("144.421") -> MatchOutcomePhrase.WIN
                    name == "good-effort-pixel.png" -> MatchOutcomePhrase.LOSS
                    else -> null
                }
                assertEquals(name + readings.map { it.rawText }, expected,
                    readings.firstNotNullOfOrNull { MatchOutcomeTextResolver.resolve(it.rawText) })
                assertTrue(readings.size in 1..2)
                assertEquals("RAW", readings.first().treatment)
                if (readings.size == 2) assertEquals("WHITE_TEXT_210_PADDED", readings.last().treatment)
                val after = IntArray(before.size)
                bitmap.getPixels(after, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                assertArrayEquals("Source pixels must stay unchanged", before, after)
                instrumentation.sendStatus(0, Bundle().apply {
                    putString("result_read", "$name ${readings.map { it.treatment + '=' + it.rawText }} elapsedMs=${(System.nanoTime()-start)/1_000_000}")
                })
            } finally { bitmap.recycle() }
        }
    }

    @Test fun resultArrivingWhileOcrIsBusyBypassesBlankBacklogAndKeepsSourceTime() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = File(instrumentation.targetContext.cacheDir, "outcome-oct24-${System.nanoTime()}")
        val store = FileCropArtifactStore(root)
        val knowledge = object : PokemonKnowledge {
            override suspend fun getPokemonByName(name: String): Pokemon? = null
            override suspend fun getPokemonById(id: Int): Pokemon? = null
            override suspend fun getAllSpeciesNames() = emptySet<String>()
        }
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match("OCT24_RESULT", custody = custody, realityTimeline = timeline, pokemonKnowledge = knowledge)
        val session = DroidballSession(match)
        val reader = PersistedOutcomeTextWitness(store)
        val phrase = PersistedOutcomePhraseWitness(
            { MatchOutcomeTextResolver.resolve(it) == MatchOutcomePhrase.WIN },
            ObserverId("YOU_WIN_WITNESS", ObservationSource.SCREEN_CAPTURE), "You Win",
        )
        val win = instrumentation.context.assets.open("battle/oct24/outcomes/144.421-MatchOutcomeCrop.png")
            .use(BitmapFactory::decodeStream)!!
        val blank = Bitmap.createBitmap(win.width, win.height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val provenance = BattleCropProvenance("MatchOutcomeCrop", 672, 1493,
            BattleCropBounds(100, 627, 100 + win.width, 627 + win.height))
        val occupied = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        OutcomeTextRecognizer.warmUp()
        val blocker = launch(Dispatchers.Default) {
            SharedLatinTextRecognizer.withReadingTurn(OcrPriority.NORMAL) {
                occupied.complete(Unit)
                release.await()
            }
        }
        val sourceNanos = System.nanoTime() - 20_000_000_000L
        try {
            occupied.await()
            phrase.start(match); reader.start(match)
            val blankArtifact = store.preservePng(blank)!!
            custody.submitTestimony(SourceId("MATCH_OUTCOME_CROP_CAPTURE"),
                CropCaptured(blankArtifact, provenance), 1_000L, null, emptyList(), sourceNanos)
            withTimeout(5_000) {
                while (SharedLatinTextRecognizer.waitingCount(OcrPriority.URGENT) == 0) delay(10)
            }
            repeat(80) { index ->
                custody.submitTestimony(SourceId("MATCH_OUTCOME_CROP_CAPTURE"),
                    CropCaptured(blankArtifact, provenance), 1_250L + index * 100,
                    null, emptyList(), sourceNanos + 250_000_000L + index * 100_000_000L)
            }
            val winArtifact = store.preservePng(win)!!
            val winTime = sourceNanos + 10_000_000_000L
            custody.submitTestimony(SourceId("MATCH_OUTCOME_CROP_CAPTURE"),
                CropCaptured(winArtifact, provenance), 11_000L, null, emptyList(), winTime)
            withTimeout(5_000) {
                while (timeline.getArticles().count { it.payload is CropCaptured } < 82) delay(10)
            }
            // Let the already-subscribed crop collector see the final Article.
            delay(50)
            val startedAt = System.nanoTime()
            release.complete(Unit)
            withTimeout(5_000) { while (session.phase.value != DroidballSessionPhase.RESULT) delay(10) }
            val articles = timeline.getArticles()
            val end = articles.single { it.payload is MatchEnded }
            assertEquals(MatchEnded(BattleResult.WIN), end.payload)
            assertEquals(11_000L, end.perceivedAt)
            assertEquals(winTime, end.monotonicTimeNanos)
            val accepted = articles.single { it.id == end.predecessorIds.single() }
            val raw = articles.single { it.id.value == accepted.evidenceReferences!!.single() }
            val crop = articles.single { it.id.value == raw.evidenceReferences!!.single() }
            assertEquals(winArtifact, (crop.payload as CropCaptured).artifact)
            val readings = articles.filter { it.sourceId.id == "MATCH_OUTCOME_TEXT_WITNESS" }
            assertTrue(readings.isNotEmpty())
            assertTrue(readings.all { it.evidenceReferences == listOf(crop.id.value) })
            assertTrue(readings.all { (it.payload as RawTestimony).ocr != null })
            // There must be no eighty-blank drain before this live result.
            val diagnostics = articles.filter { it.sourceId.id == "MATCH_OUTCOME_TEXT_WITNESS_LATENCY" }
            assertEquals(1, diagnostics.size)
            assertTrue((diagnostics.single().payload as RawTestimony).data.toString().contains("pending=81"))
            instrumentation.sendStatus(0, Bundle().apply {
                putString("result_chain_ms", "${(System.nanoTime()-startedAt)/1_000_000}")
                putString("result_diagnostic", (diagnostics.single().payload as RawTestimony).data.toString())
            })
        } finally {
            release.complete(Unit)
            blocker.join()
            reader.stop(); phrase.stop(); session.end(); match.release()
            blank.recycle(); win.recycle(); root.deleteRecursively()
        }
    }

}
