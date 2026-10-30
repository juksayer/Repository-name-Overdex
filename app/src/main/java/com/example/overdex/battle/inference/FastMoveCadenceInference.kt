package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.ActiveHpBarMotionCadenceMeasured
import com.example.overdex.battle.custody.ActiveHpBarBorderCadenceMeasured
import com.example.overdex.battle.custody.ActiveHpBarBorderPulseObserved
import com.example.overdex.battle.custody.ActiveHpBarDamageTickMeasured
import com.example.overdex.battle.custody.ApertureStatus
import com.example.overdex.battle.custody.HpBarBorderPulse
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.ActivePokemonFaintedWitnessed
import com.example.overdex.battle.custody.AttackIncoming
import com.example.overdex.battle.custody.GetReadyWitnessed
import com.example.overdex.battle.custody.ChargeMoveUsedAnnounced
import com.example.overdex.battle.custody.FastMoveEffectiveness
import com.example.overdex.battle.custody.FastMoveEffectivenessWitnessed
import com.example.overdex.battle.custody.FastMoveIdentified
import com.example.overdex.battle.custody.FastMoveRecipientVisualCadenceMeasured
import com.example.overdex.battle.custody.FastMoveSoundMeasured
import com.example.overdex.battle.custody.FastMoveUseObserved
import com.example.overdex.battle.custody.PlayerTeamSlotConfigured
import com.example.overdex.battle.custody.TestimonyPayload
import com.example.overdex.battle.custody.MatchStarted
import com.example.overdex.battle.custody.MatchEnded
import com.example.overdex.battle.audio.FastMoveReferenceMatcher
import com.example.overdex.battle.audio.FastMoveListeningGate
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.data.PokemonFormReference
import com.example.overdex.data.PokemonFormKnowledge
import com.example.overdex.data.PokemonFormCandidates
import com.example.overdex.battle.custody.PlayerTeamRosterSlotWitnessed
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import java.util.EnumMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

data class FastMoveCadenceDerivation(
    val payload: TestimonyPayload,
    val predecessorIds: List<ArticleId>,
    val confidence: Float,
    /** The observed cadence interval that this derivation describes. */
    val observedArticle: RealityArticle
)

/**
 * Current live interpretation of one combatant's legal Fast Move pool.
 *
 * This is presentation state derived from retained Timeline evidence. It is
 * deliberately recomputed as evidence arrives, so a noisy early cadence can be
 * refuted without deleting either the original measurement or the move from
 * Reference Knowledge.
 */
data class FastMoveCandidateSnapshot(
    val side: ActivePokemonSide,
    val speciesName: String,
    val remainingMoveNames: List<String>,
    val eliminatedMoveNames: List<String>,
    val evidenceKinds: List<String>,
    val identifiedMoveName: String?,
    val audioPreference: FastMoveAudioPreference? = null,
)

/**
 * Resolves measured cadence against the active species' possible fast moves.
 * It labels energy as derived and records when equal-duration resolution used
 * the explicit higher-energy assumption.
 */
