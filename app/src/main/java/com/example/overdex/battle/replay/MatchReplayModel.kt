package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.ArchivedBattleCryCandidatesMeasured
import com.example.overdex.battle.archive.ArchivedActiveHpBarBorderPulseObserved
import com.example.overdex.battle.archive.ArchivedHpBarBorderPulse
import com.example.overdex.battle.archive.ArchivedActiveHpBarDamageTickMeasured
import com.example.overdex.battle.archive.ArchivedActiveHpBarBorderCadenceMeasured
import com.example.overdex.battle.archive.ArchivedActiveHpBarMotionCadenceMeasured
import com.example.overdex.battle.archive.ArchivedActivePokemonSpeciesWitnessed
import com.example.overdex.battle.archive.ArchivedActivePokemonFaintedWitnessed
import com.example.overdex.battle.observation.EntryAnnouncementSideTracker
import com.example.overdex.battle.archive.ArchivedMatchEnded
import com.example.overdex.battle.archive.ArchivedMatchStarted
import com.example.overdex.battle.archive.ArchivedFastMoveEnergyDerived
import com.example.overdex.battle.archive.ArchivedFastMoveEffectivenessWitnessed
import com.example.overdex.battle.archive.ArchivedFastMoveIdentified
import com.example.overdex.battle.archive.ArchivedFastMoveRecipientVisualArtifactMeasured
import com.example.overdex.battle.archive.ArchivedFastMoveRecipientVisualCadenceMeasured
import com.example.overdex.battle.archive.ArchivedFastMoveUseObserved
import com.example.overdex.battle.archive.ArchivedPlayerChargeMoveEnergyFillIncreased
import com.example.overdex.battle.archive.ArchivedPlayerChargeMoveEnergyFillCadenceMeasured
import com.example.overdex.battle.archive.ArchivedChargedMoveEnergySpent
import com.example.overdex.battle.archive.ArchivedChargeMoveQteVibrationPatternInferred
import com.example.overdex.battle.archive.ArchivedChargeMoveUsedAnnounced
import com.example.overdex.battle.archive.ArchivedCountdownGlyphWitnessed
import com.example.overdex.battle.archive.ArchivedDeviceMotionPulseMeasured
import com.example.overdex.battle.archive.ArchivedGetReadyWitnessed
import com.example.overdex.battle.archive.ArchivedPokemonIdentified
import com.example.overdex.battle.archive.ArchivedRealityArticle
import com.example.overdex.battle.archive.ArchivedRawText
import com.example.overdex.battle.archive.ArchivedVsScreenWitnessed
import com.example.overdex.battle.archive.ArchivedPlayerTeamRosterSlotWitnessed
import com.example.overdex.battle.archive.ArchivedPlayerTeamSlotConfigured
import com.example.overdex.battle.archive.MatchArchive
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import kotlin.math.abs
import kotlin.math.max

