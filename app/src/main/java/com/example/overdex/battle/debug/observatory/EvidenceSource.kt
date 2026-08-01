package com.example.overdex.battle.debug.observatory

/**
 * Interface for any component that produces a stream of evidence events for the observatory.
 */
interface EvidenceSource {
    /** The human-readable name of the evidence source. */
    val name: String
    
    /**
     * Starts recording evidence from this source.
     * 
     * @param syncMatchId Optional ID of an active Match to align timelines.
     */
    fun startRecording(syncMatchId: String? = null)
    
    /**
     * Stops the current recording Match for this source.
     */
    fun stopRecording()
    
    /**
     * Clears all recorded events from this source's buffer.
     */
    fun clear()
    
    /**
     * Returns the list of captured events in strict chronological order.
     */
    fun getEvents(): List<EvidenceEvent>
}

/**
 * Base contract for a single event captured by an [EvidenceSource].
 */
interface EvidenceEvent {
    /** The monotonic sequence number of the event. */
    val sequenceNumber: Long
    /** The absolute system time when the event was captured. */
    val timestamp: Long
    /** The time in milliseconds relative to the start of the recording Match. */
    val relativeTimestamp: Long
    /** The name of the [EvidenceSource] that produced this event. */
    val sourceName: String
}
