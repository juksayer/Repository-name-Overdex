package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.ChargedMoveEnergySpent
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.observation.SpeciesTextResolver
import com.example.overdex.battle.observation.SpeciesOcrTypography
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.Pokemon
import com.example.overdex.model.Move

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
    private val activeSpeciesHistory = ArrayDeque<RealityArticle>()
    private var knownSpeciesNames: Set<String>? = null
    private data class Spend(val side: ActivePokemonSide, val species: String, val move: String, val at: Long)
    private val recentSpends = ArrayDeque<Spend>()

    suspend fun accept(
        article: RealityArticle,
        rosterSideForSpecies: (String) -> ActivePokemonSide?
    ): ChargedMoveAnnouncementDerivation? {
        (article.payload as? ActivePokemonSpeciesWitnessed)?.let {
            activeSpeciesHistory.addLast(article)
            while (activeSpeciesHistory.size > 128) activeSpeciesHistory.removeFirst()
            return null
        }
        if (article.sourceId.id != ANNOUNCEMENT_SOURCE) return null
        val text = ((article.payload as? RawTestimony)?.data as? String)?.trim() ?: return null
        val match = USED_MOVE_PATTERN.matchEntire(text) ?: COMPACT_USED_MOVE_PATTERN.matchEntire(text) ?: return null
        val at = article.monotonicTimeNanos ?: return null
        val activeAtUse = activeSpeciesHistory.filter { (it.monotonicTimeNanos ?: Long.MAX_VALUE) <= at }
            .groupBy { (it.payload as ActivePokemonSpeciesWitnessed).side }
            .mapValues { (_, appearances) -> appearances.maxBy { it.monotonicTimeNanos!! } }
        val activeSpeciesBySide = activeAtUse.mapValues { (_, appearance) ->
            (appearance.payload as ActivePokemonSpeciesWitnessed).speciesName
        }
        val speciesName = SpeciesTextResolver.resolve(
            rawText = match.groupValues[1],
            knownSpeciesNames = knownSpeciesNames ?: pokemonKnowledge.getAllSpeciesNames()
                .also { knownSpeciesNames = it }
        )
        val moveName = normalize(match.groupValues[2])
        val pokemon: Pokemon
        val move: Move
        val side: ActivePokemonSide
        val supportingIds: List<ArticleId>
        val basis: String
        if (speciesName != null) {
            pokemon = pokemonKnowledge.getPokemonByName(speciesName) ?: return null
            move = bestMoveMatches(moveName, pokemon.chargedMoves).singleOrNull()?.move ?: return null
            val activeSides = activeSpeciesBySide.filterValues { normalize(it) == normalize(speciesName) }.keys
            side = when {
                activeSides.size == 1 -> activeSides.single()
                activeSides.isNotEmpty() -> null // Mirrors still need independent direction evidence.
                else -> rosterSideForSpecies(speciesName)
            } ?: return null
            supportingIds = emptyList()
            basis = "ANNOUNCED_NAMED_CHARGED_MOVE_AND_REFERENCE_ENERGY_COST"
        } else {
            // Pokémon GO shows player nicknames here. The observed move can resolve
            // the performer only when BOTH active combatants are known and exactly
            // one can use it. A missing opponent is not proof it cannot use the move.
            if (activeSpeciesBySide.keys != ActivePokemonSide.entries.toSet()) return null
            val candidates = mutableListOf<Triple<ActivePokemonSide, Pokemon, MoveMatch>>()
            for ((activeSide, name) in activeSpeciesBySide) {
                val active = pokemonKnowledge.getPokemonByName(name) ?: return null
                bestMoveMatches(moveName, active.chargedMoves).forEach {
                    candidates += Triple(activeSide, active, it)
                }
            }
            val bestDistance = candidates.minOfOrNull { it.third.edits } ?: return null
            val candidate = candidates.filter { it.third.edits == bestDistance }.singleOrNull() ?: return null
            side = candidate.first; pokemon = candidate.second; move = candidate.third.move
            supportingIds = activeAtUse.values.map { it.id }
            basis = "ANNOUNCED_CHARGED_MOVE_AND_UNIQUE_ACTIVE_MOVEPOOL"
        }
        // OCR can publish the same on-screen announcement with and without
        // punctuation. Preserve both raw readings, but debit this use only once.
        val spend = Spend(side, normalize(pokemon.name), normalize(move.name), at)
        if (recentSpends.any {
                it.side == spend.side && it.species == spend.species && it.move == spend.move &&
                    kotlin.math.abs(it.at - at) <= ANNOUNCEMENT_EPISODE_NANOS
            }) return null
        recentSpends.addLast(spend)
        while (recentSpends.size > 64) recentSpends.removeFirst()
        return ChargedMoveAnnouncementDerivation(
            payload = ChargedMoveEnergySpent(
                side = side,
                speciesName = pokemon.name,
                moveName = move.name,
                energyCost = move.energy,
                basis = basis + when {
                    moveName == normalize(move.name) -> ""
                    SpeciesOcrTypography.glyphRepairCost(moveName, normalize(move.name)) != null -> "_OCR_LEGAL_GLYPH_REPAIR"
                    else -> "_OCR_SINGLE_CHARACTER_REPAIR"
                }
            ),
            predecessorIds = listOf(article.id) + supportingIds,
            observedArticle = article
        )
    }

    private fun normalize(value: String): String = SpeciesOcrTypography.normalizeLabel(value)

    private data class MoveMatch(val move: Move, val edits: Int)

    /** Compare the entire reading only with legal moves; clipped prefixes never select a move. */
    private fun bestMoveMatches(reading: String, moves: List<Move>): List<MoveMatch> {
        val matches = moves.distinctBy { normalize(it.name) }.mapNotNull { move ->
            val name = normalize(move.name)
            when {
                reading == name -> MoveMatch(move, 0)
                reading.length < 6 || name.length < 6 -> null
                reading.startsWith(name) || name.startsWith(reading) -> null
                SpeciesOcrTypography.glyphRepairCost(reading, name) != null -> MoveMatch(move, 1)
                singleCharacterDifference(reading, name) -> MoveMatch(move, 1)
                else -> null
            }
        }
        val best = matches.minOfOrNull { it.edits } ?: return emptyList()
        return matches.filter { it.edits == best }
    }

    private fun singleCharacterDifference(left: String, right: String): Boolean {
        if (kotlin.math.abs(left.length - right.length) > 1) return false
        var a = 0; var b = 0; var edits = 0
        while (a < left.length && b < right.length) {
            if (left[a] == right[b]) { a++; b++; continue }
            if (++edits > 1) return false
            when {
                left.length > right.length -> a++
                right.length > left.length -> b++
                else -> { a++; b++ }
            }
        }
        return edits + (left.length - a) + (right.length - b) == 1
    }

    private companion object {
        const val ANNOUNCEMENT_SOURCE = "ANNOUNCEMENT_WITNESS"
        const val ANNOUNCEMENT_EPISODE_NANOS = 4_000_000_000L
        val USED_MOVE_PATTERN = Regex("^\\s*(?:(.+?)\\s+)?used\\s+(.+?)[!.]*\\s*$", RegexOption.IGNORE_CASE)
        // Some small crops lose both word gaps: the October 13 record read "salusedDischargo".
        val COMPACT_USED_MOVE_PATTERN = Regex("^\\s*(\\p{L}{2,24})used([\\p{L}\\p{N}|$@€!]{6,})[!.]*\\s*$", RegexOption.IGNORE_CASE)
    }
}
