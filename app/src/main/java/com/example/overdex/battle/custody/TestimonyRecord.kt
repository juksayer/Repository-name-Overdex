package com.example.overdex.battle.custody

/**
 * The immutable architectural envelope for a single piece of testimony.
 * 
 * Once a [TestimonyRecord] is created and accepted by Custody, it represents 
 * a permanent historical record of what was witnessed.
 * 
 * @property sequenceNumber A monotonic counter assigned by Custody upon receipt.
 * @property timestamp The temporal position (e.g., system time) provided by the source.
 * @property sourceId The unique identifier of the testimony source.
 * @property payload The neutral testimony data.
 * @property confidence The source's certainty score (0.0 to 1.0).
 * @property evidenceReferences References to the raw evidence supporting this testimony.
 */
data class TestimonyRecord(
    override val sequenceNumber: Long,
    override val timestamp: Long,
    override val sourceId: SourceId,
    val payload: TestimonyPayload,
    val confidence: Float,
    val evidenceReferences: List<String> = emptyList()
) : CustodyRecord
