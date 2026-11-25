package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.isSideLocatedSpeciesSource
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.BattleResult

internal data class TerminalFinishRecord(
    val id: String, val at: Long, val source: String, val confidence: Float?,
    val payload: TestimonyPayload,
    val cryMatchedThroughNanos: Long? = null,
)

internal data class TerminalChargedFaintResolution(
    val side: ActivePokemonSide, val owner: TeamLossCandidate, val atNanos: Long,
    val sourceArticleId: String, val evidenceReferences: List<String>,
    val basis: String = TerminalChargedFaintResolver.BASIS,
)

/**
 * Conservative terminal-charge inference. Neither vanished HP nor the result alone
 * establishes a KO. It creates no HP measurement, count, move or match result.
 */
internal object TerminalChargedFaintResolver {
    const val BASIS = "FINAL_CHARGED_COLLAPSE_AND_RESULT"

    fun relevant(p: TestimonyPayload, source: String): Boolean = when (p) {
        is PlayerTeamSlotConfigured, is PlayerTeamRosterSlotWitnessed, is PlayerTeamRosterConfirmation -> true
        is BattleCryLocalized -> source == "LOCALIZED_BATTLE_CRY_WITNESS"
        is ActivePokemonFaintedWitnessed -> !DepartureCryFaint.isBasis(p.basis) && (p.basis.contains("POKE_BALL_COUNT_DECREASE") || p.basis == VerifiedHpFaintResolver.BASIS)
        is TerminalParticleObservations, is CompleteHpBarObserved, is ActivePokemonSpeciesWitnessed, is ActiveHpBarMeasured,
        is PlayerPokeBallCountMeasured, is OpponentBattleResourceCountMeasured,
        is ChargedMoveEnergySpent, is FastMoveUseObserved, is SpeciesCheckMeasured, is MatchOutcomeLabelWitnessed,
        is MatchEnded, MatchStarted, GetReadyWitnessed, AttackIncoming, ChargeMoveUsedAnnounced,
        ObservationRestartedByUser, ScreenIgnoredByUser -> true
        is RawTestimony -> source in RESULT_SOURCES || source == "ANNOUNCEMENT_WITNESS"
        else -> false
    }

    private val RESULT_SOURCES = setOf("YOU_WIN_WITNESS", "GOOD_EFFORT_WITNESS")
    private val USED_ANNOUNCEMENT = Regex("^(.+?)\\s+used\\s+(.+?)[!.]*$", RegexOption.IGNORE_CASE)
    private fun announcedPerformer(r: TerminalFinishRecord): String? {
        if (r.source != "ANNOUNCEMENT_WITNESS") return null
        val text = ((r.payload as? RawTestimony)?.data as? String)?.trim()
            ?.replace(Regex("\\s+"), " ") ?: return null
        val match = USED_ANNOUNCEMENT.matchEntire(text) ?: return null
        // Preserve an incomplete move label as an unknown move. Do not resolve
        // a legal move, spend energy, or synthesize an attack from this parser.
        if (match.groupValues[2].count(Char::isLetter) < 3) return null
        return match.groupValues[1]
    }
    private fun result(r: TerminalFinishRecord): BattleResult? {
        if (r.source !in RESULT_SOURCES) return null
        val expected = if (r.source == "YOU_WIN_WITNESS") BattleResult.WIN else BattleResult.LOSS
        return when (val p = r.payload) {
            is MatchOutcomeLabelWitnessed -> p.result.takeIf { it == expected }
            // These legacy witnesses only published their accepted exact phrase.
            is RawTestimony -> expected.takeIf { p.data.toString().uppercase().filter(Char::isLetter) ==
                if (expected == BattleResult.WIN) "YOUWIN" else "GOODEFFORT" }
            else -> null
        }
    }

    private fun completeColorGeometry(p: CompleteHpBarObserved): Boolean =
        p.barLeft >= 0 && p.barRight <= p.sourceWidth && p.barTop >= 0 && p.barBottom < p.sourceHeight &&
            p.barBottom > p.barTop && p.barRight - p.barLeft >= 6 * (p.barBottom - p.barTop) &&
            p.interiorColumns in 1..(p.barRight - p.barLeft)

