package com.example.overdex.battle.observation

import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.data.observation.SpeciesNameRecognizer
import com.example.overdex.data.observation.OcrPriority
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/** Accepts Team Select only after independent, freshly observed apertures agree. */
internal class PersistedTeamSelectPartyWitness(
    private val store: FileCropArtifactStore,
    private val readText: suspend (android.graphics.Bitmap, OcrPriority) -> String = { bitmap, priority ->
        AnnouncementRecognizer.recognize(bitmap, priority).value.orEmpty()
    },
) : Observer {
    override val observerId = ObserverId("TEAM_SELECT_PARTY_WITNESS", ObservationSource.SCREEN_CAPTURE)
    override val name = "Team Select Party Witness"
    private var scope: CoroutineScope? = null
    override fun start(match: Match) {
        if (scope != null) return
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { s ->
            val accepted = AtomicBoolean(false)
            val evidenceWindow = TeamSelectSurfaceEvidenceWindow()
            val evidenceLock = Mutex()
            val supported = listOf(
                TeamSelectCropContracts.leagueBadge.cropName,
                TeamSelectCropContracts.useThisParty.cropName,
                TeamSelectCropContracts.playerRosterSlot1.cropName,
                TeamSelectCropContracts.playerRosterSlot2.cropName,
                TeamSelectCropContracts.playerRosterSlot3.cropName,
                TeamSelectCropContracts.leagueText.cropName,
                TeamSelectCropContracts.restrictions.cropName,
            )
            // A clipped league/restriction row must not hold the clear button
            // label behind it. Each aperture keeps only its newest pending read;
            // the complete captured evidence remains in the Reality Timeline.
            // OCR itself still uses the one shared, bounded-priority model lane.
            supported.forEach { cropName -> s.launch {
                match.articles.filter { article ->
                    !accepted.get() &&
                        (article.monotonicTimeNanos ?: Long.MIN_VALUE) >= match.observationAttemptStartedAtNanos &&
                        (article.payload as? CropCaptured)?.cropProvenance?.cropName == cropName
                }.conflate().collect { a ->
                    if (accepted.get()) return@collect
                    val observedAt = a.monotonicTimeNanos ?: return@collect
                    if (observedAt < match.observationAttemptStartedAtNanos) return@collect
                    val crop = a.payload as CropCaptured
                    val bitmap = store.loadVerifiedPng(crop.artifact, crop.cropProvenance) ?: return@collect
                    var signal: TeamSelectSignal? = null
                    var cardSlot: Int? = null
                    try {
                        when (cropName) {
                            TeamSelectCropContracts.leagueText.cropName -> {
                                val text = readText(bitmap, OcrPriority.NORMAL).uppercase()
                                if ("LEAGUE" in text) signal = TeamSelectSignal(a.id.value, observedAt, 0.96f, a.perceivedAt)
                            }
                            TeamSelectCropContracts.leagueBadge.cropName -> {
                                TeamSelectLeagueShieldClassifier.classify(bitmap)?.let {
                                    signal = TeamSelectSignal(a.id.value, observedAt, it.confidence, a.perceivedAt)
                                }
                            }
                            TeamSelectCropContracts.playerRosterSlot1.cropName,
                            TeamSelectCropContracts.playerRosterSlot2.cropName,
                            TeamSelectCropContracts.playerRosterSlot3.cropName -> {
                                cardSlot = when (cropName) {
                                    TeamSelectCropContracts.playerRosterSlot1.cropName -> 1
                                    TeamSelectCropContracts.playerRosterSlot2.cropName -> 2
                                    else -> 3
                                }
                                TeamSelectSurfaceSignalDetector.partyCardConfidence(bitmap)?.let {
                                    signal = TeamSelectSignal(a.id.value, observedAt, it, a.perceivedAt)
                                }
                            }
                            TeamSelectCropContracts.restrictions.cropName -> {
                                val text = readText(bitmap, OcrPriority.NORMAL)
                                if (TeamSelectSurfaceSignalDetector.isRestrictionText(text)) {
                                    signal = TeamSelectSignal(a.id.value, observedAt, 0.93f, a.perceivedAt)
                                }
                            }
                            TeamSelectCropContracts.useThisParty.cropName -> {
                                val colorConfidence = TeamSelectSurfaceSignalDetector.usePartyControlConfidence(bitmap)
                                val text = readText(bitmap, OcrPriority.TRANSIENT)
                                val textAccepted = TeamSelectSurfaceSignalDetector.isUsePartyText(text)
                                // Green terrain is not the Use This Party button.
                                // Preserve the crop, but require its label to accept it.
                                val confidence = if (textAccepted) maxOf(colorConfidence ?: 0f, 0.95f) else null
                                if (confidence != null) signal = TeamSelectSignal(a.id.value, observedAt, confidence, a.perceivedAt)
                            }
                        }
                    } finally { bitmap.recycle() }
                    val measured = signal ?: return@collect
                    evidenceLock.withLock {
                        currentCoroutineContext().ensureActive()
                        if (accepted.get() || observedAt < match.observationAttemptStartedAtNanos) return@withLock
                        val result = evidenceWindow.observe(observedAt) { evidence ->
                            when (cropName) {
                                TeamSelectCropContracts.leagueText.cropName -> evidence.copy(leagueText = measured)
                                TeamSelectCropContracts.leagueBadge.cropName -> evidence.copy(leagueBadge = measured)
                                TeamSelectCropContracts.restrictions.cropName -> evidence.copy(restrictions = measured)
                                TeamSelectCropContracts.useThisParty.cropName -> evidence.copy(useThisParty = measured)
                                else -> evidence.copy(partyCards = evidence.partyCards + (requireNotNull(cardSlot) to measured))
                            }
                        } ?: return@withLock
                        // Independent readers can finish in a different order. The
                        // surface cannot precede the newest anchor it depends on.
                        accepted.set(true)
                        match.custody.submitTestimony(
                            SourceId(observerId.id),
                            TeamSelectPartyWitnessed,
                            result.latestSource.perceivedAtMillis,
                            result.confidence,
                            result.evidenceArticleIds,
                            result.latestSource.observedAtNanos
                        )
                    }
                }
            } }
        }
    }
    override fun stop() { scope?.cancel(); scope = null }
}

