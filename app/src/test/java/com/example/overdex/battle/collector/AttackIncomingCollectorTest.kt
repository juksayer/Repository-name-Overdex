package com.example.overdex.battle.collector

import android.graphics.Bitmap
import com.example.overdex.battle.custody.CustodyRecord
import com.example.overdex.battle.custody.SourceAvailabilityRecord
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.custody.SourceInputRecord
import com.example.overdex.battle.custody.TestimonyCustody
import com.example.overdex.battle.custody.TestimonyPayload
import com.example.overdex.battle.custody.TestimonyRecord
import com.example.overdex.model.observation.ObservationInput
import com.example.overdex.model.observation.SessionSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

class AttackIncomingCollectorTest {

    private val testSourceId = SourceId("TEST_COLLECTOR")

    @Test
    fun `collector reports availability on start and stop`() {
        val custody = FakeCustody()
        val input = FakeInput()
        val collector = AttackIncomingCollector(input, custody, testSourceId)

        collector.start()
        val startRecord = custody.records.find { it is SourceAvailabilityRecord } as SourceAvailabilityRecord
        assertTrue(startRecord.available)
        assertEquals(testSourceId, startRecord.sourceId)

        collector.stop()
        val lastRecord = custody.records.last { it is SourceAvailabilityRecord } as SourceAvailabilityRecord
        assertFalse(lastRecord.available)
    }

    /**
     * NOTE: Full verification of the Collector's occupation (cropping and detection) 
     * requires an Android environment (instrumented test) because the [Bitmap] 
     * class cannot be instantiated or mocked in a pure JVM unit test.
     * 
     * The tests below verify the architectural contract using fakes where possible.
     */

    @Test
    fun `collector reports input availability when visual data is supplied`() = runBlocking {
        val custody = FakeCustody()
        val input = FakeInput()
        
        // We use a dummy collector that doesn't attempt to cast or use the Bitmap
        val collector = AttackIncomingCollector(
            input, custody, testSourceId,
            crop = { _, _, _, _, _ -> null },
            detect = { null }
        )

        collector.start()
        
        // We trigger a "silent" frame that bypasses type checks in the fake input
        input.triggerSilentFrame()

        val inputRecord = custody.records.find { it is SourceInputRecord } as SourceInputRecord
        assertTrue(inputRecord.available)
        assertEquals(testSourceId, inputRecord.sourceId)
    }

    // --- Fakes ---

    private class FakeInput : ObservationInput {
        override val source = SessionSource.LIVE_CAPTURE
        private var callback: (suspend (Bitmap) -> Unit)? = null

        override suspend fun supply(onVisualData: suspend (Bitmap) -> Unit) {
            callback = onVisualData
        }

        /**
         * Simulates a frame delivery by calling the callback with a null value.
         * In a real JVM test, any non-Bitmap object will cause a ClassCastException.
         */
        suspend fun triggerSilentFrame() {
            try {
                // This will likely fail in JVM but we catch it to verify the signal was sent
                @Suppress("UNCHECKED_CAST")
                val raw = callback as? (suspend (Bitmap?) -> Unit)
                raw?.invoke(null)
            } catch (e: Throwable) {
                // Ignore cast exceptions in JVM test
            }
        }
    }

    private class FakeCustody : TestimonyCustody {
        val records = CopyOnWriteArrayList<CustodyRecord>()
        private val sequence = AtomicLong(0)

        private val _testimonyFlow = MutableSharedFlow<TestimonyRecord>(extraBufferCapacity = 64)
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
