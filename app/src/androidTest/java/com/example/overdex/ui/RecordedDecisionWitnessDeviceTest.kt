package com.example.overdex.ui

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.*
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.Pokemon
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RecordedDecisionWitnessDeviceTest {
    @Test fun repeatedIncomingWarningsAndRecordedTimerPixelsReachTheTimeline() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val store = FileCropArtifactStore(File(instrumentation.targetContext.cacheDir, "decision-witness-${System.nanoTime()}"))
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val knowledge = object : PokemonKnowledge {
            override suspend fun getPokemonByName(name: String): Pokemon? = null
            override suspend fun getPokemonById(id: Int): Pokemon? = null
            override suspend fun getAllSpeciesNames() = emptySet<String>()
        }
        val match = Match("recorded-decision-witness", custody = custody, realityTimeline = timeline, pokemonKnowledge = knowledge)
        val incoming = PersistedAttackIncomingWitness(store)
        val timer = PersistedDecisionTimerWitness(store)
        val start = System.nanoTime()
        try {
            incoming.start(match)
            timer.start(match)
            delay(100)
            listOf(12_214L to "Attack incoming!", 12_500L to "Attack incoming!",
                20_386L to "Sneasel used Foul Play!", 108_291L to "Attack incoming!").forEach { (millis, text) ->
                custody.submitTestimony(SourceId("ANNOUNCEMENT_WITNESS"), RawTestimony(text), millis,
                    null, emptyList(), start + millis * 1_000_000L)
            }
            withTimeout(5_000) {
                while (timeline.getArticles().count { it.payload is AttackIncoming } < 2) delay(20)
            }
            val warnings = timeline.getArticles().filter { it.payload is AttackIncoming }
            assertEquals(listOf(12_214_000_000L, 108_291_000_000L), warnings.map { it.monotonicTimeNanos!! - start })
            assertTrue(warnings.all { it.predecessorIds.isNotEmpty() })

            var at = start + 110_000_000_000L
            for (name in listOf("moondrop-3", "moondrop-1", "moondrop-absent")) {
                val bitmap = instrumentation.context.assets.open("battle/decision-timer/$name.png")
                    .use(BitmapFactory::decodeStream)
                try {
                    val artifact = store.preservePng(bitmap)!!
                    val provenance = BattleCropProvenance("DecisionTimerCrop", 672, 1493,
                        BattleCropBounds(273, 49, 273 + bitmap.width, 49 + bitmap.height))
                    repeat(6) {
                        custody.submitTestimony(SourceId("DECISION_TIMER_CAPTURE"), CropCaptured(artifact, provenance),
                            110_000L, null, emptyList(), at)
                        at += 350_000_000L
                        delay(350)
                    }
                } finally { bitmap.recycle() }
            }
            withTimeout(5_000) {
                while (timeline.getArticles().none { (it.payload as? BattleDecisionTimerObserved)?.secondsRemaining == 1 }) delay(20)
            }
            val readings = timeline.getArticles().filter { it.payload is BattleDecisionTimerObserved }
            assertTrue(readings.any { (it.payload as BattleDecisionTimerObserved).secondsRemaining == 3 })
            assertNull((readings.last().payload as BattleDecisionTimerObserved).secondsRemaining)
            assertTrue(readings.all { it.evidenceReferences.orEmpty().isNotEmpty() })
        } finally {
            timer.stop()
            incoming.stop()
            match.release()
        }
    }
}
