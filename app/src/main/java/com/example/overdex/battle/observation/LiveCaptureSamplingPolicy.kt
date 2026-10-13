package com.example.overdex.battle.observation

/**
 * Publication cadence for the shared MediaProjection frame stream.
 *
 * The threshold is deliberately a little shorter than one 30 Hz period. A
 * display callback that arrives a fraction early must not make us skip the
 * intended every-other frame on a 60 Hz display and wait for a third refresh.
 */
internal object LiveCaptureSamplingPolicy {
    const val TARGET_FRAMES_PER_SECOND = 30
    const val MIN_CAPTURE_INTERVAL_NANOS = 30_000_000L

    fun shouldPublish(lastPublishedNanos: Long?, receivedAtNanos: Long): Boolean =
        lastPublishedNanos == null ||
            receivedAtNanos - lastPublishedNanos >= MIN_CAPTURE_INTERVAL_NANOS
}

/** Reject our instrument and any queued transition frames before they become evidence. */
internal class LiveCaptureVisibilityGate {
    @Volatile private var resumeAfterNanos: Long = Long.MAX_VALUE

    fun instrumentVisible(visible: Boolean, nowNanos: Long) {
        resumeAfterNanos = if (visible) Long.MAX_VALUE else nowNanos + 250_000_000L
    }

    fun permits(receivedAtNanos: Long): Boolean = receivedAtNanos >= resumeAfterNanos
}
