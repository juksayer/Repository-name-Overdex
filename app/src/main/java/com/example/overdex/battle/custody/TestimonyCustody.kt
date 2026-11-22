package com.example.overdex.battle.custody

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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

    /** Result inputs and attempt boundaries, kept in original acceptance order. */
    val outcomePublicationFlow: Flow<TestimonyRecord> get() = testimonyFlow.filter { it.isOutcomePublicationInput() }

    /** Accepted receipts for one crop, including receipts preserved before collection began. */
    fun acceptedCrops(cropName: String): Flow<TestimonyRecord> =
        testimonyFlow.filter { (it.payload as? CropCaptured)?.cropProvenance?.cropName == cropName }

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

internal fun TestimonyRecord.isOutcomePublicationInput(): Boolean = when (payload) {
    is CropCaptured -> payload.cropProvenance.cropName == com.example.overdex.battle.observation.BattleCropContracts.matchOutcome.cropName
    ObservationRestartedByUser, ObservationSessionStoppedByUser, ScreenIgnoredByUser -> true
    is RawTestimony, is MatchOutcomeLabelWitnessed -> when (sourceId.id) {
        "MATCH_OUTCOME_TEXT_WITNESS", "YOU_WIN_WITNESS", "GOOD_EFFORT_WITNESS",
        com.example.overdex.battle.observation.ROCKET_SUMMARY_WITNESS -> true
        else -> false
    }
    else -> false
}

/**
 * A thread-safe, in-memory implementation of [TestimonyCustody].
 */
class InMemoryTestimonyCustody(
    private val clock: ExternalClock = SystemExternalClock
) : TestimonyCustody {
    private val lock = Any()
    private val records = mutableListOf<CustodyRecord>()
    private var nextSequenceNumber = 0L

    // Every consumer walks the preserved record ledger by index. A StateFlow only
    // announces that the ledger grew; it never carries the record itself, so a
    // burst cannot silently discard accepted testimony before Match can publish
    // the corresponding Timeline article.
    private val recordCount = MutableStateFlow(0)

    // Only requested crops get an index. It holds references to the original receipts,
    // so busy OCR can resume by index without copying pixels or waking on every battle event.
    private class ReceiptIndex(val receipts: MutableList<TestimonyRecord>) {
        val count = MutableStateFlow(receipts.size)
    }
    private val cropIndices = mutableMapOf<String, ReceiptIndex>()

    override fun acceptedCrops(cropName: String): Flow<TestimonyRecord> = synchronized(lock) {
        val index = cropIndices.getOrPut(cropName) {
            ReceiptIndex(records.filterIsInstance<TestimonyRecord>().filterTo(mutableListOf()) {
                (it.payload as? CropCaptured)?.cropProvenance?.cropName == cropName
            })
        }
        preservedRecords(index.receipts, index.count)
    }

    private val outcomeIndex = ReceiptIndex(mutableListOf())
    override val outcomePublicationFlow = preservedRecords(outcomeIndex.receipts, outcomeIndex.count)

    override val testimonyFlow: Flow<TestimonyRecord> = preservedRecords(records, recordCount)
        .filter { it is TestimonyRecord }
        .map { it as TestimonyRecord }
    override val custodyRecordFlow: Flow<CustodyRecord> = preservedRecords(records, recordCount)

    override fun submitAvailability(
        sourceId: SourceId,
        available: Boolean,
        timestamp: Long
    ): SourceAvailabilityRecord = preserve { sequenceNumber ->
        SourceAvailabilityRecord(
            sequenceNumber = sequenceNumber,
            timestamp = timestamp,
            sourceId = sourceId,
            available = available,
            monotonicTimeNanos = clock.read().monotonicTimeNanos
        )
    }

    override fun submitInputAvailability(
        sourceId: SourceId,
        available: Boolean,
        timestamp: Long
    ): SourceInputRecord = preserve { sequenceNumber ->
        SourceInputRecord(
            sequenceNumber = sequenceNumber,
            timestamp = timestamp,
            sourceId = sourceId,
            available = available,
            monotonicTimeNanos = clock.read().monotonicTimeNanos
        )
    }

    override fun submitTestimony(
        sourceId: SourceId,
        payload: TestimonyPayload,
        timestamp: Long,
        confidence: Float?,
        evidenceReferences: List<String>
    ): TestimonyRecord = submitTestimony(
        sourceId = sourceId,
        payload = payload,
        timestamp = timestamp,
        confidence = confidence,
        evidenceReferences = evidenceReferences,
        monotonicTimeNanos = clock.read().monotonicTimeNanos
    )

    override fun submitTestimony(
        sourceId: SourceId,
        payload: TestimonyPayload,
        timestamp: Long,
        confidence: Float?,
        evidenceReferences: List<String>,
        monotonicTimeNanos: Long
    ): TestimonyRecord = preserve { sequenceNumber ->
        TestimonyRecord(
            sequenceNumber = sequenceNumber,
            timestamp = timestamp,
            sourceId = sourceId,
            payload = payload,
            confidence = confidence,
            evidenceReferences = evidenceReferences,
            monotonicTimeNanos = monotonicTimeNanos
        )
    }

    override fun getRecords(): List<CustodyRecord> = synchronized(lock) { records.toList() }

    private fun <T> preservedRecords(rows: List<T>, count: StateFlow<Int>): Flow<T> = flow {
        var nextIndex = 0
        count.collect { availableCount ->
            while (nextIndex < availableCount) {
                val record = synchronized(lock) { rows[nextIndex++] }
                emit(record)
            }
        }
    }

    private fun <T : CustodyRecord> preserve(create: (Long) -> T): T = synchronized(lock) {
        val record = create(nextSequenceNumber++)
        records += record
        if (record is TestimonyRecord && record.payload is CropCaptured) {
            cropIndices[record.payload.cropProvenance.cropName]?.let { index ->
                index.receipts += record
                index.count.value = index.receipts.size
            }
        }
        if (record is TestimonyRecord && record.isOutcomePublicationInput()) {
            outcomeIndex.receipts += record
            outcomeIndex.count.value = outcomeIndex.receipts.size
        }
        recordCount.value = records.size
        record
    }
}
