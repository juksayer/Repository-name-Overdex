package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.FastMoveEnergyDerived
import com.example.overdex.battle.custody.FastMoveIdentified
import com.example.overdex.battle.custody.FastMoveUseObserved
import com.example.overdex.battle.custody.SpeciesCheckMeasured
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.data.PokemonKnowledge
import java.util.EnumMap

data class FastMoveEnergyDerivation(
    val payload: FastMoveEnergyDerived,
    val predecessorIds: List<ArticleId>,
    val confidence: Float,
    /** The canonical observed use whose battlefield time this energy belongs to. */
    val observedArticle: RealityArticle
)

/**
 * Joins one canonical Fast Move use to the current move identity and Reference
 * Knowledge. Cadence measurements never count uses here, so several witnesses
 * describing one hit cannot generate duplicate energy.
 */
class FastMoveEnergyInference(private val pokemonKnowledge: PokemonKnowledge) {
    private data class Identity(
        val article: RealityArticle,
        val payload: FastMoveIdentified,
        val energyPerUse: Int
    )

    private val activeSpecies = EnumMap<ActivePokemonSide, String>(ActivePokemonSide::class.java)
    private val identities = EnumMap<ActivePokemonSide, Identity>(ActivePokemonSide::class.java)
    private val pendingUses = EnumMap<ActivePokemonSide, MutableList<RealityArticle>>(ActivePokemonSide::class.java)
    private val attributedUseIds = mutableSetOf<String>()

    suspend fun accept(article: RealityArticle): List<FastMoveEnergyDerivation> {
        when (val payload = article.payload) {
            is SpeciesCheckMeasured -> if (
                payload.status == "OPENED" && payload.reason in setOf("ENTRY", "EMPTY_HP")
            ) {
                identities.remove(payload.side)
                activeSpecies.remove(payload.side)
                pendingUses.remove(payload.side)
                return emptyList()
            }

            is ActivePokemonSpeciesWitnessed -> {
                if (activeSpecies[payload.side] != payload.speciesName) {
                    identities.remove(payload.side)
                    activeSpecies[payload.side] = payload.speciesName
                }
                return emptyList()
            }

            is FastMoveIdentified -> {
                val pokemon = pokemonKnowledge.getPokemonByName(payload.speciesName)
                val move = pokemon?.fastMoves?.firstOrNull {
                    normalize(it.name) == normalize(payload.moveName)
                } ?: return emptyList()
                val identity = Identity(article, payload, move.energy)
                identities[payload.side] = identity
                val waiting = pendingUses.remove(payload.side).orEmpty()
                return waiting.mapNotNull { derive(it, identity) }
            }

            is FastMoveUseObserved -> {
                if (!attributedUseIds.add(payload.useId)) return emptyList()
                val identity = identities[payload.attackingSide]
                if (identity == null) {
                    pendingUses.getOrPut(payload.attackingSide) { mutableListOf() } += article
                    return emptyList()
                }
                return listOfNotNull(derive(article, identity))
            }

            else -> return emptyList()
        }
        return emptyList()
    }

    private fun derive(useArticle: RealityArticle, identity: Identity): FastMoveEnergyDerivation? {
        val use = useArticle.payload as? FastMoveUseObserved ?: return null
        if (use.attackingSide != identity.payload.side) return null
        if (use.attackerSpeciesName != null &&
            normalize(use.attackerSpeciesName) != normalize(identity.payload.speciesName)
        ) return null
        return FastMoveEnergyDerivation(
            payload = FastMoveEnergyDerived(
                side = use.attackingSide,
                moveName = identity.payload.moveName,
                observedCompletedUses = 1,
                energyPerUse = identity.energyPerUse,
                totalEnergyGenerated = identity.energyPerUse,
                basis = "OBSERVED_FAST_MOVE_USE_TIMES_IDENTIFIED_MOVE_REFERENCE_ENERGY"
            ),
            predecessorIds = listOf(useArticle.id, identity.article.id),
            confidence = combineConfidence(useArticle.confidence, identity.article.confidence),
            observedArticle = useArticle
        )
    }

    private fun combineConfidence(use: Float?, identity: Float?): Float =
        ((use ?: DEFAULT_USE_CONFIDENCE) * (identity ?: DEFAULT_IDENTITY_CONFIDENCE))
            .coerceIn(0f, 1f)

    private fun normalize(value: String): String = value.uppercase().filter(Char::isLetterOrDigit)

    private companion object {
        const val DEFAULT_USE_CONFIDENCE = 0.75f
        const val DEFAULT_IDENTITY_CONFIDENCE = 0.70f
    }
}
