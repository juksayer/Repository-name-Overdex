package com.example.overdex.battle.audio

import com.example.overdex.battle.custody.*

/** Audio attention spans the whole fight, including entry animations and charged sequences.
 * Those sounds are observations; downstream combat rules still decide whether an attack occurred.
 */
internal class FastMoveListeningGate {
    private var start: Long? = null
    private var end: Long? = null
    private var stopped = false
    private var openingId: String? = null
    private val activeNames = mutableMapOf<ActivePokemonSide, String>()
    private val faintedNames = mutableSetOf<Pair<ActivePokemonSide, String>>()
    private val awaitingReplacement = mutableSetOf<ActivePokemonSide>()
    private var faints = 0
    @Volatile var generation: Long = 0
        private set

    @Synchronized fun observe(payload: TestimonyPayload, at: Long?, articleId: String? = null) {
        when (payload) {
            ObservationRestartedByUser -> {
                generation++
                start = null; end = null; stopped = false; openingId = null
                activeNames.clear(); faintedNames.clear(); awaitingReplacement.clear(); faints = 0
            }
            ScreenIgnoredByUser, ObservationSessionStoppedByUser -> {
                generation++
                stopped = true
            }
            is CountdownGlyphWitnessed -> if (payload.glyph.uppercase() in setOf("3", "2", "1", "GO", "GO!")) {
                open(at, articleId)
            }
            // A late observer can miss every glyph. A witnessed charged phase
            // establishes that battle is underway, not when the match began.
            MatchStarted, AttackIncoming, GetReadyWitnessed, ChargeMoveUsedAnnounced -> open(at, articleId)
            is MatchEnded -> if (at != null) end = minOf(end ?: at, at)
            is ActivePokemonSpeciesWitnessed -> {
                val name = payload.speciesName.lowercase().trim()
                val old = activeNames.put(payload.side, name)
                if (old != name && payload.side to name !in faintedNames) awaitingReplacement.remove(payload.side)
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

    private fun open(at: Long?, id: String?) {
        if (at == null || stopped || end != null) return
        if (start == null || at < start!!) { start = at; openingId = id }
    }

    @Synchronized fun allows(at: Long): Boolean = !stopped && start?.let {
        at >= it && (end == null || at < end!!)
    } == true

    /** Historical windows may finish processing after the result, but never across a manual reset. */
    @Synchronized fun accepts(from: Long, until: Long, windowGeneration: Long): Boolean =
        windowGeneration == generation && until > from && allows(from) &&
            (end == null || until <= end!!)

    @Synchronized fun triggerIds(): List<String> = listOfNotNull(openingId)
}
