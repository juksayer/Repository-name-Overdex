package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.ActiveHpBarMotionCadenceMeasured
import com.example.overdex.battle.custody.ActiveHpBarBorderCadenceMeasured
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.FastMoveEffectiveness
import com.example.overdex.battle.custody.FastMoveEffectivenessWitnessed
import com.example.overdex.battle.custody.FastMoveIdentified
import com.example.overdex.battle.custody.FastMoveRecipientVisualCadenceMeasured
import com.example.overdex.battle.custody.FastMoveUseObserved
import com.example.overdex.battle.custody.PlayerTeamSlotConfigured
import com.example.overdex.battle.custody.TestimonyPayload
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import java.util.EnumMap
import kotlin.math.abs
import kotlin.math.max

data class FastMoveCadenceDerivation(
    val payload: TestimonyPayload,
    val predecessorIds: List<ArticleId>,
    val confidence: Float,
    /** The observed cadence interval that this derivation describes. */
    val observedArticle: RealityArticle
)

/**
 * Resolves measured cadence against the active species' possible fast moves.
 * It labels energy as derived and records when equal-duration resolution used
 * the explicit higher-energy assumption.
 */
class FastMoveCadenceInference(private val pokemonKnowledge: PokemonKnowledge) {
    private enum class CadenceBasis { BORDER_PULSE, RECIPIENT_VISUAL_ARTIFACT, BAR_MOTION }
    private data class CadenceEvidence(
        val article: RealityArticle,
        val intervalNanos: Long,
        val basis: CadenceBasis
    )
    private data class ConfiguredPlayerMove(
        val moveName: String,
        val articleId: ArticleId,
        val confidence: Float
    )

    private data class SideState(
        val speciesName: String,
        val speciesArticleId: ArticleId,
        val types: List<PokemonType>,
        val possibleMoves: List<Move>,
        val cadenceEvidence: MutableList<CadenceEvidence> = mutableListOf(),
        var identifiedMove: Move? = null,
        var selectedBasis: CadenceBasis? = null,
        var assumedHigherEnergyTieBreak: Boolean = false,
        var identificationEvidence: List<ArticleId> = emptyList()
    )

    private val states = EnumMap<ActivePokemonSide, SideState>(ActivePokemonSide::class.java)
    private val pending = EnumMap<ActivePokemonSide, MutableList<RealityArticle>>(ActivePokemonSide::class.java)
    private val appearanceStarts = EnumMap<ActivePokemonSide, Long>(ActivePokemonSide::class.java)
    private val configuredPlayerMoves = mutableMapOf<String, ConfiguredPlayerMove>()
    private val pendingEffectiveness = mutableListOf<RealityArticle>()
    private val recentUses = mutableListOf<RealityArticle>()


