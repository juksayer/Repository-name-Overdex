package com.example.overdex.battle.observation

import android.graphics.Bitmap
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.AnchorRegion
import com.example.overdex.model.observation.ObservationInput
import com.example.overdex.model.observation.RecognitionResult
import com.example.overdex.model.observation.SessionSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

class SpeciesWitnessTest {

    private val testObserverId = com.example.overdex.battle.timeline.observer.ObserverId(
        "TEST_SPECIES", 
        com.example.overdex.battle.timeline.observer.ObservationSource.SCREEN_CAPTURE
    )

    @Test
    fun `successful recognition results in custody submission`() = runBlocking {
        // Arrange
        val custody = FakeCustody()
        val match = Match(
            matchId = "M1",
            custody = custody,
            realityTimeline = InMemoryRealityTimeline(),
            pokemonKnowledge = FakePokemonKnowledge()
        )
        val input = FakeInput()
        val calibration = BattleCalibration(
            enemyNameRegion = AnchorRegion(0.1f, 0.1f, 0.5f, 0.1f)
        )
        
        val witness = SpeciesWitness(
            input = input,
            calibration = calibration,
            observerId = testObserverId,
            recognize = { RecognitionResult("Pikachu", 1.0f, "Mock") },
            crop = { _, _ -> null } 
        )

        // Act
        witness.start(match)
        delay(50)
        
        // Use the same trick as AttackIncomingCollectorTest to trigger a frame with null bitmap in JVM
        input.triggerSilentFrame {
            // Assert inside the callback scope to ensure it ran
        }
        delay(50)

        // Assert
        val records = custody.records
        
        // 1. Availability Signal
        assertTrue("Start availability record missing", records.any { it is SourceAvailabilityRecord && it.available })
        
        // 2. Input Availability Signal
        assertTrue("Input availability record missing", records.any { it is SourceInputRecord && it.available })

        // 3. Neutral Testimony
        val testimony = records.filterIsInstance<TestimonyRecord>()
        assertEquals("Testimony record missing", 1, testimony.size)
        assertEquals(SourceId("TEST_SPECIES"), testimony[0].sourceId)
        assertEquals(RawTestimony("Pikachu"), testimony[0].payload)
        assertEquals(1.0f, testimony[0].confidence)
    }

    // --- Fakes ---

    private class FakeInput : ObservationInput {
        override val source = SessionSource.LIVE_CAPTURE
        private var callback: (suspend (Bitmap) -> Unit)? = null

        override suspend fun supply(onVisualData: suspend (Bitmap) -> Unit) {
            callback = onVisualData
        }

        suspend fun triggerSilentFrame(block: () -> Unit) {
            try {
                @Suppress("UNCHECKED_CAST")
                val raw = callback as? (suspend (Bitmap?) -> Unit)
                raw?.invoke(null)
                block()
            } catch (e: Throwable) {
                // Ignore JVM class cast issues if they occur
            }
        }
    }

    private class FakeCustody : TestimonyCustody {
        val records = CopyOnWriteArrayList<CustodyRecord>()
        private val sequence = AtomicLong(0)

        private val _testimonyFlow = MutableSharedFlow<TestimonyRecord>(replay = 64, extraBufferCapacity = 64)
        override val testimonyFlow: Flow<TestimonyRecord> = _testimonyFlow.asSharedFlow()

        override fun submitAvailability(sourceId: SourceId, available: Boolean, timestamp: Long): SourceAvailabilityRecord {
            val r = SourceAvailabilityRecord(sequence.getAndIncrement(), timestamp, sourceId, available)
            records.add(r)
            return r
        }

        override fun submitInputAvailability(sourceId: SourceId, available: Boolean, timestamp: Long): SourceInputRecord {
            val r = SourceInputRecord(sequence.getAndIncrement(), timestamp, sourceId, available)
            records.add(r)
            return r
        }

        override fun submitTestimony(sourceId: SourceId, payload: TestimonyPayload, timestamp: Long, confidence: Float, evidenceReferences: List<String>): TestimonyRecord {
            val r = TestimonyRecord(sequence.getAndIncrement(), timestamp, sourceId, payload, confidence, evidenceReferences)
            records.add(r)
            _testimonyFlow.tryEmit(r)
            return r
        }

        override fun getRecords(): List<CustodyRecord> = records.toList()
    }
}