    fun resolve(input: List<TerminalFinishRecord>): List<TerminalChargedFaintResolution> {
        val groups = input.groupBy { it.id }
        val conflicts = groups.values.filter { it.distinct().size > 1 }.flatten()
        val rows = groups.values.mapNotNull { it.distinct().singleOrNull() }.sortedBy { it.at }
        val reset = rows.lastOrNull { it.payload == ObservationRestartedByUser || it.payload == ScreenIgnoredByUser }?.at
            ?: Long.MIN_VALUE
        val start = rows.firstOrNull { it.at >= reset && it.payload == MatchStarted }?.at
            ?: rows.firstOrNull { it.at >= reset && BattleStartEvidence.accepts(it.payload, it.source) }?.at
            ?: return emptyList()
        val outcome = rows.firstOrNull { it.at >= start && result(it) != null } ?: return emptyList()
        val expected = result(outcome)!!
        if (rows.any { it.at >= start && result(it)?.let { r -> r != expected } == true } ||
            rows.any { (it.payload as? MatchEnded)?.result?.let { r -> it.at >= start && r != expected } == true }) return emptyList()
        val side = if (expected == BattleResult.WIN) ActivePokemonSide.OPPONENT else ActivePokemonSide.PLAYER
        val attacker = if (side == ActivePokemonSide.OPPONENT) ActivePokemonSide.PLAYER else ActivePokemonSide.OPPONENT
        val eligible = rows.filter { it.at in start..outcome.at }
        fun usedCue(r: TerminalFinishRecord) = eligible.filter { it.at == r.at &&
            it.source == "CHARGE_MOVE_USED_ANNOUNCEMENT_WITNESS" && it.payload == ChargeMoveUsedAnnounced }
            .minByOrNull { it.id }
        fun identity(r: TerminalFinishRecord, s: ActivePokemonSide): ActivePokemonSpeciesWitnessed? =
            (r.payload as? ActivePokemonSpeciesWitnessed)?.takeIf { it.side == s &&
                isSideLocatedSpeciesSource(r.source) && r.confidence?.let { c -> c.isFinite() && c in .8f..1f } == true }
        fun count(r: TerminalFinishRecord): Int? = when (val p = r.payload) {
            is PlayerPokeBallCountMeasured -> p.visibleCount.takeIf { side == ActivePokemonSide.PLAYER &&
                p.maximumCount == 3 && r.source == "PLAYER_POKE_BALLS_WITNESS" }
            is OpponentBattleResourceCountMeasured -> p.visibleCount.takeIf { side == ActivePokemonSide.OPPONENT &&
                p.maximumCount == 3 && p.resource == OpponentBattleResource.POKE_BALLS && r.source == "OPPONENT_POKE_BALLS_WITNESS" }
            else -> null
        }
        // A batch can straddle the result's source time or publish afterward.
        // Its individual frame clocks, not its envelope clock, define eligibility.
        val batches = rows.filter { r -> r.source == "TERMINAL_PARTICLE_WITNESS" &&
            (r.payload as? TerminalParticleObservations)?.requestedRecipient == side }
        val resolved = mutableListOf<TerminalChargedFaintResolution>()
        for ((phase, sourceBatches) in batches.groupBy { (it.payload as TerminalParticleObservations).phaseAtNanos }) {
            val frames = sourceBatches.flatMap { r ->
                val p = r.payload as TerminalParticleObservations
                p.samples.filter { it.sourceAtNanos in maxOf(start, phase) until p.sourceDeadlineNanos &&
                    it.sourceAtNanos <= outcome.at }.map { TerminalEffectFrame(r.id, it) }
            }
            val ordinary = TerminalCollapseSequences.find(frames)
            val barSupport = eligible.filter { it.at >= phase && it.source == "COMPLETE_${side.name}_HP_BAR_FRAME_WITNESS" &&
                (it.payload as? CompleteHpBarObserved)?.side == side }
            val colorPairs = barSupport.mapNotNull { orange ->
                val p = orange.payload as CompleteHpBarObserved
                if (p.liveColumns != 0 || p.damageColumns !in 3..(p.interiorColumns / 4) ||
                    p.status !in setOf("DAMAGE_COLOR", "SUSTAINED_DAMAGE_COLOR", "UNRESOLVED_COLOR") ||
                    !completeColorGeometry(p)) return@mapNotNull null
                val red = barSupport.lastOrNull { prior ->
                    val q = prior.payload as CompleteHpBarObserved
                    orange.at - prior.at in 1..600_000_000L && q.liveColumns in 1..(q.interiorColumns / 4) &&
                        q.liveColumns > q.damageColumns && completeColorGeometry(q) &&
                        q.sourceWidth == p.sourceWidth && q.sourceHeight == p.sourceHeight &&
                        kotlin.math.abs(q.barLeft - p.barLeft) <= 8 && kotlin.math.abs(q.barTop - p.barTop) <= 20
                }
                red?.let { it to orange }
            }
            fun supportsCore(pair: Pair<TerminalFinishRecord, TerminalFinishRecord>,
                sample: TerminalParticleSample, core: TerminalFlashFeature): Boolean {
                val p = pair.second.payload as CompleteHpBarObserved
                return sample.sourceAtNanos - pair.second.at in 200_000_000L..2_600_000_000L &&
                    p.sourceWidth == sample.sourceWidth && p.sourceHeight == sample.sourceHeight &&
                    core.x in (p.barLeft - p.sourceWidth * .06)..(p.barRight + p.sourceWidth * .14) &&
                    kotlin.math.abs(core.y - p.barTop) <= p.sourceHeight * .20
            }
            val supported = if (ordinary.isEmpty() && colorPairs.isNotEmpty()) TerminalCollapseSequences.find(frames) { sample, core ->
                colorPairs.any { supportsCore(it, sample, core) }
            } else emptyList()
            val sequences: List<TerminalFinishSequence> = ordinary + supported +
                if (side == ActivePokemonSide.PLAYER) TerminalAppearanceSequences.find(frames) else emptyList()
            for (sequence in sequences) {
                if (outcome.at - sequence.clearedAt !in 0..20_000_000_000L) continue
                val charge = eligible.filter { it.at <= sequence.startedAt &&
                    (it.payload is ChargedMoveEnergySpent || (announcedPerformer(it) != null && usedCue(it) != null)) }
                    .maxWithOrNull(compareBy<TerminalFinishRecord> { it.at }
                        .thenBy { it.payload is ChargedMoveEnergySpent }.thenBy { it.id }) ?: continue
                if (charge.at < phase || sequence.startedAt - charge.at !in 0..6_000_000_000L) continue
                val move = charge.payload as? ChargedMoveEnergySpent
                val chargeSupport = mutableListOf(charge)
                val performer = if (move != null) {
                    if (move.side != attacker || charge.source != "CHARGED_MOVE_ANNOUNCEMENT_INTERPRETER" ||
                        move.basis != "ANNOUNCED_NAMED_CHARGED_MOVE_AND_REFERENCE_ENERGY_COST") continue
                    move.speciesName
                } else {
                    // A clearly named performer + native Used cue establishes a
                    // charge occurrence even when its move label is clipped. Its
                    // direction additionally requires the latest independent phase.
                    val direction = eligible.lastOrNull { it.at <= charge.at &&
                        (it.payload == AttackIncoming || it.payload == GetReadyWitnessed) } ?: continue
                    if (charge.at - direction.at !in 0..20_000_000_000L ||
                        (attacker == ActivePokemonSide.OPPONENT &&
                            (direction.payload != AttackIncoming || direction.source != "ATTACK_INCOMING_WITNESS")) ||
                        (attacker == ActivePokemonSide.PLAYER &&
                            (direction.payload != GetReadyWitnessed || direction.source != "GET_READY_WITNESS"))) continue
                    // Co-timed contrary directions are ambiguous, irrespective of delivery order.
                    if (eligible.any { it.at == direction.at &&
                            (it.payload == AttackIncoming || it.payload == GetReadyWitnessed) &&
                            it.payload != direction.payload }) continue
                    chargeSupport += direction
                    chargeSupport += requireNotNull(usedCue(charge))
                    requireNotNull(announcedPerformer(charge))
                }
                if (eligible.any { other -> other.at == charge.at && when(val p=other.payload) {
                        is ChargedMoveEnergySpent -> p.side != attacker ||
                            TeamLossResolver.speciesKey(p.speciesName) != TeamLossResolver.speciesKey(performer)
                        is RawTestimony -> announcedPerformer(other)?.let {
                            TeamLossResolver.speciesKey(it) != TeamLossResolver.speciesKey(performer) } == true
                        else -> false
                    } }) continue
                val colorProof = if (sequence in supported) colorPairs.lastOrNull { (_, orange) ->
                    sequence.startedAt - orange.at in 200_000_000L..2_600_000_000L &&
                        orange.at - charge.at in -500_000_000L..2_000_000_000L
                } ?: continue else null
                val ownerRow = eligible.lastOrNull { it.at <= charge.at && identity(it, side) != null } ?: continue
                val owner = identity(ownerRow, side)!!
                val userRow = eligible.lastOrNull { it.at <= charge.at && identity(it, attacker) != null } ?: continue
                // Distinct IDs disagreeing at one source instant are also ambiguous.
                if (eligible.any { it.at == ownerRow.at && identity(it, side)?.let { p ->
                        TeamLossResolver.speciesKey(p.speciesName) != TeamLossResolver.speciesKey(owner.speciesName) } == true } ||
                    eligible.any { it.at == userRow.at && identity(it, attacker)?.let { p ->
                        TeamLossResolver.speciesKey(p.speciesName) != TeamLossResolver.speciesKey(performer) } == true }) continue
                if (TeamLossResolver.speciesKey(identity(userRow, attacker)!!.speciesName) !=
                    TeamLossResolver.speciesKey(performer)) continue
                val lastCount = eligible.lastOrNull { it.at <= sequence.startedAt && count(it) != null } ?: continue
                // Remaining team size is stable between losses. The event-driven ball
                // reader intentionally sleeps after a confirmed count, so elapsed time
                // alone cannot revoke it. Match/reset scope and contrary counts below
                // still prevent carrying this evidence into another battle.
                if (count(lastCount) != 1) continue
                if (eligible.any { it.at >= lastCount.at && count(it)?.let { n -> n > 1 } == true }) continue
                // Source conflicts cannot be silently dropped to create a convincing finish.
                if (conflicts.any { it.at in minOf(ownerRow.at, userRow.at, lastCount.at, phase,
                        chargeSupport.minOf { proof -> proof.at })..outcome.at }) continue
                val ownerKey = TeamLossResolver.speciesKey(owner.speciesName)
                val attackerKey = TeamLossResolver.speciesKey(performer)
                val invalidated = rows.any { r ->
                    val p = r.payload
                    when {
                        r.at >= charge.at && p is ActivePokemonSpeciesWitnessed ->
                            r.source == "ENTRY_ANNOUNCEMENT_SPECIES_WITNESS" ||
                                (identity(r, p.side) != null && TeamLossResolver.speciesKey(p.speciesName) !=
                                    if (p.side == side) ownerKey else attackerKey)
                        p is SpeciesCheckMeasured -> p.status == "OPENED" && p.reason == "SWITCH" &&
                            p.triggerMonotonicNanos in charge.at..outcome.at
                        r.at > charge.at && r.at <= outcome.at ->
                            p == GetReadyWitnessed || p == AttackIncoming || p is ChargedMoveEnergySpent ||
                                (p == ChargeMoveUsedAnnounced && r.at-charge.at > 4_000_000_000L) ||
                                (p is FastMoveUseObserved && FastMoveUseEvidence.establishesUse(p.evidenceKinds)) ||
                                (r.at >= sequence.startedAt && p is ActiveHpBarMeasured && p.side == side &&
                                    p.filledFraction > 0f && p.filledFraction <= 1f && r.source in setOf(
                                    "${side.name}_ACTIVE_HP_BAR_WITNESS", "CHARGED_IMPACT_${side.name}_HP_BAR_FRAME_WITNESS"))
                        else -> false
                    }
                }
                if (invalidated || colorProof?.let { proof -> barSupport.any {
                        it.at > proof.second.at && (it.payload as CompleteHpBarObserved).liveColumns > 0
                    } } == true) continue
                // A later independent positive frame with a fresh same-species badge
                // defeats this conclusion even if that evidence was recognized late.
                if (rows.any { hp ->
                        val p = hp.payload as? ActiveHpBarMeasured
                        p?.side == side && p.filledFraction in Float.MIN_VALUE..1f && hp.at > sequence.clearedAt &&
                            hp.source == "${side.name}_ACTIVE_HP_BAR_WITNESS" && rows.any { id ->
                                id.at in sequence.clearedAt..hp.at && identity(id, side)?.let {
                                    TeamLossResolver.speciesKey(it.speciesName) == ownerKey } == true }
                    }) continue
                val sourceId = frames.lastOrNull { it.sample.sourceAtNanos == sequence.clearedAt }?.articleId ?: continue
                resolved += TerminalChargedFaintResolution(side,
                    TeamLossCandidate(ownerRow.id, ownerRow.at, owner.speciesName, owner.speciesId),
                    sequence.clearedAt, sourceId,
                    (sequence.evidenceReferences + chargeSupport.map { it.id } +
                        listOfNotNull(colorProof?.first?.id, colorProof?.second?.id) +
                        listOf(ownerRow.id, userRow.id, lastCount.id, outcome.id)).distinct())
            }
        }
        // Two separate candidate departures before the result leave timing ambiguous.
        return resolved.distinctBy { it.atNanos }.singleOrNull()?.let(::listOf).orEmpty()
    }
}