    suspend fun accept(article: RealityArticle): List<FastMoveCadenceDerivation> {
        val configured = article.payload as? PlayerTeamSlotConfigured
        if (configured != null) {
            configuredPlayerMoves[normalize(configured.speciesName)] = ConfiguredPlayerMove(
                moveName = configured.fastMoveName,
                articleId = article.id,
                confidence = article.confidence ?: 1f
            )
            return emptyList()
        }
        val check = article.payload as? com.example.overdex.battle.custody.SpeciesCheckMeasured
        if (check?.status == "OPENED" && check.reason in setOf("ENTRY", "EMPTY_HP")) {
            states.remove(check.side)
            pending.remove(check.side)
            pendingEffectiveness.clear()
            recentUses.removeAll { (it.payload as FastMoveUseObserved).attackingSide == check.side }
            appearanceStarts[check.side] = check.triggerMonotonicNanos
            return emptyList()
        }
        val species = article.payload as? ActivePokemonSpeciesWitnessed
        if (species != null) {
            // Reconfirming an unchanged badge must not reset timing or recount energy.
            if (states[species.side]?.speciesName == species.speciesName) return emptyList()
            recentUses.removeAll { (it.payload as FastMoveUseObserved).attackingSide == species.side }
            val pokemon = species.speciesId?.let { pokemonKnowledge.getPokemonById(it) }
                ?: pokemonKnowledge.getPokemonByName(species.speciesName)
            val possibleMoves = pokemon?.fastMoves.orEmpty().filter { it.isFast && it.turns != null }
            val configuredMove = if (species.side == ActivePokemonSide.PLAYER) {
                configuredPlayerMoves[normalize(species.speciesName)]
            } else null
            val candidateMoves = possibleMoves.filter {
                configuredMove == null || normalize(it.name) == normalize(configuredMove.moveName)
            }
            val configuredIdentity = configuredMove?.let { configuredKnowledge ->
                candidateMoves.singleOrNull()?.takeIf {
                    normalize(it.name) == normalize(configuredKnowledge.moveName)
                }
            }
            val state = SideState(
                speciesName = species.speciesName,
                speciesArticleId = article.id,
                types = pokemon?.types.orEmpty(),
                possibleMoves = candidateMoves,
                identifiedMove = configuredIdentity,
                identificationEvidence = configuredMove
                    ?.takeIf { configuredIdentity != null }
                    ?.let { listOf(article.id, it.articleId) }
                    .orEmpty()
            )
            states[species.side] = state
            val early = pending.remove(species.side).orEmpty()
            val effectiveness = if (states.size == ActivePokemonSide.entries.size) {
                pendingEffectiveness.toList().also { pendingEffectiveness.clear() }
            } else emptyList()
            val configuredResult = if (configuredIdentity != null) {
                val configuration = requireNotNull(configuredMove)
                listOf(FastMoveCadenceDerivation(
                    payload = FastMoveIdentified(
                        side = species.side,
                        speciesName = species.speciesName,
                        moveName = configuredIdentity.name,
                        moveDurationNanos = configuredIdentity.turns!! * TURN_NANOS,
                        observedMedianIntervalNanos = null,
                        cadenceSampleCount = 0,
                        basis = "CONFIGURED_PLAYER_TEAM_AND_ACTIVE_SPECIES"
                    ),
                    predecessorIds = state.identificationEvidence,
                    confidence = ((article.confidence ?: 1f) * configuration.confidence).coerceIn(0f, 1f),
                    observedArticle = article
                ))
            } else emptyList()
            return configuredResult + (effectiveness + early).flatMap { accept(it) }
        }

        val effectiveness = article.payload as? FastMoveEffectivenessWitnessed
        if (effectiveness != null) {
            if (states.size < ActivePokemonSide.entries.size || alignedUses(article, effectiveness).isEmpty()) {
                if (pendingEffectiveness.none { it.id == article.id }) pendingEffectiveness += article
                if (pendingEffectiveness.size > MAX_EFFECTIVENESS_HISTORY) pendingEffectiveness.removeAt(0)
                return emptyList()
            }
            return acceptEffectiveness(article, effectiveness)
        }

        val use = article.payload as? FastMoveUseObserved
        if (use != null) {
            if (recentUses.none { (it.payload as FastMoveUseObserved).useId == use.useId }) recentUses += article
            if (recentUses.size > MAX_USE_HISTORY) recentUses.removeAt(0)
            if (states.size < ActivePokemonSide.entries.size) return emptyList()
            val newlyAligned = pendingEffectiveness.filter { pendingArticle ->
                val pendingPayload = pendingArticle.payload as FastMoveEffectivenessWitnessed
                alignedUses(pendingArticle, pendingPayload).isNotEmpty()
            }
            pendingEffectiveness.removeAll(newlyAligned.toSet())
            return newlyAligned.flatMap { pendingArticle ->
                acceptEffectiveness(
                    pendingArticle,
                    pendingArticle.payload as FastMoveEffectivenessWitnessed
                )
            }
        }

        val cadence = when (val payload = article.payload) {
            is ActiveHpBarMotionCadenceMeasured -> CadenceInput(
                attackingSide = payload.movingSide,
                intervalNanos = payload.intervalNanos,
                basis = CadenceBasis.BAR_MOTION
            )
            is ActiveHpBarBorderCadenceMeasured -> CadenceInput(
                attackingSide = payload.damagedBarSide.opposite(),
                intervalNanos = payload.intervalNanos,
                basis = CadenceBasis.BORDER_PULSE
            )
            is FastMoveRecipientVisualCadenceMeasured -> CadenceInput(
                attackingSide = payload.damagedSide.opposite(),
                intervalNanos = payload.intervalNanos,
                basis = CadenceBasis.RECIPIENT_VISUAL_ARTIFACT
            )
            else -> return emptyList()
        }
        val attackingSide = cadence.attackingSide
        val start = appearanceStarts[attackingSide]
        val end = article.monotonicTimeNanos
        // A cadence spanning a witnessed change cannot be assigned to the new combatant.
        if (start != null && (end == null || end - cadence.intervalNanos < start)) return emptyList()
        val state = states[attackingSide] ?: run {
            val waiting = pending.getOrPut(attackingSide) { mutableListOf() }
            if (waiting.none { it.id == article.id }) waiting += article
            // The Timeline retains the complete evidence; this is only the live working set.
            if (waiting.size > MAX_CADENCE_HISTORY) waiting.removeAt(0)
            return emptyList()
        }
        if (state.cadenceEvidence.any { it.article.id == article.id })
            return emptyList()
        state.cadenceEvidence += CadenceEvidence(article, cadence.intervalNanos, cadence.basis)
        if (state.cadenceEvidence.size > MAX_CADENCE_HISTORY) state.cadenceEvidence.removeAt(0)

        // Once another independent signal has identified a move, the first
        // duration-compatible cadence measurement can account for a use. Three
        // samples are still required before cadence itself may replace identity.
        if (state.identifiedMove != null && state.selectedBasis == null &&
            intervalMatchesMove(cadence.intervalNanos, state.identifiedMove!!)
        ) {
            state.selectedBasis = cadence.basis
        }

        // Border pulses are direct hit cadence and motion remains a fallback.
        // Charge-button fill transitions are intentionally absent here: one Fast
        // Move can produce several visible fill stages, so those measurements are
        // preserved as energy evidence without pretending each stage is a move.
        val resolved = listOf(
            CadenceBasis.BORDER_PULSE,
            CadenceBasis.RECIPIENT_VISUAL_ARTIFACT,
            CadenceBasis.BAR_MOTION
        ).firstNotNullOfOrNull { basis ->
            val evidence = state.cadenceEvidence.filter { it.basis == basis }
            resolveMove(state.possibleMoves, evidence.map { it.intervalNanos })
                ?.let { resolution ->
                    ResolvedCadence(
                        resolution,
                        basis,
                        resolution.supportingIndices.map(evidence::get)
                    )
                }
        }
        val newlyIdentified = resolved != null && (
            state.identifiedMove != resolved.move.move ||
                state.selectedBasis != resolved.basis ||
                state.assumedHigherEnergyTieBreak != resolved.move.assumedHigherEnergyTieBreak
            )
        if (newlyIdentified) {
            state.identifiedMove = resolved!!.move.move
            state.selectedBasis = resolved.basis
            state.assumedHigherEnergyTieBreak = resolved.move.assumedHigherEnergyTieBreak
            state.identificationEvidence = buildList {
                add(state.speciesArticleId)
                addAll(resolved.evidence.map { it.article.id })
            }
        }
        val move = state.identifiedMove ?: return emptyList()
        val selectedBasis = state.selectedBasis ?: return emptyList()
        val selectedCadence = state.cadenceEvidence.filter {
            it.basis == selectedBasis && intervalMatchesMove(it.intervalNanos, move)
        }
        val intervals = selectedCadence.map { it.intervalNanos }
        val median = median(intervals)
        val predecessors = state.identificationEvidence
        val timingError = abs(median - move.turns!! * TURN_NANOS)
        val timingConfidence = (1f - timingError.toFloat() / max(move.turns * TURN_NANOS, 1L))
            .coerceIn(0f, 1f) * if (state.assumedHigherEnergyTieBreak) 0.7f else 1f
        val output = mutableListOf<FastMoveCadenceDerivation>()
        if (newlyIdentified) {
            output += FastMoveCadenceDerivation(
                payload = FastMoveIdentified(
                    side = attackingSide,
                    speciesName = state.speciesName,
                    moveName = move.name,
                    moveDurationNanos = move.turns * TURN_NANOS,
                    observedMedianIntervalNanos = median,
                    cadenceSampleCount = intervals.size,
                    basis = "${selectedBasis.name}_CADENCE_AND_REFERENCE_KNOWLEDGE" +
                        if (state.assumedHigherEnergyTieBreak) "_HIGHER_ENERGY_TIE_BREAK_ASSUMPTION" else ""
                ),
                predecessorIds = predecessors,
                confidence = timingConfidence,
                observedArticle = article
            )
        }
        return output
    }