/** Pure projection of archive evidence into replay-safe scene state. */
class MatchReplayModel(
    private val archive: MatchArchive,
    speciesIdsByName: Map<String, Int> = emptyMap(),
    archivedCropIdentities: List<ReplayIdentityObservation> = emptyList(),
    private val fastMoveTypesByName: Map<String, PokemonType> = emptyMap(),
    fastMovesBySpeciesName: Map<String, List<Move>> = emptyMap()
) {
    val timedArticles: List<ArchivedRealityArticle> = archive.articles
        .filter { it.monotonicTimeNanos != null }
        .sortedBy { it.monotonicTimeNanos }

    val startNanos: Long = timedArticles.firstOrNull()?.monotonicTimeNanos ?: 0L
    val endNanos: Long = timedArticles.lastOrNull()?.monotonicTimeNanos ?: startNanos
    /** The witnessed GO boundary, independent from the earlier recording start. */
    val matchStartNanos: Long? = timedArticles
        .firstOrNull { it.payload is ArchivedMatchStarted }
        ?.monotonicTimeNanos
    private val configuredPlayerMembers = timedArticles
        .mapNotNull { it.payload as? ArchivedPlayerTeamSlotConfigured }
        .distinctBy { it.slot }
        .sortedBy { it.slot }
    private val configuredPlayerMovesBySpecies = configuredPlayerMembers.associate {
        normalizeSpeciesName(it.speciesName) to it.fastMoveName
    }

    // A replay receives a completed archive, not a live stream. Build the identity
    // tracks once so every frame can use facts learned anywhere in that archive.
    private val canonicalSpecies = speciesIdsByName.entries.associateBy { normalizeSpeciesName(it.key) }
    private val reconstructedTracks = reconstructAnnouncementTracks()
    private val cropTracks = archivedCropIdentities
        .sortedBy { it.atNanos }
        .partition { it.side == "PLAYER" }
    /** Menus remain part of the record, but the reanimated battlefield begins at battle evidence. */
    private val battlePresentationStartNanos = sequenceOf(
        timedArticles.firstNotNullOfOrNull { article ->
            when (val payload = article.payload) {
                ArchivedVsScreenWitnessed,
                is ArchivedCountdownGlyphWitnessed,
                is ArchivedActivePokemonSpeciesWitnessed -> article.monotonicTimeNanos
                is ArchivedRawText -> article.monotonicTimeNanos?.takeIf {
                    article.sourceId == "ANNOUNCEMENT_WITNESS" &&
                        ENTRY_PATTERN.matches(payload.value.trim())
                }
                else -> null
            }
        },
        reconstructedTracks.first.minOfOrNull { it.atNanos },
        reconstructedTracks.second.minOfOrNull { it.atNanos },
        cropTracks.first.minOfOrNull { it.atNanos },
        cropTracks.second.minOfOrNull { it.atNanos },
    ).filterNotNull().minOrNull() ?: startNanos
    private val configuredOpeningPlayer = configuredPlayerMembers.firstOrNull { it.slot == 1 }?.let {
        listOf(ReplayIdentityObservation("PLAYER", it.speciesName, it.speciesId, battlePresentationStartNanos, "CURRENT TEAM CONFIGURATION"))
    }.orEmpty()
    private val playerIdentityTrack = ReplayIdentityTrack.from(
        timedArticles,
        "PLAYER",
        configuredOpeningPlayer + reconstructedTracks.first + cropTracks.first,
        battlePresentationStartNanos,
        matchStartNanos,
    )
    private val opponentIdentityTrack = ReplayIdentityTrack.from(
        timedArticles,
        "OPPONENT",
        reconstructedTracks.second + cropTracks.second,
        battlePresentationStartNanos,
        matchStartNanos,
    )
    private val playerHpTrack = ReplayHpTrack(timedArticles, "PLAYER")
    private val opponentHpTrack = ReplayHpTrack(timedArticles, "OPPONENT")
    private val identifiedFastMoves = timedArticles
        .mapNotNull { it.payload as? ArchivedFastMoveIdentified }
        .filterNot { it.basis.startsWith(CHARGE_FILL_BASIS) }
    private val possibleFastMoves = fastMovesBySpeciesName.entries.associate {
        normalizeSpeciesName(it.key) to it.value
    }
    private val bufferedMoveCache = mutableMapOf<String, Move?>()
    private val timedArticlesById = timedArticles.associateBy(ArchivedRealityArticle::articleId)
    private val replayQteMilestonesByPatternId = reconstructQteMilestones()
    private val replayQteMilestones = replayQteMilestonesByPatternId.values
        .flatten()
        .distinctBy { "${it.side}:${it.atNanos}:${it.label}" }
        .sortedBy { it.atNanos }
    private val recordedFastMoveUses = reconstructFastMoveUses()
    private val recordedChargedMoveUses = reconstructChargedMoveUses()
    private val replayHapticEvents = buildList {
        addAll(replayQteMilestones.map { ReplayHapticEvent(it.atNanos, pulseCount = 1) })
        timedArticles.forEach { article ->
            val vibration = article.payload as? ArchivedChargeMoveQteVibrationPatternInferred
                ?: return@forEach
            if (replayQteMilestonesByPatternId.containsKey(article.articleId)) return@forEach
            // Older archives may contain only the accepted aggregate pattern,
            // without predecessor links to its physical pulses.
            val hapticAt = article.monotonicTimeNanos ?: return@forEach
            val corroborated = timedArticles.any { support ->
                val supportAt = support.monotonicTimeNanos ?: return@any false
                abs(supportAt - hapticAt) <= CHARGE_MOVE_SEQUENCE_MERGE_NANOS &&
                    (support.payload is ArchivedGetReadyWitnessed ||
                        support.payload is ArchivedChargeMoveUsedAnnounced ||
                        support.payload is ArchivedChargedMoveEnergySpent)
            }
            if (corroborated) {
                add(ReplayHapticEvent(hapticAt, pulseCount = vibration.pulseCount.coerceAtLeast(1)))
            }
        }
    }.distinctBy { "${it.atNanos}:${it.pulseCount}" }.sortedBy { it.atNanos }

    fun crossedArticleBoundary(fromNanos: Long, toNanos: Long): Boolean {
        if (fromNanos == toNanos) return false
        val lower = minOf(fromNanos, toNanos)
        val upper = maxOf(fromNanos, toNanos)
        return timedArticles.any { it.monotonicTimeNanos!! > lower && it.monotonicTimeNanos!! <= upper }
    }

    /** Haptic testimony crossed during forward playback. Scrubbing stays silent. */
    fun hapticEventsBetween(fromExclusiveNanos: Long, toInclusiveNanos: Long): List<ReplayHapticEvent> {
        if (toInclusiveNanos <= fromExclusiveNanos) return emptyList()
        return replayHapticEvents.filter { event ->
            event.atNanos > fromExclusiveNanos && event.atNanos <= toInclusiveNanos
        }
    }

    fun sceneAt(monotonicTimeNanos: Long): ReplayScene {
        val articles = timedArticles.takeWhile { it.monotonicTimeNanos!! <= monotonicTimeNanos }
        val speciesNames = articles.mapNotNull { (it.payload as? ArchivedPokemonIdentified)?.species }.distinct()
        val candidateArticle = articles.lastOrNull { it.payload is ArchivedBattleCryCandidatesMeasured }
        val candidate = candidateArticle?.payload as? ArchivedBattleCryCandidatesMeasured
        val latest = articles.lastOrNull()
        val countdownGlyph = articles.asReversed().firstNotNullOfOrNull { article ->
            val glyph = article.payload as? ArchivedCountdownGlyphWitnessed ?: return@firstNotNullOfOrNull null
            val observedAt = article.monotonicTimeNanos ?: return@firstNotNullOfOrNull null
            glyph.glyph.takeIf { monotonicTimeNanos - observedAt < COUNTDOWN_GLYPH_VISUAL_NANOS }
        }
        val qteMilestone = replayQteMilestones.lastOrNull { milestone ->
            monotonicTimeNanos - milestone.atNanos in 0 until QTE_MILESTONE_VISUAL_NANOS
        }
        // The prebuilt tracks choose the correct known identity interval.
        val player = applyFaintState("PLAYER", playerIdentityTrack.combatantAt(monotonicTimeNanos), articles)
        val opponent = applyFaintState("OPPONENT", opponentIdentityTrack.combatantAt(monotonicTimeNanos), articles)
        // A ranked cry candidate stays measurable evidence only. It cannot
        // create a replay combatant or sprite without an identity article.

        val playerGeneratedEnergy = articles.sumOf { article ->
            (article.payload as? ArchivedFastMoveEnergyDerived)
                ?.takeIf { it.side == "PLAYER" && !it.basis.contains(CHARGE_FILL_BASIS) }
                ?.totalEnergyGenerated ?: 0
        }
        val opponentGeneratedEnergy = articles.sumOf { article ->
            (article.payload as? ArchivedFastMoveEnergyDerived)
                ?.takeIf { it.side == "OPPONENT" && !it.basis.contains(CHARGE_FILL_BASIS) }
                ?.totalEnergyGenerated ?: 0
        }
        val playerSpentEnergy = articles.sumOf { article ->
            (article.payload as? ArchivedChargedMoveEnergySpent)
                ?.takeIf { it.side == "PLAYER" }
                ?.energyCost ?: 0
        }
        val opponentSpentEnergy = articles.sumOf { article ->
            (article.payload as? ArchivedChargedMoveEnergySpent)
                ?.takeIf { it.side == "OPPONENT" }
                ?.energyCost ?: 0
        }
        // Replay is buffered. Raw, individually timed hit witnesses establish that
        // an attack happened even before cadence identifies its move. A later move
        // identity supplies the name and type without moving the original event.
        val fastMoveActions = recordedFastMoveUses.mapNotNull { use ->
            val ageNanos = monotonicTimeNanos - use.observedAtNanos
            if (ageNanos !in 0 until FAST_MOVE_VISUAL_NANOS) return@mapNotNull null
            val bufferedMove = bufferedMoveFor(use.side, use.observedAtNanos)
            val moveName = use.moveName
                ?: identifiedMoveFor(use.side, use.observedAtNanos)
                ?: bufferedMove?.name
            val type = moveName?.let { fastMoveTypesByName[normalizeSpeciesName(it)] }
                ?: moveName?.let { possibleMoveFor(use.side, use.observedAtNanos, it)?.type }
                ?: bufferedMove?.type
                ?: unambiguousFastMoveType(use.side, use.observedAtNanos)
            ReplayFastMoveAction(
                side = use.side,
                moveName = moveName,
                type = type,
                startedAtNanos = use.observedAtNanos,
                progress = ageNanos.toFloat() / FAST_MOVE_VISUAL_NANOS,
                evidenceKinds = use.evidenceKinds
            )
        }
        val chargeMoveActions = recordedChargedMoveUses.mapNotNull { use ->
            val ageNanos = monotonicTimeNanos - use.animationAtNanos
            if (ageNanos !in 0 until CHARGE_MOVE_VISUAL_NANOS) return@mapNotNull null
            ReplayChargedMoveAction(
                side = use.side,
                moveName = use.moveName,
                startedAtNanos = use.animationAtNanos,
                progress = ageNanos.toFloat() / CHARGE_MOVE_VISUAL_NANOS,
                evidenceKinds = use.evidenceKinds
            )
        }

        return ReplayScene(
            player = player,
            opponent = opponent,
            observedSpeciesNames = speciesNames,
            candidateSpeciesId = candidate?.candidates?.maxByOrNull { it.similarity }?.speciesId,
            candidateSimilarity = candidate?.candidates?.maxByOrNull { it.similarity }?.similarity,
            outcome = (articles.lastOrNull { it.payload is ArchivedMatchEnded }?.payload as? ArchivedMatchEnded)?.result,
            playerGeneratedEnergy = playerGeneratedEnergy,
            opponentGeneratedEnergy = opponentGeneratedEnergy,
            playerSpentEnergy = playerSpentEnergy,
            opponentSpentEnergy = opponentSpentEnergy,
            fastMoveActions = fastMoveActions,
            chargedMoveActions = chargeMoveActions,
            countdownGlyph = countdownGlyph,
            qteMilestone = qteMilestone?.label,
            qteMilestoneSide = qteMilestone?.side,
            latestEvidenceLabel = latest?.payload?.let(::payloadLabel) ?: "Awaiting first timed evidence",
            playerHp = player?.let {
                playerHpTrack.stateAt(monotonicTimeNanos, playerIdentityTrack.appearanceStartAt(monotonicTimeNanos), it.isFainted)
            },
            opponentHp = opponent?.let {
                opponentHpTrack.stateAt(monotonicTimeNanos, opponentIdentityTrack.appearanceStartAt(monotonicTimeNanos), it.isFainted)
            },
        )
    }

    /**
     * Builds exact Nice/Great/Excellent instants from accepted patterns. Older
     * archives can be repaired in memory from their retained short pulses and
     * charge-move cues; the archive itself and its raw evidence stay unchanged.
     */
    private fun reconstructQteMilestones(): Map<String, List<ReplayQteMilestone>> {
        val sequences = linkedMapOf<String, List<ReplayQteMilestone>>()
        val usedPulseIds = mutableSetOf<String>()

        timedArticles.forEach { article ->
            val vibration = article.payload as? ArchivedChargeMoveQteVibrationPatternInferred
                ?: return@forEach
            val expectedCount = vibration.pulseCount.coerceIn(1, QTE_MILESTONE_LABELS.size)
            val linkedPulses = article.predecessorIds
                .mapNotNull(timedArticlesById::get)
                .filter { it.payload is ArchivedDeviceMotionPulseMeasured }
                .sortedBy { it.monotonicTimeNanos }
                .take(expectedCount)
            if (linkedPulses.size < expectedCount) return@forEach
            sequences[article.articleId] = linkedPulses.toMilestones(vibration.side)
            usedPulseIds += linkedPulses.map { it.articleId }
        }

        val getReadyCues = timedArticles.filter(::isReplayGetReadyCue)
        val chargeMoveCues = timedArticles.filter { it.payload is ArchivedChargeMoveUsedAnnounced }
        chargeMoveCues.forEach { closingCue ->
            val closingAt = closingCue.monotonicTimeNanos ?: return@forEach
            val alreadyCovered = sequences.values.flatten().any { milestone ->
                milestone.atNanos in (closingAt - REPLAY_QTE_CLOSE_ASSOCIATION_NANOS)..closingAt
            }
            if (alreadyCovered) return@forEach
            val getReady = getReadyCues
                .filter { cue -> cue.monotonicTimeNanos?.let { it in (closingAt - REPLAY_QTE_WINDOW_NANOS)..closingAt } == true }
                .maxByOrNull { it.monotonicTimeNanos ?: Long.MIN_VALUE }
            val start = getReady?.monotonicTimeNanos ?: (closingAt - REPLAY_QTE_WINDOW_NANOS)
            val selected = latestPlausibleQteRun(
                shortReplayMotionPulses(usedPulseIds).filter { pulse ->
                    pulse.monotonicTimeNanos?.let { it in start..closingAt } == true
                }
            ).takeLast(QTE_MILESTONE_LABELS.size)
            val minimum = if (getReady != null) 1 else QTE_MILESTONE_LABELS.size
            if (selected.size < minimum) return@forEach
            sequences["reconstructed:${closingCue.articleId}"] = selected.toMilestones("PLAYER")
            usedPulseIds += selected.map { it.articleId }
        }

        // A Get Ready cue and all three physical milestones are sufficient even
        // if the closing announcement witness did not survive in an old archive.
        getReadyCues.forEach { cue ->
            val cueAt = cue.monotonicTimeNanos ?: return@forEach
            val alreadyCovered = sequences.values.flatten().any { milestone ->
                milestone.atNanos in cueAt..(cueAt + REPLAY_QTE_WINDOW_NANOS)
            }
            if (alreadyCovered) return@forEach
            val selected = latestPlausibleQteRun(
                shortReplayMotionPulses(usedPulseIds).filter { pulse ->
                    pulse.monotonicTimeNanos?.let { it in cueAt..(cueAt + REPLAY_QTE_WINDOW_NANOS) } == true
                }
            ).takeLast(QTE_MILESTONE_LABELS.size)
            if (selected.size < QTE_MILESTONE_LABELS.size) return@forEach
            sequences["reconstructed:${cue.articleId}"] = selected.toMilestones("PLAYER")
            usedPulseIds += selected.map { it.articleId }
        }
        return sequences
    }

    private fun shortReplayMotionPulses(usedPulseIds: Set<String>): List<ArchivedRealityArticle> =
        timedArticles.filter { article ->
            if (article.articleId in usedPulseIds) return@filter false
            val pulse = article.payload as? ArchivedDeviceMotionPulseMeasured ?: return@filter false
            pulse.durationNanos in REPLAY_QTE_MIN_PULSE_NANOS..REPLAY_QTE_MAX_PULSE_NANOS
        }

    private fun latestPlausibleQteRun(source: List<ArchivedRealityArticle>): List<ArchivedRealityArticle> {
        var run = mutableListOf<ArchivedRealityArticle>()
        source.sortedBy { it.monotonicTimeNanos }.forEach { pulse ->
            val at = pulse.monotonicTimeNanos ?: return@forEach
            val previousAt = run.lastOrNull()?.monotonicTimeNanos
            when {
                previousAt == null -> run += pulse
                at - previousAt < REPLAY_QTE_MIN_INTER_PULSE_NANOS -> run[run.lastIndex] = pulse
                at - previousAt <= REPLAY_QTE_MAX_INTER_PULSE_NANOS -> run += pulse
                else -> run = mutableListOf(pulse)
            }
            while (run.size > QTE_MILESTONE_LABELS.size) run.removeAt(0)
        }
        return run
    }

    private fun List<ArchivedRealityArticle>.toMilestones(side: String): List<ReplayQteMilestone> =
        take(QTE_MILESTONE_LABELS.size).mapIndexed { index, pulse ->
            ReplayQteMilestone(
                atNanos = requireNotNull(pulse.monotonicTimeNanos),
                label = QTE_MILESTONE_LABELS[index],
                side = side,
            )
        }

    private fun isReplayGetReadyCue(article: ArchivedRealityArticle): Boolean {
        if (article.payload is ArchivedGetReadyWitnessed) return true
        if (article.sourceId != "ANNOUNCEMENT_WITNESS") return false
        val raw = (article.payload as? ArchivedRawText)?.value ?: return false
        return raw.uppercase().filter(Char::isLetterOrDigit).contains("GETREA")
    }


    private fun payloadLabel(payload: Any): String = when (payload) {
        is ArchivedPokemonIdentified -> "SPECIES: ${payload.species}"
        is ArchivedBattleCryCandidatesMeasured -> "BATTLE CRY CANDIDATE"
        is ArchivedActivePokemonFaintedWitnessed -> "FAINTED: ${payload.speciesName ?: payload.side}"
        is ArchivedMatchEnded -> "MATCH ENDED: ${payload.result}"
        is ArchivedFastMoveIdentified -> "FAST MOVE: ${payload.moveName}"
        is ArchivedFastMoveEnergyDerived -> "FAST ENERGY: ${payload.moveName}"
        is ArchivedPlayerTeamSlotConfigured -> "CURRENT TEAM [${payload.slot}]: ${payload.speciesName} / ${payload.fastMoveName}"
        is ArchivedActiveHpBarMotionCadenceMeasured -> "FAST MOVE MOTION: ${payload.movingSide}"
        is ArchivedPlayerChargeMoveEnergyFillIncreased -> "PLAYER ENERGY FILL INCREASED"
        is ArchivedPlayerChargeMoveEnergyFillCadenceMeasured -> "PLAYER ENERGY FILL CADENCE"
        is ArchivedActiveHpBarBorderPulseObserved -> "FAST MOVE HIT: ${oppositeSide(payload.damagedBarSide)}"
        is ArchivedHpBarBorderPulse -> "HP BORDER PULSE: ${payload.barSide} ${payload.status}"
        is ArchivedActiveHpBarDamageTickMeasured -> "HP LOSS: ${payload.damagedSide} -${(payload.lostFraction * 100).toInt()}%"
        is ArchivedFastMoveRecipientVisualArtifactMeasured -> "FAST MOVE VISUAL: ${oppositeSide(payload.damagedSide)}"
        is ArchivedFastMoveUseObserved -> "FAST MOVE USE: ${payload.attackingSide}"
        is ArchivedFastMoveEffectivenessWitnessed -> "${payload.effectiveness.replace('_', ' ')}${payload.damagedSide?.let { " ON $it" }.orEmpty()}"
        is ArchivedChargedMoveEnergySpent -> "CHARGED ENERGY: ${payload.moveName} -${payload.energyCost}"
        is ArchivedDeviceMotionPulseMeasured -> "DEVICE MOTION PULSE"
        is ArchivedChargeMoveQteVibrationPatternInferred -> "CHARGE MOVE QTE: ${payload.pulseCount} PULSES"
        else -> payload::class.simpleName.orEmpty().replace("Archived", "")
    }

    private fun applyFaintState(
        side: String,
        combatant: ReplayCombatant?,
        articles: List<ArchivedRealityArticle>
    ): ReplayCombatant? {
        combatant ?: return null
        val faintArticle = articles.lastOrNull { article ->
            val faint = article.payload as? ArchivedActivePokemonFaintedWitnessed ?: return@lastOrNull false
            faint.side == side && faint.speciesName?.let {
                normalizeSpeciesName(it) == normalizeSpeciesName(combatant.speciesName)
            } == true
        } ?: return combatant
        // A persistent team badge can be read again after the faint. That
        // repeated identity does not revive the combatant. Once a replacement
        // species is witnessed, sceneAt() selects that different combatant and
        // this species-specific faint no longer applies.
        return combatant.copy(isFainted = true)
    }

    /**
     * A completed archive can combine the early Get Ready cue, later haptic
     * pattern, and named charge-move announcement. The strongest later evidence
     * selects the animation instant while every supporting kind stays visible.
     */
    private fun reconstructChargedMoveUses(): List<ReplayChargedMoveUse> {
        val evidence = timedArticles.mapNotNull { article ->
            val atNanos = article.monotonicTimeNanos ?: return@mapNotNull null
            when (val payload = article.payload) {
                ArchivedGetReadyWitnessed -> ReplayChargedMoveUse(
                    side = "PLAYER",
                    animationAtNanos = atNanos,
                    lastEvidenceAtNanos = atNanos,
                    moveName = null,
                    anchorPriority = 1,
                    evidenceKinds = setOf("GET_READY")
                )
                is ArchivedChargeMoveQteVibrationPatternInferred -> ReplayChargedMoveUse(
                    side = payload.side,
                    animationAtNanos = atNanos,
                    lastEvidenceAtNanos = atNanos,
                    moveName = null,
                    anchorPriority = 2,
                    evidenceKinds = setOf("QTE_VIBRATION_PATTERN")
                )
                is ArchivedChargedMoveEnergySpent -> ReplayChargedMoveUse(
                    side = payload.side,
                    animationAtNanos = atNanos,
                    lastEvidenceAtNanos = atNanos,
                    moveName = payload.moveName,
                    anchorPriority = 3,
                    evidenceKinds = setOf("NAMED_CHARGED_MOVE")
                )
                else -> null
            }
        }.filter { it.side.isBattleSide() }.sortedBy { it.animationAtNanos }

        return evidence.groupBy { it.side }.values.flatMap { sideEvidence ->
            sideEvidence.fold(mutableListOf<ReplayChargedMoveUse>()) { merged, current ->
                val previous = merged.lastOrNull()
                if (
                    previous != null &&
                    current.animationAtNanos - previous.lastEvidenceAtNanos <= CHARGE_MOVE_SEQUENCE_MERGE_NANOS
                ) {
                    val replaceAnchor = current.anchorPriority > previous.anchorPriority
                    merged[merged.lastIndex] = previous.copy(
                        animationAtNanos = if (replaceAnchor) current.animationAtNanos else previous.animationAtNanos,
                        lastEvidenceAtNanos = maxOf(previous.lastEvidenceAtNanos, current.lastEvidenceAtNanos),
                        moveName = current.moveName ?: previous.moveName,
                        anchorPriority = maxOf(previous.anchorPriority, current.anchorPriority),
                        evidenceKinds = previous.evidenceKinds + current.evidenceKinds
                    )
                } else {
                    merged += current
                }
                merged
            }
        }.sortedBy { it.animationAtNanos }
    }

    /**
     * Produces one replay event per observed use. Independent witnesses from the
     * same captured moment are coalesced, while consecutive 0.5-second moves
     * remain separate events.
     */
    private fun reconstructFastMoveUses(): List<ReplayFastMoveUse> {
        val fusedUses = timedArticles.mapNotNull { article ->
            val payload = article.payload as? ArchivedFastMoveUseObserved ?: return@mapNotNull null
            ReplayFastMoveUse(
                side = payload.attackingSide,
                observedAtNanos = article.monotonicTimeNanos ?: return@mapNotNull null,
                moveName = null,
                evidenceKinds = payload.evidenceKinds.toSet(),
                canonical = true
            )
        }.filter { it.side.isBattleSide() }.sortedBy { it.observedAtNanos }

        val evidence = timedArticles.mapNotNull { article ->
            val atNanos = article.monotonicTimeNanos ?: return@mapNotNull null
            when (val payload = article.payload) {
                is ArchivedHpBarBorderPulse -> payload
                    .takeIf { it.status == "PRESENT" }
                    ?.let {
                        ReplayFastMoveUse(
                            side = oppositeSide(it.barSide),
                            observedAtNanos = atNanos,
                            moveName = null,
                            evidenceKinds = setOf("HP_BORDER_PULSE")
                        )
                    }
                is ArchivedActiveHpBarBorderPulseObserved -> ReplayFastMoveUse(
                    side = oppositeSide(payload.damagedBarSide),
                    observedAtNanos = atNanos,
                    moveName = null,
                    evidenceKinds = setOf("HP_BORDER_PULSE")
                )
                is ArchivedActiveHpBarDamageTickMeasured -> ReplayFastMoveUse(
                    side = oppositeSide(payload.damagedSide),
                    observedAtNanos = atNanos,
                    moveName = null,
                    evidenceKinds = setOf("HP_DAMAGE_TICK")
                )
                is ArchivedFastMoveRecipientVisualArtifactMeasured -> ReplayFastMoveUse(
                    side = oppositeSide(payload.damagedSide),
                    observedAtNanos = atNanos,
                    moveName = null,
                    evidenceKinds = setOf("RECIPIENT_VISUAL_ARTIFACT")
                )
                is ArchivedActiveHpBarMotionCadenceMeasured -> ReplayFastMoveUse(
                    side = payload.movingSide,
                    observedAtNanos = atNanos,
                    moveName = null,
                    evidenceKinds = setOf("HP_BAR_MOTION_CADENCE")
                )
                is ArchivedActiveHpBarBorderCadenceMeasured -> ReplayFastMoveUse(
                    side = oppositeSide(payload.damagedBarSide),
                    observedAtNanos = atNanos,
                    moveName = null,
                    evidenceKinds = setOf("HP_BORDER_CADENCE")
                )
                is ArchivedFastMoveRecipientVisualCadenceMeasured -> ReplayFastMoveUse(
                    side = oppositeSide(payload.damagedSide),
                    observedAtNanos = atNanos,
                    moveName = null,
                    evidenceKinds = setOf("RECIPIENT_VISUAL_CADENCE")
                )
                is ArchivedPlayerChargeMoveEnergyFillIncreased -> ReplayFastMoveUse(
                    side = "PLAYER",
                    observedAtNanos = atNanos,
                    moveName = null,
                    evidenceKinds = setOf("PLAYER_CHARGE_FILL_INCREASE")
                )
                is ArchivedFastMoveEnergyDerived -> payload
                    .takeIf {
                        it.observedCompletedUses == 1 &&
                            it.side.isBattleSide() &&
                            !it.basis.contains(CHARGE_FILL_BASIS)
                    }
                    ?.let {
                        ReplayFastMoveUse(
                            side = it.side,
                            observedAtNanos = atNanos,
                            moveName = it.moveName,
                            evidenceKinds = setOf("ENERGY_DERIVATION")
                        )
                    }
                else -> null
            }
        }.filter { it.side.isBattleSide() }.sortedBy { it.observedAtNanos }

        // A canonical article suppresses only the raw witnesses that belong to
        // that same use. It must not erase an uncovered use elsewhere in the
        // match merely because at least one fused article survived archiving.
        return (fusedUses + evidence)
            .groupBy { it.side }
            .values
            .flatMap { sideEvidence ->
            val ordered = sideEvidence.sortedWith(
                compareBy<ReplayFastMoveUse> { it.observedAtNanos }
                    .thenByDescending { it.canonical }
            )
            ordered.fold(mutableListOf<ReplayFastMoveUse>()) { merged, current ->
                val previous = merged.lastOrNull()
                if (
                    previous != null &&
                    current.observedAtNanos - previous.observedAtNanos <= FAST_MOVE_WITNESS_MERGE_NANOS
                ) {
                    merged[merged.lastIndex] = previous.copy(
                        moveName = previous.moveName ?: current.moveName,
                        evidenceKinds = previous.evidenceKinds + current.evidenceKinds,
                        canonical = previous.canonical || current.canonical
                    )
                } else {
                    merged += current
                }
                merged
            }
        }.sortedBy { it.observedAtNanos }
    }

    private fun identifiedMoveFor(side: String, atNanos: Long): String? {
        val speciesName = when (side) {
            "PLAYER" -> playerIdentityTrack.combatantAt(atNanos)?.speciesName
            "OPPONENT" -> opponentIdentityTrack.combatantAt(atNanos)?.speciesName
            else -> null
        }
        if (side == "PLAYER" && speciesName != null) {
            configuredPlayerMovesBySpecies[normalizeSpeciesName(speciesName)]?.let { return it }
        }
        return identifiedFastMoves
            .asSequence()
            .filter { it.side == side }
            .filter { identified ->
                speciesName == null || normalizeSpeciesName(identified.speciesName) == normalizeSpeciesName(speciesName)
            }
            .lastOrNull()
            ?.moveName
    }

    /**
     * A move name remains undisclosed until cadence identifies it. Its type is
     * still known when every legal Fast Move for the active species shares the
     * same type, so replay may render that type without inventing a move name.
     */
    private fun unambiguousFastMoveType(side: String, atNanos: Long): PokemonType? {
        val speciesName = when (side) {
            "PLAYER" -> playerIdentityTrack.combatantAt(atNanos)?.speciesName
            "OPPONENT" -> opponentIdentityTrack.combatantAt(atNanos)?.speciesName
            else -> null
        } ?: return null
        return possibleFastMoves[normalizeSpeciesName(speciesName)]
            .orEmpty()
            .map { it.type }
            .distinct()
            .singleOrNull()
    }

    /**
     * Replay owns the complete record, so it may apply a move conclusion learned
     * later to earlier animation without changing the archived testimony. This
     * uses only cadence from the same active-species interval and still requires
     * three direct duration matches.
     */
    private fun bufferedMoveFor(side: String, atNanos: Long): Move? {
        val combatant = combatantFor(side, atNanos) ?: return null
        val cacheKey = "$side:${normalizeSpeciesName(combatant.speciesName)}:${combatant.identityEstablishedAtNanos}"
        return bufferedMoveCache.getOrPut(cacheKey) {
            val moves = possibleFastMoves[normalizeSpeciesName(combatant.speciesName)].orEmpty()
            if (moves.isEmpty()) return@getOrPut null
            REPLAY_CADENCE_BASIS_PRIORITY.firstNotNullOfOrNull { basis ->
                val intervals = timedArticles.mapNotNull { article ->
                    val observedAt = article.monotonicTimeNanos ?: return@mapNotNull null
                    val cadence = replayCadence(article.payload) ?: return@mapNotNull null
                    if (cadence.side != side || cadence.basis != basis) return@mapNotNull null
                    val active = combatantFor(side, observedAt) ?: return@mapNotNull null
                    if (normalizeSpeciesName(active.speciesName) != normalizeSpeciesName(combatant.speciesName)) {
                        return@mapNotNull null
                    }
                    if (observedAt - cadence.intervalNanos < active.identityEstablishedAtNanos) {
                        return@mapNotNull null
                    }
                    cadence.intervalNanos
                }
                resolveBufferedMove(moves, intervals)
            }
        }
    }

    private fun possibleMoveFor(side: String, atNanos: Long, moveName: String): Move? {
        val speciesName = combatantFor(side, atNanos)?.speciesName ?: return null
        return possibleFastMoves[normalizeSpeciesName(speciesName)]
            .orEmpty()
            .firstOrNull { normalizeSpeciesName(it.name) == normalizeSpeciesName(moveName) }
    }

    private fun combatantFor(side: String, atNanos: Long): ReplayCombatant? = when (side) {
        "PLAYER" -> playerIdentityTrack.combatantAt(atNanos)
        "OPPONENT" -> opponentIdentityTrack.combatantAt(atNanos)
        else -> null
    }

    private fun replayCadence(payload: Any): ReplayCadence? = when (payload) {
        is ArchivedActiveHpBarBorderCadenceMeasured -> ReplayCadence(
            oppositeSide(payload.damagedBarSide), payload.intervalNanos, "HP_BORDER"
        )
        is ArchivedFastMoveRecipientVisualCadenceMeasured -> ReplayCadence(
            oppositeSide(payload.damagedSide), payload.intervalNanos, "RECIPIENT_VISUAL"
        )
        is ArchivedActiveHpBarMotionCadenceMeasured -> ReplayCadence(
            payload.movingSide, payload.intervalNanos, "HP_MOTION"
        )
        else -> null
    }

    private fun resolveBufferedMove(moves: List<Move>, intervals: List<Long>): Move? {
        if (intervals.size < REPLAY_REQUIRED_CADENCE_INTERVALS) return null
        val durationGroups = moves
            .filter { it.turns != null }
            .groupBy { it.turns!! * REPLAY_TURN_NANOS }
            .mapNotNull { (expected, sameDurationMoves) ->
                val tolerance = max(
                    REPLAY_MIN_CADENCE_TOLERANCE_NANOS,
                    (expected * REPLAY_RELATIVE_CADENCE_TOLERANCE).toLong()
                )
                val support = intervals.count { abs(it - expected) <= tolerance }
                support.takeIf { it >= REPLAY_REQUIRED_CADENCE_INTERVALS }
                    ?.let { ReplayMoveCandidate(sameDurationMoves, support) }
            }
        val strongestCount = durationGroups.maxOfOrNull { it.support } ?: return null
        val strongest = durationGroups.filter { it.support == strongestCount }.singleOrNull() ?: return null
        strongest.moves.singleOrNull()?.let { return it }
        val highestEnergy = strongest.moves.maxOfOrNull { it.energy } ?: return null
        return strongest.moves.filter { it.energy == highestEnergy }.singleOrNull()
    }

    /**
     * Older or interrupted recordings can contain exact announcement text even
     * when the typed species witness failed. A completed replay may use that
     * preserved text without changing the archive or pretending it was live OCR.
     */
    private fun reconstructAnnouncementTracks(): Pair<List<ReplayIdentityObservation>, List<ReplayIdentityObservation>> {
        if (canonicalSpecies.isEmpty()) return emptyList<ReplayIdentityObservation>() to emptyList()
        val roster = timedArticles.mapNotNull {
            when (val payload = it.payload) {
                is ArchivedPlayerTeamRosterSlotWitnessed -> payload.speciesName
                is ArchivedPlayerTeamSlotConfigured -> payload.speciesName
                else -> null
            }
        }.map(::normalizeSpeciesName).toSet()
        val player = mutableListOf<ReplayIdentityObservation>()
        val opponent = mutableListOf<ReplayIdentityObservation>()
        val entrySides = EntryAnnouncementSideTracker()
        timedArticles.forEach { article ->
            if (article.sourceId != "ANNOUNCEMENT_WITNESS") return@forEach
            val text = (article.payload as? ArchivedRawText)?.value?.trim() ?: return@forEach
            val announced = ENTRY_PATTERN.matchEntire(text)?.groupValues?.get(1)?.trim()
            val moveUser = MOVE_PATTERN.matchEntire(text)?.groupValues?.get(1)?.trim()
            val candidate = announced ?: moveUser ?: return@forEach
            val species = canonicalSpecies[normalizeSpeciesName(candidate)] ?: return@forEach
            val atNanos = article.monotonicTimeNanos ?: return@forEach
            val side = if (announced != null) {
                entrySides.attribute(
                    species.key,
                    roster
                )?.side?.name ?: return@forEach
            } else {
                entrySides.knownSide(species.key, roster)?.name ?: when {
                    normalizeSpeciesName(species.key) in roster -> "PLAYER"
                    roster.size >= 3 -> "OPPONENT"
                    opponent.lastOrNull { it.atNanos <= atNanos }?.speciesName
                        ?.let(::normalizeSpeciesName) != normalizeSpeciesName(species.key) -> "PLAYER"
                    else -> "OPPONENT"
                }
            }
            val observation = ReplayIdentityObservation(
                side = side,
                speciesName = species.key,
                speciesId = species.value,
                atNanos = atNanos,
                basis = "RECONSTRUCTED FROM ARCHIVED ANNOUNCEMENT"
            )
            val destination = if (side == "PLAYER") player else opponent
            if (destination.lastOrNull()?.speciesName != observation.speciesName) destination += observation
        }
        return player to opponent
    }

    companion object {
        private val ENTRY_PATTERN = Regex("^Go,\\s*(.+?)!*$", RegexOption.IGNORE_CASE)
        private val MOVE_PATTERN = Regex("^(.+?)\\s+used\\s+.+!*$", RegexOption.IGNORE_CASE)

        fun referencedSpeciesNames(archive: MatchArchive): Set<String> = buildSet {
            archive.articles.forEach { article ->
                when (val payload = article.payload) {
                    is ArchivedActivePokemonSpeciesWitnessed -> add(payload.speciesName)
                    is ArchivedActivePokemonFaintedWitnessed -> payload.speciesName?.let(::add)
                    is ArchivedPokemonIdentified -> add(payload.species)
                    is ArchivedPlayerTeamRosterSlotWitnessed -> add(payload.speciesName)
                    is ArchivedPlayerTeamSlotConfigured -> add(payload.speciesName)
                    is ArchivedRawText -> if (article.sourceId == "ANNOUNCEMENT_WITNESS") {
                        ENTRY_PATTERN.matchEntire(payload.value.trim())?.groupValues?.get(1)?.trim()?.let(::add)
                        MOVE_PATTERN.matchEntire(payload.value.trim())?.groupValues?.get(1)?.trim()?.let(::add)
                    }
                    else -> Unit
                }
            }
        }

        private fun normalizeSpeciesName(value: String): String =
            value.uppercase().filter(Char::isLetterOrDigit)
    }
}

