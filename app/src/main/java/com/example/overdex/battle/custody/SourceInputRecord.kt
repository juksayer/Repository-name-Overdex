package com.example.overdex.battle.custody

/**
 * Represents a historical record of source-input availability for a testimony source.
 */
data class SourceInputRecord(
    override val sequenceNumber: Long,
    override val timestamp: Long,
    override val sourceId: SourceId,
    val available: Boolean
) : CustodyRecord
