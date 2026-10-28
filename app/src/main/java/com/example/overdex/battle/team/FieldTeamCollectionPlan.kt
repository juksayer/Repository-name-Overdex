package com.example.overdex.battle.team

import com.example.overdex.model.OwnedPokemon
import com.example.overdex.model.OwnedPokemonTeams

/** Explicitly reviewed card choices; species similarity never selects a specimen. */
data class FieldTeamCollectionPlan(
    val name: String,
    val team: CurrentBattleTeam,
    val newCards: List<OwnedPokemon>,
    val updatedCards: List<OwnedPokemon>,
) {
    companion object {
        fun from(draft: FieldTeamDraft, existing: List<OwnedPokemon>, now: Long): FieldTeamCollectionPlan {
            require(draft.collectionReady) { "Choose or import a card for each slot, finish moves, and name the team." }
            val name = draft.teamName.trim()
            require(name.isNotBlank() && name.none { it == ',' || it == '\n' || it == '\r' }) {
                "Use a team name without commas or line breaks."
            }
            val byId = existing.associateBy { it.id }
            val inserts = mutableListOf<OwnedPokemon>()
            val updates = mutableListOf<OwnedPokemon>()
            val members = draft.slots.mapIndexed { index, slot ->
                val reference = requireNotNull(slot.pokemon)
                val imported = slot.newCard?.toOwnedPokemon()
                val card = imported ?: requireNotNull(byId[slot.ownedPokemonId]) { "That card is no longer available. Choose it again." }
                require(card.speciesId == reference.id && (card.speciesName == null || card.speciesName.equals(reference.name, true))) {
                    "That card has a different Pokémon form. Choose the correct card."
                }
                val saved = card.copy(speciesName = reference.name, fastMove = slot.fastMove,
                    chargedMove1 = slot.chargedMoves.firstOrNull(), chargedMove2 = slot.chargedMoves.getOrNull(1),
                    teams = OwnedPokemonTeams.parse((card.teams + name).joinToString(",")), updatedAt = now)
                if (imported != null) {
                    // A previous partially completed save may already contain this stable UUID.
                    // Do not replace a card the trainer edited after that successful insert.
                    byId[saved.id]?.let { prior ->
                        require(prior.speciesId == saved.speciesId &&
                            (prior.speciesName == null || prior.speciesName.equals(saved.speciesName, true))) {
                            "The imported card no longer matches this Pokémon. Review it again."
                        }
                    }
                    inserts += saved
                } else updates += saved
                CurrentBattleTeamMember(index + 1, reference.id, reference.name,
                    requireNotNull(slot.fastMove), slot.chargedMoves, card.id)
            }
            require(members.map { it.ownedPokemonId }.distinct().size == 3) { "Each slot needs a different owned Pokémon." }
            return FieldTeamCollectionPlan(name, CurrentBattleTeam(members), inserts, updates)
        }
    }
}
