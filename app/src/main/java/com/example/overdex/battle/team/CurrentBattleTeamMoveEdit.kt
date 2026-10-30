package com.example.overdex.battle.team

import com.example.overdex.model.OwnedPokemon
import com.example.overdex.model.Pokemon

/** An explicit correction changes this member's moves without replacing its specimen. */
class CurrentBattleTeamMoveEdit private constructor(
    val originalMember: CurrentBattleTeamMember,
    val team: CurrentBattleTeam,
) {
    val member: CurrentBattleTeamMember get() = team.members.single { it.slot == originalMember.slot }

    fun updateOwnedCard(card: OwnedPokemon, updatedAt: Long): OwnedPokemon {
        require(card.id == member.ownedPokemonId && card.speciesId == member.speciesId &&
            (card.speciesName.isNullOrBlank() || card.speciesName.equals(member.speciesName, true))) {
            "This linked card does not match the team member. Choose the correct card before editing its moves."
        }
        return card.copy(fastMove = member.fastMoveName,
            chargedMove1 = member.chargedMoveNames.getOrNull(0),
            chargedMove2 = member.chargedMoveNames.getOrNull(1), updatedAt = updatedAt)
    }

    companion object {
        fun prepare(team: CurrentBattleTeam, expectedMember: CurrentBattleTeamMember, reference: Pokemon,
                    fastMove: String, chargedMoves: List<String>): CurrentBattleTeamMoveEdit {
            require(team.members.singleOrNull { it.slot == expectedMember.slot } == expectedMember) {
                "The current team changed. Return to the team and select this Pokémon again."
            }
            require(reference.id == expectedMember.speciesId && reference.name.equals(expectedMember.speciesName, true)) {
                "Could not read this Pokémon's exact species and form."
            }
            val fast = reference.fastMoves.singleOrNull { it.name.equals(fastMove, true) }?.name
                ?: throw IllegalArgumentException("Choose a Fast Move this Pokémon can learn.")
            require(chargedMoves.size in 1..2 && chargedMoves.map { it.lowercase() }.distinct().size == chargedMoves.size) {
                "Choose one or two different Charged Moves."
            }
            val charged = chargedMoves.map { name ->
                reference.chargedMoves.singleOrNull { it.name.equals(name, true) }?.name
                    ?: throw IllegalArgumentException("Choose Charged Moves this Pokémon can learn.")
            }
            val updated = expectedMember.copy(fastMoveName = fast, chargedMoveNames = charged)
            return CurrentBattleTeamMoveEdit(expectedMember,
                team.copy(members = team.members.map { if (it.slot == updated.slot) updated else it }))
        }
    }
}
