package com.example.overdex.model

/**
 * A shared interface for any record that can be chronologically ordered within a Match timeline.
 */
interface TimelineRecord {
    /** The temporal position of the record (e.g., when it was perceived or occurred). */
    val perceivedAt: Long
}