private class ReplayIdentityTrack private constructor(
    private val observations: List<ReplayIdentityObservation>,
    private val visibleFromNanos: Long,
) {
    fun appearanceStartAt(cursorNanos: Long): Long {
        var index = observations.indexOfLast { it.atNanos <= cursorNanos }.coerceAtLeast(0)
        while (index > 0 && normalize(observations[index - 1].speciesName) == normalize(observations[index].speciesName)) {
            index--
        }
        return if (index == 0) visibleFromNanos else observations[index].atNanos
    }

    fun combatantAt(cursorNanos: Long): ReplayCombatant? {
        if (cursorNanos < visibleFromNanos) return null
        // The first known combatant is available for the opening interval, but
        // its later establishment stays visible in the replay UI. Each later
        // identity article begins the next known interval.
        val observation = observations.lastOrNull { it.atNanos <= cursorNanos }
            ?: observations.firstOrNull()
            ?: return null
        return ReplayCombatant(
            observation.speciesName,
            observation.speciesId,
            observation.atNanos,
            observation.basis
        )
    }

    companion object {
        fun from(
            articles: List<ArchivedRealityArticle>,
            side: String,
            reconstructed: List<ReplayIdentityObservation>,
            visibleFromNanos: Long,
            matchStartNanos: Long? = null,
        ): ReplayIdentityTrack {
            val observed = articles.mapNotNull { article ->
                val species = article.payload as? ArchivedActivePokemonSpeciesWitnessed ?: return@mapNotNull null
                if (species.side != side) return@mapNotNull null
                ReplayIdentityObservation(
                    side,
                    species.speciesName,
                    species.speciesId,
                    article.monotonicTimeNanos ?: return@mapNotNull null,
                    "OBSERVED SPECIES",
                    sourceId = article.sourceId,
                    confidence = article.confidence,
                    articleId = article.articleId,
                )
            }
            val withoutBriefCryContradictions = observed.filterNot { candidate ->
                candidate.sourceId == DECISIVE_CRY_SOURCE &&
                    isBracketedCryContradiction(candidate, reconstructed + observed)
            }
            val timeAdjustedObserved = backdateImmediateOpeningPlayerSwitch(
                side = side,
                articles = articles,
                reconstructed = reconstructed,
                observed = withoutBriefCryContradictions,
                matchStartNanos = matchStartNanos,
            )
            // Completed replay can learn different intervals from different
            // evidence paths. One accepted identity must not discard switches
            // recovered from archived crops or announcements. At the same
            // instant, stronger live testimony sorts last and therefore wins.
            val combined = (reconstructed + timeAdjustedObserved).sortedWith(
                compareBy<ReplayIdentityObservation> { it.atNanos }
                    .thenBy { identityBasisPriority(it.basis) }
            )
            return ReplayIdentityTrack(combined, visibleFromNanos)
        }

        private fun isBracketedCryContradiction(
            candidate: ReplayIdentityObservation,
            all: List<ReplayIdentityObservation>,
        ): Boolean {
            val strongVisual = all.filter { observation ->
                observation.side == candidate.side &&
                    observation !== candidate &&
                    observation.sourceId != DECISIVE_CRY_SOURCE &&
                    (observation.sourceId?.contains("SPECIES_OVERLAY_PIPELINE") == true ||
                        observation.basis.contains("CROP") ||
                        observation.basis.contains("ANNOUNCEMENT"))
            }
            val before = strongVisual.lastOrNull { it.atNanos < candidate.atNanos }
            val after = strongVisual.firstOrNull { it.atNanos > candidate.atNanos }
            return before != null && after != null &&
                candidate.atNanos - before.atNanos <= CRY_CONTRADICTION_WINDOW_NANOS &&
                after.atNanos - candidate.atNanos <= CRY_CONTRADICTION_WINDOW_NANOS &&
                normalize(before.speciesName) == normalize(after.speciesName) &&
                normalize(candidate.speciesName) != normalize(before.speciesName)
        }

        /**
         * A completed archive may learn an immediate opening switch from badge
         * OCR only after that Pokemon has already attacked. Move the confirmed
         * identity back to its first player-hit evidence, without claiming it
         * was present before any battlefield evidence of the replacement.
         */
        private fun backdateImmediateOpeningPlayerSwitch(
            side: String,
            articles: List<ArchivedRealityArticle>,
            reconstructed: List<ReplayIdentityObservation>,
            observed: List<ReplayIdentityObservation>,
            matchStartNanos: Long?,
        ): List<ReplayIdentityObservation> {
            if (side != "PLAYER" || matchStartNanos == null) return observed
            val configuredLead = reconstructed.firstOrNull {
                it.basis == "CURRENT TEAM CONFIGURATION"
            } ?: return observed
            val replacement = observed
                .filter {
                    it.atNanos >= matchStartNanos &&
                        it.atNanos - matchStartNanos <= IMMEDIATE_OPENING_SWITCH_WINDOW_NANOS &&
                        normalize(it.speciesName) != normalize(configuredLead.speciesName)
                }
                .minByOrNull { it.atNanos }
                ?: return observed
            val firstPlayerHit = articles.firstNotNullOfOrNull { article ->
                val at = article.monotonicTimeNanos ?: return@firstNotNullOfOrNull null
                if (at !in matchStartNanos..replacement.atNanos) return@firstNotNullOfOrNull null
                val isPlayerHit = when (val payload = article.payload) {
                    is ArchivedFastMoveUseObserved -> payload.attackingSide == "PLAYER"
                    is ArchivedActiveHpBarDamageTickMeasured -> payload.damagedSide == "OPPONENT"
                    is ArchivedActiveHpBarBorderPulseObserved -> payload.damagedBarSide == "OPPONENT"
                    is ArchivedHpBarBorderPulse -> payload.barSide == "OPPONENT" && payload.status == "PRESENT"
                    is ArchivedFastMoveRecipientVisualArtifactMeasured -> payload.damagedSide == "OPPONENT"
                    else -> false
                }
                at.takeIf { isPlayerHit }
            } ?: return observed
            return observed.map {
                if (it.articleId == replacement.articleId) it.copy(
                    atNanos = firstPlayerHit,
                    basis = "OBSERVED SPECIES / FIRST PLAYER HIT",
                ) else it
            }
        }

        private fun normalize(value: String): String = value.uppercase().filter(Char::isLetterOrDigit)

        private fun identityBasisPriority(basis: String): Int = when {
            basis == "CURRENT TEAM CONFIGURATION" -> 0
            basis.contains("ANNOUNCEMENT") -> 1
            basis.contains("CROP") -> 2
            basis.startsWith("OBSERVED SPECIES") -> 3
            else -> 1
        }

        private const val DECISIVE_CRY_SOURCE = "DECISIVE_BATTLE_CRY_SPECIES_WITNESS"
        private const val CRY_CONTRADICTION_WINDOW_NANOS = 4_000_000_000L
        private const val IMMEDIATE_OPENING_SWITCH_WINDOW_NANOS = 10_000_000_000L
    }
}

