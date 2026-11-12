package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.archive.*
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.data.BattleCalibration
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.AnchorRegion
import com.example.overdex.model.Pokemon
import com.example.overdex.model.observation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test

/** Actual Android bitmap -> live Witness -> Custody -> portable archive. No live game or projection. */
class TerminalParticleWitnessDeviceTest {
    @Test fun savedFrameReadingsRespectOcclusionAndKeepSourceClocksWithoutProducingFaints() = runBlocking {
        checkSavedFrames(AcceptedChargeCaptureCueKind.PLAYER_QTE_PROGRESS)
    }
    @Test fun incomingChargeMeasuresAppearanceIndependentlyAndStopsAtObscuration() = runBlocking {
        checkSavedFrames(AcceptedChargeCaptureCueKind.ATTACK_INCOMING)
    }
    private suspend fun checkSavedFrames(cue: AcceptedChargeCaptureCueKind) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = context.assets.open("battle_samples/moondrop-20261110-20-waterqte2.jpg").use {
            requireNotNull(BitmapFactory.decodeStream(it))
        }.let { original ->
            Bitmap.createScaledBitmap(original,672,1493,true).also { if(it !== original) original.recycle() }
        }
        val custody = InMemoryTestimonyCustody()
        val match = Match("particle-device-test",custody=custody,realityTimeline=InMemoryRealityTimeline(),
            pokemonKnowledge=object : PokemonKnowledge {
                override suspend fun getPokemonByName(name: String): Pokemon? = null
                override suspend fun getPokemonById(id: Int): Pokemon? = null
                override suspend fun getAllSpeciesNames() = emptySet<String>()
            })
        val input = Frames()
        val gate = TerminalVisualCaptureGate().also { it.beginSession() }
        val witness = TerminalParticleWitness(input,BattleCalibration(),gate)
        val times = mutableListOf<Long>()
        try {
            witness.start(match)
            withTimeout(5000) { while(input.frames.subscriptionCount.value != 1) delay(5) }
            val phase = System.nanoTime()
            assertTrue(gate.prepare(AcceptedChargeCaptureCue(cue,
                "controlled-qte-cue",phase,phase,System.currentTimeMillis())))
            repeat(6) { index ->
                val at = System.nanoTime()
                times += at
                input.frames.emit(CapturedVisualFrame(bitmap,System.currentTimeMillis(),at,
                    retainedBadgeRegion=if(index==2) AnchorRegion(0f,0f,1f,1f) else null,
                    overlayRegion=if(index==4) AnchorRegion(0f,0f,1f,1f) else null,
                    publication=VisualFramePublication(1,index + 1L,at)))
                delay(60)
            }
            withTimeout(5000) {
                while(custody.getRecords().filterIsInstance<TestimonyRecord>().sumOf {
                    (it.payload as? TerminalParticleObservations)?.samples?.size ?: 0
                } < 6) delay(10)
            }
            witness.stop()
            match.awaitAcceptedEvidencePublished()
            val archive = MatchArchiveSource(MatchId(match.matchId),match.realityTimeline).snapshot()
            val restored = MatchArchiveSerializer.deserialize(MatchArchiveSerializer.serialize(archive))
            val batches = restored.articles.mapNotNull { it.payload as? ArchivedTerminalParticleObservations }
            val readings = batches.flatMap { it.samples }
            assertEquals(times,readings.map { it.sourceAtNanos })
            assertEquals("BASELINE",readings[0].status)
            assertEquals("SAMPLED",readings[1].status)
            assertEquals("OBSCURED",readings[2].status)
            assertEquals("BASELINE",readings[3].status)
            assertEquals("OBSCURED",readings[4].status)
            assertEquals("BASELINE",readings[5].status)
            assertTrue(batches.last().ended)
            assertEquals(6,batches.last().totalSamples)
            assertTrue(restored.articles.none { it.payload is ArchivedActivePokemonFaintedWitnessed ||
                it.payload is ArchivedActiveHpBarMeasured || it.payload is ArchivedFastMoveUseObserved })
            assertTrue(readings.filter { it.status == "OBSCURED" }.all { it.points.isEmpty() })
            if(cue==AcceptedChargeCaptureCueKind.ATTACK_INCOMING) {
                assertEquals("BASELINE",readings[0].appearance!!.status)
                assertNotNull(readings[1].appearance!!.backgroundChange)
                assertNull(readings[2].appearance)
                assertEquals("BASELINE",readings[3].appearance!!.status)
                assertNull(readings[4].appearance)
                assertEquals("BASELINE",readings[5].appearance!!.status)
                assertEquals(listOf(listOf(0,373,185,710),listOf(220,300,336,560),listOf(0,210,150,315)),
                    readings[0].appearance!!.regions)
            } else assertTrue(readings.all { it.appearance==null })
        } finally { witness.stop(); match.release(); bitmap.recycle() }
    }

    private class Frames : ObservationInput {
        override val source = SessionSource.DEBUG
        override val supportsIndependentFrameCollectors = true
        val frames = MutableSharedFlow<CapturedVisualFrame>()
        override suspend fun supply(onVisualData: suspend (Bitmap) -> Unit) = frames.collect { onVisualData(it.bitmap) }
        override suspend fun supplyFrames(onFrame: suspend (CapturedVisualFrame) -> Unit) = frames.collect(onFrame)
    }
}
