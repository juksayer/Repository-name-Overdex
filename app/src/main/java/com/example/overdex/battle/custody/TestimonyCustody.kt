package com.example.overdex.battle.custody

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import com.example.overdex.battle.time.ExternalClock
import com.example.overdex.battle.time.SystemExternalClock

/**
 * The "Bagman": Responsible for the safe, immutable preservation of testimony source 
 * operating signals and testimony.
 */
interface TestimonyCustody {
    /**
     * A stream of accepted testimony records.
     */
    val testimonyFlow: Flow<TestimonyRecord>

    /**
     * Every immutable custody record, including witness operating transitions.
     * Implementations that only provide testimony retain their historic behavior.
     */
    val custodyRecordFlow: Flow<CustodyRecord>
        get() = testimonyFlow.map { it as CustodyRecord }
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
        confidence: Float? = null,
        evidenceReferences: List<String> = emptyList()
    ): TestimonyRecord

    /** Preserves a source-captured monotonic reading alongside testimony. */
    fun submitTestimony(
        sourceId: SourceId,
        payload: TestimonyPayload,
        timestamp: Long,
        confidence: Float?,
        evidenceReferences: List<String>,
        monotonicTimeNanos: Long
    ): TestimonyRecord = submitTestimony(sourceId, payload, timestamp, confidence, evidenceReferences)

    /**
     * Retrieves all preserved records in the order they were received.
     */
    fun getRecords(): List<CustodyRecord>
}

/**
 * A thread-safe, in-memory implementation of [TestimonyCustody].
 */
class InMemoryTestimonyCustody(
    private val clock: ExternalClock = SystemExternalClock
) : TestimonyCustody {
    private val records = CopyOnWriteArrayList<CustodyRecord>()
    private val sequenceCounter = AtomicLong(0)

    private val _testimonyFlow = MutableSharedFlow<TestimonyRecord>(replay = 64, extraBufferCapacity = 64)
    override val testimonyFlow = _testimonyFlow.asSharedFlow()
    private val _custodyRecordFlow = MutableSharedFlow<CustodyRecord>(replay = 64, extraBufferCapacity = 128)
    override val custodyRecordFlow = _custodyRecordFlow.asSharedFlow()

    override fun submitAvailability(
        sourceId: SourceId,
        available: Boolean,
        timestamp: Long
    ): SourceAvailabilityRecord {
        val record = SourceAvailabilityRecord(
            sequenceNumber = sequenceCounter.getAndIncrement(),
            timestamp = timestamp,
            sourceId = sourceId,
            available = available,
            monotonicTimeNanos = clock.read().monotonicTimeNanos
        )
        records.add(record)
        _custodyRecordFlow.tryEmit(record)
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
            available = available,
            monotonicTimeNanos = clock.read().monotonicTimeNanos
        )
        records.add(record)
        _custodyRecordFlow.tryEmit(record)
        return record
    }

    override fun submitTestimony(
        sourceId: SourceId,
        payload: TestimonyPayload,
        timestamp: Long,
        confidence: Float?,
        evidenceReferences: List<String>
    ): TestimonyRecord {
        val record = TestimonyRecord(
            sequenceNumber = sequenceCounter.getAndIncrement(),
            timestamp = timestamp,
            sourceId = sourceId,
            payload = payload,
            confidence = confidence,
            evidenceReferences = evidenceReferences,
            monotonicTimeNanos = clock.read().monotonicTimeNanos
        )
        records.add(record)
        _custodyRecordFlow.tryEmit(record)
        _testimonyFlow.tryEmit(record)
        return record
    }

    override fun submitTestimony(
        sourceId: SourceId,
        payload: TestimonyPayload,
        timestamp: Long,
        confidence: Float?,
        evidenceReferences: List<String>,
        monotonicTimeNanos: Long
    ): TestimonyRecord {
        val record = TestimonyRecord(
            sequenceNumber = sequenceCounter.getAndIncrement(),
            timestamp = timestamp,
            sourceId = sourceId,
            payload = payload,
            confidence = confidence,
            evidenceReferences = evidenceReferences,
            monotonicTimeNanos = monotonicTimeNanos
        )
        records.add(record)
        _custodyRecordFlow.tryEmit(record)
        _testimonyFlow.tryEmit(record)
        return record
    }

    override fun getRecords(): List<CustodyRecord> = records.toList()
}
