package com.example.overdex.battle.observation

import android.graphics.Bitmap
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.observation.ObservationInput
import com.example.overdex.model.observation.SessionSource
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Exercises real Witness lifecycle and Custody; pixel accuracy uses the recorded-image tests. */
class TerminalParticleWitnessTest {
    @Test fun `measurement closes and publishes a zero-frame interval on stop without stopping shared gate`() = runBlocking {
        val custody = InMemoryTestimonyCustody()
        val match = Match("particle-lifecycle",custody=custody,realityTimeline=InMemoryRealityTimeline(),pokemonKnowledge=FakePokemonKnowledge())
        val gate = TerminalVisualCaptureGate().also { it.beginSession() }
        val input = Input(true)
        val witness = TerminalParticleWitness(input,BattleCalibration(),gate)
        try {
            witness.start(match)
            val at = System.nanoTime()
            gate.prepare(AcceptedChargeCaptureCue(AcceptedChargeCaptureCueKind.ATTACK_INCOMING,"prompt",at,at,1000))
            withTimeout(5000) { while(input.collectors.get()==0) delay(5) }
            // The independent 100ms expiry task must observe an assignment even
            // if the visual source goes silent before its first frame.
            delay(250)
            witness.stop()
            assertNotNull(gate.particleAssignment())
            val rows = custody.getRecords().filterIsInstance<TestimonyRecord>()
            val batch = rows.single { it.payload is TerminalParticleObservations }
            val payload = batch.payload as TerminalParticleObservations
            assertTrue(payload.ended)
            assertEquals(0,payload.totalSamples)
            assertTrue(payload.samples.isEmpty())
            assertEquals(at,batch.monotonicTimeNanos)
            assertEquals(listOf("prompt"),batch.evidenceReferences)
            assertFalse(rows.any { it.payload is ActivePokemonFaintedWitnessed || it.payload is ActiveHpBarMeasured })
            match.awaitAcceptedEvidencePublished()
            assertTrue(match.realityTimeline.getArticles().any { it.payload == payload })
        } finally { witness.stop(); match.release() }
    }

    @Test fun `one-shot inputs do not acquire an extra frame consumer`() = runBlocking {
        val match = Match("particle-one-shot",custody=InMemoryTestimonyCustody(),realityTimeline=InMemoryRealityTimeline(),pokemonKnowledge=FakePokemonKnowledge())
        val input = Input(false)
        val witness = TerminalParticleWitness(input,BattleCalibration(),TerminalVisualCaptureGate())
        try {
            witness.start(match); witness.start(match)
            delay(150)
            assertEquals(0,input.collectors.get())
        } finally { witness.stop(); match.release() }
    }

    private class Input(override val supportsIndependentFrameCollectors: Boolean) : ObservationInput {
        override val source = SessionSource.DEBUG
        val collectors = AtomicInteger()
        override suspend fun supply(onVisualData: suspend (Bitmap) -> Unit) {
            collectors.incrementAndGet()
            awaitCancellation()
        }
    }
}
