package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.ActivePokemonFaintedWitnessed
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.observation.TeamRosterSpeciesAttributor
import com.example.overdex.battle.reality.RealityArticle

/** A lingering species badge cannot reopen a combatant's already closed life. */
internal class FaintedCombatants {
    private val faintTimes = mutableMapOf<Pair<ActivePokemonSide, String>, Long>()

    fun observe(article: RealityArticle) {
        val faint = article.payload as? ActivePokemonFaintedWitnessed ?: return
        val name = faint.speciesName ?: return
        val at = article.monotonicTimeNanos ?: return
        val key = faint.side to key(name)
        faintTimes[key] = minOf(at, faintTimes[key] ?: at)
    }

    fun contains(side: ActivePokemonSide, name: String) = faintTimes.containsKey(side to key(name))
    fun at(side: ActivePokemonSide, name: String, time: Long): Boolean =
        faintTimes[side to key(name)]?.let { time >= it } == true

    private fun key(name: String) = TeamRosterSpeciesAttributor.speciesKey(name)
}
