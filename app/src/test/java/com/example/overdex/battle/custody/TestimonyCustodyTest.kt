package com.example.overdex.battle.custody

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TestimonyCustodyTest {

    private val sourceA = SourceId("S_A")
    private val sourceB = SourceId("S_B")

    @Test
    fun `testimony enters custody and emerges unchanged with metadata preserved`() {
        val custody = InMemoryTestimonyCustody()
        val payload = RawTestimony(data = "Pikachu")
        val timestamp = 123456789L
        val confidence = 0.95f
        val evidenceRefs = listOf("frame_001.png", "ocr_result.json")

        val record = custody.submitTestimony(
            sourceId = sourceA,
            payload = payload,
            timestamp = timestamp,
            confidence = confidence,
            evidenceReferences = evidenceRefs
        )

        // Verify preservation
        assertEquals(sourceA, record.sourceId)
        assertEquals(payload, record.payload)
        assertEquals(timestamp, record.timestamp)
        assertEquals(confidence, record.confidence)
        assertEquals(evidenceRefs, record.evidenceReferences)
        
        // Verify Custody-assigned sequence
        assertEquals(0L, record.sequenceNumber)

        // Verify retrieval from store
        val retrieved = custody.getRecords().first() as TestimonyRecord
        assertEquals(record, retrieved)
    }

    @Test
    fun `source availability and input states are preserved and distinguishable`() {
        val custody = InMemoryTestimonyCustody()
        val t1 = 1000L
        val t2 = 1001L

        val r1 = custody.submitAvailability(sourceA, true, t1)
        val r2 = custody.submitInputAvailability(sourceA, true, t2)

        assertTrue(r1.available)
        assertEquals(t1, r1.timestamp)
        assertEquals(sourceA, r1.sourceId)

        assertTrue(r2.available)
        assertEquals(t2, r2.timestamp)
        assertEquals(sourceA, r2.sourceId)

        val records = custody.getRecords()
        assertTrue(records[0] is SourceAvailabilityRecord)
        assertTrue(records[1] is SourceInputRecord)
    }

    @Test
    fun `silence is distinguishable from lack of input`() {
        val custody = InMemoryTestimonyCustody()
        
        // Scenario 1: Source online, but no input
        custody.submitAvailability(sourceA, true, 100L)
        custody.submitInputAvailability(sourceA, false, 101L)
        
        // Scenario 2: Source online, input available, but no testimony (silence)
        custody.submitAvailability(sourceB, true, 200L)
        custody.submitInputAvailability(sourceB, true, 201L)
        // (Time passes, no submitTestimony called)

        val records = custody.getRecords()
        
        // Source A state at end
        val aInput = records.filter { it.sourceId == sourceA }.last() as SourceInputRecord
        assertEquals(false, aInput.available)

        // Source B state at end
        val bInput = records.filter { it.sourceId == sourceB }.last() as SourceInputRecord
        assertEquals(true, bInput.available)
        
        // Verify no testimony for either
        assertTrue(records.none { it is TestimonyRecord })
    }

    @Test
    fun `minimum required testimony is accepted`() {
        val custody = InMemoryTestimonyCustody()
        val timestamp = 500L
        
        val record = custody.submitTestimony(
            sourceId = sourceA,
            payload = RawTestimony(""),
            timestamp = timestamp,
            confidence = 0f
        )

        assertEquals(sourceA, record.sourceId)
        assertEquals(timestamp, record.timestamp)
    }

    @Test
    fun `custody assigns monotonically increasing sequence numbers across mixed types`() {
        val custody = InMemoryTestimonyCustody()
        
        val r1 = custody.submitAvailability(sourceA, true, 100L)
        val r2 = custody.submitInputAvailability(sourceA, true, 101L)
        val r3 = custody.submitTestimony(sourceA, RawTestimony("Data"), 102L, 1.0f)
        val r4 = custody.submitAvailability(sourceA, false, 103L)

        assertEquals(0L, r1.sequenceNumber)
        assertEquals(1L, r2.sequenceNumber)
        assertEquals(2L, r3.sequenceNumber)
        assertEquals(3L, r4.sequenceNumber)
        
        val allRecords = custody.getRecords()
        assertEquals(4, allRecords.size)
        for (i in 0 until 4) {
            assertEquals(i.toLong(), allRecords[i].sequenceNumber)
        }
    }

    @Test
    fun `custody is thread-safe for concurrent submissions`() {
        val custody = InMemoryTestimonyCustody()
        val threadCount = 10
        val submissionsPerThread = 100
        val totalSubmissions = threadCount * submissionsPerThread
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        repeat(threadCount) { threadIdx ->
            executor.execute {
                try {
                    repeat(submissionsPerThread) { subIdx ->
                        val id = SourceId("S_$threadIdx")
                        when (subIdx % 3) {
                            0 -> custody.submitAvailability(id, true, System.currentTimeMillis())
                            1 -> custody.submitInputAvailability(id, true, System.currentTimeMillis())
                            2 -> custody.submitTestimony(id, RawTestimony("D"), System.currentTimeMillis(), 1.0f)
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        executor.shutdown()

        val records = custody.getRecords()
        assertEquals(totalSubmissions, records.size)
        
        val sequenceNumbers = records.map { it.sequenceNumber }.toSet()
        assertEquals(totalSubmissions, sequenceNumbers.size)
    }

    @Test
    fun `custody protects internal store from external modification`() {
        val custody = InMemoryTestimonyCustody()
        custody.submitAvailability(sourceA, true, 100L)
        
        val records = custody.getRecords()
        
        try {
            (records as MutableList).add(
                SourceAvailabilityRecord(999L, 0L, SourceId("Hacker"), false)
            )
        } catch (e: Exception) {
            // Unmodifiable or CopyOnWrite results in no affect on custody
        }

        assertEquals(1, custody.getRecords().size)
        assertEquals(sourceA, custody.getRecords().first().sourceId)
    }
}
