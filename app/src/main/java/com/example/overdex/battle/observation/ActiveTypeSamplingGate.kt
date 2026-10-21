package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource
import kotlinx.coroutines.*

/** Type badges change at entries. Keep a recovery probe even when every entry cue was missed. */
class ActiveTypeSamplingGate : Observer {
    override val observerId = ObserverId("ACTIVE_TYPE_SAMPLING_GATE", ObservationSource.SCREEN_CAPTURE)
    override val name = "Active Type Sampling Gate"
    override val managesAvailability = true
    private data class State(var last: Long? = null, var remaining: Int = 3, var species: String? = null)
    private val states = ActivePokemonSide.entries.associateWith { State() }
    private var scope: CoroutineScope? = null

    @Synchronized fun accepts(side: ActivePokemonSide, at: Long): Boolean {
        val state = states.getValue(side)
        return state.last?.let { at - it >= if (state.remaining > 0) 200_000_000L else 1_500_000_000L } ?: true
    }
    @Synchronized fun captured(side: ActivePokemonSide, at: Long) {
        val state = states.getValue(side)
        state.last = at
        state.remaining = (state.remaining - 1).coerceAtLeast(0)
    }
    @Synchronized internal fun trigger(side: ActivePokemonSide? = null) {
        states.filterKeys { side == null || it == side }.values.forEach { it.remaining = 3 }
    }
    @Synchronized internal fun species(side: ActivePokemonSide, name: String) {
        val state = states.getValue(side)
        if (!name.equals(state.species, ignoreCase = true)) { state.species = name; trigger(side) }
    }

    override fun start(match: Match) {
        if (scope != null) return
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { workers ->
            workers.launch(start = CoroutineStart.UNDISPATCHED) {
                match.articles.collect { article ->
                    when (val p = article.payload) {
                        is MatchStarted -> trigger()
                        is ActivePokemonSpeciesWitnessed -> species(p.side, p.speciesName)
                        is ActivePokemonFaintedWitnessed -> trigger(p.side)
                        is SpeciesCheckMeasured -> if (p.status == "OPENED" &&
                            p.reason in setOf("ENTRY", "SWITCH", "FAINT", "CRY", "MATCH_START")) trigger(p.side)
                        is BattleCryCandidatesMeasured -> if (p.candidates.isNotEmpty() && p.cueKind != "FAST_MOVE_IMPACT") trigger()
                        is RawTestimony -> if (article.sourceId.id == "ANNOUNCEMENT_WITNESS" &&
                            (p.data as? String)?.trim()?.startsWith("Go,", true) == true) trigger()
                        else -> Unit
                    }
                }
            }
        }
    }
    override fun stop() {
        scope?.cancel(); scope = null
        synchronized(this) { states.values.forEach { it.last = null; it.remaining = 3; it.species = null } }
    }
}
