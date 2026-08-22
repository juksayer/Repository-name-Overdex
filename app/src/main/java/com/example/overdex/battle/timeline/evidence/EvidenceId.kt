package com.example.overdex.battle.timeline.evidence

/**
 * Uniquely identifies a specific piece of Evidence within the Overdex system.
 * 
 * Unlike sourceId, which identifies the origin (who), the EvidenceId identifies
 * the individual phenomenon captured (which one).
 */
data class EvidenceId(val value: String)
