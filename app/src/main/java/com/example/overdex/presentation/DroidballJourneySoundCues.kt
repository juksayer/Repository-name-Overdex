package com.example.overdex.presentation

enum class DroidballSoundCue { ROLL_START, ROLL_STOP, SKID, EDGE_IMPACT }

/** Sound follows visible movement; late frames never replay expired rolling intervals. */
class DroidballJourneySoundCues {
    private var currentState: DroidballJourneyState? = null
    private var lastElapsedMillis: Long? = null
    private var activeRollInterval: Int? = null
    private var sawArrivalRoll = false
    private var arrivalSkidHandled = false
    private var sawArrivalSnap = false

    /** A removed/paused renderer must not leave a loop playing without frame ticks. */
    fun hidden(state: DroidballJourneyState): List<DroidballSoundCue> {
        val current = currentState ?: return emptyList()
        if (current.generation != state.generation || current.phase != state.phase) return emptyList()
        val wasRolling = activeRollInterval != null
        activeRollInterval = null
        lastElapsedMillis = null
        sawArrivalRoll = false
        sawArrivalSnap = false
        // A resumed roll can restart, but a played or skipped skid belongs to
        // this generation and must never replay when its renderer resumes.
        return if (wasRolling) listOf(DroidballSoundCue.ROLL_STOP) else emptyList()
    }

    fun phaseChanged(state: DroidballJourneyState): List<DroidballSoundCue> {
        val previous = currentState
        if (previous != null && state.generation <= previous.generation) return emptyList()

        val cues = buildList {
            if (activeRollInterval != null) add(DroidballSoundCue.ROLL_STOP)
            if (previous != null && completedVisibleArrival(previous, state)) {
                add(DroidballSoundCue.EDGE_IMPACT)
            }
        }
        currentState = state
        lastElapsedMillis = null
        activeRollInterval = null
        sawArrivalRoll = false
        arrivalSkidHandled = false
        sawArrivalSnap = false
        return cues
    }

    fun frame(state: DroidballJourneyState, elapsedMillis: Long): List<DroidballSoundCue> {
        // Rendering can arrive before the global state collector observes this generation.
        val cues = phaseChanged(state).toMutableList()
        val current = currentState ?: return cues
        if (state.generation != current.generation || state.phase != current.phase ||
            state.startedAtNanos != current.startedAtNanos) return cues

        val elapsed = elapsedMillis.coerceAtLeast(0L)
        if (lastElapsedMillis?.let { elapsed <= it } == true) return cues
        lastElapsedMillis = elapsed

        val interval = rollingInterval(state.phase, elapsed)
        if (interval != activeRollInterval) {
            if (activeRollInterval != null) cues += DroidballSoundCue.ROLL_STOP
            if (interval != null) cues += DroidballSoundCue.ROLL_START
            activeRollInterval = interval
        }
        if (state.phase == DroidballJourneyPhase.ARRIVING) {
            if (interval == 0) sawArrivalRoll = true
            if (interval == 1) sawArrivalSnap = true
            val duration = DroidballChoreography.ARRIVAL_MILLIS
            if (!arrivalSkidHandled && elapsed >= ceilingPercent(duration, 39L)) {
                arrivalSkidHandled = true
                // Brake only after a visible roll. A first frame in or beyond
                // the skid window must not replay an entrance the user missed.
                if (sawArrivalRoll && elapsed < ceilingPercent(duration, 50L)) {
                    cues += DroidballSoundCue.SKID
                }
            }
        }
        return cues
    }

    private fun completedVisibleArrival(previous: DroidballJourneyState, next: DroidballJourneyState): Boolean {
        val duration = DroidballChoreography.ARRIVAL_MILLIS
        return previous.phase == DroidballJourneyPhase.ARRIVING && next.phase == DroidballJourneyPhase.FIELD &&
            next.generation == previous.generation + 1 && sawArrivalSnap &&
            (lastElapsedMillis ?: 0L) >= ceilingPercent(duration, 90L) &&
            next.startedAtNanos >= previous.startedAtNanos &&
            next.startedAtNanos - previous.startedAtNanos >= duration * 1_000_000L
    }

    /** Half-open windows mirror the movement intervals in [DroidballChoreography]. */
    private fun rollingInterval(phase: DroidballJourneyPhase, elapsed: Long): Int? {
        val duration = DroidballChoreography.durationMillis(phase)
        if (elapsed >= duration) return null
        return when (phase) {
            DroidballJourneyPhase.DEPARTING -> if (elapsed >= duration * 57L / 100L) 0 else null
            DroidballJourneyPhase.ARRIVING -> when {
                elapsed < ceilingPercent(duration, 39L) -> 0
                elapsed >= duration * 85L / 100L -> 1
                else -> null
            }
            DroidballJourneyPhase.RETURNING -> if (elapsed >= duration * 12L / 100L) 0 else null
            DroidballJourneyPhase.DOCKING -> if (elapsed < ceilingPercent(duration, 55L)) 0 else null
            else -> null
        }
    }

    private fun ceilingPercent(duration: Long, percent: Long) = (duration * percent + 99L) / 100L
}