    private fun acceptEffectiveness(
        article: RealityArticle,
        witnessed: FastMoveEffectivenessWitnessed
    ): List<FastMoveCadenceDerivation> {
        val attackingSides = alignedUses(article, witnessed)
            .map { (it.payload as FastMoveUseObserved).attackingSide }
            .distinct()
        val candidates = attackingSides.flatMap { attackingSide ->
            val attacker = states[attackingSide] ?: return@flatMap emptyList()
            val defender = states[attackingSide.opposite()] ?: return@flatMap emptyList()
            attacker.possibleMoves
                .filter { move -> effectivenessMatches(move.type, defender.types, witnessed.effectiveness) }
                .map { move -> EffectivenessMoveCandidate(attackingSide, move, attacker, defender) }
        }
        val candidate = candidates.singleOrNull() ?: return emptyList()
        if (candidate.attacker.identifiedMove == candidate.move) return emptyList()

        candidate.attacker.identifiedMove = candidate.move
        candidate.attacker.selectedBasis = null
        candidate.attacker.assumedHigherEnergyTieBreak = false
        candidate.attacker.identificationEvidence = listOf(
            candidate.attacker.speciesArticleId,
            candidate.defender.speciesArticleId,
            article.id
        ).distinct()

        return listOf(
            FastMoveCadenceDerivation(
                payload = FastMoveIdentified(
                    side = candidate.attackingSide,
                    speciesName = candidate.attacker.speciesName,
                    moveName = candidate.move.name,
                    moveDurationNanos = candidate.move.turns!! * TURN_NANOS,
                    observedMedianIntervalNanos = null,
                    cadenceSampleCount = 0,
                    basis = if (witnessed.damagedSide != null) {
                        "SIDE_LOCATED_EFFECTIVENESS_TEXT_AND_ACTIVE_SPECIES_REFERENCE_KNOWLEDGE"
                    } else {
                        "EFFECTIVENESS_TEXT_AND_ACTIVE_SPECIES_REFERENCE_KNOWLEDGE_UNIQUE_DIRECTION"
                    }
                ),
                predecessorIds = candidate.attacker.identificationEvidence,
                confidence = ((article.confidence ?: 0.85f) * if (witnessed.damagedSide == null) 0.9f else 1f)
                    .coerceIn(0f, 1f),
                observedArticle = article
            )
        )
    }

