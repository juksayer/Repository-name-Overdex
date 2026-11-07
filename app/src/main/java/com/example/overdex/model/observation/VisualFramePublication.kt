package com.example.overdex.model.observation

import java.util.concurrent.atomic.AtomicLong

/**
 * Process-local progress of frames accepted by the live visual feed. This is
 * publication metadata, not the source capture time or proof a reader saw it.
 */
data class VisualFramePublication(
    val streamId: Long,
    val sequenceNumber: Long,
    val publishedAtMonotonicTimeNanos: Long,
)

/** Keeps publication progress readable without collecting or retaining a bitmap. */
internal class VisualFramePublicationTracker {
    private val lock = Any()
    private var activeStreamId: Long? = null
    private var sequenceNumber = 0L
    @Volatile private var latest: VisualFramePublication? = null

    fun latest(): VisualFramePublication? = latest

    fun startStream(): Long = synchronized(lock) {
        val streamId = nextStreamId.incrementAndGet()
        activeStreamId = streamId
        sequenceNumber = 0L
        latest = null
        streamId
    }

    /** A rejected emission must not consume a sequence number or advertise progress. */
    fun publish(
        streamId: Long,
        publishedAtMonotonicTimeNanos: Long,
        tryEmit: (VisualFramePublication) -> Boolean,
    ): Boolean = synchronized(lock) {
        if (activeStreamId != streamId) return@synchronized false
        val publication = VisualFramePublication(streamId, sequenceNumber + 1L,
            publishedAtMonotonicTimeNanos)
        if (!tryEmit(publication)) return@synchronized false
        // An emitter may resume an unconfined consumer inline. If that consumer
        // stops/replaces capture, its newer state must win over this old frame.
        if (activeStreamId == streamId) {
            sequenceNumber = publication.sequenceNumber
            latest = publication
        }
        true
    }

    /** An old capture callback or service teardown cannot reopen/clear a newer stream. */
    fun stopStream(streamId: Long) = synchronized(lock) {
        if (activeStreamId == streamId) {
            activeStreamId = null
            sequenceNumber = 0L
            latest = null
        }
    }

    private companion object {
        val nextStreamId = AtomicLong()
    }
}