/** Classifies the standard league while retaining the shield crop as its evidence. */
class PersistedTeamSelectLeagueWitness(private val store: FileCropArtifactStore) : Observer {
    override val observerId = ObserverId("TEAM_SELECT_LEAGUE_WITNESS", ObservationSource.SCREEN_CAPTURE)
    override val name = "Team Select League Witness"
    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                var accepted = false
                var surfaceAccepted = false
                var pendingBadge: com.example.overdex.battle.reality.RealityArticle? = null
                match.articles.collect { article ->
                    if (accepted) return@collect
                    if ((article.monotonicTimeNanos ?: Long.MIN_VALUE) < match.observationAttemptStartedAtNanos) return@collect
                    if (article.payload is TeamSelectPartyWitnessed) surfaceAccepted = true
                    val incoming = article.payload as? CropCaptured
                    if (incoming?.cropProvenance?.cropName == TeamSelectCropContracts.leagueBadge.cropName) {
                        pendingBadge = article
                    } else if (article.payload !is TeamSelectPartyWitnessed) return@collect
                    if (!surfaceAccepted) return@collect
                    val badgeArticle = pendingBadge ?: return@collect
                    val crop = badgeArticle.payload as CropCaptured
                    if ((article.monotonicTimeNanos ?: return@collect) -
                        (badgeArticle.monotonicTimeNanos ?: return@collect) > 2_000_000_000L) return@collect
                    val bitmap = store.loadVerifiedPng(crop.artifact, crop.cropProvenance) ?: return@collect
                    val league = try {
                        TeamSelectLeagueShieldClassifier.classify(bitmap)
                    } finally {
                        bitmap.recycle()
                    } ?: return@collect
                    val monotonic = badgeArticle.monotonicTimeNanos ?: return@collect
                    accepted = true
                    match.custody.submitTestimony(
                        SourceId(observerId.id),
                        TeamSelectLeagueWitnessed(league.timelineName),
                        badgeArticle.perceivedAt,
                        league.confidence,
                        listOf(badgeArticle.id.value),
                        monotonic
                    )
                }
            }
        }
    }

    override fun stop() { scope?.cancel(); scope = null }
}

