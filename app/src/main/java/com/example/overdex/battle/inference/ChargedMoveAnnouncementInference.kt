package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.ChargedMoveEnergySpent
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.observation.SpeciesTextResolver
import com.example.overdex.data.PokemonKnowledge

data class ChargedMoveAnnouncementDerivation(
    val payload: ChargedMoveEnergySpent,
    val predecessorIds: List<ArticleId>,
    val observedArticle: RealityArticle
)

/**
 * Resolves a preserved “<Pokémon> used <move>!” announcement against immutable
 * Pokédex knowledge. It remains silent if the announcement, move, or performer
 * is ambiguous; the original raw announcement stays available either way.
 */
class ChargedMoveAnnouncementInference(
    private val pokemonKnowledge: PokemonKnowledge
) {
    private val activeSpeciesBySide = mutableMapOf<ActivePokemonSide, String>()
    private var knownSpeciesNames: Set<String>? = null

    suspend fun accept(
        article: RealityArticle,
        rosterSideForSpecies: (String) -> ActivePokemonSide?
    ): ChargedMoveAnnouncementDerivation? {
        (article.payload as? ActivePokemonSpeciesWitnessed)?.let { witnessed ->
            activeSpeciesBySide[witnessed.side] = witnessed.speciesName
            return null
        }
        if (article.sourceId.id != ANNOUNCEMENT_SOURCE) return null
        val text = ((article.payload as? RawTestimony)?.data as? String)?.trim() ?: return null
        val match = USED_MOVE_PATTERN.matchEntire(text) ?: return null
        val speciesName = SpeciesTextResolver.resolve(
            rawText = match.groupValues[1],
            knownSpeciesNames = knownSpeciesNames ?: pokemonKnowledge.getAllSpeciesNames()
                .also { knownSpeciesNames = it }
        ) ?: return null
        val pokemon = pokemonKnowledge.getPokemonByName(speciesName) ?: return null
        val moveName = normalize(match.groupValues[2])
        val move = pokemon.chargedMoves.singleOrNull { normalize(it.name) == moveName } ?: return null
        val activeSides = activeSpeciesBySide
            .filterValues { normalize(it) == normalize(speciesName) }
            .keys
        val side = when {
            activeSides.size == 1 -> activeSides.single()
            activeSides.isNotEmpty() -> null // e.g. mirror match: do not invent a side.
            else -> rosterSideForSpecies(speciesName)
        } ?: return null
        return ChargedMoveAnnouncementDerivation(
            payload = ChargedMoveEnergySpent(
                side = side,
                speciesName = pokemon.name,
                moveName = move.name,
                energyCost = move.energy,
                basis = "ANNOUNCED_NAMED_CHARGED_MOVE_AND_REFERENCE_ENERGY_COST"
            ),
            predecessorIds = listOf(article.id),
            observedArticle = article
        )
    }

    private fun normalize(value: String): String = value.uppercase().filter(Char::isLetterOrDigit)

    private companion object {
        const val ANNOUNCEMENT_SOURCE = "ANNOUNCEMENT_WITNESS"
        val USED_MOVE_PATTERN = Regex("^\\s*(.+?)\\s+used\\s+(.+?)[!.]*\\s*$", RegexOption.IGNORE_CASE)
    }
}
