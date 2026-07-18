package com.example.overdex.battle.timeline.evidence

/**
 * Supporting data from screen or video frames.
 */
data class VisualEvidence(
    override val sourceId: String,
    val frameUri: String
) : Evidence

/**
 * Supporting data from audio captures.
 */
data class AudioEvidence(
    override val sourceId: String,
    val audioUri: String
) : Evidence

/**
 * Supporting data from internal application or system state.
 */
data class StateEvidence(
    override val sourceId: String,
    val stateKey: String,
    val value: String
) : Evidence
