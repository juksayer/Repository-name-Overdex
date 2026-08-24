package com.example.overdex.battle.witness

import android.graphics.Bitmap
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.Match
import com.example.overdex.battle.observation.FakePokemonKnowledge
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.observation.ObservationInput
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
import kotlin.time.Duration.Companion.milliseconds

class AnnouncementWitnessTest {

    @Test
    fun `Witness assigns vocabulary from PokemonKnowledge once on start`() = runBlocking {
        // Arrange
        val custody = FakeCustody()
        val knowledge = object : FakePokemonKnowledge() {
            var callCount = 0
            override suspend fun getAllSpeciesNames(): Set<String> {
                callCount++
                return setOf("PIKACHU")
            }
        }
        val match = Match(
            matchId = "M1",
            custody = custody,
            realityTimeline = InMemoryRealityTimeline(),
            pokemonKnowledge = knowledge
        )
        val input = FakeInput()
        val calibration = BattleCalibration()
        val witness = AnnouncementWitness(input, calibration)

        // Act
        witness.start(match)
        delay(50.milliseconds)

        // Assert
        assertEquals("Should call getAllSpeciesNames exactly once on start", 1, knowledge.callCount)
        
        match.release()
    }

    @Test
    fun `Witness submits raw announcement testimony to custody on match`() = runBlocking {
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
            moveBannerRegion = com.example.overdex.model.AnchorRegion(0.1f, 0.1f, 0.5f, 0.1f)
        )
        
        val mockResult = com.example.overdex.model.observation.RecognitionResult(
            value = "GO, PIKACHU!",
            confidence = 1.0f,
            recognizer = "Mock"
        )
        
        val witness = AnnouncementWitness(
            input = input,
            calibration = calibration,
            recognize = { _, _ -> mockResult },
            crop = { _, _ -> null } // Stub for JVM
        )

        // Act
        witness.start(match)
        delay(50.milliseconds)
        
        input.triggerFrame()
        delay(50.milliseconds)

        // Assert
        val testimony = custody.getRecords().filterIsInstance<TestimonyRecord>()
        assertEquals(1, testimony.size)
        assertEquals("GO, PIKACHU!", (testimony[0].payload as RawTestimony).data)
        assertEquals(1.0f, testimony[0].confidence)
        
        match.release()
    }

    // --- Fakes ---

    private class FakeInput : ObservationInput {
        override val source = SessionSource.LIVE_CAPTURE
        private var callback: (suspend (Bitmap) -> Unit)? = null

        override suspend fun supply(onVisualData: suspend (Bitmap) -> Unit) {
            callback = onVisualData
        }

        suspend fun triggerFrame() {
            // Supply a non-null object that can be cast if needed, or null if the Witness handles it
            try {
                @Suppress("UNCHECKED_CAST")
                val raw = callback as? (suspend (Bitmap?) -> Unit)
                raw?.invoke(null)
            } catch (e: Throwable) {}
        }
    }

    private class FakeCustody : TestimonyCustody {
        private val records = CopyOnWriteArrayList<CustodyRecord>()
        private val _testimonyFlow = MutableSharedFlow<TestimonyRecord>(replay = 64, extraBufferCapacity = 64)
        override val testimonyFlow: Flow<TestimonyRecord> = _testimonyFlow.asSharedFlow()

        override fun submitAvailability(sourceId: SourceId, available: Boolean, timestamp: Long): SourceAvailabilityRecord {
            val r = SourceAvailabilityRecord(0, timestamp, sourceId, available)
            records.add(r)
            return r
        }

        override fun submitInputAvailability(sourceId: SourceId, available: Boolean, timestamp: Long): SourceInputRecord {
            val r = SourceInputRecord(0, timestamp, sourceId, available)
            records.add(r)
            return r
        }

        override fun submitTestimony(sourceId: SourceId, payload: TestimonyPayload, timestamp: Long, confidence: Float, evidenceReferences: List<String>): TestimonyRecord {
            val r = TestimonyRecord(0, timestamp, sourceId, payload, confidence, evidenceReferences)
            records.add(r)
            _testimonyFlow.tryEmit(r)
            return r
        }

        override fun getRecords(): List<CustodyRecord> = records.toList()
    }
}
