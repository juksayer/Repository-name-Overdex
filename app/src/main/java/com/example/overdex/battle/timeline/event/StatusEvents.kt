package com.example.overdex.battle.timeline.event

import com.example.overdex.battle.timeline.observer.ObserverId

/**
 * Recorded when a trainer switches their active Pokémon.
 */
data class PokemonSwitched(
    override val timestamp: Long,
    override val observerId: ObserverId,
    val pokemonId: String
) : TimelineEvent

/**
 * Recorded when a Pokémon's HP reaches zero.
 */
data class PokemonFainted(
    override val timestamp: Long,
    override val observerId: ObserverId,
    val pokemonId: String
) : TimelineEvent