data class ReplayIdentityObservation(
    val side: String,
    val speciesName: String,
    val speciesId: Int?,
    val atNanos: Long,
    val basis: String,
    val sourceId: String? = null,
    val confidence: Float? = null,
    val articleId: String? = null,
)

data class ReplayScene(
    /** The fixed replay coordinate system: PLAYER is left; OPPONENT is right. */
    val player: ReplayCombatant?,
    val opponent: ReplayCombatant?,
    val observedSpeciesNames: List<String>,
    val candidateSpeciesId: Int?,
    val candidateSimilarity: Float?,
    val outcome: String?,
    /** Energy generated from observed, identified fast-move cadence. */
    val playerGeneratedEnergy: Int,
    /** Energy generated from observed, identified fast-move cadence. */
    val opponentGeneratedEnergy: Int,
    /** Cost of named charged moves confirmed by preserved announcements. */
    val playerSpentEnergy: Int,
    /** Cost of named charged moves confirmed by preserved announcements. */
    val opponentSpentEnergy: Int,
    val fastMoveActions: List<ReplayFastMoveAction>,
    val chargedMoveActions: List<ReplayChargedMoveAction>,
    /** 3, 2, 1, or GO only during its preserved on-screen interval. */
    val countdownGlyph: String?,
    /** QTE grade milestone at the exact preserved physical pulse time. */
    val qteMilestone: String?,
    val qteMilestoneSide: String?,
    val latestEvidenceLabel: String,
    val playerHp: ReplayHpState? = null,
    val opponentHp: ReplayHpState? = null,
)

