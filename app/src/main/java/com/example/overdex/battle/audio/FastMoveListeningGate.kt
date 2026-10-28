package com.example.overdex.battle.audio

import com.example.overdex.battle.custody.*
import kotlin.math.abs

/** Audio attention spans VS through the fight, including entry animations and charged sequences.
 * Those sounds are observations; downstream combat rules still decide whether an attack occurred.
 */
internal class FastMoveListeningGate {
    private var start: Long? = null
    private var end: Long? = null
    private var stopped = false
    private var openingIds = emptyList<String>()
    private var attemptFromNanos = Long.MIN_VALUE
    private data class Evidence(val at: Long, val id: String?)
    private val firstSpecies = mutableMapOf<ActivePokemonSide, Evidence>()
    private var pendingDamage: Evidence? = null
    private val activeNames = mutableMapOf<ActivePokemonSide, String>()
    private val faintedNames = mutableSetOf<Pair<ActivePokemonSide, String>>()
    private val awaitingReplacement = mutableSetOf<ActivePokemonSide>()
    private var faints = 0
    @Volatile var generation: Long = 0
        private set

    @Synchronized fun observe(payload: TestimonyPayload, at: Long?, articleId: String? = null) {
        // A completed OCR/measurement job can arrive after a manual restart.
        // Its old battlefield time must not open the replacement attempt.
        if (at != null && at < attemptFromNanos) return
        when (payload) {
            ObservationRestartedByUser -> {
                generation++
                start = null; end = null; stopped = false; openingIds = emptyList()
                attemptFromNanos = at ?: attemptFromNanos
                firstSpecies.clear(); pendingDamage = null
                activeNames.clear(); faintedNames.clear(); awaitingReplacement.clear(); faints = 0
            }
            ScreenIgnoredByUser, ObservationSessionStoppedByUser -> {
                generation++
                stopped = true
            }
            is CountdownGlyphWitnessed -> if (payload.glyph.uppercase() in setOf("3", "2", "1", "GO", "GO!")) {
                open(at, articleId)
            }
            VsScreenWitnessed -> open(at, articleId)
            // A late observer can miss every glyph. A witnessed charged phase
            // establishes that battle is underway, not when the match began.
            MatchStarted, AttackIncoming, GetReadyWitnessed, ChargeMoveUsedAnnounced -> open(at, articleId)
            is MatchEnded -> if (at != null) end = minOf(end ?: at, at)
            is ActivePokemonSpeciesWitnessed -> {
                val name = payload.speciesName.lowercase().trim()
                val old = activeNames.put(payload.side, name)
                if (old != name && payload.side to name !in faintedNames) awaitingReplacement.remove(payload.side)
                if (at != null && name.isNotEmpty() &&
                    at < (firstSpecies[payload.side]?.at ?: Long.MAX_VALUE)) {
                    firstSpecies[payload.side] = Evidence(at, articleId)
                    openFromCombatIfReady()
                }
            }
            is ActiveHpBarDamageTickMeasured -> {
                // The same persistent-loss validity check used by Fast Move
                // accounting. Both active sides supply battle context; names,
                // full bars, flashes and countdown button shading alone do not.
                if (at != null && payload.beforeFraction in 0f..1f &&
                    payload.afterFraction in 0f..1f && payload.lostFraction > 0f &&
                    payload.beforeFraction > payload.afterFraction &&
                    abs(payload.beforeFraction - payload.afterFraction - payload.lostFraction) < .001f) {
                    if (at > (pendingDamage?.at ?: Long.MIN_VALUE))
                        pendingDamage = Evidence(at, articleId)
                    openFromCombatIfReady()
                }
            }
            is ActivePokemonFaintedWitnessed -> {
                val began = start ?: return
                if (at == null || at < began || end != null || stopped) return
                val name = payload.speciesName?.lowercase()?.trim() ?: activeNames[payload.side]
                if (payload.side in awaitingReplacement || (name != null && payload.side to name in faintedNames)) return
                if (name != null) faintedNames += payload.side to name
                awaitingReplacement += payload.side
                faints++
                if (faints >= 5) end = at
            }
            else -> Unit
        }
    }

    private fun openFromCombatIfReady() {
        val damage = pendingDamage ?: return
        val context = ActivePokemonSide.entries.map { firstSpecies[it] ?: return }
        // Delivery order is not source order. A later species frame cannot
        // retroactively establish context for an earlier ambiguous HP change.
        if (context.any { it.at > damage.at }) return
        open(damage.at, (context.mapNotNull { it.id } + listOfNotNull(damage.id)).distinct())
    }

    private fun open(at: Long?, id: String?) = open(at, listOfNotNull(id))

    private fun open(at: Long?, ids: List<String>) {
        if (at == null || stopped || end != null) return
        if (start == null || at < start!!) { start = at; openingIds = ids }
    }

    @Synchronized fun allows(at: Long): Boolean = !stopped && start?.let {
        at >= it && (end == null || at < end!!)
    } == true

    /** Historical windows may finish processing after the result, but never across a manual reset. */
    @Synchronized fun accepts(from: Long, until: Long, windowGeneration: Long): Boolean =
        windowGeneration == generation && until > from && allows(from) &&
            (end == null || until <= end!!)

    @Synchronized fun triggerIds(): List<String> = openingIds
}