    private fun alignedUses(
        effectivenessArticle: RealityArticle,
        witnessed: FastMoveEffectivenessWitnessed
    ): List<RealityArticle> {
        val effectivenessAt = effectivenessArticle.monotonicTimeNanos ?: return emptyList()
        return recentUses.filter { useArticle ->
            val use = useArticle.payload as FastMoveUseObserved
            val useAt = useArticle.monotonicTimeNanos ?: return@filter false
            abs(effectivenessAt - useAt) <= EFFECTIVENESS_USE_ALIGNMENT_NANOS &&
                (witnessed.damagedSide == null || witnessed.damagedSide == use.damagedSide)
        }
    }

    private fun effectivenessMatches(
        attackType: PokemonType,
        defenderTypes: List<PokemonType>,
        witnessed: FastMoveEffectiveness
    ): Boolean {
        if (defenderTypes.isEmpty()) return false
        val multiplier = defenderTypes.fold(1.0) { value, defenderType ->
            value * when {
                attackType in defenderType.getWeaknesses() -> 1.6
                attackType in defenderType.getResistances() -> 0.625
                else -> 1.0
            }
        }
        return when (witnessed) {
            FastMoveEffectiveness.SUPER_EFFECTIVE -> multiplier > 1.0
            FastMoveEffectiveness.NOT_VERY_EFFECTIVE -> multiplier < 1.0
        }
    }

    internal fun resolveUniqueMove(moves: List<Move>, observedIntervals: List<Long>): Move? =
        resolveMove(moves, observedIntervals)?.move

