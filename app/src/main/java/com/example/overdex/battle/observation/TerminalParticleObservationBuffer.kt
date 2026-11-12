package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.TerminalParticleFeature
import com.example.overdex.battle.custody.TerminalParticleObservations
import com.example.overdex.battle.custody.TerminalParticleSample
import com.example.overdex.model.observation.VisualFramePublication

/** Single-owner, numeric-only buffer. It never retains Bitmaps or creates a battle event. */
internal class TerminalParticleObservationBuffer(
    private val resetPixels: () -> Unit,
    private val emit: (TerminalParticleObservations, List<String>, Long, Long) -> Unit,
    private val now: () -> Long = System::nanoTime,
) {
    private var assignment: TerminalParticleAssignment? = null
    private val samples = mutableListOf<TerminalParticleSample>()
    private var lastAt: Long? = null
    private var lastWall = 0L
    private var total = 0
    private var lastPublication: VisualFramePublication? = null

    fun synchronize(next: TerminalParticleAssignment?, wallTime: Long) {
        val old = assignment
        if (old?.phaseAt != next?.phaseAt || old?.side != next?.side) {
            flush(ended = true)
            resetPixels()
            lastAt = null; lastPublication = null; total = 0
            lastWall = wallTime
        } else if (old != next) {
            // Keep the context actually available when the readings were made.
            // Later Used recognition is separate evidence, not a rewritten batch.
            flush(ended = false)
        }
        assignment = next
    }

    fun accept(
        width: Int, height: Int, bounds: BattleCropBounds?, at: Long, wallTime: Long,
        publication: VisualFramePublication?, readable: Boolean,
        independentAppearanceReadable: Boolean = false,
        measure: () -> TerminalParticleFrameMeasurement,
    ): String? {
        val window = assignment ?: return null
        if (at !in window.phaseAt until window.until || at <= (lastAt ?: Long.MIN_VALUE) || total >= MAX_SAMPLES) return null
        val started = now()
        val priorPublication = lastPublication
        val streamChanged = priorPublication != null && publication?.streamId != priorPublication.streamId
        val oldPublication = priorPublication != null && publication != null &&
            (publication.streamId < priorPublication.streamId ||
                (publication.streamId == priorPublication.streamId && publication.sequenceNumber <= priorPublication.sequenceNumber))
        val unavailable = when {
            started - at !in 0..TerminalVisualCaptureGate.MAX_SOURCE_AGE_NANOS -> "SOURCE_STALE"
            oldPublication -> "OLD_PUBLICATION"
            bounds == null && !independentAppearanceReadable -> "INVALID_BOUNDS"
            !readable -> "OBSCURED"
            else -> null
        }
        if (unavailable != null || streamChanged) resetPixels()
        val reading = if (unavailable == null) measure() else null
        val status = unavailable ?: if (streamChanged && reading?.status == TerminalParticleReadStatus.BASELINE)
            "STREAM_CHANGED" else requireNotNull(reading).status.name
        // Do not let an old stream replace the current publication watermark.
        if (!oldPublication) lastPublication = publication
        lastAt = at; lastWall = wallTime; total++
        samples += TerminalParticleSample(at, wallTime, reading?.previousSourceAtNanos, status, width, height,
            bounds?.let { listOf(it.left, it.top, it.right, it.bottom) },
            publication?.streamId, publication?.sequenceNumber, (now() - started).coerceAtLeast(0L),
            reading?.cluster.orEmpty().map { TerminalParticleFeature(it.x, it.y, it.area, it.width, it.height,
                it.freshFraction, it.haloCoverage, it.haloWeakestQuadrant, it.brightnessFalloff) },
            reading?.flashes.orEmpty(), reading?.appearance)
        if (samples.size >= BATCH_SIZE || at - samples.first().sourceAtNanos >= BATCH_SPAN_NANOS) flush(false)
        return status
    }

    fun flushIfDue() {
        if (samples.firstOrNull()?.let { now() - it.sourceAtNanos >= BATCH_SPAN_NANOS } == true) flush(false)
    }

    fun flush(ended: Boolean) {
        val window = assignment ?: return
        if (samples.isEmpty() && !ended) return
        emit(TerminalParticleObservations(window.side, window.phaseAt, window.until,
            samples.toList(), ended, total), window.evidenceReferences, lastAt ?: window.phaseAt, lastWall)
        samples.clear()
    }

    companion object {
        const val BATCH_SIZE = 20
        const val MAX_SAMPLES = 600
        const val BATCH_SPAN_NANOS = 1_000_000_000L
    }
}
