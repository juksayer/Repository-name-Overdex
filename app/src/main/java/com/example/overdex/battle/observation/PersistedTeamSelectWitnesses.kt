package com.example.overdex.battle.observation

import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.data.observation.SpeciesNameRecognizer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/** Accepts Team Select only after independent, freshly observed apertures agree. */
class PersistedTeamSelectPartyWitness(private val store: FileCropArtifactStore) : Observer {
    override val observerId = ObserverId("TEAM_SELECT_PARTY_WITNESS", ObservationSource.SCREEN_CAPTURE)
    override val name = "Team Select Party Witness"
    private var scope: CoroutineScope? = null
    override fun start(match: Match) {
        if (scope != null) return
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { s -> s.launch {
            var accepted = false
            var evidence = TeamSelectSurfaceEvidence()
            match.articles.collect { a ->
                if (accepted) return@collect
                if ((a.monotonicTimeNanos ?: Long.MIN_VALUE) < match.observationAttemptStartedAtNanos) return@collect
                val crop = a.payload as? CropCaptured ?: return@collect
                val cropName = crop.cropProvenance.cropName
                val supported = setOf(
                    TeamSelectCropContracts.leagueText.cropName,
                    TeamSelectCropContracts.leagueBadge.cropName,
                    TeamSelectCropContracts.playerRosterSlot1.cropName,
                    TeamSelectCropContracts.playerRosterSlot2.cropName,
                    TeamSelectCropContracts.playerRosterSlot3.cropName,
                    TeamSelectCropContracts.restrictions.cropName,
                    TeamSelectCropContracts.useThisParty.cropName
                )
                if (cropName !in supported) return@collect
                val observedAt = a.monotonicTimeNanos ?: return@collect
                val bitmap = store.loadVerifiedPng(crop.artifact, crop.cropProvenance) ?: return@collect
                var signal: TeamSelectSignal? = null
                var cardSlot: Int? = null
                try {
                    when (cropName) {
                        TeamSelectCropContracts.leagueText.cropName -> {
                            val text = AnnouncementRecognizer.recognize(bitmap).value.orEmpty().uppercase()
                            if ("LEAGUE" in text) signal = TeamSelectSignal(a.id.value, observedAt, 0.96f)
                        }
                        TeamSelectCropContracts.leagueBadge.cropName -> {
                            TeamSelectLeagueShieldClassifier.classify(bitmap)?.let {
                                signal = TeamSelectSignal(a.id.value, observedAt, it.confidence)
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
                                signal = TeamSelectSignal(a.id.value, observedAt, it)
                            }
                        }
                        TeamSelectCropContracts.restrictions.cropName -> {
                            val text = AnnouncementRecognizer.recognize(bitmap).value.orEmpty()
                            if (TeamSelectSurfaceSignalDetector.isRestrictionText(text)) {
                                signal = TeamSelectSignal(a.id.value, observedAt, 0.93f)
                            }
                        }
                        TeamSelectCropContracts.useThisParty.cropName -> {
                            val colorConfidence = TeamSelectSurfaceSignalDetector.usePartyControlConfidence(bitmap)
                            val text = AnnouncementRecognizer.recognize(bitmap).value.orEmpty()
                            val textAccepted = TeamSelectSurfaceSignalDetector.isUsePartyText(text)
                            val confidence = when {
                                colorConfidence != null && textAccepted -> maxOf(colorConfidence, 0.97f)
                                textAccepted -> 0.95f
                                else -> colorConfidence
                            }
                            if (confidence != null) signal = TeamSelectSignal(a.id.value, observedAt, confidence)
                        }
                    }
                } finally { bitmap.recycle() }
                val measured = signal ?: return@collect
                evidence = when (cropName) {
                    TeamSelectCropContracts.leagueText.cropName -> evidence.copy(leagueText = measured)
                    TeamSelectCropContracts.leagueBadge.cropName -> evidence.copy(leagueBadge = measured)
                    TeamSelectCropContracts.restrictions.cropName -> evidence.copy(restrictions = measured)
                    TeamSelectCropContracts.useThisParty.cropName -> evidence.copy(useThisParty = measured)
                    else -> evidence.copy(partyCards = evidence.partyCards + (requireNotNull(cardSlot) to measured))
                }
                val result = TeamSelectSurfaceContract.evaluate(evidence) ?: return@collect
                accepted = true
                match.custody.submitTestimony(
                    SourceId(observerId.id),
                    TeamSelectPartyWitnessed,
                    a.perceivedAt,
                    result.confidence,
                    result.evidenceArticleIds,
                    observedAt
                )
            }
        } }
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
                match.articles.collect { article ->
                    if (accepted) return@collect
                    if ((article.monotonicTimeNanos ?: Long.MIN_VALUE) < match.observationAttemptStartedAtNanos) return@collect
                    val crop = article.payload as? CropCaptured ?: return@collect
                    if (crop.cropProvenance.cropName != TeamSelectCropContracts.leagueBadge.cropName) return@collect
                    val bitmap = store.loadVerifiedPng(crop.artifact, crop.cropProvenance) ?: return@collect
                    val league = try {
                        TeamSelectLeagueShieldClassifier.classify(bitmap)
                    } finally {
                        bitmap.recycle()
                    } ?: return@collect
                    val monotonic = article.monotonicTimeNanos ?: return@collect
                    accepted = true
                    match.custody.submitTestimony(
                        SourceId(observerId.id),
                        TeamSelectLeagueWitnessed(league.timelineName),
                        article.perceivedAt,
                        league.confidence,
                        listOf(article.id.value),
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
    override val observerId = ObserverId("TEAM_SELECT_PLAYER_SLOT_${slot}_WITNESS", ObservationSource.SCREEN_CAPTURE)
    override val name = "Team Select Player Slot $slot Witness"
    private var scope: CoroutineScope? = null
    override fun start(match: Match) {
        if (scope != null) return
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { s -> s.launch {
            var surfaceAccepted = false
            var accepted = false
            val knownNames = match.pokemonKnowledge.getAllSpeciesNames()
            match.articles.collect { a ->
                if ((a.monotonicTimeNanos ?: Long.MIN_VALUE) < match.observationAttemptStartedAtNanos) return@collect
                if (a.payload is TeamSelectPartyWitnessed) { surfaceAccepted = true; return@collect }
                if (!surfaceAccepted || accepted) return@collect
                val crop = a.payload as? CropCaptured ?: return@collect
                if (crop.cropProvenance.cropName != cropName) return@collect
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
                accepted = true
                match.custody.submitTestimony(
                    SourceId(observerId.id),
                    PlayerTeamRosterSlotWitnessed(slot, species.name, species.id),
                    a.perceivedAt,
                    resolution.confidence,
                    listOf(a.id.value),
                    a.monotonicTimeNanos ?: return@collect
                )
            }
        } }
    }
    override fun stop() { scope?.cancel(); scope = null }
}
