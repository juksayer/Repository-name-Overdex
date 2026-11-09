package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource
import kotlinx.coroutines.*

/** Resolve type badges in bounded bursts at battle lifecycle cues, then leave them at rest. */
class ActiveTypeSamplingGate : Observer {
    override val observerId = ObserverId("ACTIVE_TYPE_SAMPLING_GATE", ObservationSource.SCREEN_CAPTURE)
    override val name = "Active Type Sampling Gate"
    override val managesAvailability = true
    private data class State(
        var last: Long? = null,
        var remaining: Int = 3,
        var species: String? = null,
        var armedAt: Long = Long.MIN_VALUE,
        var speciesAt: Long = Long.MIN_VALUE,
        var resolved: Boolean = false,
    )
    private val states = ActivePokemonSide.entries.associateWith { State() }
    private var scope: CoroutineScope? = null

    @Synchronized fun accepts(side: ActivePokemonSide, at: Long): Boolean {
        val state = states.getValue(side)
        return state.remaining > 0 && at >= state.armedAt &&
            (state.last?.let { at - it >= SAMPLE_INTERVAL_NANOS } ?: true)
    }
    @Synchronized fun captured(side: ActivePokemonSide, at: Long) {
        val state = states.getValue(side)
        state.last = at
        state.remaining = (state.remaining - 1).coerceAtLeast(0)
    }
    /** Captured attempts may still be waiting after the capture budget has been spent. */
    @Synchronized fun needsReading(side: ActivePokemonSide, capturedAt: Long): Boolean {
        val state = states.getValue(side)
        return !state.resolved && capturedAt >= state.armedAt
    }
    @Synchronized internal fun trigger(side: ActivePokemonSide? = null, at: Long = Long.MIN_VALUE) {
        states.filterKeys { side == null || it == side }.values.forEach {
            if (at == Long.MIN_VALUE || at > it.armedAt) {
                it.armedAt = maxOf(it.armedAt, at)
                it.remaining = 3
                it.resolved = false
            }
        }
    }
    @Synchronized internal fun species(side: ActivePokemonSide, name: String, at: Long = Long.MIN_VALUE) {
        val state = states.getValue(side)
        if (at < state.speciesAt) return
        state.speciesAt = at
        if (!name.equals(state.species, ignoreCase = true)) { state.species = name; trigger(side, at) }
    }
    @Synchronized internal fun resolved(side: ActivePokemonSide, at: Long) {
        val state = states.getValue(side)
        // A queued read for the departed appearance cannot close its replacement's burst.
        if (at >= state.armedAt) { state.remaining = 0; state.resolved = true }
    }

    internal fun observe(payload: TestimonyPayload, at: Long, sourceId: String = "") {
        when (payload) {
            is MatchStarted -> trigger(at = at)
            is ActivePokemonSpeciesWitnessed -> species(payload.side, payload.speciesName, at)
            is ActivePokemonTypesWitnessed -> resolved(payload.side, at)
            is ActivePokemonFaintedWitnessed -> trigger(payload.side, at)
            GetReadyWitnessed, AttackIncoming, ChargeMoveUsedAnnounced,
            is ChargeMoveQteVibrationPatternInferred -> trigger(at = at)
            is SpeciesCheckMeasured -> if (payload.status == "OPENED" &&
                payload.reason in setOf("ENTRY", "SWITCH", "FAINT", "CRY", "MATCH_START", "CHARGE", "QTE")) {
                trigger(payload.side, payload.triggerMonotonicNanos)
            }
            is BattleCryCandidatesMeasured -> if (payload.candidates.isNotEmpty() && payload.cueKind != "FAST_MOVE_IMPACT") trigger(at = at)
            is RawTestimony -> if (sourceId == "ANNOUNCEMENT_WITNESS" &&
                (payload.data as? String)?.trim()?.startsWith("Go,", true) == true) trigger(at = at)
            else -> Unit
        }
    }

    override fun start(match: Match) {
        if (scope != null) return
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { workers ->
            workers.launch(start = CoroutineStart.UNDISPATCHED) {
                match.articles.collect { article ->
                    article.monotonicTimeNanos?.let { observe(article.payload, it, article.sourceId.id) }
                }
            }
        }
    }
    override fun stop() {
        scope?.cancel(); scope = null
        synchronized(this) { states.values.forEach { it.apply {
            last = null; remaining = 3; species = null; armedAt = Long.MIN_VALUE; speciesAt = Long.MIN_VALUE; resolved = false
        } } }
    }

    private companion object { const val SAMPLE_INTERVAL_NANOS = 200_000_000L }
}