class FastMoveCadenceInference(
    private val pokemonKnowledge: PokemonKnowledge,
    private val opponentFormReferences: PokemonFormKnowledge = PokemonFormKnowledge(pokemonKnowledge),
) {
    private enum class CadenceBasis {
        OBSERVED_USES,
        BORDER_PULSE,
        AUDIO_PROFILE,
        RECIPIENT_VISUAL_ARTIFACT,
        BAR_MOTION,
    }
    private data class CadenceEvidence(
        val article: RealityArticle,
        val intervalNanos: Long,
        val basis: CadenceBasis,
        val predecessorIds: List<ArticleId> = listOf(article.id),
        val useEvidenceKind: String? = null,
        val supportingArticles: List<RealityArticle> = emptyList(),
        val supportingImpactSpan: Pair<Long, Long>? = null,
    )
    private data class ConfiguredPlayerMove(
        val speciesId: Int?,
        val moveName: String,
        val articleId: ArticleId,
        val confidence: Float
    )

    private data class EffectivenessEvidence(
        val article: RealityArticle,
        val effectiveness: FastMoveEffectiveness,
        val defenderTypeOptions: List<List<PokemonType>>,
        val defenderArticleId: ArticleId,
    )

    private data class ObservedUseEndpoint(
        val useArticle: RealityArticle,
        val sourceArticle: RealityArticle,
    )

    private data class SideState(
        val side: ActivePokemonSide,
        val speciesName: String,
        val speciesArticleId: ArticleId,
        val activeSinceNanos: Long,
        // First readable identity may arrive after several attacks. Only an
        // actual entry/switch/faint boundary can exclude those earlier inputs.
        val appearanceBoundaryNanos: Long?,
        // Each inner list describes one compatible form. Combining the lists
        // would invent a Ground/Electric/Steel Stunfisk instead of alternatives.
        val typeOptions: List<List<PokemonType>>,
        val possibleMoves: List<Move>,
        val playerTeamSlot: Int? = null,
        val audioActiveSinceNanos: Long = activeSinceNanos,
        val appearanceEvidence: List<ArticleId> = emptyList(),
        val cadenceEvidence: MutableList<CadenceEvidence> = mutableListOf(),
        val effectivenessEvidence: MutableList<EffectivenessEvidence> = mutableListOf(),
        var identifiedMove: Move? = null,
        var selectedBasis: CadenceBasis? = null,
        var assumedHigherEnergyTieBreak: Boolean = false,
        var identificationEvidence: List<ArticleId> = emptyList(),
        val lastDirectUseArticles: MutableMap<String, ObservedUseEndpoint> = mutableMapOf(),
        var lastSoundOnsetNanos: Long? = null,
        var lastSoundArticle: RealityArticle? = null,
        var corroborationSignature: String = "",
        var identityBasis: String = "",
        var identityConfidence: Float = 0f,
        // Established measurement families survive a QTE's interval reset.
        var identityCadenceFamilies: Set<EvidenceFamily> = emptySet(),
        val referenceSounds: MutableList<ReferenceSoundEvidence> = mutableListOf(),
    )

    private data class ReferenceSoundEvidence(
        val article: RealityArticle, val moveName: String, val alignedStartNanos: Long,
        val segmentStartNanos: Long, val predecessors: List<ArticleId>,
        val impactSupportAtNanos: Long? = null,
    )

    private val states = EnumMap<ActivePokemonSide, SideState>(ActivePokemonSide::class.java)
    private val fainted = FaintedCombatants()
    // A switch changes the active appearance, not the combatant's learned move.
    // Include side and form name: mirror opponents never inherit our configured move.
    private val combatants = mutableMapOf<Pair<ActivePokemonSide, String>, SideState>()
    private val pending = EnumMap<ActivePokemonSide, MutableList<RealityArticle>>(ActivePokemonSide::class.java)
    private val appearanceStarts = EnumMap<ActivePokemonSide, Long>(ActivePokemonSide::class.java)
    private val configurationScope = PlayerTeamConfigurationScope()
    private val configuredPlayerMoves = mutableMapOf<String, ConfiguredPlayerMove>()
    private val configuredPlayerSlots = mutableMapOf<Int, Pair<String, ConfiguredPlayerMove>>()
    private val playerReferenceSlots = mutableMapOf<Int, Pair<String, ArticleId>>()
    private val pendingEffectiveness = mutableListOf<RealityArticle>()
    private val recentUses = mutableListOf<RealityArticle>()
    // Fused uses retain occurrence/ownership proof, but each measurement family
    // has its own source instant. An early flash must not redate a later HP loss.
    private val rawImpactArticles = linkedMapOf<ArticleId, RealityArticle>()
    private var cadenceFloorNanos = Long.MIN_VALUE
    private val audioIdentityGate = FastMoveListeningGate()
    private val audioPreferences = FastMoveAudioPreferenceTracker()
    private var latestEvidenceAtNanos = Long.MIN_VALUE
    private val pendingReferenceSounds = mutableListOf<RealityArticle>()
    private val audioEntryCues = mutableMapOf<ActivePokemonSide, Pair<Long, ArticleId>>()
    private val chargedEffectivenessWindow = ChargedMoveEffectivenessWindow()


    suspend fun accept(article: RealityArticle): List<FastMoveCadenceDerivation> {
        if (configurationScope.accept(article)) {
            // The scan can be another owned copy of the same species. Neither its
            // configured move nor a cached pre-battle identity transfers to it.
            configuredPlayerMoves.clear()
            configuredPlayerSlots.clear()
            configurationScope.current().values.forEach { current ->
                val p = current.payload as PlayerTeamSlotConfigured
                configuredPlayerSlots[p.slot] = normalize(p.speciesName) to ConfiguredPlayerMove(
                    p.speciesId, p.fastMoveName, current.id, current.confidence ?: 1f)
            }
            configuredPlayerMoves.putAll(configuredPlayerSlots.values)
            playerReferenceSlots.clear()
            (article.payload as com.example.overdex.battle.custody.PlayerTeamRosterConfirmation)
                .speciesBySlot.forEachIndexed { index, name ->
                    if (!name.isNullOrBlank()) playerReferenceSlots[index + 1] = name to article.id
                }
            states.remove(ActivePokemonSide.PLAYER)
            combatants.keys.removeAll { it.first == ActivePokemonSide.PLAYER }
            pending.remove(ActivePokemonSide.PLAYER)
            appearanceStarts.remove(ActivePokemonSide.PLAYER)
            recentUses.removeAll { (it.payload as FastMoveUseObserved).attackingSide == ActivePokemonSide.PLAYER }
            rawImpactArticles.clear()
            pendingEffectiveness.clear()
            audioPreferences.clear()
            pendingReferenceSounds.clear()
            return emptyList()
        }
        article.monotonicTimeNanos?.let { latestEvidenceAtNanos = maxOf(latestEvidenceAtNanos, it) }
        // Capture starts at VS, but fast-move identity needs GO or a
        // witnessed in-progress phase when observation began late.
        if (article.payload !is com.example.overdex.battle.custody.CountdownGlyphWitnessed &&
            article.payload !is com.example.overdex.battle.custody.VsScreenWitnessed) {
            audioIdentityGate.observe(article.payload, article.monotonicTimeNanos, article.id.value)
        }
        if (article.payload is com.example.overdex.battle.custody.ObservationRestartedByUser ||
            article.payload is com.example.overdex.battle.custody.ScreenIgnoredByUser) playerReferenceSlots.clear()
        (article.payload as? PlayerTeamRosterSlotWitnessed)?.let { roster ->
            if (configurationScope.latestConfirmationNanos?.let {
                    (article.monotonicTimeNanos ?: Long.MIN_VALUE) <= it } == true) return@let
            val previous = playerReferenceSlots[roster.slot]
            val name = PokemonFormReference.mergeRosterName(previous?.first, roster.speciesName,
                explicitSprite = roster.spriteVariant != null)
            // Keep the article that actually supplied the form, not a later bare OCR read.
            playerReferenceSlots[roster.slot] = name to
                if (name != roster.speciesName && previous != null) previous.second else article.id
        }
        if (article.payload is MatchEnded || article.payload is com.example.overdex.battle.custody.ScreenIgnoredByUser ||
            article.payload is com.example.overdex.battle.custody.ObservationRestartedByUser) {
            audioPreferences.clear()
            pendingReferenceSounds.clear()
            audioEntryCues.clear()
            rawImpactArticles.clear()
            states.values.forEach { it.referenceSounds.clear() }
        }
        fainted.observe(article)
        val announcedText = (article.payload as? com.example.overdex.battle.custody.RawTestimony)?.data as? String
        if (article.payload is ChargeMoveUsedAnnounced ||
            article.payload is com.example.overdex.battle.custody.ChargedMoveEnergySpent ||
            (article.sourceId.id == "ANNOUNCEMENT_WITNESS" && announcedText != null &&
                ChargedMoveEffectivenessWindow.isMoveAnnouncement(announcedText))) {
            article.monotonicTimeNanos?.let(chargedEffectivenessWindow::observeImpact)
        }
        val chargeGuard = when (article.payload) {
            AttackIncoming -> 12_000_000_000L
            GetReadyWitnessed -> 8_000_000_000L
            ChargeMoveUsedAnnounced -> 750_000_000L
            else -> null
        }
        if (chargeGuard != null) {
            audioPreferences.clear()
            article.monotonicTimeNanos?.let { cadenceFloorNanos = it + chargeGuard }
            states.values.forEach {
                it.cadenceEvidence.clear()
                it.lastDirectUseArticles.clear()
                it.lastSoundArticle = null
                it.lastSoundOnsetNanos = null
                it.referenceSounds.clear()
            }
            pending.clear()
            pendingEffectiveness.clear()
            recentUses.clear()
            rawImpactArticles.clear()
            pendingReferenceSounds.clear()
            return emptyList()
        }
        val configured = article.payload as? PlayerTeamSlotConfigured
        if (configured != null) {
            if (!configurationScope.isCurrent(article)) return emptyList()
            playerReferenceSlots[configured.slot] = configured.speciesName to article.id
            val oldName = configuredPlayerSlots[configured.slot]?.first
            configuredPlayerSlots[configured.slot] = normalize(configured.speciesName) to ConfiguredPlayerMove(
                speciesId = configured.speciesId,
                moveName = configured.fastMoveName,
                articleId = article.id,
                confidence = article.confidence ?: 1f
            )
            configuredPlayerMoves.clear()
            configuredPlayerMoves.putAll(configuredPlayerSlots.values)
            val name = normalize(configured.speciesName)
            combatants.keys.removeAll { it.first == ActivePokemonSide.PLAYER && it.second in setOf(oldName, name) }
            val active = states[ActivePokemonSide.PLAYER] ?: return emptyList()
            if (normalize(active.speciesName) != name) {
                if (normalize(active.speciesName) == oldName && oldName !in configuredPlayerMoves) {
                    states.remove(ActivePokemonSide.PLAYER)
                    pending.remove(ActivePokemonSide.PLAYER)
                }
                return emptyList()
            }
            // Changing a move before GO must not wait for a second species read.
            // Another slot's edit leaves this active combatant untouched.
            val pokemon = pokemonKnowledge.getPokemonByName(configured.speciesName)
            val moves = pokemon?.fastMoves.orEmpty().filter {
                it.isFast && it.turns != null
            }.distinctBy { normalize(it.name) }
            val updated = active.copy(
                typeOptions = listOf(pokemon?.types.orEmpty()),
                possibleMoves = moves, identifiedMove = moves.singleOrNull { normalize(it.name) == normalize(configured.fastMoveName) },
                cadenceEvidence = mutableListOf(), selectedBasis = null,
                lastDirectUseArticles = mutableMapOf(),
                identificationEvidence = listOf(active.speciesArticleId, article.id),
                identityBasis = "CONFIGURED_PLAYER_TEAM_AND_ACTIVE_SPECIES",
                identityConfidence = article.confidence ?: 1f,
                identityCadenceFamilies = emptySet(),
                assumedHigherEnergyTieBreak = false,
            )
            states[ActivePokemonSide.PLAYER] = updated
            combatants[ActivePokemonSide.PLAYER to normalize(active.speciesName)] = updated
            return referenceIdentity(updated, ActivePokemonSide.PLAYER, article)
        }
        val faint = article.payload as? ActivePokemonFaintedWitnessed
        if (faint != null) {
            val active = states[faint.side]
            val at = article.monotonicTimeNanos ?: Long.MIN_VALUE
            if (at >= (active?.activeSinceNanos ?: Long.MIN_VALUE) &&
                (faint.speciesName == null || active == null || normalize(faint.speciesName) == normalize(active.speciesName))) {
                states.remove(faint.side)
                pending.remove(faint.side)
                pendingEffectiveness.clear()
                recentUses.removeAll { (it.payload as FastMoveUseObserved).attackingSide == faint.side }
                appearanceStarts[faint.side] = at
            }
            return emptyList()
        }
        val check = article.payload as? com.example.overdex.battle.custody.SpeciesCheckMeasured
        if (check?.status == "OPENED" && check.reason in setOf("ENTRY", "FAINT", "SWITCH", "CRY")) {
            audioPreferences.clear()
            val prior = audioEntryCues[check.side]
            if (prior == null || check.triggerMonotonicNanos - prior.first > AUDIO_ENTRY_WINDOW_NANOS) {
                audioEntryCues[check.side] = check.triggerMonotonicNanos to article.id
            }
        }
        // Scan requests do not invalidate an accepted combatant. They can scope
        // still-unidentified evidence, but only an actual faint or new identity
        // closes a known appearance.
        if (check?.status == "OPENED" && check.reason in setOf("ENTRY", "EMPTY_HP", "FAINT", "SWITCH") &&
            states[check.side] == null && check.triggerMonotonicNanos > (appearanceStarts[check.side] ?: Long.MIN_VALUE)) {
            states.remove(check.side)
            pending.remove(check.side)
            pendingEffectiveness.clear()
            recentUses.removeAll { (it.payload as FastMoveUseObserved).attackingSide == check.side }
            appearanceStarts[check.side] = check.triggerMonotonicNanos
            return emptyList()
        }
        val species = article.payload as? ActivePokemonSpeciesWitnessed
        if (species != null) {
            if (species.side == ActivePokemonSide.PLAYER && configurationScope.latestConfirmationNanos?.let {
                    (article.monotonicTimeNanos ?: Long.MIN_VALUE) <= it } == true) return emptyList()
            if (fainted.contains(species.side, species.speciesName)) return emptyList()
            // Reconfirming an unchanged badge must not reset timing or recount energy.
            val priorState = states[species.side]
            val sameAppearance = species.appearanceStartedAtNanos == null ||
                species.appearanceStartedAtNanos == priorState?.activeSinceNanos
            if (priorState?.speciesName?.let(::normalize) == normalize(species.speciesName) &&
                sameAppearance && priorState.playerTeamSlot == species.playerTeamSlot) {
                val cue = audioEntryCues[species.side]
                if (cue != null && (article.monotonicTimeNanos ?: Long.MIN_VALUE) >= cue.first) {
                    audioEntryCues.remove(species.side)
                    val confirmed = priorState.copy(appearanceEvidence =
                        (priorState.appearanceEvidence + article.predecessorIds + cue.second + article.id).distinct().takeLast(8))
                    states[species.side] = confirmed
                    combatants[species.side to normalize(species.speciesName)] = confirmed
                    val sounds = pendingReferenceSounds.toList().also { pendingReferenceSounds.clear() }
                    return sounds.flatMap { accept(it) }
                }
                return emptyList()
            }
            // Delayed OCR of a departed combatant cannot roll the active side back.
            if (priorState != null && article.monotonicTimeNanos != null &&
                article.monotonicTimeNanos < priorState.activeSinceNanos
            ) return emptyList()
            val explicitAppearanceStart = appearanceStarts[species.side]
            val entryCue = audioEntryCues.remove(species.side)?.takeIf { (at, _) ->
                at >= (priorState?.activeSinceNanos ?: Long.MIN_VALUE) &&
                    (article.monotonicTimeNanos ?: Long.MIN_VALUE) - at in 0..AUDIO_ENTRY_WINDOW_NANOS
            }
            val activeSince = species.appearanceStartedAtNanos ?: if (priorState == null) {
                explicitAppearanceStart ?: article.monotonicTimeNanos ?: Long.MIN_VALUE
            } else {
                article.monotonicTimeNanos ?: Long.MIN_VALUE
            }
            if (priorState != null) appearanceStarts[species.side] = activeSince
            val opponentReferences = if (species.side == ActivePokemonSide.OPPONENT)
                opponentFormReferences.forObservedName(species.speciesName, species.speciesId) else null
            val referenceNames = if (species.side == ActivePokemonSide.PLAYER && species.playerTeamSlot != null)
                listOf(species.speciesName)
                else if (species.side == ActivePokemonSide.PLAYER)
                PokemonFormReference.playerReferenceNames(species.speciesName, playerReferenceSlots.values.map { it.first })
                else requireNotNull(opponentReferences).names
            val referenceName = referenceNames.singleOrNull()
            val formEvidence = if (species.side == ActivePokemonSide.PLAYER &&
                referenceNames != listOf(species.speciesName))
                playerReferenceSlots.values.filter { it.first in referenceNames }.map { it.second } else emptyList()
            val referencePokemon = opponentReferences?.pokemon ?: referenceNames.map { name ->
                pokemonKnowledge.getPokemonByName(name)
                    ?: species.speciesId?.takeIf { '(' !in name }?.let { pokemonKnowledge.getPokemonById(it) }
            }
            val typeOptions = referencePokemon.map { it?.types.orEmpty() }.distinct()
            val possibleMoves = (opponentReferences ?: PokemonFormCandidates(referenceNames, referencePokemon)).fastMoves
            val configuredMove = if (species.side == ActivePokemonSide.PLAYER && referenceName != null) {
                configuredPlayerMoves[normalize(referenceName)]
            } else null
            val candidateMoves = possibleMoves
            val configuredIdentity = configuredMove?.let { configuredKnowledge ->
                candidateMoves.singleOrNull { normalize(it.name) == normalize(configuredKnowledge.moveName) }
            }
            val referenceIdentity = configuredIdentity ?: candidateMoves.singleOrNull()
            val combatantKey = species.side to normalize(species.speciesName)
            // Shadow/cosmetic alternatives with identical typing and Fast Move
            // pools must not erase a learned move whenever that combatant returns.
            val oneBattleReference = referencePokemon.isNotEmpty() && referencePokemon.none { it == null } &&
                referencePokemon.filterNotNull().map { it.types.toSet() to it.fastMoves.toSet() }.distinct().size == 1
            val remembered = combatants[combatantKey].takeIf { oneBattleReference }
            val retainedMove = remembered?.identifiedMove?.takeIf { it in candidateMoves }
            val state = SideState(
                side = species.side,
                speciesName = species.speciesName,
                speciesArticleId = article.id,
                activeSinceNanos = activeSince,
                appearanceBoundaryNanos = species.appearanceStartedAtNanos ?:
                    if (priorState != null) activeSince else explicitAppearanceStart,
                typeOptions = typeOptions,
                possibleMoves = candidateMoves,
                playerTeamSlot = species.playerTeamSlot,
                audioActiveSinceNanos = entryCue?.let { maxOf(it.first, explicitAppearanceStart ?: Long.MIN_VALUE) }
                    ?: activeSince,
                appearanceEvidence = (listOfNotNull(entryCue?.second) + formEvidence).distinct(),
                effectivenessEvidence = remembered?.effectivenessEvidence?.toMutableList() ?: mutableListOf(),
                identifiedMove = referenceIdentity ?: retainedMove,
                identificationEvidence = when {
                    configuredIdentity != null -> (listOf(article.id, requireNotNull(configuredMove).articleId) + formEvidence).distinct()
                    referenceIdentity != null -> listOf(article.id) + formEvidence
                    retainedMove != null -> (listOf(article.id) + remembered.identificationEvidence + formEvidence).distinct()
                    else -> emptyList()
                },
                identityBasis = when {
                    configuredIdentity != null -> "CONFIGURED_PLAYER_TEAM_AND_ACTIVE_SPECIES"
                    referenceIdentity != null -> "ACTIVE_SPECIES_UNIQUE_FAST_MOVE_REFERENCE_KNOWLEDGE"
                    retainedMove != null -> remembered.identityBasis
                    else -> ""
                },
                identityConfidence = if (referenceIdentity != null) {
                    ((article.confidence ?: 1f) * (configuredMove?.confidence ?: 1f)).coerceIn(0f, 1f)
                } else remembered?.identityConfidence ?: 0f,
                assumedHigherEnergyTieBreak = retainedMove != null && remembered.assumedHigherEnergyTieBreak,
                identityCadenceFamilies = if (retainedMove != null) remembered.identityCadenceFamilies else emptySet(),
            )
            states[species.side] = state
            combatants[combatantKey] = state
            val early = pending.remove(species.side).orEmpty()
            val earlyUses = recentUses.filter { useArticle ->
                val use = useArticle.payload as FastMoveUseObserved
                val at = useArticle.monotonicTimeNanos
                use.attackingSide == species.side &&
                    (explicitAppearanceStart == null || (at != null && at >= explicitAppearanceStart)) &&
                    (use.attackerSpeciesName == null ||
                        normalize(use.attackerSpeciesName) == normalize(species.speciesName))
            }.sortedBy { it.monotonicTimeNanos }
            val effectiveness = if (states.size == ActivePokemonSide.entries.size) {
                pendingEffectiveness.toList().also { pendingEffectiveness.clear() }
            } else emptyList()
            val referenceResult = referenceIdentity(state, species.side, article)
            val earlySounds = if (states.size == 2) pendingReferenceSounds.toList().also { pendingReferenceSounds.clear() }
                else emptyList()
            val recoveredResults = (effectiveness + early + earlyUses + earlySounds)
                .distinctBy { it.id }
                .sortedBy { it.monotonicTimeNanos }
                .flatMap { accept(it) }
            return if (configuredIdentity == null &&
                recoveredResults.any { it.payload is FastMoveIdentified }
            ) {
                recoveredResults
            } else {
                referenceResult + recoveredResults
            }
        }

        val effectiveness = article.payload as? FastMoveEffectivenessWitnessed
        if (effectiveness != null) {
            val hasDirection = effectiveness.damagedSide != null
            if (states.size < ActivePokemonSide.entries.size || (!hasDirection && alignedUses(article, effectiveness).isEmpty())) {
                if (pendingEffectiveness.none { it.id == article.id }) pendingEffectiveness += article
                if (pendingEffectiveness.size > MAX_EFFECTIVENESS_HISTORY) pendingEffectiveness.removeAt(0)
                return emptyList()
            }
            return acceptEffectiveness(article, effectiveness)
        }

        if (rawImpactKind(article) != null) {
            rawImpactArticles[article.id] = article
            while (rawImpactArticles.size > MAX_RAW_IMPACT_HISTORY) {
                rawImpactArticles.remove(rawImpactArticles.keys.first())
            }
            // Match can publish a fused use before delivering its final raw
            // predecessor to this inference. Resolve that deferred stream now.
            val cadenceResults = recentUses.filter { article.id in it.predecessorIds }.flatMap {
                acceptObservedUseCadence(it, it.payload as FastMoveUseObserved)
            }
            // Audio analysis may finish before its independent visual support.
            // Revisit the same immutable window without counting it twice.
            val audioResults = pendingReferenceSounds.toList().flatMap {
                acceptReferenceSound(it, it.payload as FastMoveSoundMeasured)
            }
            return cadenceResults + audioResults
        }

        val use = article.payload as? FastMoveUseObserved
        if (use != null) {
            if (recentUses.none { (it.payload as FastMoveUseObserved).useId == use.useId }) recentUses += article
            if (recentUses.size > MAX_USE_HISTORY) recentUses.removeAt(0)
            val effectivenessResults = if (states.size == ActivePokemonSide.entries.size) {
                val newlyAligned = pendingEffectiveness.filter { pendingArticle ->
                    val pendingPayload = pendingArticle.payload as FastMoveEffectivenessWitnessed
                    alignedUses(pendingArticle, pendingPayload).isNotEmpty()
                }
                pendingEffectiveness.removeAll(newlyAligned.toSet())
                newlyAligned.flatMap { pendingArticle ->
                    acceptEffectiveness(
                        pendingArticle,
                        pendingArticle.payload as FastMoveEffectivenessWitnessed
                    )
                }
            } else {
                emptyList()
            }
            val cadenceResults = acceptObservedUseCadence(article, use)
            // Fused impact testimony can arrive after the raw motion. Revisit
            // those retained samples even when this is the first use in a stream.
            val delayedMotionResults = if (cadenceResults.isEmpty()) {
                states[use.attackingSide]?.let { publishCadenceIdentity(article, it) }.orEmpty()
            } else emptyList()
            return effectivenessResults + cadenceResults + delayedMotionResults
        }

        val sound = article.payload as? FastMoveSoundMeasured
        if (sound != null) return if (sound.analysisVersion == FastMoveReferenceMatcher.VERSION)
            acceptReferenceSound(article, sound) else acceptSoundCadence(article, sound)

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
        return acceptCadence(article, cadence)
    }

    /**
     * Audio does not name a move here. It contributes only the measured spacing
     * between audible onsets, on the side inherited from the visual cue that
     * requested the clip. Reference-sound matching remains a later, separately
     * cited conclusion.
     */
    private fun referenceIdentity(state: SideState, side: ActivePokemonSide, article: RealityArticle): List<FastMoveCadenceDerivation> {
        val move = state.identifiedMove ?: return emptyList()
        return listOf(FastMoveCadenceDerivation(
            payload = FastMoveIdentified(side = side, speciesName = state.speciesName, moveName = move.name,
                moveDurationNanos = move.turns!! * TURN_NANOS, observedMedianIntervalNanos = null,
                cadenceSampleCount = 0, basis = state.identityBasis),
            predecessorIds = state.identificationEvidence, confidence = state.identityConfidence, observedArticle = article,
        ))
    }

    /** The microphone contains BOTH attackers. The visual requester does not establish ownership. */
    private fun acceptReferenceSound(article: RealityArticle, sound: FastMoveSoundMeasured): List<FastMoveCadenceDerivation> {
        val clipAt = article.monotonicTimeNanos ?: return emptyList()
        if (sound.referenceCandidates.isEmpty()) return emptyList()
        pendingReferenceSounds.removeAll { it.id == article.id }
        fun retainUnassigned(): List<FastMoveCadenceDerivation> {
            if (pendingReferenceSounds.none { it.id == article.id }) pendingReferenceSounds += article
            pendingReferenceSounds.removeAll { latestEvidenceAtNanos - (it.monotonicTimeNanos ?: clipAt) > 12_000_000_000L }
            if (pendingReferenceSounds.size > 24) pendingReferenceSounds.removeAt(0)
            return emptyList()
        }
        if (states.size < 2 || states.values.any { it.possibleMoves.isEmpty() }) return retainUnassigned()
        val supportingContext = states.values.flatMap { listOf(it.speciesArticleId) + it.appearanceEvidence } +
            configuredPlayerSlots.values.map { it.second.articleId } +
            sound.referenceSearchScopes.flatMap { it.contextArticleIds }.map(::ArticleId)
        val context = audioPreferenceContext()
        // Apply phase/appearance guards to each candidate's source segment, not
        // the unrestricted winner's unrelated alignment.
        val allowed = contextualAudioChoices(sound.referenceCandidates, context, sound.referenceMatchAccepted,
            sound.referenceSearchScopes).filter { choice ->
            val candidate = choice.candidate
            val segmentAt = clipAt + candidate.matchingOffsetNanos
            val alignedStart = segmentAt - candidate.referenceOffsetNanos
            val phaseAllowed = segmentAt >= cadenceFloorNanos && audioIdentityGate.allows(segmentAt) &&
                audioIdentityGate.allows(alignedStart)
            val waitingForEntry = audioEntryCues.values.any { segmentAt >= it.first }
            if (phaseAllowed && waitingForEntry) retainUnassigned()
            phaseAllowed && !waitingForEntry && states.values.none { alignedStart < it.audioActiveSinceNanos }
        }
        // Only guarded candidates can update presentation. Keep all their same-
        // side alternatives for ranking and coverage checks.
        audioPreferences.observe(article.id, clipAt, sound.referenceCandidates, context,
            referenceMatchAccepted = sound.referenceMatchAccepted, contextEvidence = supportingContext,
            searchScopes = sound.referenceSearchScopes, allowedSides = allowed.map { it.side }.toSet())
        return allowed.filter { it.strict }.flatMap { choice ->
            val candidate = choice.candidate
            val segmentAt = clipAt + candidate.matchingOffsetNanos
            val alignedStart = segmentAt - candidate.referenceOffsetNanos
            val state = states.getValue(choice.side)
            val move = state.possibleMoves.single { normalize(it.name) == normalize(candidate.moveName) }
            val history = state.referenceSounds
            history.removeAll { segmentAt - it.segmentStartNanos > 12_000_000_000L }
            val minimumDistinctStart = maxOf(300_000_000L, move.turns!! * TURN_NANOS * 85 / 100)
            if (history.any { normalize(it.moveName) == normalize(move.name) &&
                    (abs(it.alignedStartNanos - alignedStart) < minimumDistinctStart ||
                        abs(it.segmentStartNanos - segmentAt) < 400_000_000L ||
                        (!choice.legacyGlobalMatch && abs(clipAt - (it.article.monotonicTimeNanos ?: clipAt)) < 1_200_000_000L)) }) {
                return@flatMap emptyList()
            }
            val impact = if (choice.requiresImpactSupport) audioImpactSupport(choice.side, segmentAt,
                candidate.matchedDurationNanos, history) else null
            if (choice.requiresImpactSupport && impact == null) return@flatMap retainUnassigned()
            history += ReferenceSoundEvidence(article, move.name, alignedStart, segmentAt,
                supportingContext + listOfNotNull(impact?.id), impact?.monotonicTimeNanos)
            history.sortBy { it.segmentStartNanos }
            if (history.size > 16) history.removeAt(0)
            val agreeing = history.takeLast(2).takeIf { it.size == 2 && it.all { evidence ->
                normalize(evidence.moveName) == normalize(move.name)
            } } ?: return@flatMap emptyList()
            if (state.identityBasis.startsWith("CONFIGURED_PLAYER_TEAM")) return@flatMap emptyList()
            if (state.identifiedMove == move && state.identityBasis.startsWith("AUDIO_REFERENCE")) return@flatMap emptyList()
            state.identifiedMove = move
            state.selectedBasis = null
            state.corroborationSignature = ""
            state.assumedHigherEnergyTieBreak = false
            state.identificationEvidence = agreeing.flatMap { it.predecessors + it.article.id }.distinct()
            state.identityBasis = "AUDIO_REFERENCE_REPEATED_UNIQUE_LEGAL_SIDE" +
                if (agreeing.any { it.impactSupportAtNanos != null }) "_SUPPORTED_BY_SIDE_IMPACT" else ""
            state.identityConfidence = .90f // Derived confidence, never a spectral-score probability.
            val conclusionAt = agreeing.maxOf { maxOf(it.segmentStartNanos, it.impactSupportAtNanos ?: Long.MIN_VALUE) }
            referenceIdentity(state, choice.side, article.copy(
                perceivedAt = article.perceivedAt + (conclusionAt - clipAt) / 1_000_000L,
                monotonicTimeNanos = conclusionAt,
            ))
        }
    }

    /** Raw recipient-side impact only: audio-derived uses and forecasts cannot corroborate themselves. */
    private fun audioImpactSupport(side: ActivePokemonSide, segmentAt: Long, duration: Long,
        history: List<ReferenceSoundEvidence>): RealityArticle? {
        val floor = maxOf(cadenceFloorNanos, states.values.maxOf { it.audioActiveSinceNanos })
        return rawImpactArticles.values.filter { raw ->
            val at = raw.monotonicTimeNanos ?: return@filter false
            rawImpactRecipient(raw) == side.opposite() && at >= floor &&
                at >= segmentAt - AUDIO_IMPACT_ALIGNMENT_NANOS &&
                at <= segmentAt + duration + AUDIO_IMPACT_ALIGNMENT_NANOS &&
                audioIdentityGate.allows(at) && !chargedEffectivenessWindow.contains(at) &&
                history.none { it.impactSupportAtNanos?.let { used -> abs(used - at) < MIN_USE_INTERVAL_NANOS } == true }
        }.minByOrNull { abs(requireNotNull(it.monotonicTimeNanos) - (segmentAt + duration / 2)) }
    }

    private fun acceptSoundCadence(
        article: RealityArticle,
        sound: FastMoveSoundMeasured,
    ): List<FastMoveCadenceDerivation> {
        if (!sound.audible) return emptyList()
        val side = sound.attackingSide ?: return emptyList()
        val onset = sound.soundOnsetMonotonicNanos ?: return emptyList()
        val state = states[side] ?: run {
            val waiting = pending.getOrPut(side) { mutableListOf() }
            if (waiting.none { it.id == article.id }) waiting += article
            if (waiting.size > MAX_CADENCE_HISTORY) waiting.removeAt(0)
            return emptyList()
        }
        if (onset < max(appearanceStarts[side] ?: Long.MIN_VALUE, cadenceFloorNanos)) return emptyList()
        val previousAt = state.lastSoundOnsetNanos
        val previousArticle = state.lastSoundArticle
        state.lastSoundOnsetNanos = onset
        state.lastSoundArticle = article
        if (previousAt == null || previousArticle == null || onset <= previousAt) return emptyList()
        val interval = onset - previousAt
        if (interval < MIN_USE_INTERVAL_NANOS) return emptyList()
        return acceptCadence(
            article,
            CadenceInput(
                attackingSide = side,
                intervalNanos = interval,
                basis = CadenceBasis.AUDIO_PROFILE,
                predecessorIds = listOf(previousArticle.id, article.id),
                eventAtNanos = onset,
            )
        )
    }

    /**
     * The fused FastMoveUseObserved stream is already the best statement that a
     * particular attack occurred, but an unresolved flash/damage phase offset
     * can still split one hit into two uses. Compare like evidence with like:
     * damage-to-damage and flash-to-flash, never damage-to-flash. Keep the streams
     * separate in voting so their shared impact cannot count twice.
     */
    private fun acceptObservedUseCadence(
        article: RealityArticle,
        use: FastMoveUseObserved,
    ): List<FastMoveCadenceDerivation> {
        if (use.evidenceKinds.none(DIRECT_USE_EVIDENCE_KINDS::contains)) return emptyList()
        val state = states[use.attackingSide] ?: return emptyList()
        val at = article.monotonicTimeNanos ?: return emptyList()
        if (at < cadenceFloorNanos || !useBelongsTo(state, article, use)) return emptyList()
        val streams = use.evidenceKinds.filter { it in IMPACT_USE_EVIDENCE_KINDS }
            .ifEmpty { use.evidenceKinds.filter { it in DIRECT_USE_EVIDENCE_KINDS } }.distinct()
        return streams.flatMap { kind ->
            val source = if (kind in IMPACT_USE_EVIDENCE_KINDS && article.predecessorIds.isNotEmpty()) {
                article.predecessorIds.mapNotNull(rawImpactArticles::get)
                    .filter { rawImpactKind(it) == kind && rawImpactRecipient(it) == use.damagedSide }
                    .filter { it.monotonicTimeNanos != null }
                    .minByOrNull { it.monotonicTimeNanos!! }
                    ?: return@flatMap emptyList()
            } else article // Legacy/source-less uses retain their recorded timing.
            if (!useBelongsTo(state, source, use)) return@flatMap emptyList()
            val endpoint = ObservedUseEndpoint(article, source)
            val sourceAt = source.monotonicTimeNanos ?: return@flatMap emptyList()
            val previous = state.lastDirectUseArticles[kind]
            val previousAt = previous?.sourceArticle?.monotonicTimeNanos
            if (previous == null || previousAt == null) {
                state.lastDirectUseArticles[kind] = endpoint
                return@flatMap emptyList()
            }
            val interval = sourceAt - previousAt
            if (interval < MIN_USE_INTERVAL_NANOS) return@flatMap emptyList()
            state.lastDirectUseArticles[kind] = endpoint
            acceptCadence(
                source,
                CadenceInput(
                    attackingSide = use.attackingSide,
                    intervalNanos = interval,
                    basis = CadenceBasis.OBSERVED_USES,
                    predecessorIds = listOf(previous.useArticle.id, article.id,
                        previous.sourceArticle.id, source.id).distinct(),
                    useEvidenceKind = kind,
                )
            )
        }
    }

    private fun rawImpactKind(article: RealityArticle): String? = when (val payload = article.payload) {
        is ActiveHpBarDamageTickMeasured -> "HP_DAMAGE_TICK"
        is ActiveHpBarBorderPulseObserved -> "HP_BORDER_PULSE"
        is HpBarBorderPulse -> "HP_BORDER_PULSE".takeIf { payload.status == ApertureStatus.PRESENT }
        else -> null
    }

    private fun rawImpactRecipient(article: RealityArticle): ActivePokemonSide? = when (val payload = article.payload) {
        is ActiveHpBarDamageTickMeasured -> payload.damagedSide
        is ActiveHpBarBorderPulseObserved -> payload.damagedBarSide
        is HpBarBorderPulse -> payload.barSide
        else -> null
    }

    private fun acceptCadence(
        article: RealityArticle,
        cadence: CadenceInput,
    ): List<FastMoveCadenceDerivation> {
        val attackingSide = cadence.attackingSide
        val start = max(appearanceStarts[attackingSide] ?: Long.MIN_VALUE, cadenceFloorNanos)
        val end = cadence.eventAtNanos ?: article.monotonicTimeNanos
        // A cadence spanning a witnessed change cannot be assigned to the new combatant.
        if (end == null || end - cadence.intervalNanos < start) return emptyList()
        val state = states[attackingSide] ?: run {
            val waiting = pending.getOrPut(attackingSide) { mutableListOf() }
            if (waiting.none { it.id == article.id }) waiting += article
            // The Timeline retains the complete evidence; this is only the live working set.
            if (waiting.size > MAX_CADENCE_HISTORY) waiting.removeAt(0)
            return emptyList()
        }
        if (state.cadenceEvidence.any { it.article.id == article.id && it.useEvidenceKind == cadence.useEvidenceKind })
            return emptyList()
        state.cadenceEvidence += CadenceEvidence(
            article,
            cadence.intervalNanos,
            cadence.basis,
            cadence.predecessorIds.ifEmpty { listOf(article.id) },
            cadence.useEvidenceKind,
        )
        if (state.cadenceEvidence.size > MAX_CADENCE_HISTORY) state.cadenceEvidence.removeAt(0)

        return publishCadenceIdentity(article, state)
    }

    private fun publishCadenceIdentity(
        article: RealityArticle,
        state: SideState,
    ): List<FastMoveCadenceDerivation> {
        val attackingSide = state.side
        val resolved = resolveCombinedCadence(state)
        val corroboration = resolved?.let { corroborationFor(state, it) }
        val previousLabels = state.corroborationSignature.split('+').filter { it.isNotBlank() }.toSet()
        val currentLabels = corroboration?.labels.orEmpty().toSet()
        // Silence from a corroborating witness is not a contradiction. Keep the
        // established proof instead of downgrading energy whenever one lane pauses.
        if (resolved != null && state.identifiedMove == resolved.move.move &&
            state.selectedBasis != null && previousLabels.containsAll(currentLabels) &&
            state.assumedHigherEnergyTieBreak == resolved.move.assumedHigherEnergyTieBreak
        ) return emptyList()
        val corroborationSignature = (if (state.identifiedMove == resolved?.move?.move) {
            previousLabels + currentLabels
        } else currentLabels).sorted().joinToString("+")
        val newlyIdentified = resolved != null && (
            state.identifiedMove != resolved.move.move ||
                state.selectedBasis != resolved.basis ||
                state.assumedHigherEnergyTieBreak != resolved.move.assumedHigherEnergyTieBreak ||
                state.corroborationSignature != corroborationSignature
            )
        val priorEvidence = if (state.identifiedMove == resolved?.move?.move) state.identificationEvidence else emptyList()
        if (newlyIdentified) {
            val sameMove = state.identifiedMove == resolved!!.move.move
            if (!sameMove) state.identityBasis = "" // Do not attach the old configuration to contradictory evidence.
            state.identityCadenceFamilies =
                (if (sameMove) state.identityCadenceFamilies else emptySet()) + resolved.evidence.map(::family)
            state.identifiedMove = resolved.move.move
            state.selectedBasis = resolved.basis
            state.assumedHigherEnergyTieBreak = resolved.move.assumedHigherEnergyTieBreak
            state.corroborationSignature = corroborationSignature
            state.identificationEvidence = buildList {
                add(state.speciesArticleId)
                addAll(state.appearanceEvidence)
                addAll(priorEvidence)
                addAll(resolved.evidence.flatMap { it.predecessorIds })
                addAll(corroboration?.predecessorIds.orEmpty())
            }.distinct()
        }
        val move = state.identifiedMove ?: return emptyList()
        val selectedBasis = state.selectedBasis ?: return emptyList()
        val selectedCadence = resolved?.evidence.orEmpty().filter { intervalMatchesMove(it.intervalNanos, move) }
        if (selectedCadence.isEmpty()) return emptyList()
        val intervals = selectedCadence.map { it.intervalNanos }
        val median = median(intervals)
        val predecessors = state.identificationEvidence
        val timingError = abs(median - move.turns!! * TURN_NANOS)
        val timingConfidence = ((1f - timingError.toFloat() / max(move.turns * TURN_NANOS, 1L)) +
            0.06f * corroborationSignature.split('+').count { it.isNotBlank() })
            .coerceIn(0f, 0.99f) * if (state.assumedHigherEnergyTieBreak) 0.7f else 1f
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
                    basis = buildString {
                        val authoritative = state.identityBasis.takeIf {
                            it.startsWith("CONFIGURED_PLAYER_TEAM") || it.contains("EFFECTIVENESS_TEXT") ||
                                it.startsWith("ACTIVE_SPECIES_UNIQUE_FAST_MOVE")
                                || it.startsWith("AUDIO_REFERENCE")
                        }
                        if (authoritative != null) append(authoritative.substringBefore("_SUPPORTED_BY_") + "_SUPPORTED_BY_")
                        append("${selectedBasis.name}_CADENCE_AND_REFERENCE_KNOWLEDGE")
                        if (selectedBasis == CadenceBasis.OBSERVED_USES &&
                            resolved.evidence.any { it.useEvidenceKind in IMPACT_USE_EVIDENCE_KINDS }) {
                            append("_CONSISTENT_IMPACT_STREAM")
                        }
                        if (state.assumedHigherEnergyTieBreak) append("_HIGHER_ENERGY_TIE_BREAK_ASSUMPTION")
                        if (corroborationSignature.isNotBlank()) append("_CORROBORATED_BY_$corroborationSignature")
                    }.also { state.identityBasis = it }
                ),
                predecessorIds = predecessors,
                confidence = timingConfidence.also { state.identityConfidence = it },
                // Late impact testimony must not date the conclusion before
                // a later motion sample that was needed to resolve it.
                observedArticle = (resolved.evidence.flatMap { listOf(it.article) + it.supportingArticles } + article)
                    .maxBy { it.monotonicTimeNanos ?: Long.MIN_VALUE }
            )
        }
        return output
    }

    private fun acceptEffectiveness(
        article: RealityArticle,
        witnessed: FastMoveEffectivenessWitnessed
    ): List<FastMoveCadenceDerivation> {
        if ((article.monotonicTimeNanos ?: Long.MAX_VALUE) < cadenceFloorNanos) return emptyList()
        if (article.monotonicTimeNanos?.let(chargedEffectivenessWindow::contains) == true) return emptyList()
        val attackingSides = witnessed.damagedSide?.let { listOf(it.opposite()) }
            ?: alignedUses(article, witnessed)
                .map { (it.payload as FastMoveUseObserved).attackingSide }
                .distinct()
        val attributableSides = attackingSides.filter { side ->
            val attacker = states[side] ?: return@filter false
            val defender = states[side.opposite()] ?: return@filter false
            // Incomplete reference types are absence of evidence, not positive
            // effectiveness support or evidence for assigning unsided text.
            if (defender.typeOptions.isEmpty() || defender.typeOptions.any { it.isEmpty() }) return@filter false
            attacker.possibleMoves.any { effectivenessCompatible(it.type, defender.typeOptions, witnessed.effectiveness) }
        }
        // Unsided text during simultaneous hits cannot narrow both combatants.
        if (witnessed.damagedSide == null && attributableSides.size != 1) return emptyList()
        val candidates = attributableSides.flatMap { attackingSide ->
            val attacker = states[attackingSide] ?: return@flatMap emptyList()
            val defender = states[attackingSide.opposite()] ?: return@flatMap emptyList()
            val observedAt = article.monotonicTimeNanos
            if (observedAt != null &&
                (observedAt < attacker.activeSinceNanos || observedAt < defender.activeSinceNanos)
            ) {
                // OCR may finish after a switch. Never apply an older phrase to
                // the newer pair merely because its article arrived later.
                return@flatMap emptyList()
            }
            if (attacker.effectivenessEvidence.none { it.article.id == article.id }) {
                // Keep the first and newest read of a run, so repeated text
                // cannot evict the constraints learned against earlier defenders.
                val lastTwo = attacker.effectivenessEvidence.takeLast(2)
                if (lastTwo.size == 2 && lastTwo.all {
                    it.effectiveness == witnessed.effectiveness && it.defenderTypeOptions == defender.typeOptions
                }) attacker.effectivenessEvidence.removeAt(attacker.effectivenessEvidence.lastIndex)
                attacker.effectivenessEvidence += EffectivenessEvidence(
                    article = article,
                    effectiveness = witnessed.effectiveness,
                    defenderTypeOptions = defender.typeOptions,
                    defenderArticleId = defender.speciesArticleId,
                )
                if (attacker.effectivenessEvidence.size > MAX_EFFECTIVENESS_HISTORY) {
                    attacker.effectivenessEvidence.removeAt(0)
                }
            }
            candidateMovesFromEffectiveness(attacker)
                .filter { move -> effectivenessCompatible(move.type, defender.typeOptions, witnessed.effectiveness) }
                .map { move -> EffectivenessMoveCandidate(attackingSide, move, attacker, defender) }
        }
        val candidate = candidates.singleOrNull() ?: return emptyList()
        if (candidate.attacker.identifiedMove == candidate.move &&
            !candidate.attacker.assumedHigherEnergyTieBreak &&
            (candidate.attacker.identityBasis.contains("EFFECTIVENESS_TEXT") ||
                candidate.attacker.identityBasis.startsWith("CONFIGURED_PLAYER_TEAM"))
        ) return emptyList()

        candidate.attacker.identifiedMove = candidate.move
        candidate.attacker.selectedBasis = null
        candidate.attacker.assumedHigherEnergyTieBreak = false
        candidate.attacker.identificationEvidence = (listOf(
            candidate.attacker.speciesArticleId, candidate.defender.speciesArticleId,
        ) + candidate.attacker.appearanceEvidence + candidate.defender.appearanceEvidence +
            candidate.attacker.effectivenessEvidence.flatMap { listOf(it.article.id, it.defenderArticleId) }).distinct()
        candidate.attacker.identityBasis = if (witnessed.damagedSide != null) {
            "SIDE_LOCATED_EFFECTIVENESS_TEXT_AND_ACTIVE_SPECIES_REFERENCE_KNOWLEDGE"
        } else {
            "EFFECTIVENESS_TEXT_AND_ACTIVE_SPECIES_REFERENCE_KNOWLEDGE_UNIQUE_DIRECTION"
        }
        candidate.attacker.identityConfidence = ((article.confidence ?: 0.85f) *
            if (witnessed.damagedSide == null) 0.9f else 1f).coerceIn(0f, 1f)

        return listOf(
            FastMoveCadenceDerivation(
                payload = FastMoveIdentified(
                    side = candidate.attackingSide,
                    speciesName = candidate.attacker.speciesName,
                    moveName = candidate.move.name,
                    moveDurationNanos = candidate.move.turns!! * TURN_NANOS,
                    observedMedianIntervalNanos = null,
                    cadenceSampleCount = 0,
                    basis = candidate.attacker.identityBasis,
                ),
                predecessorIds = candidate.attacker.identificationEvidence,
                confidence = candidate.attacker.identityConfidence,
                observedArticle = article
            )
        )
    }

    /**
     * Returns the current non-destructive candidate set for the live HUD.
     * One cadence interval may narrow presentation immediately; formal identity
     * and energy attribution still require the stricter evidence rules above.
     */
    fun candidateSnapshot(side: ActivePokemonSide): FastMoveCandidateSnapshot? {
        val state = states[side] ?: return null
        val allMoves = state.possibleMoves
        if (allMoves.isEmpty()) return FastMoveCandidateSnapshot(
            side = side,
            speciesName = state.speciesName,
            remainingMoveNames = emptyList(),
            eliminatedMoveNames = emptyList(),
            evidenceKinds = emptyList(),
            identifiedMoveName = null,
        )

        val identified = state.identifiedMove
        // An energy tie-break must not hide equally compatible moves or suppress
        // their audio preference. It still remains a forecast hypothesis.
        val remaining = if (identified != null && !state.assumedHigherEnergyTieBreak) {
            listOf(identified)
        } else {
            candidateMovesFromEvidence(state)
        }
        val remainingKeys = remaining.map { normalize(it.name) }.toSet()
        val preference = if ((identified == null || state.assumedHigherEnergyTieBreak) &&
            audioEntryCues.isEmpty() && audioIdentityGate.allows(latestEvidenceAtNanos)) {
            audioPreferences.preference(side, audioPreferenceContext(), latestEvidenceAtNanos)
                ?.takeIf { normalize(it.moveName) in remainingKeys }
        } else null
        return FastMoveCandidateSnapshot(
            side = side,
            speciesName = state.speciesName,
            remainingMoveNames = remaining.map { it.name },
            eliminatedMoveNames = allMoves.filterNot { normalize(it.name) in remainingKeys }.map { it.name },
            evidenceKinds = buildList {
                if (state.cadenceEvidence.isNotEmpty()) add("CADENCE")
                if (state.cadenceEvidence.any { it.basis == CadenceBasis.AUDIO_PROFILE } ||
                    state.identityBasis.startsWith("AUDIO_REFERENCE")) add("AUDIO")
                if (state.effectivenessEvidence.isNotEmpty()) add("EFFECTIVENESS")
            },
            identifiedMoveName = identified?.name,
            audioPreference = preference,
        )
    }

    private fun audioPreferenceContext() = states.map { (side, state) ->
        AudioPreferenceCombatant(side, state.speciesName, state.audioActiveSinceNanos, state.possibleMoves.map { it.name },
            state.identifiedMove?.name?.takeIf { state.identityBasis.startsWith("CONFIGURED_PLAYER_TEAM") })
    }

    private enum class EvidenceFamily { IMPACT, MOTION, AUDIO, VISUAL }

    private data class CadenceVote(
        val basis: CadenceBasis,
        val family: EvidenceFamily,
        /** Includes faster moves whose intermediate impacts may have been missed. */
        val candidates: List<Move>,
        /** Only direct cycle matches can establish identity or corroborate energy. */
        val directCandidates: List<Move>,
        val evidence: List<CadenceEvidence>,
    )

    private fun family(evidence: CadenceEvidence): EvidenceFamily = when (evidence.basis) {
        CadenceBasis.BORDER_PULSE -> EvidenceFamily.IMPACT
        CadenceBasis.BAR_MOTION -> EvidenceFamily.MOTION
        CadenceBasis.AUDIO_PROFILE -> EvidenceFamily.AUDIO
        CadenceBasis.RECIPIENT_VISUAL_ARTIFACT -> EvidenceFamily.VISUAL
        CadenceBasis.OBSERVED_USES -> {
            val kinds = evidence.useEvidenceKind?.let(::listOf)
                ?: (evidence.article.payload as? FastMoveUseObserved)?.evidenceKinds.orEmpty()
            when {
                kinds.any { it == "HP_BORDER_PULSE" || it == "HP_DAMAGE_TICK" } -> EvidenceFamily.IMPACT
                "HP_BAR_MOTION_COMPLETION" in kinds -> EvidenceFamily.MOTION
                else -> EvidenceFamily.VISUAL
            }
        }
    }

    private data class ImpactEndpoint(val at: Long, val predecessorIds: List<ArticleId>, val article: RealityArticle)

    private fun useBelongsTo(state: SideState, article: RealityArticle, use: FastMoveUseObserved): Boolean {
        val at = article.monotonicTimeNanos ?: return false
        if (use.attackingSide != state.side || at < maxOf(state.appearanceBoundaryNanos ?: Long.MIN_VALUE, cadenceFloorNanos)) return false
        val name = use.attackerSpeciesName ?: return true
        if (normalize(name) == normalize(state.speciesName)) return true
        // A bare game label can corroborate its resolved form, but two distinct
        // qualified forms or another species cannot borrow each other's hits.
        return ('(' !in name || '(' !in state.speciesName) &&
            normalize(name.substringBefore(" (")) == normalize(state.speciesName.substringBefore(" ("))
    }

    /**
     * A victim can bob with every incoming hit. Motion is useful for timing only
     * when this side independently dealt damage at both ends of that cycle.
     * Bound the proof to source time and this appearance, not arrival order or a
     * historical attack elsewhere in the match. Raw measurements stay retained.
     */
    private fun motionImpactEndpoints(state: SideState): List<ImpactEndpoint> {
        val floor = maxOf(state.appearanceBoundaryNanos ?: Long.MIN_VALUE, cadenceFloorNanos)
        return buildList {
            state.cadenceEvidence.filter { family(it) == EvidenceFamily.IMPACT }.forEach { evidence ->
                val end = evidence.article.monotonicTimeNanos ?: return@forEach
                add(ImpactEndpoint(end, evidence.predecessorIds, evidence.article))
                add(ImpactEndpoint(end - evidence.intervalNanos, evidence.predecessorIds, evidence.article))
            }
            recentUses.forEach { article ->
                val use = article.payload as FastMoveUseObserved
                val at = article.monotonicTimeNanos ?: return@forEach
                if (useBelongsTo(state, article, use) && use.evidenceKinds.any(IMPACT_USE_EVIDENCE_KINDS::contains)) {
                    add(ImpactEndpoint(at, listOf(article.id), article))
                }
            }
        }.filter { it.at >= floor }.distinctBy { it.at }
    }

    private fun supportedMotion(evidence: CadenceEvidence, impacts: List<ImpactEndpoint>): CadenceEvidence? {
        val end = evidence.article.monotonicTimeNanos ?: return null
        val start = end - evidence.intervalNanos
        val starts = impacts.filter { abs(it.at - start) <= MOTION_IMPACT_ALIGNMENT_NANOS }
        val ends = impacts.filter { abs(it.at - end) <= MOTION_IMPACT_ALIGNMENT_NANOS }
        var best: Pair<ImpactEndpoint, ImpactEndpoint>? = null
        var bestDistance = Long.MAX_VALUE
        for (first in starts) for (last in ends) {
            // One hit (or its nearly coincident fused copy) cannot prove two endpoints.
            val span = last.at - first.at
            if (span < MIN_USE_INTERVAL_NANOS || !intervalMatchesExpected(span, evidence.intervalNanos)) continue
            val distance = abs(first.at - start) + abs(last.at - end)
            if (distance < bestDistance) {
                best = first to last
                bestDistance = distance
            }
        }
        val pair = best ?: return null
        return evidence.copy(
            predecessorIds = (evidence.predecessorIds + pair.first.predecessorIds + pair.second.predecessorIds).distinct(),
            supportingArticles = listOf(pair.first.article, pair.second.article).distinctBy { it.id },
            supportingImpactSpan = pair.first.at to pair.second.at,
        )
    }

    /** Each measurement family gets one vote, even when a fused use repeats it. */
    private fun cadenceVotes(state: SideState): List<CadenceVote> {
        val eligible = candidateMovesForCadence(state)
        val latestAt = state.cadenceEvidence.maxOfOrNull { it.article.monotonicTimeNanos ?: 0L } ?: 0L
        val impacts = motionImpactEndpoints(state)
        val votes = state.cadenceEvidence.filter {
            latestAt - (it.article.monotonicTimeNanos ?: latestAt) <= CADENCE_WINDOW_NANOS
        }.mapNotNull { evidence ->
            if (family(evidence) == EvidenceFamily.MOTION) supportedMotion(evidence, impacts) else evidence
        }.groupBy { Triple(it.basis, family(it), it.useEvidenceKind) }.mapNotNull { (key, samples) ->
            // Two reaction cycles cannot reuse the same observed hit pair as
            // two independently supported cadence samples.
            val evidence = samples.asReversed()
                .distinctBy { it.supportingImpactSpan ?: it.article.id }
                .asReversed().sortedBy { it.article.monotonicTimeNanos }
            val intervals = evidence.map { it.intervalNanos }
            val recent = evidence.takeLast(REQUIRED_INTERVALS)
            val matching = eligible.filter { move -> recent.all { intervalMatchesMove(it.intervalNanos, move) } }
            val supporting = when {
                matching.isNotEmpty() -> recent
                else -> resolveMove(eligible, intervals)?.supportingIndices?.map(evidence::get)
                    ?: listOf(evidence.last())
            }
            val directCandidates = eligible.filter { move -> supporting.all { intervalMatchesMove(it.intervalNanos, move) } }
            val candidates = eligible.filter { move -> supporting.all {
                intervalMatchesMove(it.intervalNanos, move) ||
                    (key.second == EvidenceFamily.IMPACT && intervalMayContainMissedImpacts(it.intervalNanos, move))
            } }
            candidates.takeIf { it.isNotEmpty() }?.let {
                CadenceVote(key.first, key.second, it, directCandidates, supporting)
            }
        }
        // Recipient-region transients still lack a validated direction/type
        // catalogue. Keep their existing fallback hypothesis, but do not let
        // them overrule measured hits or supply independent energy confirmation.
        return votes.filter { it.family != EvidenceFamily.VISUAL }.ifEmpty { votes }
    }

    private fun rankedCadenceCandidates(state: SideState, votes: List<CadenceVote>): List<Move> {
        if (votes.isEmpty()) return candidateMovesForCadence(state)
        val scores = candidateMovesForCadence(state).associateWith { move ->
            val families = votes.filter { move in it.candidates }.groupBy { it.family }
            // Counting the number of samples, then the independent families,
            // lets two different one-interval measurements resolve together.
            val count = families.values.sumOf { v -> v.maxOf { it.evidence.size.coerceAtMost(REQUIRED_INTERVALS) } }
            count to families.size
        }
        val best = scores.values.maxWithOrNull(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
        return scores.filterValues { it == best }.keys.toList()
    }

    private fun resolveCombinedCadence(state: SideState): ResolvedCadence? {
        val votes = cadenceVotes(state)
        val candidates = rankedCadenceCandidates(state, votes)
        // Different durations with equal support remain unresolved. An energy
        // preference is only allowed within one measured duration.
        if (candidates.map { it.turns }.distinct().size != 1) return null
        // Compatible gaps keep a move in contention, but do not testify to its
        // duration or create independent support for an energy award.
        val matchingVotes = votes.filter { vote -> candidates.any { it in vote.directCandidates } }
        val families = matchingVotes.groupBy { it.family }
        val enoughSamples = matchingVotes.any { it.evidence.size >= REQUIRED_INTERVALS }
        if (!enoughSamples && families.size < 2) return null
        // Equal timing cannot refute an explicitly equipped move. Keep that
        // hypothesis until type/effectiveness/audio or incompatible timing does.
        val configuredCandidate = state.identifiedMove?.takeIf {
            state.identityBasis.startsWith("CONFIGURED_PLAYER_TEAM") && it in candidates
        }
        val chosen = configuredCandidate?.let { MoveResolution(it, false, emptySet()) }
            ?: chooseMove(candidates, emptySet()) ?: return null
        val supporting = matchingVotes.filter { chosen.move in it.directCandidates }
            .groupBy { it.family }.values.map { group ->
                group.maxWith(compareBy<CadenceVote> { it.evidence.size }.thenBy { -it.basis.ordinal })
            }
        // Recipient effects have no validated direction/type catalogue yet.
        // In the recorded idle Camerupt match, the opponent's effects supplied
        // a convincing Rock Smash rhythm on our side without a single hit by us.
        // Keep that raw rhythm and its provisional candidates, never certify it
        // as a move identity or a contradiction of the player's saved moveset.
        if (supporting.all { it.family == EvidenceFamily.VISUAL }) return null
        if (state.identifiedMove != chosen.move && state.identityBasis.startsWith("CONFIGURED_PLAYER_TEAM") &&
            supporting.none { it.family == EvidenceFamily.IMPACT && it.evidence.size >= REQUIRED_INTERVALS } &&
            supporting.count { it.family != EvidenceFamily.VISUAL } < 2
        ) return null
        val establishedTurns = state.identifiedMove?.turns
        val proposedTurns = chosen.move.turns
        if (state.identifiedMove != chosen.move && !state.assumedHigherEnergyTieBreak &&
            EvidenceFamily.IMPACT in state.identityCadenceFamilies &&
            supporting.none { it.family == EvidenceFamily.IMPACT && it.evidence.size >= REQUIRED_INTERVALS } &&
            supporting.count { it.family != EvidenceFamily.VISUAL } < 2
        ) {
            // A camera bob after Earth Power is not evidence that Camerupt's
            // previously measured Incinerate became Ember. Keep the raw motion,
            // but require renewed impact evidence or independent corroboration
            // to replace an identity established from actual hit intervals.
            return null
        }
        if (!state.assumedHigherEnergyTieBreak && establishedTurns != null && establishedTurns > 0 &&
            proposedTurns != null && proposedTurns > establishedTurns && proposedTurns % establishedTurns == 0
        ) {
            // Missing every other Water Gun hit produces Powder Snow's spacing.
            // Those larger gaps do not contradict the previously observed faster
            // cadence, even after a charge clears the recent interval window.
            // Require repeated positive evidence from independent families before
            // changing to the slower harmonic. Raw pulses and their fused uses
            // still count as one family; unvalidated visual effects cannot decide it.
            // Reference audio and repeated effectiveness can also correct identity
            // through their own paths, without manufacturing the missing attacks.
            val repeatedFamilies = supporting.filter {
                it.family != EvidenceFamily.VISUAL && it.evidence.size >= REQUIRED_INTERVALS
            }.map { it.family }.distinct()
            if (repeatedFamilies.size < 2) return null
        }
        val primary = supporting.maxWithOrNull(
            compareBy<CadenceVote> { it.evidence.size }.thenBy { -it.basis.ordinal }
        ) ?: return null
        return ResolvedCadence(chosen, primary.basis, supporting.flatMap { it.evidence })
    }

    private fun candidateMovesFromEvidence(state: SideState): List<Move> =
        rankedCadenceCandidates(state, cadenceVotes(state))

    /** Fragmented HP ticks cannot restore Tackle after repeated Spark spectra established identity. */
    private fun candidateMovesForCadence(state: SideState): List<Move> {
        val eligible = candidateMovesFromEffectiveness(state)
        return if (state.identityBasis.startsWith("AUDIO_REFERENCE")) eligible.filter { it == state.identifiedMove }
            else eligible
    }

    /**
     * Keep compatible constraints across different defenders. Silence cannot
     * restore a ruled-out move. A conflicting read must repeat in a later hit
     * window before replacing the constraints; the Timeline retains both.
     */
    private fun candidateMovesFromEffectiveness(state: SideState): List<Move> {
        var remaining = state.possibleMoves
        var conflicting: EffectivenessEvidence? = null
        state.effectivenessEvidence.forEach { evidence ->
            val matches = state.possibleMoves.filter { move ->
                effectivenessCompatible(move.type, evidence.defenderTypeOptions, evidence.effectiveness)
            }
            if (matches.isEmpty()) return@forEach
            val intersection = remaining.filter { it in matches }
            if (intersection.isNotEmpty()) {
                remaining = intersection
                conflicting = null
            } else {
                val previous = conflicting
                val previousAt = previous?.article?.monotonicTimeNanos
                val at = evidence.article.monotonicTimeNanos
                if (previous != null && previous.effectiveness == evidence.effectiveness &&
                    previous.defenderTypeOptions == evidence.defenderTypeOptions && previousAt != null && at != null &&
                    at - previousAt >= EFFECTIVENESS_RECHECK_NANOS
                ) {
                    remaining = matches
                    conflicting = null
                } else {
                    conflicting = evidence
                }
            }
        }
        return remaining
    }

    private fun corroborationFor(state: SideState, resolved: ResolvedCadence): Corroboration {
        val primary = resolved.evidence.first { it.basis == resolved.basis }
        val primaryFamily = family(primary)
        val labels = linkedSetOf<String>()
        val predecessors = linkedSetOf<ArticleId>()
        resolved.evidence.filter { family(it) != primaryFamily }.forEach {
            labels += it.basis.name
            predecessors.addAll(it.predecessorIds)
        }
        state.effectivenessEvidence.filter { evidence ->
            effectivenessCompatible(resolved.move.move.type, evidence.defenderTypeOptions, evidence.effectiveness)
        }.forEach { evidence ->
            labels += "EFFECTIVENESS"
            predecessors += evidence.article.id
            predecessors += evidence.defenderArticleId
        }
        return Corroboration(labels.toList().sorted(), predecessors.toList())
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

    /** Keep a move when any still-possible form could explain the witnessed text. */
    private fun effectivenessCompatible(
        attackType: PokemonType,
        defenderTypeOptions: List<List<PokemonType>>,
        witnessed: FastMoveEffectiveness,
    ): Boolean = defenderTypeOptions.isEmpty() || defenderTypeOptions.any { types ->
        // Missing type knowledge cannot rule a move out. With complete knowledge,
        // ambiguity is removed only when every compatible form contradicts it.
        types.isEmpty() || effectivenessMatches(attackType, types, witnessed)
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

    private fun intervalMayContainMissedImpacts(intervalNanos: Long, move: Move): Boolean {
        val expected = (move.turns ?: return false) * TURN_NANOS
        if (expected <= 0L) return false
        val cycles = (intervalNanos.toDouble() / expected).roundToLong()
        // A regular 1.5-second damage gap can contain three 0.5-second attacks.
        // This is compatibility only: neither the cycles nor absent hits are recorded.
        return cycles >= 2 && intervalMatchesExpected(intervalNanos, expected * cycles)
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
        val basis: CadenceBasis,
        val predecessorIds: List<ArticleId> = emptyList(),
        val eventAtNanos: Long? = null,
        val useEvidenceKind: String? = null,
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
    private data class Corroboration(
        val labels: List<String>,
        val predecessorIds: List<ArticleId>,
    )
    private data class EffectivenessMoveCandidate(
        val attackingSide: ActivePokemonSide,
        val move: Move,
        val attacker: SideState,
        val defender: SideState
    )

    private companion object {
        const val AUDIO_ENTRY_WINDOW_NANOS = 3_000_000_000L
        const val TURN_NANOS = 500_000_000L
        // Three observed attacks provide two elapsed intervals. This meets the
        // 1.5-second latency target for 500 ms moves without using frame count as time.
        const val REQUIRED_INTERVALS = 2
        const val CADENCE_WINDOW_NANOS = 8_000_000_000L
        const val EFFECTIVENESS_RECHECK_NANOS = 500_000_000L
        const val CLUSTER_HISTORY = 12
        const val MAX_CADENCE_HISTORY = 64
        const val MAX_EFFECTIVENESS_HISTORY = 16
        const val MAX_USE_HISTORY = 16
        const val MAX_RAW_IMPACT_HISTORY = 256
        const val EFFECTIVENESS_USE_ALIGNMENT_NANOS = 500_000_000L
        const val MOTION_IMPACT_ALIGNMENT_NANOS = 450_000_000L
        const val AUDIO_IMPACT_ALIGNMENT_NANOS = 450_000_000L
        const val MIN_USE_INTERVAL_NANOS = 300_000_000L
        const val MIN_TOLERANCE_NANOS = 160_000_000L
        const val RELATIVE_TOLERANCE = 0.15
        val IMPACT_USE_EVIDENCE_KINDS = setOf("HP_DAMAGE_TICK", "HP_BORDER_PULSE")
        val DIRECT_USE_EVIDENCE_KINDS = setOf(
            "HP_BORDER_PULSE",
            "HP_DAMAGE_TICK",
            "HP_BAR_MOTION_COMPLETION",
            "RECIPIENT_VISUAL_ARTIFACT",
        )
    }
}
