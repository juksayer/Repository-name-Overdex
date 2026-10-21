package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.data.BattleCalibration
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.data.observation.OcrPriority
import com.example.overdex.data.observation.SharedLatinTextRecognizer
import com.example.overdex.model.Pokemon
import com.example.overdex.model.observation.CapturedVisualFrame
import com.example.overdex.model.observation.ObservationInput
import com.example.overdex.model.observation.SessionSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.*
import org.junit.Test

/** Real button pixels must still be read when the shared OCR lane has been busy. */
class PlayerControlOcrSchedulingDeviceTest {
    @Test fun allFourReadersQueueFairlyAndReplaceStalePixelsAfterAdmission() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.context.assets.open("player_controls/oct19/afternoon-abomasnow.png")
            .use(BitmapFactory::decodeStream)!!
        val blank = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val frames = MutableSharedFlow<CapturedVisualFrame>()
        val input = object : ObservationInput {
            override val source = SessionSource.LIVE_CAPTURE
            override suspend fun supply(onVisualData: suspend (Bitmap) -> Unit) { frames.collect { onVisualData(it.bitmap) } }
            override suspend fun supplyFrames(onFrame: suspend (CapturedVisualFrame) -> Unit) { frames.collect { onFrame(it) } }
        }
        val knowledge = object : PokemonKnowledge {
            override suspend fun getPokemonByName(name: String): Pokemon? = null
            override suspend fun getPokemonById(id: Int): Pokemon? = null
            override suspend fun getAllSpeciesNames() = emptySet<String>()
        }
        val timeline = InMemoryRealityTimeline()
        val match = Match("control-ocr-contention", custody = InMemoryTestimonyCustody(),
            realityTimeline = timeline, pokemonKnowledge = knowledge)
        val store = FileCropArtifactStore(java.io.File(instrumentation.targetContext.cacheDir,
            "control-ocr-contention").apply { mkdirs() })
        val witnesses = listOf(false, true).flatMap { effectiveness -> (1..2).map { slot ->
            LivePlayerControlTextWitness(input, BattleCalibration(), store, { true }, slot, effectiveness)
        } }
        val acquired = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val blocker = launch(Dispatchers.Default) {
            SharedLatinTextRecognizer.withReadingTurn(OcrPriority.URGENT) {
                acquired.complete(Unit)
                release.await()
            }
        }
        var sender: Job? = null
        try {
            acquired.await()
            witnesses.forEach { it.start(match) }
            withTimeout(5_000) { while (frames.subscriptionCount.value != 4) delay(20) }
            frames.emit(CapturedVisualFrame(blank, System.currentTimeMillis(), System.nanoTime(), 1080, 2400))
            withTimeout(5_000) {
                while (SharedLatinTextRecognizer.waitingCount(OcrPriority.NORMAL) != 4) delay(20)
            }
            // The original blank crop will be stale by admission. The replacement
            // must retain the newer frame's timestamp and cited pixels.
            delay(1_700)
            val freshAt = System.nanoTime()
            frames.emit(CapturedVisualFrame(bitmap, System.currentTimeMillis(), freshAt, 1080, 2400))
            release.complete(Unit)
            sender = launch {
                repeat(50) {
                    delay(100)
                    frames.emit(CapturedVisualFrame(bitmap, System.currentTimeMillis(), System.nanoTime(), 1080, 2400))
                }
            }
            withTimeout(15_000) {
                while ((1..2).any { slot ->
                    timeline.getArticles().none { (it.payload as? PlayerChargedMoveEffectivenessObserved)
                        ?.let { p -> p.slot == slot && p.effectiveness == FastMoveEffectiveness.NOT_VERY_EFFECTIVE } == true } ||
                    timeline.getArticles().none { (it.payload as? PlayerChargeMoveLabelRead)?.let { p ->
                        p.slot == slot && MoveTextAliases.matchesButtonLabel(p.text,
                            if (slot == 1) "Weather Ball (Ice)" else "Energy Ball") } == true }
                }) delay(50)
            }
            val readings = timeline.getArticles().filter {
                it.payload is PlayerChargeMoveLabelRead || it.payload is PlayerChargedMoveEffectivenessObserved
            }
            assertTrue(readings.all { it.monotonicTimeNanos!! >= freshAt })
            assertTrue(readings.all { it.evidenceReferences.orEmpty().isNotEmpty() })
            assertFalse(timeline.getArticles().any { it.payload is FastMoveUseObserved || it.payload is ChargedMoveEnergySpent })
        } finally {
            release.complete(Unit)
            blocker.cancelAndJoin()
            sender?.cancelAndJoin()
            witnesses.forEach { it.stop() }
            match.release()
            blank.recycle()
            bitmap.recycle()
        }
    }
}