data class ReplayFastMoveAction(
    val side: String,
    val moveName: String?,
    val type: PokemonType?,
    val startedAtNanos: Long,
    val progress: Float,
    val evidenceKinds: Set<String> = emptySet()
)

data class ReplayChargedMoveAction(
    val side: String,
    val moveName: String?,
    val startedAtNanos: Long,
    val progress: Float,
    val evidenceKinds: Set<String> = emptySet()
)

data class ReplayHapticEvent(
    val atNanos: Long,
    val pulseCount: Int
)

private data class ReplayQteMilestone(val atNanos: Long, val label: String, val side: String)

private data class ReplayFastMoveUse(
    val side: String,
    val observedAtNanos: Long,
    val moveName: String?,
    val evidenceKinds: Set<String>,
    val canonical: Boolean = false
)

private data class ReplayChargedMoveUse(
    val side: String,
    val animationAtNanos: Long,
    val lastEvidenceAtNanos: Long,
    val moveName: String?,
    val anchorPriority: Int,
    val evidenceKinds: Set<String>
)

private data class ReplayCadence(val side: String, val intervalNanos: Long, val basis: String)
private data class ReplayMoveCandidate(val moves: List<Move>, val support: Int)

data class ReplayCombatant(
    val speciesName: String,
    val speciesId: Int?,
    val identityEstablishedAtNanos: Long,
    val identityBasis: String = "OBSERVED SPECIES",
    val isFainted: Boolean = false
)

