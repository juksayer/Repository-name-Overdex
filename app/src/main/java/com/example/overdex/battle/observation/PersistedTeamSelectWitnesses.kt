package com.example.overdex.battle.observation

import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.data.observation.ObservationRecognizer
import com.example.overdex.model.observation.CaptureObservation
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/** Accepts the pre-battle surface from its independently preserved League-text crop. */
class PersistedTeamSelectPartyWitness(private val store: FileCropArtifactStore) : Observer {
    override val observerId = ObserverId("TEAM_SELECT_PARTY_WITNESS", ObservationSource.SCREEN_CAPTURE)
    override val name = "Team Select Party Witness"
    private var scope: CoroutineScope? = null
    override fun start(match: Match) {
        if (scope != null) return
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { s -> s.launch {
            var accepted = false
            match.articles.collect { a ->
                if (accepted) return@collect
                val crop = a.payload as? CropCaptured ?: return@collect
                if (crop.cropProvenance.cropName != TeamSelectCropContracts.leagueText.cropName) return@collect
                val bitmap = store.loadVerifiedPng(crop.artifact) ?: return@collect
                val isLeague = try { AnnouncementRecognizer.recognize(bitmap).value?.uppercase()?.contains("LEAGUE") == true } finally { bitmap.recycle() }
                if (!isLeague) return@collect
                accepted = true
                match.custody.submitTestimony(SourceId(observerId.id), TeamSelectPartyWitnessed, a.perceivedAt, null, listOf(a.id.value), a.monotonicTimeNanos ?: return@collect)
            }
        } }
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
            var surfaceAccepted = false; var accepted = false
            match.articles.collect { a ->
                if (a.payload is TeamSelectPartyWitnessed) { surfaceAccepted = true; return@collect }
                if (!surfaceAccepted || accepted) return@collect
                val crop = a.payload as? CropCaptured ?: return@collect
                if (crop.cropProvenance.cropName != cropName) return@collect
                val bitmap = store.loadVerifiedPng(crop.artifact) ?: return@collect
                val name = try {
                    ObservationRecognizer.recognize(CaptureObservation("SpeciesName", bitmap), "TEAM_SELECT")
                        .firstOrNull { it.recognizer == "SpeciesNameRecognizer" && it.value is String }?.value as? String
                } finally { bitmap.recycle() } ?: return@collect
                val species = match.pokemonKnowledge.getPokemonByName(name)
                accepted = true
                match.custody.submitTestimony(SourceId(observerId.id), PlayerTeamRosterSlotWitnessed(slot, name, species?.id), a.perceivedAt, null, listOf(a.id.value), a.monotonicTimeNanos ?: return@collect)
            }
        } }
    }
    override fun stop() { scope?.cancel(); scope = null }
}
