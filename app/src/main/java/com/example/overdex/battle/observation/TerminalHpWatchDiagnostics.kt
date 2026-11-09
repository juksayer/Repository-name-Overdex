package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.model.observation.VisualFramePublication

/** Bounded, bitmap-free accounting for the independent narrow reader. */
internal class TerminalHpWatchDiagnostics {
    private var frames = 0L
    private var advances = 0L
    private var skipped = 0L
    private var previousPublication: VisualFramePublication? = null
    private var previousSource: Long? = null
    private var maxSourceGap = 0L
    private var checks = 0L
    private var elapsed = 0L
    private var maxElapsed = 0L
    private var cpu = 0L
    private var cpuUnavailable = 0L
    private val outcomes = mutableMapOf<String, Long>()
    private val published = mutableMapOf<ActivePokemonSide, Long>()
    private var suppressed = 0L

    @Synchronized fun resetContinuity() {
        previousSource = null
        previousPublication = null
    }

    @Synchronized fun receive(sourceAt: Long, publication: VisualFramePublication?) {
        frames++
        previousSource?.let { if (sourceAt > it) maxSourceGap = maxOf(maxSourceGap, sourceAt - it) }
        if (previousSource == null || sourceAt > previousSource!!) previousSource = sourceAt
        val previous = previousPublication
        if (publication != null && previous != null && publication.streamId == previous.streamId &&
            publication.sequenceNumber > previous.sequenceNumber) {
            val advance = publication.sequenceNumber - previous.sequenceNumber
            advances += advance
            skipped += advance - 1L
        }
        if (publication != null && (previous == null || publication.streamId > previous.streamId ||
                (publication.streamId == previous.streamId && publication.sequenceNumber > previous.sequenceNumber))) {
            previousPublication = publication
        }
    }

    @Synchronized fun checked(outcome: String, elapsedNanos: Long, cpuNanos: Long?) {
        checks++
        elapsed += elapsedNanos.coerceAtLeast(0L)
        maxElapsed = maxOf(maxElapsed, elapsedNanos)
        if (cpuNanos == null) cpuUnavailable++ else cpu += cpuNanos.coerceAtLeast(0L)
        outcomes[outcome] = (outcomes[outcome] ?: 0L) + 1L
    }

    @Synchronized fun published(side: ActivePokemonSide) { published[side] = (published[side] ?: 0L) + 1L }
    @Synchronized fun suppressed() { suppressed++ }

    /**
     * Cumulative values. Regular receipt/location counters describe acquisition;
     * its shared sampling/submission/recording counters include both readers.
     */
    @Synchronized fun summary(reason: String): String = buildString {
        append("kind=TERMINAL_HP_WATCH reason=$reason frames=$frames sequenceAdvances=$advances skipped=$skipped")
        append(" maxSourceGapNanos=$maxSourceGap checks=$checks elapsedNanos=$elapsed maxCheckNanos=$maxElapsed")
        append(" cpuNanos=$cpu cpuUnavailable=$cpuUnavailable suppressed=$suppressed")
        outcomes.toSortedMap().forEach { (outcome, count) -> append(" $outcome=$count") }
        ActivePokemonSide.entries.forEach { append(" published${it.name}=${published[it] ?: 0L}") }
    }
}
