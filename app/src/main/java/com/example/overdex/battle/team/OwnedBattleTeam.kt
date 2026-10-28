package com.example.overdex.battle.team

import com.example.overdex.model.OwnedPokemon
import com.example.overdex.model.Pokemon

/** Resolve a named specimen team from current cards, including their latest edited moves. */
data class OwnedBattleTeamChoice(val name: String, val members: List<OwnedPokemon>) {
    fun resolve(species: Map<Int, Pokemon>, referencesByName: Map<String, Pokemon> = emptyMap()): CurrentBattleTeam? {
        if (members.size != 3) return null
        val configured = members.mapIndexed { index, owned ->
            val explicitName = owned.speciesName?.trim()?.takeIf(String::isNotEmpty)
            val reference = if (explicitName == null) species[owned.speciesId] else {
                // A form-specific card must not borrow the ordinary species' moves.
                referencesByName.entries.firstOrNull { (name, reference) ->
                    name.equals(explicitName, ignoreCase = true) &&
                        reference.name.equals(explicitName, ignoreCase = true)
                }?.value ?: species[owned.speciesId]?.takeIf { it.name.equals(explicitName, ignoreCase = true) }
            }
            if (reference == null || reference.id != owned.speciesId) return null
            val fast = reference.fastMoves.firstOrNull { it.name.equals(owned.fastMove, true) } ?: return null
            val first = reference.chargedMoves.firstOrNull { it.name.equals(owned.chargedMove1, true) } ?: return null
            val second = owned.chargedMove2?.takeIf(String::isNotBlank)?.let { name ->
                reference.chargedMoves.firstOrNull { it.name.equals(name, true) } ?: return null
            }
            CurrentBattleTeamMember(index + 1, reference.id, reference.name, fast.name,
                listOfNotNull(first.name, second?.name).distinct(), owned.id)
        }
        return CurrentBattleTeam(configured)
    }

    companion object {
        fun fromCards(cards: List<OwnedPokemon>): List<OwnedBattleTeamChoice> {
            val names = cards.flatMap { it.teams }.distinctBy { it.lowercase(java.util.Locale.ROOT) }
            return names.sortedBy { it.lowercase(java.util.Locale.ROOT) }.map { name ->
                OwnedBattleTeamChoice(name, cards.filter { card -> card.teams.any { it.equals(name, true) } }
                    .sortedWith(compareBy<OwnedPokemon> { it.createdAt }.thenBy { it.id }))
            }
        }
    }
}
