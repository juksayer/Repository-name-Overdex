package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.ActivePokemonFaintedWitnessed
import com.example.overdex.battle.custody.FastMoveEnergyDerived
import com.example.overdex.battle.custody.FastMoveIdentified
import com.example.overdex.battle.custody.FastMoveUseObserved
import com.example.overdex.battle.custody.SpeciesCheckMeasured
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.data.PokemonFormKnowledge
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
class FastMoveEnergyInference(
    private val pokemonKnowledge: PokemonKnowledge,
    private val opponentFormReferences: PokemonFormKnowledge = PokemonFormKnowledge(pokemonKnowledge),
) {
    private data class Identity(
        val article: RealityArticle,
        val payload: FastMoveIdentified,
        val energyPerUse: Int,
        val referenceDurationNanos: Long,
    )

    private val activeSpecies = EnumMap<ActivePokemonSide, String>(ActivePokemonSide::class.java)
    private val fainted = FaintedCombatants()
    private val identities = EnumMap<ActivePokemonSide, Identity>(ActivePokemonSide::class.java)
    private val pendingUses = EnumMap<ActivePokemonSide, MutableList<RealityArticle>>(ActivePokemonSide::class.java)
    private val attributedUseIds = mutableSetOf<String>()
    private val timingRejectedUseIds = mutableSetOf<String>()
    private val timing = FastMoveEnergyTiming()
    private val appearanceStarts = EnumMap<ActivePokemonSide, Long>(ActivePokemonSide::class.java)
    private val learnedIdentities = mutableMapOf<Pair<ActivePokemonSide, String>, Identity>()
    private val playerAppearances = java.util.TreeMap<Long, ActivePokemonSpeciesWitnessed>()
    private val speciesHistory = EnumMap<ActivePokemonSide, java.util.TreeMap<Long, String>>(ActivePokemonSide::class.java)

    suspend fun accept(article: RealityArticle): List<FastMoveEnergyDerivation> {
        fainted.observe(article)
        timing.observe(article)
        when (val payload = article.payload) {
            is ActivePokemonFaintedWitnessed -> {
                val active = activeSpecies[payload.side]
                val at = article.monotonicTimeNanos ?: Long.MIN_VALUE
                val currentStart = appearanceStarts[payload.side] ?: Long.MIN_VALUE
                val closesCurrent = at >= currentStart &&
                    (payload.speciesName == null || active == null || normalize(payload.speciesName) == normalize(active))
                val historicalSpecies = speciesHistory[payload.side]?.floorEntry(at)?.value
                val closesHistorical = at < currentStart && historicalSpecies != null &&
                    (payload.speciesName == null || normalize(payload.speciesName) == normalize(historicalSpecies))
                if (closesCurrent || closesHistorical) timing.fainted(payload.side, at)
                if (closesCurrent) {
                    identities.remove(payload.side)
                    activeSpecies.remove(payload.side)
                    appearanceStarts[payload.side] = at
                }
                return emptyList()
            }
            is SpeciesCheckMeasured -> if (
                payload.status == "OPENED" && payload.reason in setOf("ENTRY", "EMPTY_HP", "FAINT", "SWITCH") &&
                activeSpecies[payload.side] == null &&
                payload.triggerMonotonicNanos > (appearanceStarts[payload.side] ?: Long.MIN_VALUE)
            ) {
                identities.remove(payload.side)
                activeSpecies.remove(payload.side)
                // A later identity may still resolve named uses from the departed
                // combatant. Keep those uses; an entry only closes unsided context.
                appearanceStarts[payload.side] = payload.triggerMonotonicNanos
                return emptyList()
            }

            is ActivePokemonSpeciesWitnessed -> {
                if (payload.side == ActivePokemonSide.PLAYER && payload.appearanceStartedAtNanos != null)
                    playerAppearances[payload.appearanceStartedAtNanos] = payload
                payload.appearanceStartedAtNanos?.let { boundary ->
                    // A delayed historical appearance still constrains its own
                    // uses even when it must not replace the current combatant.
                    if (!fainted.at(payload.side, payload.speciesName, boundary)) {
                        timing.appearanceStarted(payload.side, boundary)
                        speciesHistory.getOrPut(payload.side) { java.util.TreeMap() }[boundary] = payload.speciesName
                    }
                }
                if (fainted.contains(payload.side, payload.speciesName)) return emptyList()
                if (activeSpecies[payload.side]?.let(::normalize) != normalize(payload.speciesName) ||
                    (payload.appearanceStartedAtNanos != null && payload.appearanceStartedAtNanos != appearanceStarts[payload.side])) {
                    val at = article.monotonicTimeNanos ?: Long.MIN_VALUE
                    if (at < (appearanceStarts[payload.side] ?: Long.MIN_VALUE)) return emptyList()
                    identities.remove(payload.side)
                    if (activeSpecies.containsKey(payload.side) || payload.appearanceStartedAtNanos != null ||
                        appearanceStarts.containsKey(payload.side)) {
                        val boundary = payload.appearanceStartedAtNanos ?: at
                        appearanceStarts[payload.side] = boundary
                        timing.appearanceStarted(payload.side, boundary)
                    }
                    activeSpecies[payload.side] = payload.speciesName
                    speciesHistory.getOrPut(payload.side) { java.util.TreeMap() }[
                        payload.appearanceStartedAtNanos ?: at] = payload.speciesName
                }
                return emptyList()
            }

            is FastMoveIdentified -> {
                // A single cadence source (and especially a higher-energy tie
                // break) is useful as a live HUD hypothesis, not yet an exact
                // energy identity. Preserve it on the Timeline and wait for a
                // second source, unique reference pool, configured player move,
                // or uniquely resolving effectiveness text.
                if (!identityIsReadyForExactEnergy(payload.basis)) {
                    identities.remove(payload.side)
                    return emptyList()
                }
                val possibleMoves = if (payload.side == ActivePokemonSide.OPPONENT)
                    opponentFormReferences.forObservedName(payload.speciesName).fastMoves
                else pokemonKnowledge.getPokemonByName(payload.speciesName)?.fastMoves.orEmpty()
                val move = possibleMoves.firstOrNull {
                    normalize(it.name) == normalize(payload.moveName)
                } ?: return emptyList()
                val turns = move.turns?.takeIf { it > 0 } ?: return emptyList()
                if (!move.isFast || move.energy <= 0) return emptyList()
                val identity = Identity(article, payload, move.energy, turns.toLong() * 500_000_000L)
                learnedIdentities[payload.side to normalize(payload.speciesName)] = identity
                val active = activeSpecies[payload.side]
                if (!fainted.contains(payload.side, payload.speciesName) && ((active == null && (article.monotonicTimeNanos ?: Long.MIN_VALUE) >=
                        (appearanceStarts[payload.side] ?: Long.MIN_VALUE)) ||
                    (active != null && normalize(active) == normalize(payload.speciesName)))) {
                    identities[payload.side] = identity
                }
                val waiting = pendingUses[payload.side] ?: return emptyList()
                val resolved = waiting.sortedBy { it.monotonicTimeNanos ?: Long.MAX_VALUE }.mapNotNull { useArticle ->
                    val use = useArticle.payload as FastMoveUseObserved
                    // Unknown older uses cannot inherit a replacement's species.
                    if (use.attackerSpeciesName == null &&
                        (useArticle.monotonicTimeNanos ?: Long.MIN_VALUE) <
                        (appearanceStarts[payload.side] ?: Long.MIN_VALUE)
                    ) null else derive(useArticle, identity)
                }
                val resolvedIds = resolved.map { it.observedArticle.id }.toSet()
                waiting.removeAll { it.id in resolvedIds ||
                    (it.payload as FastMoveUseObserved).useId in timingRejectedUseIds }
                return resolved
            }

            is FastMoveUseObserved -> {
                // Charge-button shade can advance through several visual steps
                // during one move, and also changes during the countdown. It is
                // supporting evidence, not a trustworthy one-use counter by
                // itself. Exact energy requires a battlefield hit/use witness.
                if (!FastMoveUseEvidence.establishesUse(payload.evidenceKinds)) return emptyList()
                if (!attributedUseIds.add(payload.useId)) return emptyList()
                val historical = (article.monotonicTimeNanos ?: Long.MAX_VALUE) <
                    (appearanceStarts[payload.attackingSide] ?: Long.MIN_VALUE)
                val identity = if (historical) payload.attackerSpeciesName?.let {
                    val resolved = if (payload.attackingSide == ActivePokemonSide.PLAYER)
                        playerAppearances.floorEntry(article.monotonicTimeNanos ?: Long.MIN_VALUE)?.value else null
                    learnedIdentities[payload.attackingSide to normalize(resolved?.speciesName ?: it)]
                } else identities[payload.attackingSide]
                val derived = identity?.let { derive(article, it) }
                if (derived != null) return listOf(derived)
                if (payload.useId !in timingRejectedUseIds)
                    pendingUses.getOrPut(payload.attackingSide) { mutableListOf() } += article
                return emptyList()
            }

            else -> return emptyList()
        }
        return emptyList()
    }

    private fun derive(useArticle: RealityArticle, identity: Identity): FastMoveEnergyDerivation? {
        val use = useArticle.payload as? FastMoveUseObserved ?: return null
        if (use.useId in timingRejectedUseIds) return null
        if (fainted.at(use.attackingSide, identity.payload.speciesName,
                useArticle.monotonicTimeNanos ?: return null)) return null
        if (use.attackingSide != identity.payload.side) return null
        val resolvedPlayer = if (use.attackingSide == ActivePokemonSide.PLAYER)
            playerAppearances.floorEntry(useArticle.monotonicTimeNanos ?: return null)?.value else null
        if (resolvedPlayer?.playerTeamSlot != null &&
            normalize(resolvedPlayer.speciesName) != normalize(identity.payload.speciesName)) return null
        if (use.attackerSpeciesName != null && normalize(use.attackerSpeciesName) != normalize(identity.payload.speciesName)) {
            val resolved = resolvedPlayer
            val sameResolvedAppearance = use.attackingSide == ActivePokemonSide.PLAYER && resolved?.playerTeamSlot != null &&
                normalize(resolved.speciesName) == normalize(identity.payload.speciesName) &&
                '(' !in use.attackerSpeciesName &&
                com.example.overdex.battle.observation.TeamRosterSpeciesAttributor.speciesKey(use.attackerSpeciesName) ==
                com.example.overdex.battle.observation.TeamRosterSpeciesAttributor.speciesKey(resolved.speciesName)
            if (!sameResolvedAppearance) return null
        }
        val observedAt = useArticle.monotonicTimeNanos ?: return null
        if (!timing.reserve(use.attackingSide, observedAt, identity.referenceDurationNanos)) {
            timingRejectedUseIds += use.useId
            return null
        }
        return FastMoveEnergyDerivation(
            payload = FastMoveEnergyDerived(
                side = use.attackingSide,
                moveName = identity.payload.moveName,
                observedCompletedUses = 1,
                energyPerUse = identity.energyPerUse,
                totalEnergyGenerated = identity.energyPerUse,
                basis = "OBSERVED_FAST_MOVE_USE_TIMES_IDENTIFIED_MOVE_REFERENCE_ENERGY_DURATION_BOUNDED"
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

    private fun identityIsReadyForExactEnergy(basis: String): Boolean =
        !basis.contains(HIGHER_ENERGY_TIE_BREAK_ASSUMPTION) &&
            (basis.startsWith("CONFIGURED_PLAYER_TEAM_AND_ACTIVE_SPECIES") ||
                basis.startsWith("ACTIVE_SPECIES_UNIQUE_FAST_MOVE_REFERENCE_KNOWLEDGE") ||
                basis.startsWith("AUDIO_REFERENCE_REPEATED_UNIQUE_LEGAL_SIDE") ||
                basis.contains("EFFECTIVENESS_TEXT") ||
                basis.contains("_CORROBORATED_BY_"))

    private companion object {
        const val DEFAULT_USE_CONFIDENCE = 0.75f
        const val DEFAULT_IDENTITY_CONFIDENCE = 0.70f
        const val HIGHER_ENERGY_TIE_BREAK_ASSUMPTION = "HIGHER_ENERGY_TIE_BREAK_ASSUMPTION"
    }
}