    private fun resolveMove(moves: List<Move>, observedIntervals: List<Long>): MoveResolution? {
        if (observedIntervals.size < REQUIRED_INTERVALS) return null
        // Prefer a clean latest run so later evidence can promptly refute an
        // earlier assumption. Charge-control fill measurements can contain an
        // isolated missed or duplicate edge, however, so a unique three-sample
        // direct-duration cluster in the short history is also admissible.
        val recentStart = observedIntervals.size - REQUIRED_INTERVALS
        resolveDurationGroup(
            moves,
            observedIntervals.indices.filter { it >= recentStart }.toSet(),
            observedIntervals
        )?.let { return it }

        val historyStart = (observedIntervals.size - CLUSTER_HISTORY).coerceAtLeast(0)
        val durationGroups = moves
            .filter { it.turns != null }
            .groupBy { it.turns!! * TURN_NANOS }
            .mapNotNull { (expected, sameDurationMoves) ->
                val supporting = observedIntervals.indices
                    .filter { it >= historyStart && intervalMatchesExpected(observedIntervals[it], expected) }
                    .toSet()
                supporting.takeIf { it.size >= REQUIRED_INTERVALS }
                    ?.let { DurationCandidate(sameDurationMoves, it) }
            }
        val strongestCount = durationGroups.maxOfOrNull { it.supportingIndices.size } ?: return null
        val strongest = durationGroups.filter { it.supportingIndices.size == strongestCount }
        if (strongest.size != 1) return null
        return chooseMove(strongest.single().moves, strongest.single().supportingIndices)
    }

    private fun resolveDurationGroup(
        moves: List<Move>,
        indices: Set<Int>,
        observedIntervals: List<Long>
    ): MoveResolution? {
        if (indices.size < REQUIRED_INTERVALS) return null
        val candidates = moves.filter { move ->
            indices.all { intervalMatchesMove(observedIntervals[it], move) }
        }
        if (candidates.isEmpty()) return null
        return chooseMove(candidates, indices)
    }

    private fun chooseMove(moves: List<Move>, supportingIndices: Set<Int>): MoveResolution? {
        moves.singleOrNull()?.let { return MoveResolution(it, false, supportingIndices) }
        val highestEnergy = moves.maxOfOrNull { it.energy } ?: return null
        val preferred = moves.filter { it.energy == highestEnergy }.singleOrNull() ?: return null
        return MoveResolution(preferred, true, supportingIndices)
    }

    private fun intervalMatchesMove(intervalNanos: Long, move: Move): Boolean {
        val expected = (move.turns ?: return false) * TURN_NANOS
        return intervalMatchesExpected(intervalNanos, expected)
    }

    private fun intervalMatchesExpected(intervalNanos: Long, expectedNanos: Long): Boolean {
        val tolerance = max(MIN_TOLERANCE_NANOS, (expectedNanos * RELATIVE_TOLERANCE).toLong())
        return abs(intervalNanos - expectedNanos) <= tolerance
    }

    private fun median(values: List<Long>): Long {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle]
        else (sorted[middle - 1] / 2L) + (sorted[middle] / 2L)
    }

    private fun ActivePokemonSide.opposite(): ActivePokemonSide = when (this) {
        ActivePokemonSide.PLAYER -> ActivePokemonSide.OPPONENT
        ActivePokemonSide.OPPONENT -> ActivePokemonSide.PLAYER
    }

    private fun normalize(value: String): String = value.uppercase().filter(Char::isLetterOrDigit)

    private data class CadenceInput(
        val attackingSide: ActivePokemonSide,
        val intervalNanos: Long,
        val basis: CadenceBasis
    )

    private data class MoveResolution(
        val move: Move,
        val assumedHigherEnergyTieBreak: Boolean,
        val supportingIndices: Set<Int>
    )
    private data class DurationCandidate(val moves: List<Move>, val supportingIndices: Set<Int>)
    private data class ResolvedCadence(
        val move: MoveResolution,
        val basis: CadenceBasis,
        val evidence: List<CadenceEvidence>
    )
    private data class EffectivenessMoveCandidate(
        val attackingSide: ActivePokemonSide,
        val move: Move,
        val attacker: SideState,
        val defender: SideState
    )

    private companion object {
        const val TURN_NANOS = 500_000_000L
        // Three observed attacks provide two elapsed intervals. This meets the
        // 1.5-second latency target for 500 ms moves without using frame count as time.
        const val REQUIRED_INTERVALS = 2
        const val CLUSTER_HISTORY = 12
        const val MAX_CADENCE_HISTORY = 64
        const val MAX_EFFECTIVENESS_HISTORY = 16
        const val MAX_USE_HISTORY = 16
        const val EFFECTIVENESS_USE_ALIGNMENT_NANOS = 500_000_000L
        const val MIN_TOLERANCE_NANOS = 160_000_000L
        const val RELATIVE_TOLERANCE = 0.15
    }
}
