package com.example.overdex.battle.debug.observatory

/**
 * A formal interface for any component that produces a stream of evidence events
 * for the Signal Observatory.
 */
interface EvidenceSource {
    val name: String
    
    /**
     * Starts recording evidence.
     * @param syncSessionId Optional ID of an active ObservationSession to align timelines.
     */
    fun startRecording(syncSessionId: String? = null)
    
    /**
     * Stops the current recording session.
     */
    fun stopRecording()
    
    /**
     * Clears all recorded events.
     */
    fun clear()
    
    /**
     * Returns the list of captured events in strict chronological order.
     */
    fun getEvents(): List<EvidenceEvent>
}

/**
 * Base interface for all evidence events captured by the observatory.
 */
interface EvidenceEvent {
    val sequenceNumber: Long
    val timestamp: Long
    val relativeTimestamp: Long
    val sourceName: String
}