/** Shared live projection; it retains only eligible raw inputs, never derived faints. */
internal class LiveTerminalChargedFaintHistory(private val side: ActivePokemonSide,
    private val onRebuild: ((String, Int) -> Unit)? = null,
    private val cache: FaintHistoryCache? = null) {
    private val rows = linkedMapOf<String, TerminalFinishRecord>()
    private val conflicts = mutableListOf<TerminalFinishRecord>()
    private val sources = mutableMapOf<String, RealityArticle>()
    private var matchId: String? = null
    private var hasResult = false
    private val cryTimes = java.util.TreeSet<Long>()
    private val candidateCries = mutableSetOf<Long>()
    private data class CryWindow(val span: LongRange, val eligibleDeparture: Boolean)
    private val cryWindows = mutableMapOf<Int, MutableList<CryWindow>>()
    private val orangeTimes = java.util.TreeSet<Long>()
    var resolutions: List<TerminalChargedFaintResolution> = emptyList()
        private set
    fun source(id: String) = sources[id]
    fun hasIndependentReturnProof(identityId: String): Boolean {
        val id = rows[identityId] ?: return false
        if (conflicts.any { it.id == identityId }) return false
        val p = id.payload as? ActivePokemonSpeciesWitnessed ?: return false
        if (p.side != side || !isSideLocatedSpeciesSource(id.source) ||
            id.confidence?.let { it.isFinite() && it in .8f..1f } != true) return false
        return rows.values.any { r ->
            val hp = r.payload as? ActiveHpBarMeasured
            hp?.side == side && r.source == "${side.name}_ACTIVE_HP_BAR_WITNESS" &&
                conflicts.none { it.id == r.id } &&
                hp.filledFraction in Float.MIN_VALUE..1f && r.at >= id.at &&
                rows.values.none { other -> other.at in id.at..r.at &&
                    (other.payload as? ActivePokemonSpeciesWitnessed)?.let {
                        it.side == side && isSideLocatedSpeciesSource(other.source) &&
                            TeamLossResolver.speciesKey(it.speciesName) != TeamLossResolver.speciesKey(p.speciesName)
                    } == true }
        }
    }
    fun accept(a: RealityArticle): Boolean {
        val at = a.monotonicTimeNanos ?: return false
        if (matchId != null && a.matchId?.value != matchId) return false
        if (a.matchId != null) matchId = a.matchId.value
        if (!TerminalChargedFaintResolver.relevant(a.payload, a.sourceId.id)) return false
        val r = TerminalFinishRecord(a.id.value, at, a.sourceId.id, a.confidence, a.payload)
        if (rows[r.id] == r) return false
        rows[r.id]?.let { conflicts += it }
        rows[r.id] = r; sources[r.id] = a
        hasResult = hasResult || a.payload is MatchEnded || a.payload is MatchOutcomeLabelWitnessed ||
            a.sourceId.id == "YOU_WIN_WITNESS" || a.sourceId.id == "GOOD_EFFORT_WITNESS"
        val payload = a.payload
        if (side == ActivePokemonSide.OPPONENT && payload is BattleCryLocalized && payload.durationNanos > 0 &&
            payload.correlation in com.example.overdex.battle.audio.CryWaveformAcceptance.MIN_CORRELATION..1f) {
            val windows = cryWindows.getOrPut(payload.speciesId) { mutableListOf() }
            val window = at until at + payload.durationNanos
            val eligibleDeparture = payload.correlation >= DepartureCryFaint.MIN_REPEAT_CORRELATION
            for (other in windows) {
                if (other.span.last < at && eligibleDeparture) candidateCries += at
                if (window.last < other.span.first && other.eligibleDeparture) candidateCries += other.span.first
            }
            windows += CryWindow(window, eligibleDeparture)
        }
        if (payload is BattleCryLocalized && payload.correlation in DepartureCryFaint.MIN_IMPACT_CORRELATION..1f) {
            cryTimes += at
            if (orangeTimes.floor(at)?.let { at - it <= 2_000_000_000L } == true) candidateCries += at
        }
        if (payload is CompleteHpBarObserved && payload.side == side && DepartureCryFaint.orange(payload)) {
            orangeTimes += at
            candidateCries += cryTimes.subSet(at, true, at + 2_000_000_000L, true)
        }
        val nearCry = candidateCries.isNotEmpty() && when (payload) {
            is PlayerTeamSlotConfigured, is PlayerTeamRosterSlotWitnessed, is PlayerTeamRosterConfirmation -> true
            is FastMoveUseObserved -> resolutions.any { it.basis == DepartureCryFaint.LAST && at >= it.atNanos && payload.attackingSide == side }
            is ActivePokemonSpeciesWitnessed, is ActivePokemonFaintedWitnessed, is PlayerPokeBallCountMeasured,
            MatchStarted, ScreenIgnoredByUser, ObservationRestartedByUser -> true
            is OpponentBattleResourceCountMeasured -> payload.resource == OpponentBattleResource.POKE_BALLS
            is CompleteHpBarObserved, is ActiveHpBarMeasured -> {
                val measuredSide = if (payload is CompleteHpBarObserved) payload.side else (payload as ActiveHpBarMeasured).side
                measuredSide == side && (candidateCries.any { at in (it - 3_000_000_000L)..(it + 3_000_000_000L) } ||
                    resolutions.any { it.basis == DepartureCryFaint.LAST && at > it.atNanos })
            }
            is BattleCryLocalized -> true
            is RawTestimony -> DepartureCryFaint.entryName(payload) != null
            else -> false
        }
        if (!hasResult && !nearCry) return false
        val input = rows.values.toList() + conflicts
        val compute = {
            onRebuild?.invoke("terminal_charge_${side.name}", input.size)
            (DepartureCryFaint.resolve(input) + TerminalChargedFaintResolver.resolve(input)).filter { it.side == side }
                .distinctBy { TeamLossResolver.speciesKey(it.owner.speciesName) }
        }
        val next = cache?.terminal(side, input, compute) ?: compute()
        if (next == resolutions) return false
        resolutions = next
        return true
    }
}

internal fun isRevisableVisualFaint(basis: String?) =
    basis == VerifiedHpFaintResolver.BASIS || basis == TerminalChargedFaintResolver.BASIS || DepartureCryFaint.isBasis(basis)
