package com.example.overdex.battle.timeline.event

import com.example.overdex.battle.timeline.observer.ObserverId

/**
 * A [TimelineEvent] recorded when a trainer switches their active Pokémon.
 * 
 * @property pokemonId The identifier of the Pokémon entering the battle.
 */
data class PokemonSwitched(
    override val timestamp: Long,
    override val observerId: ObserverId,
    val pokemonId: String
) : TimelineEvent

/**
 * A [TimelineEvent] recorded when a Pokémon's HP reaches zero and it leaves the battle.
 * 
 * @property pokemonId The identifier of the Pokémon that fainted.
 */
data class PokemonFainted(
    override val timestamp: Long,
    override val observerId: ObserverId,
    val pokemonId: String
) : TimelineEvent