private const val FAST_MOVE_VISUAL_NANOS = 450_000_000L
private const val COUNTDOWN_GLYPH_VISUAL_NANOS = 850_000_000L
private const val QTE_MILESTONE_VISUAL_NANOS = 700_000_000L
private const val REPLAY_QTE_WINDOW_NANOS = 10_000_000_000L
private const val REPLAY_QTE_MIN_PULSE_NANOS = 25_000_000L
private const val REPLAY_QTE_MAX_PULSE_NANOS = 300_000_000L
private const val REPLAY_QTE_MIN_INTER_PULSE_NANOS = 80_000_000L
private const val REPLAY_QTE_MAX_INTER_PULSE_NANOS = 3_000_000_000L
private const val REPLAY_QTE_CLOSE_ASSOCIATION_NANOS = 1_500_000_000L
private const val CHARGE_MOVE_VISUAL_NANOS = 1_800_000_000L
private const val CHARGE_MOVE_SEQUENCE_MERGE_NANOS = 8_000_000_000L
private const val FAST_MOVE_WITNESS_MERGE_NANOS = 450_000_000L
private const val REPLAY_TURN_NANOS = 500_000_000L
private const val REPLAY_REQUIRED_CADENCE_INTERVALS = 3
private const val REPLAY_MIN_CADENCE_TOLERANCE_NANOS = 160_000_000L
private const val REPLAY_RELATIVE_CADENCE_TOLERANCE = 0.15
private val REPLAY_CADENCE_BASIS_PRIORITY = listOf(
    "HP_BORDER",
    "RECIPIENT_VISUAL",
    "HP_MOTION"
)
private const val CHARGE_FILL_BASIS = "CHARGE_MOVE_ENERGY_FILL"
private val QTE_MILESTONE_LABELS = listOf("NICE", "GREAT", "EXCELLENT")

private fun oppositeSide(side: String): String = when (side.uppercase()) {
    "PLAYER" -> "OPPONENT"
    "OPPONENT" -> "PLAYER"
    else -> side.uppercase()
}

private fun String.isBattleSide(): Boolean = this == "PLAYER" || this == "OPPONENT"