/** One witness, one Player roster-slot signal, after Team Select has been accepted. */
class PersistedPlayerTeamRosterSlotWitness(
    private val store: FileCropArtifactStore,
    private val slot: Int,
    private val cropName: String
) : Observer {
    override val observerId = ObserverId("TEAM_SELECT_PLAYER_SLOT_${slot}_OCR_WITNESS", ObservationSource.SCREEN_CAPTURE)
    override val name = "Team Select Player Slot $slot OCR Witness"
    private var scope: CoroutineScope? = null
    override fun start(match: Match) {
        if (scope != null) return
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { s -> s.launch {
            var surfaceAccepted = false
            var accepted = false
            var pendingCrop: com.example.overdex.battle.reality.RealityArticle? = null
            val knownNames = match.pokemonKnowledge.getAllSpeciesNames()
            match.articles.collect { a ->
                if (accepted) return@collect
                if ((a.monotonicTimeNanos ?: Long.MIN_VALUE) < match.observationAttemptStartedAtNanos) return@collect
                if (a.payload is TeamSelectPartyWitnessed) {
                    surfaceAccepted = true
                } else {
                    val incoming = a.payload as? CropCaptured ?: return@collect
                    if (incoming.cropProvenance.cropName != cropName) return@collect
                    pendingCrop = a
                }
                // These names cannot establish the surface. Retain the newest
                // source, then spend OCR time once the party check has succeeded.
                if (!surfaceAccepted) return@collect
                val source = pendingCrop ?: return@collect
                val crop = source.payload as CropCaptured
                val bitmap = store.loadVerifiedPng(crop.artifact, crop.cropProvenance) ?: return@collect
                val readings = try {
                    SpeciesNameRecognizer.recognizeCandidates(bitmap) { rawText ->
                        FastOverlaySpeciesResolver.resolveDetailed(rawText, knownNames) != null
                    }
                } finally { bitmap.recycle() }
                val resolution = readings.firstNotNullOfOrNull { reading ->
                    FastOverlaySpeciesResolver.resolveDetailed(reading.text, knownNames)
                } ?: return@collect
                val species = match.pokemonKnowledge.getPokemonByName(resolution.speciesName) ?: return@collect
                val candidate = PendingRosterSlot(
                    SourceId(observerId.id),
                    PlayerTeamRosterSlotWitnessed(slot, species.name, species.id),
                    source.perceivedAt,
                    resolution.confidence,
                    listOf(source.id.value),
                    source.monotonicTimeNanos ?: return@collect
                )
                currentCoroutineContext().ensureActive()
                if (candidate.monotonicTimeNanos < match.observationAttemptStartedAtNanos) return@collect
                accepted = true
                submitRosterCandidate(match, candidate)
            }
        } }
    }
    override fun stop() { scope?.cancel(); scope = null }
}

/**
 * Matches the actual Pokémon GO artwork in one Team Select card. This remains
 * independent of the text witness because the text may be a nickname or only
 * symbols while the card still contains stable species artwork.
 */
class PersistedPlayerTeamRosterSpriteWitness(
    private val store: FileCropArtifactStore,
    private val matcher: TeamSelectSpriteMatcher,
    private val slot: Int,
    private val cropName: String,
) : Observer {
    override val observerId = ObserverId("TEAM_SELECT_PLAYER_SLOT_${slot}_VISUAL_WITNESS", ObservationSource.SCREEN_CAPTURE)
    override val name = "Team Select Player Slot $slot Visual Witness"
    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                var surfaceAccepted = false
                var accepted = false
                var pending: PendingRosterSlot? = null
                match.articles.collect { article ->
                    if ((article.monotonicTimeNanos ?: Long.MIN_VALUE) < match.observationAttemptStartedAtNanos) return@collect
                    if (article.payload is TeamSelectPartyWitnessed) {
                        surfaceAccepted = true
                        pending?.let { candidate ->
                            accepted = true
                            submitRosterCandidate(match, candidate)
                        }
                        return@collect
                    }
                    if (accepted) return@collect
                    val crop = article.payload as? CropCaptured ?: return@collect
                    if (crop.cropProvenance.cropName != cropName) return@collect
                    val bitmap = store.loadVerifiedPng(crop.artifact, crop.cropProvenance) ?: return@collect
                    val visualMatch = try {
                        matcher.match(bitmap)
                    } finally {
                        bitmap.recycle()
                    } ?: return@collect
                    val species = match.pokemonKnowledge.getPokemonBySpriteVariant(
                        visualMatch.speciesId, visualMatch.variant
                    ) ?: return@collect
                    val candidate = PendingRosterSlot(
                        sourceId = SourceId(observerId.id),
                        payload = PlayerTeamRosterSlotWitnessed(slot, species.name, species.id,
                            visualMatch.variant, visualMatch.distinctFormMargin),
                        perceivedAt = article.perceivedAt,
                        confidence = visualMatch.confidence,
                        evidenceArticleIds = listOf(article.id.value),
                        monotonicTimeNanos = article.monotonicTimeNanos ?: return@collect,
                    )
                    if (surfaceAccepted) {
                        accepted = true
                        submitRosterCandidate(match, candidate)
                    } else {
                        pending = candidate
                    }
                }
            }
        }
    }

    override fun stop() {
        scope?.cancel()
        scope = null
    }
}

private data class PendingRosterSlot(
    val sourceId: SourceId,
    val payload: PlayerTeamRosterSlotWitnessed,
    val perceivedAt: Long,
    val confidence: Float,
    val evidenceArticleIds: List<String>,
    val monotonicTimeNanos: Long,
)

private fun submitRosterCandidate(match: Match, candidate: PendingRosterSlot) {
    match.custody.submitTestimony(
        candidate.sourceId,
        candidate.payload,
        candidate.perceivedAt,
        candidate.confidence,
        candidate.evidenceArticleIds,
        candidate.monotonicTimeNanos,
    )
}
