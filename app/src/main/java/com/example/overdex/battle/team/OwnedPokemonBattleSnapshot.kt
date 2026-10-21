package com.example.overdex.battle.team

import com.example.overdex.model.OwnedPokemon
import com.example.overdex.model.PokemonSize
import kotlinx.serialization.Serializable

/** Frozen at team configuration time. Editing a card never rewrites its past matches. */
@Serializable
data class OwnedPokemonBattleSnapshot(
    val ownedPokemonId: String,
    val speciesId: Int,
    val nickname: String? = null,
    val cp: Int? = null,
    val isShadow: Boolean = false,
    val isPurified: Boolean = false,
    val isShiny: Boolean = false,
    val size: PokemonSize = PokemonSize.UNKNOWN,
) {
    companion object {
        fun from(card: OwnedPokemon) = OwnedPokemonBattleSnapshot(card.id, card.speciesId,
            card.displayName, card.cp, card.isShadow, card.isPurified, card.isShiny, card.size)
    }
}
