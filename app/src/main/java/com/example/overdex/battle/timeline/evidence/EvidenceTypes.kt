package com.example.overdex.battle.timeline.evidence

/**
 * Supporting visual data from screen captures or video frames.
 * 
 * @property sourceId Identifier of the source capture.
 * @property frameUri URI or path to the stored image frame.
 */
data class VisualEvidence(
    override val sourceId: String,
    val frameUri: String? = null
) : Evidence

/**
 * Supporting data from audio captures or microphone streams.
 * 
 * @property sourceId Identifier of the source audio segment.
 * @property audioUri URI or path to the stored audio file.
 */
data class AudioEvidence(
    override val sourceId: String,
    val audioUri: String
) : Evidence

/**
 * Supporting data derived from internal application or system state.
 * 
 * @property sourceId Identifier of the system component providing the state.
 * @property stateKey The specific key or property being recorded.
 * @property value The value of the state at the time of recording.
 */
data class StateEvidence(
    override val sourceId: String,
    val stateKey: String,
    val value: String
) : Evidence
