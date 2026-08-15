package com.example.overdex.battle.custody

/**
 * The minimal common abstraction for any record held in custody.
 * 
 * This interface exists solely to allow a mixed chronological collection
 * of different record types (testimony, availability, input) without 
 * imposing a complex domain hierarchy.
 */
interface CustodyRecord {
    val sequenceNumber: Long
    val timestamp: Long
    val sourceId: SourceId
}
