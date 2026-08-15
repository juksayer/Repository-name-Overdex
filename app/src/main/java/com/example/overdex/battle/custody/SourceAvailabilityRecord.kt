package com.example.overdex.battle.custody

/**
 * Represents a historical record of a source's operational availability (online/offline).
 */
data class SourceAvailabilityRecord(
    override val sequenceNumber: Long,
    override val timestamp: Long,
    override val sourceId: SourceId,
    val available: Boolean
) : CustodyRecord
