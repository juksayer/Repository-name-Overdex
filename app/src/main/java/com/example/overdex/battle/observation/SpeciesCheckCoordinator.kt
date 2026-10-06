package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.SpeciesCheckMeasured

/** Monotonic species checks with a measured latency target. Capture workers and OCR share this per-match state. */
class SpeciesCheckCoordinator(
    private val now: () -> Long = System::nanoTime,
    private val report: (SpeciesCheckMeasured, List<String>) -> Unit
) {
    data class Window(val id: Long, val openedAt: Long, val triggerAt: Long, val reason: String, val triggerId: String?)
    private data class State(
        var window: Window? = null,
        var nextRecoveryAt: Long = 0,
        var candidate: String? = null,
        val reads: MutableList<String> = mutableListOf(),
        var completed: Window? = null,
        var targetMissed: Boolean = false,
        var deliveredSpecies: String? = null,
        val recentWindows: LinkedHashMap<Long, Window> = linkedMapOf(),
        var latestAcceptedWindowOpenedAt: Long = Long.MIN_VALUE
    )
    private val states = ActivePokemonSide.entries.associateWith { State() }
    private var serial = 0L
    private var stopped = false

    @Synchronized fun request(side: ActivePokemonSide, reason: String, triggerId: String?, triggerAt: Long = now()) {
        if (stopped) return
        val state = states.getValue(side)
        reportMissedTarget(side, state)
        if (state.window != null) {
            // A lifecycle cue supersedes an unrelated recovery/countdown check.
            // Repeated samples from the same cue keep their in-flight agreement.
            if (reason !in LIFECYCLE_REASONS || state.window?.triggerId == triggerId) return
            val superseded = state.window!!
            emit(side, superseded, "SUPERSEDED", null, listOfNotNull(superseded.triggerId))
            state.recentWindows.remove(superseded.id)
        }
        val window = Window(++serial, now(), triggerAt, reason, triggerId)
        state.window = window
        state.recentWindows[window.id] = window
        while (state.recentWindows.size > MAX_RETAINED_WINDOWS) {
            state.recentWindows.remove(state.recentWindows.keys.first())
        }
        state.targetMissed = false
        state.candidate = null
        state.reads.clear()
        emit(side, window, "OPENED", null, listOfNotNull(triggerId))
    }

    @Synchronized fun captureEnabled(side: ActivePokemonSide): Boolean {
        if (stopped) return false
        val state = states.getValue(side)
        reportMissedTarget(side, state)
        // An occasional recovery window prevents a missed cue from freezing identity.
        if (state.window == null && now() >= state.nextRecoveryAt) request(side, "RECOVERY", null)
        return state.window != null
    }

    @Synchronized fun windowFor(side: ActivePokemonSide, capturedAt: Long): Window? {
        val state = states.getValue(side)
        reportMissedTarget(side, state)
        return state.window?.takeIf { capturedAt >= it.openedAt }
    }

    /** Two agreeing, distinct source frames; raw contradictory reads remain separate testimony. */
    @Synchronized fun read(side: ActivePokemonSide, windowId: Long, species: String?, cropId: String): List<String>? {
        val state = states.getValue(side)
        reportMissedTarget(side, state)
        val window = state.window?.takeIf { it.id == windowId } ?: return null
        if (species == null) { state.candidate = null; state.reads.clear(); return null }
        if (state.candidate != species) {
            if (state.candidate != null) emit(side, window, "DISAGREEMENT", species, state.reads + cropId)
            state.candidate = species
            state.reads.clear()
        }
        if (cropId !in state.reads) state.reads += cropId
        if (state.reads.size < 2) return null
        val refs = listOfNotNull(window.triggerId) + state.reads
        state.window = null
        state.nextRecoveryAt = now() + RECOVERY_INTERVAL_NANOS
        state.completed = window
        state.deliveredSpecies = species
        emit(side, window, "CONFIRMED", species, refs)
        return refs
    }

    /** Accepts one high-confidence, purpose-specific badge read for live presentation. */
    @Synchronized fun acceptImmediate(
        side: ActivePokemonSide,
        windowId: Long,
        species: String,
        cropId: String,
        capturedAtMonotonicTimeNanos: Long = now()
    ): List<String>? {
        val state = states.getValue(side)
        val window = state.recentWindows[windowId] ?: return null
        if (capturedAtMonotonicTimeNanos < window.openedAt ||
            capturedAtMonotonicTimeNanos - window.openedAt > MAX_WINDOW_NANOS ||
            window.openedAt < state.latestAcceptedWindowOpenedAt
        ) return null
        val refs = listOfNotNull(window.triggerId) + cropId
        if (state.window?.id == windowId) state.window = null
        state.nextRecoveryAt = now() + RECOVERY_INTERVAL_NANOS
        state.completed = window
        state.latestAcceptedWindowOpenedAt = window.openedAt
        state.deliveredSpecies = species
        state.candidate = species
        state.reads.clear()
        state.reads += cropId
        emit(side, window, "CONFIRMED", species, refs)
        return refs
    }

    /** Called after the identity article updates presentation, including repeated confirmations. */
    @Synchronized fun delivered(side: ActivePokemonSide, species: String, articleId: String) {
        val state = states.getValue(side)
        val window = state.completed?.takeIf { state.deliveredSpecies == species } ?: return
        emit(side, window, "IDENTITY_DELIVERED", species, listOf(articleId))
        state.completed = null
    }

    @Synchronized fun isChecking(side: ActivePokemonSide): Boolean = states.getValue(side).window != null

    @Synchronized fun isStopped(): Boolean = stopped

    @Synchronized fun tick() { states.forEach { (side, state) -> reportMissedTarget(side, state) } }

    /**
     * Closes only the current attempt's incomplete work. The Match and its
     * earlier testimony remain intact, and later Team Select cues may open new
     * windows in the same field session.
     */
    @Synchronized fun restartObservationAttempt() {
        if (stopped) return
        states.forEach { (side, state) ->
            state.window?.let { emit(side, it, "RESTARTED", state.candidate, listOfNotNull(it.triggerId) + state.reads) }
            state.window = null
            state.nextRecoveryAt = 0L
            state.candidate = null
            state.reads.clear()
            state.completed = null
            state.targetMissed = false
            state.deliveredSpecies = null
            state.recentWindows.clear()
            state.latestAcceptedWindowOpenedAt = Long.MIN_VALUE
        }
    }

    @Synchronized fun stop() {
        stopped = true
        states.forEach { (side, state) ->
            state.window?.let { emit(side, it, "STOPPED", null, listOfNotNull(it.triggerId)) }
            state.window = null
        }
    }
    private fun reportMissedTarget(side: ActivePokemonSide, state: State) {
        val window = state.window ?: return
        val elapsed = now() - window.openedAt
        if (!state.targetMissed && elapsed >= TARGET_NANOS) {
            emit(side, window, "TIMED_OUT", state.candidate, listOfNotNull(window.triggerId) + state.reads)
            // Keep collecting briefly after the latency target. This preserves
            // a late second agreeing read without leaving stale OCR active for
            // the rest of the match.
            state.targetMissed = true
        }
        if (elapsed >= MAX_WINDOW_NANOS) {
            emit(side, window, "EXPIRED", state.candidate, listOfNotNull(window.triggerId) + state.reads)
            state.window = null
            state.candidate = null
            state.reads.clear()
            state.nextRecoveryAt = now() + RECOVERY_INTERVAL_NANOS
        }
    }
    private fun emit(side: ActivePokemonSide, window: Window, status: String, species: String?, refs: List<String>) {
        report(SpeciesCheckMeasured(side, window.id, status, window.reason, window.triggerAt,
            (now() - window.triggerAt).coerceAtLeast(0), TARGET_NANOS, species), refs)
    }
    companion object {
        const val TARGET_NANOS = 1_500_000_000L
        const val MAX_WINDOW_NANOS = 3_000_000_000L
        const val SAMPLE_INTERVAL_NANOS = 150_000_000L
        const val RECOVERY_INTERVAL_NANOS = 5_000_000_000L
        const val MAX_RETAINED_WINDOWS = 8
        private val LIFECYCLE_REASONS = setOf("ENTRY", "SWITCH", "FAINT", "CRY", "MATCH_START")
    }
}
