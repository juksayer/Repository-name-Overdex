package com.example.overdex.battle.audio

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.data.PokemonFormCandidates
import com.example.overdex.data.PokemonFormKnowledge
import com.example.overdex.data.PokemonFormReference
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.PokemonType
import java.util.Locale
import java.util.TreeMap

/** Legal reference search space for one unchanged, source-time audio interval.
 * This does not identify an attack or attribute a mixed sound to either side.
 */
internal data class FastMoveAudioCandidateSnapshot(
    val generation: Long,
    val fromNanos: Long,
    val untilNanos: Long,
    val allowedMoveNames: Set<String>,
    val sideMoves: Map<ActivePokemonSide, Set<String>>,
    val sideSpeciesNames: Map<ActivePokemonSide, String>,
    val sideEvidenceIds: Map<ActivePokemonSide, List<String>>,
    val evidenceIds: List<String>,
    val allActiveSidesKnown: Boolean,
    internal val contextKey: String,
)

/**
 * Builds small legal audio pools before expensive sound comparisons. Histories
 * use observation time, so a delayed successor name cannot label earlier PCM.
 * Captures crossing a context boundary are deliberately left unclassified.
 */
internal class FastMoveAudioCandidateContext(
    private val knowledge: PokemonKnowledge,
    private val forms: PokemonFormKnowledge = PokemonFormKnowledge(knowledge),
) {
    private data class Event(val article: RealityArticle, val at: Long, val order: Long)
    private data class Active(
        val name: String, val dex: Int?, val slot: Int?, val since: Long, val ids: List<String>,
    )
    private data class Types(val values: Set<PokemonType>, val id: String)
    private data class Configuration(val value: PlayerTeamSlotConfigured, val id: String)
    private data class Roster(val name: String, val id: String)
    private class State {
        val active = mutableMapOf<ActivePokemonSide, Active>()
        val types = mutableMapOf<ActivePokemonSide, Types>()
        val configurations = mutableMapOf<Int, Configuration>()
        val roster = mutableMapOf<Int, Roster>()
        val pending = mutableMapOf<ActivePokemonSide, Pair<Long, String>>()
        var stopped = false
        var resetAt = Long.MIN_VALUE
        var confirmedAt = Long.MIN_VALUE

        fun key(): String = buildString {
            append("reset=").append(resetAt).append(";confirmed=").append(confirmedAt).append(";stopped=").append(stopped)
            ActivePokemonSide.entries.forEach { side ->
                append(';').append(side).append('=')
                active[side]?.let { append(normalize(it.name)).append('/').append(it.dex)
                    .append('/').append(it.slot).append('/').append(it.since) }
                append('/').append(types[side]?.values?.map { it.name }?.sorted())
                append('/').append(pending[side]?.first)
            }
            roster.toSortedMap().forEach { (slot, value) ->
                append(";r").append(slot).append('=').append(normalize(value.name))
            }
            configurations.toSortedMap().forEach { (slot, config) ->
                append(";c").append(slot).append('=').append(normalize(config.value.speciesName))
                    .append('/').append(config.value.speciesId).append('/').append(normalize(config.value.fastMoveName))
            }
        }
    }

    private val events = TreeMap<Pair<Long, Long>, Event>(compareBy<Pair<Long, Long>> { it.first }.thenBy { it.second })
    private val ids = mutableSetOf<String>()
    private var order = 0L
    @Volatile var generation: Long = 0L
        private set

    /** Ingestion never suspends or waits for reference knowledge. */
    @Synchronized fun observe(article: RealityArticle) {
        if (!relevant(article.payload)) return
        val at = effectiveTime(article) ?: return
        if (!ids.add(article.id.value)) return
        val before = stateAt(at).key()
        val key = at to order++
        events[key] = Event(article, at, key.second)
        if (stateAt(at).key() == before) {
            // Frequent unchanged badge/type reads must not continually cancel work.
            events.remove(key)
        } else generation++
    }

    suspend fun snapshot(fromNanos: Long, untilNanos: Long): FastMoveAudioCandidateSnapshot? {
        if (untilNanos <= fromNanos) return null
        val captured = synchronized(this) {
            val state = stableState(fromNanos, untilNanos) ?: return null
            Triple(state, state.key(), generation)
        }
        val (state, key, capturedGeneration) = captured
        if (state.stopped || state.active.isEmpty()) return null
        val sideMoves = linkedMapOf<ActivePokemonSide, Set<String>>()
        val sideSpecies = linkedMapOf<ActivePokemonSide, String>()
        val sideEvidence = linkedMapOf<ActivePokemonSide, List<String>>()
        val evidence = mutableListOf<String>()
        for (side in ActivePokemonSide.entries) {
            val active = state.active[side] ?: continue
            var references = if (side == ActivePokemonSide.OPPONENT) {
                forms.forObservedName(active.name, active.dex)
            } else {
                val slottedName = active.slot?.let { state.roster[it]?.name }
                    ?.takeIf { base(it) == base(active.name) }
                val names = if ('(' in active.name) listOf(active.name)
                    else if (slottedName != null) listOf(slottedName)
                    else PokemonFormReference.playerReferenceNames(active.name, state.roster.values.map { it.name })
                if ('(' !in active.name && names == listOf(active.name) &&
                    state.roster.values.none { base(it.name) == base(active.name) }) {
                    forms.forObservedName(active.name, active.dex)
                } else PokemonFormCandidates(names, names.map { name ->
                        knowledge.getPokemonByName(name) ?: active.dex?.takeIf { '(' !in name }
                            ?.let { knowledge.getPokemonById(it) }
                    })
            }
            // A missing form prevents an exhaustive legal pool. Do not quietly
            // use the ordinary form when its regional reference is unavailable.
            if (!references.complete) continue
            state.types[side]?.let { observed ->
                val compatible = references.names.zip(references.pokemon)
                    .filter { (_, pokemon) -> pokemon?.types?.toSet() == observed.values }
                references = PokemonFormCandidates(compatible.map { it.first }, compatible.map { it.second })
            }
            val legal = references.fastMoves.map { it.name }.toSet()
            if (legal.isEmpty()) continue
            val ownEvidence = active.ids.toMutableList()
            val narrowed = if (side == ActivePokemonSide.PLAYER) {
                val possibleSlots = active.slot?.let(::listOf) ?: state.roster.filterValues {
                    base(it.name) == base(active.name)
                }.keys.toList()
                val configured = possibleSlots.singleOrNull()?.let { state.configurations[it] }
                    ?.takeIf { config ->
                        references.names.singleOrNull()?.let(::normalize) == normalize(config.value.speciesName) &&
                            (active.dex == null || active.dex == config.value.speciesId)
                    }
                val exact = configured?.let { config -> legal.singleOrNull {
                    normalize(it) == normalize(config.value.fastMoveName)
                } }
                if (exact != null) {
                    ownEvidence += requireNotNull(configured).id
                    setOf(exact)
                } else legal
            } else legal
            sideMoves[side] = narrowed
            sideSpecies[side] = references.names.singleOrNull() ?: active.name
            state.types[side]?.let { ownEvidence += it.id }
            if (side == ActivePokemonSide.PLAYER) {
                state.roster.filterValues { base(it.name) == base(active.name) }.values.forEach { ownEvidence += it.id }
            }
            sideEvidence[side] = ownEvidence.distinct()
            evidence += ownEvidence
        }
        val allowed = sideMoves.values.flatten().toSet()
        if (allowed.isEmpty()) return null
        val result = FastMoveAudioCandidateSnapshot(capturedGeneration, fromNanos, untilNanos,
            allowed, sideMoves.toMap(), sideSpecies.toMap(), sideEvidence.toMap(), evidence.distinct(),
            ActivePokemonSide.entries.all { it in sideMoves }, key)
        return result.takeIf(::isCurrent)
    }

    /** Also checked after matching and persistence, before custody publication. */
    @Synchronized fun isCurrent(snapshot: FastMoveAudioCandidateSnapshot): Boolean =
        snapshot.generation == generation &&
            stableState(snapshot.fromNanos, snapshot.untilNanos)?.key() == snapshot.contextKey

    private fun stableState(from: Long, until: Long): State? {
        val result = State()
        var keyAtStart: String? = null
        for (event in events.values) {
            if (event.at >= until) break
            if (event.at > from && keyAtStart == null) keyAtStart = result.key()
            apply(result, event)
            if (keyAtStart != null && result.key() != keyAtStart) return null
        }
        return result
    }

    private fun stateAt(at: Long): State = State().also { state ->
        events.values.asSequence().takeWhile { it.at <= at }.forEach { apply(state, it) }
    }

    private fun apply(state: State, event: Event) {
        val article = event.article
        val id = article.id.value
        val payload = article.payload
        fun clear(side: ActivePokemonSide) { state.active.remove(side); state.types.remove(side) }
        fun species(side: ActivePokemonSide, name: String, dex: Int?, slot: Int?, explicitStart: Long?,
            resolvedIdentity: Boolean = false) {
            if (side == ActivePokemonSide.PLAYER && event.at <= state.confirmedAt) return
            val prior = state.active[side]
            val merged = if (prior != null && base(prior.name) == base(name))
                PokemonFormReference.mergeRosterName(prior.name, name, explicitSprite = resolvedIdentity) else name
            val same = prior != null && normalize(prior.name) == normalize(merged) &&
                (slot == null || prior.slot == slot) && (explicitStart == null || prior.since == explicitStart)
            if (!same) state.types.remove(side)
            val boundary = state.pending.remove(side)
            state.active[side] = Active(merged, dex ?: prior?.dex.takeIf { prior != null && base(prior.name) == base(name) },
                slot ?: prior?.slot.takeIf { same },
                if (same) requireNotNull(prior).since else explicitStart ?: boundary?.first ?: event.at,
                if (same) requireNotNull(prior).ids + listOfNotNull(id.takeIf { dex != null && dex != prior.dex })
                else listOfNotNull(id, boundary?.second))
        }
        when (payload) {
            ObservationRestartedByUser -> {
                state.active.clear(); state.types.clear(); state.pending.clear()
                state.configurations.clear(); state.roster.clear(); state.stopped = false; state.resetAt = event.at
                state.confirmedAt = Long.MIN_VALUE
            }
            ScreenIgnoredByUser, ObservationSessionStoppedByUser, is MatchEnded -> state.stopped = true
            is PlayerTeamRosterConfirmation -> if (payload.confirmed) {
                state.configurations.clear(); state.roster.clear(); clear(ActivePokemonSide.PLAYER)
                state.confirmedAt = event.at
                payload.speciesBySlot.forEachIndexed { index, name ->
                    if (!name.isNullOrBlank()) state.roster[index + 1] = Roster(name, id)
                }
            }
            is PlayerTeamSlotConfigured -> if (event.at > state.confirmedAt) {
                state.configurations[payload.slot] = Configuration(payload, id)
                state.roster[payload.slot] = Roster(payload.speciesName, id)
            }
            is PlayerTeamRosterSlotWitnessed -> if (event.at > state.confirmedAt) {
                val merged = PokemonFormReference.mergeRosterName(state.roster[payload.slot]?.name,
                    payload.speciesName, payload.spriteVariant != null)
                state.roster[payload.slot] = Roster(merged, id)
            }
            is ActivePokemonSpeciesWitnessed -> species(payload.side, payload.speciesName, payload.speciesId,
                payload.playerTeamSlot, payload.appearanceStartedAtNanos, resolvedIdentity = payload.playerTeamSlot != null)
            is PlayerActiveIdentityResolved -> species(ActivePokemonSide.PLAYER, payload.speciesName,
                payload.speciesId, payload.teamSlot, payload.appearanceStartedAtNanos, resolvedIdentity = true)
            is ActivePokemonTypesWitnessed -> state.types[payload.side] = Types(payload.types.toSet(), id)
            is ActivePokemonFaintedWitnessed -> if (payload.speciesName == null || state.active[payload.side] == null ||
                state.active[payload.side]?.let { base(it.name) == base(payload.speciesName) } == true) {
                clear(payload.side); state.pending[payload.side] = event.at to id
            }
            is SpeciesCheckMeasured -> if (payload.status == "OPENED" &&
                payload.reason in setOf("ENTRY", "SWITCH", "FAINT", "CRY", "EMPTY_HP")) {
                clear(payload.side); state.pending[payload.side] = event.at to id
            }
            else -> Unit
        }
    }

    private fun relevant(payload: TestimonyPayload): Boolean = when (payload) {
        is ActivePokemonSpeciesWitnessed, is PlayerActiveIdentityResolved, is ActivePokemonTypesWitnessed,
        is ActivePokemonFaintedWitnessed, is SpeciesCheckMeasured, is PlayerTeamSlotConfigured,
        is PlayerTeamRosterSlotWitnessed, is PlayerTeamRosterConfirmation, is MatchEnded,
        ObservationRestartedByUser, ScreenIgnoredByUser, ObservationSessionStoppedByUser -> true
        else -> false
    }

    private fun effectiveTime(article: RealityArticle): Long? = when (val payload = article.payload) {
        is SpeciesCheckMeasured -> payload.triggerMonotonicNanos
        is PlayerActiveIdentityResolved -> payload.appearanceStartedAtNanos
        is ActivePokemonSpeciesWitnessed -> payload.appearanceStartedAtNanos ?: article.monotonicTimeNanos
        else -> article.monotonicTimeNanos
    }

    companion object {
        private fun normalize(value: String) = value.uppercase(Locale.ROOT).filter(Char::isLetterOrDigit)
        private fun base(value: String) = normalize(value.substringBefore(" ("))
    }
}
