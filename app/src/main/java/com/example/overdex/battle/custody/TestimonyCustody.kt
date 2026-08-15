package com.example.overdex.battle.custody

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * The "Bagman": Responsible for the safe, immutable preservation of testimony source 
 * operating signals and testimony.
 */
interface TestimonyCustody {
    /**
     * Accepts a source operational availability signal (online/offline).
     */
    fun submitAvailability(
        sourceId: SourceId,
        available: Boolean,
        timestamp: Long
    ): SourceAvailabilityRecord

    /**
     * Accepts a source-input availability signal.
     */
    fun submitInputAvailability(
        sourceId: SourceId,
        available: Boolean,
        timestamp: Long
    ): SourceInputRecord

    /**
     * Accepts testimony and preserves it in an immutable [TestimonyRecord].
     * 
     * The custody mechanism is responsible for assigning the monotonic sequence number.
     * 
     * @return The immutable record of the accepted testimony.
     */
    fun submitTestimony(
        sourceId: SourceId,
        payload: TestimonyPayload,
        timestamp: Long,
        confidence: Float,
        evidenceReferences: List<String> = emptyList()
    ): TestimonyRecord

    /**
     * Retrieves all preserved records in the order they were received.
     */
    fun getRecords(): List<CustodyRecord>
}

/**
 * A thread-safe, in-memory implementation of [TestimonyCustody].
 */
class InMemoryTestimonyCustody : TestimonyCustody {
    private val records = CopyOnWriteArrayList<CustodyRecord>()
    private val sequenceCounter = AtomicLong(0)

    override fun submitAvailability(
        sourceId: SourceId,
        available: Boolean,
        timestamp: Long
    ): SourceAvailabilityRecord {
        val record = SourceAvailabilityRecord(
            sequenceNumber = sequenceCounter.getAndIncrement(),
            timestamp = timestamp,
            sourceId = sourceId,
            available = available
        )
        records.add(record)
        return record
    }

    override fun submitInputAvailability(
        sourceId: SourceId,
        available: Boolean,
        timestamp: Long
    ): SourceInputRecord {
        val record = SourceInputRecord(
            sequenceNumber = sequenceCounter.getAndIncrement(),
            timestamp = timestamp,
            sourceId = sourceId,
            available = available
        )
        records.add(record)
        return record
    }

    override fun submitTestimony(
        sourceId: SourceId,
        payload: TestimonyPayload,
        timestamp: Long,
        confidence: Float,
        evidenceReferences: List<String>
    ): TestimonyRecord {
        val record = TestimonyRecord(
            sequenceNumber = sequenceCounter.getAndIncrement(),
            timestamp = timestamp,
            sourceId = sourceId,
            payload = payload,
            confidence = confidence,
            evidenceReferences = evidenceReferences
        )
        records.add(record)
        return record
    }

    override fun getRecords(): List<CustodyRecord> = records.toList()
}
