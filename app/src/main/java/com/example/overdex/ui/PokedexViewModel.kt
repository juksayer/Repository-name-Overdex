package com.example.overdex.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.overdex.CalibrationManager
import com.example.overdex.battle.custody.InMemoryTestimonyCustody
import com.example.overdex.battle.custody.MatchRecordStarted
import com.example.overdex.battle.custody.BattleOverlayOpened
import com.example.overdex.battle.custody.BattleOverlayOpenReason
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.debug.observatory.ObservationRecorder
import com.example.overdex.battle.observation.PersistedCountdownGlyphWitness
import com.example.overdex.battle.observation.PersistedVsScreenWitness
import com.example.overdex.battle.observation.PersistedOutOfBattleMenuWitness
import com.example.overdex.battle.observation.DroidballService
import com.example.overdex.battle.observation.DroidballSignal
import com.example.overdex.battle.observation.DroidballSession
import com.example.overdex.battle.observation.DroidballMatchLedger
import com.example.overdex.battle.observation.NextMatchVsWatcher
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.MatchEnded
import com.example.overdex.battle.custody.VisualCaptureGapObserved
import com.example.overdex.battle.observation.DroidballOverlayPresentation
import com.example.overdex.battle.observation.Match
import com.example.overdex.battle.observation.ObservationDispatcher

import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.observation.CropCaptureWitness
import com.example.overdex.battle.observation.BattleWitnessContracts
import com.example.overdex.battle.observation.FirstLiveCombatRouter
import com.example.overdex.battle.observation.PersistedSpeciesWitness
import com.example.overdex.battle.observation.CustomCropCaptureWitness
import com.example.overdex.battle.observation.PersistedOutcomeTextWitness
import com.example.overdex.battle.observation.PersistedOutcomePhraseWitness
import com.example.overdex.battle.observation.PersistedAnnouncementWitness
import com.example.overdex.battle.observation.PersistedAnnouncementSpeciesWitness
import com.example.overdex.battle.observation.PersistedPlayerEntrySpeciesWitness
import com.example.overdex.battle.observation.PersistedAnnouncementPhraseWitness
import com.example.overdex.battle.observation.PersistedAttackIncomingWitness
import com.example.overdex.battle.observation.PersistedPlayerInactiveHpBarWitness
import com.example.overdex.battle.observation.PersistedActiveHpBarCadenceWitness
import com.example.overdex.battle.observation.LiveActiveHpBarWitness
import com.example.overdex.battle.observation.LiveActiveHpBarBorderCadenceWitness
import com.example.overdex.battle.observation.LiveActiveHpBarBorderPulseWitness
import com.example.overdex.battle.observation.LiveActiveHpBarFrameHub
import com.example.overdex.battle.observation.LiveOverlaySpeciesPipeline
import com.example.overdex.battle.observation.PersistedPlayerInactiveSpeciesSpriteWitness
import com.example.overdex.battle.observation.PersistedTrainerInactiveTimerOverlayClearanceWitness
import com.example.overdex.battle.observation.PokemonGoTypeIconMatcher
import com.example.overdex.battle.observation.PersistedActivePokemonTypeWitness
import com.example.overdex.battle.observation.TeamSelectCalibration
import com.example.overdex.battle.observation.TeamSelectCropContracts
import com.example.overdex.battle.observation.TeamSelectCropCaptureWitness
import com.example.overdex.battle.observation.PersistedTeamSelectPartyWitness
import com.example.overdex.battle.observation.PersistedPlayerTeamRosterSlotWitness
import com.example.overdex.data.observation.YouWinRecognizer
import com.example.overdex.data.observation.GoodEffortRecognizer
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.artifact.FileAudioArtifactStore
import com.example.overdex.battle.audio.AudioCaptureWitness
import com.example.overdex.battle.audio.PersistedBattleCryCandidateWitness
import com.example.overdex.battle.audio.PersistedCountdownCryCueWitness
import com.example.overdex.battle.audio.PersistedFastMoveSoundWitness
import com.example.overdex.battle.archive.ArchiveDirectoryManager
import com.example.overdex.battle.archive.MatchArchiveExportMode
import com.example.overdex.battle.archive.MatchArchiveSource
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource as ObserverSource
import com.example.overdex.data.FallbackSpriteProvider
import com.example.overdex.data.FieldNoteRepository
import com.example.overdex.data.GameMasterLoader
import com.example.overdex.data.GithubSpriteProvider
import com.example.overdex.data.LocalSpriteProvider
import com.example.overdex.data.PokemonJsonLoader
import com.example.overdex.data.PokemonRepository
import com.example.overdex.data.SpeciesJsonLoader
import com.example.overdex.data.SpriteProvider
import com.example.overdex.data.local.PokedexDatabase
import com.example.overdex.data.local.PokemonEntity
import com.example.overdex.data.observation.DroidballObservationInput
import com.example.overdex.model.*
import com.example.overdex.model.navigation.ActionNode
import com.example.overdex.model.navigation.DirectoryNode
import com.example.overdex.model.navigation.InstrumentCommand
import com.example.overdex.model.navigation.InstrumentTree
import com.example.overdex.model.observation.InstrumentDeploymentState
import com.example.overdex.model.observation.ObservationSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.util.Collections

class PokedexViewModel(application: Application) : AndroidViewModel(application) {
    /**
     * Keep identity crops responsive, while placing all other PNG work on a
     * bounded lane.  An unbounded IO pool allowed dozens of full-size crop
     * encodes to coexist and made the process vulnerable to memory reclaim.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val identityCaptureDispatcher = Dispatchers.IO.limitedParallelism(2)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val auxiliaryCaptureDispatcher = Dispatchers.IO.limitedParallelism(2)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val combatCaptureDispatcher = Dispatchers.IO.limitedParallelism(2)

    private val db = PokedexDatabase.getDatabase(application)
    private val pokemonDao = db.pokemonDao()

    val spriteProvider: SpriteProvider = FallbackSpriteProvider(
        primary = LocalSpriteProvider(application.assets),
        secondary = GithubSpriteProvider(),
    )

    private val gameMasterLoader = GameMasterLoader(application)
    private val pokemonRepository = PokemonRepository(pokemonDao, spriteProvider, gameMasterLoader)
    private val pokemonLoader = PokemonJsonLoader(application)
    private val fieldNoteRepository = FieldNoteRepository(application)
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    private val _searchRequest = MutableStateFlow(SearchRequest())
    val searchRequest = _searchRequest.asStateFlow()

    private val _observationSessionState = MutableStateFlow(value = ObservationSessionState.IDLE)
    val observationSessionState = _observationSessionState.asStateFlow()

    private val _deploymentState = MutableStateFlow(InstrumentDeploymentState.IDLE)
    val deploymentState = _deploymentState.asStateFlow()

    private val _frameCount = MutableStateFlow(0L)
    val frameCount = _frameCount.asStateFlow()

    private var observationDispatcher = ObservationDispatcher()
    private var droidballSignalJob: kotlinx.coroutines.Job? = null
    private var nextMatchVsWatcher: NextMatchVsWatcher? = null
    private val fieldMatchLedger = DroidballMatchLedger()
    private val archiveDirectoryManager = ArchiveDirectoryManager(application)
    /** Export work survives an Activity/ViewModel recreation while the process remains alive. */
    private val archiveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val archiveMutex = Mutex()
    private var activeCheckpointJob: Job? = null
    /** A Match record is retained once, whether it ends by a result or capture stops early. */
    private val retainedMatchIds = Collections.synchronizedSet(mutableSetOf<String>())

    private val _activeMatch = MutableStateFlow<Match?>(null)
    val activeMatch = _activeMatch.asStateFlow()
    private val _droidballSession = MutableStateFlow<DroidballSession?>(null)
    val droidballSession = _droidballSession.asStateFlow()
    private val _latestMatchArchiveSource =
        MutableStateFlow<com.example.overdex.battle.archive.MatchArchiveSource?>(null)
    val latestMatchArchiveSource = _latestMatchArchiveSource.asStateFlow()

    fun getFieldNotes(pokemonId: Int) = flow {
        emit(fieldNoteRepository.getFieldNotesForPokemon(pokemonId))
    }.flowOn(Dispatchers.IO)

    // Instrument Workspace
    private val instrumentTree = InstrumentTree(
        listOf(
            ActionNode("Launch Droidball", InstrumentCommand.LaunchDroidball),
            ActionNode("OVERDEX", InstrumentCommand.OpenSearch),
            DirectoryNode("BATTLE", listOf(
                ActionNode("Roster", InstrumentCommand.OpenCollection),
                DirectoryNode("Match", listOf(
                    ActionNode("Match Summary", InstrumentCommand.OpenBattleLogs)
                )),
                ActionNode("History", InstrumentCommand.OpenBattleHistory)
            )),
            DirectoryNode("INTUEOR", listOf(
                ActionNode("Calibration", InstrumentCommand.OpenCalibration),
                ActionNode("Signal Observatory", InstrumentCommand.OpenSignalObservatory)
            )),
            ActionNode("PROFILE", InstrumentCommand.OpenProfile)
        )
    )

    private val _treeState = MutableStateFlow(instrumentTree.getState())
    val treeState = _treeState.asStateFlow()

    // Command handling
    private val _pendingCommand = MutableSharedFlow<InstrumentCommand>(extraBufferCapacity = 1)
    val pendingCommand = _pendingCommand.asSharedFlow()

    fun handleUp() {
        instrumentTree.moveSelection(-1)
        _treeState.value = instrumentTree.getState()
    }

    fun handleDown() {
        instrumentTree.moveSelection(1)
        _treeState.value = instrumentTree.getState()
    }

    fun handleA() {
        instrumentTree.executeSelected()?.let { command ->
            _pendingCommand.tryEmit(command)
        }
        _treeState.value = instrumentTree.getState()
    }

    fun handleB() {
        if (!instrumentTree.navigateBack()) {
            // Root back action if needed, or ignore
        }
        _treeState.value = instrumentTree.getState()
    }

    // App-session state for the boot sequence
    private val _hasBootedInSession = MutableStateFlow(value = false)
    val hasBootedInSession = _hasBootedInSession.asStateFlow()

    fun markBooted() {
        _hasBootedInSession.value = true
    }

    fun toggleObservation() {
        val current = _deploymentState.value
        if (current == InstrumentDeploymentState.IDLE) {
            startObservation()
        } else if (current == InstrumentDeploymentState.OBSERVING || 
                   current == InstrumentDeploymentState.DEPLOYING ||
                   current == InstrumentDeploymentState.READY) {
            stopObservation()
        }
    }

    fun startObservation() {
        if (_deploymentState.value != InstrumentDeploymentState.IDLE) return
        _deploymentState.value = InstrumentDeploymentState.REQUESTING_PERMISSIONS
    }

    fun onPermissionsGranted() {
        _deploymentState.value = InstrumentDeploymentState.READY
    }

    fun deployInstrument(resultCode: Int, data: android.content.Intent) {
        startFreshMatch(resultCode, data, startCaptureService = true)
    }

    private fun startFreshMatch(resultCode: Int, data: android.content.Intent, startCaptureService: Boolean): Match {
        _deploymentState.value = InstrumentDeploymentState.DEPLOYING
        
        // Initialize Match
        val matchId = java.util.UUID.randomUUID().toString()
        val match = Match(
            matchId = matchId,
            custody = InMemoryTestimonyCustody(),
            realityTimeline = InMemoryRealityTimeline(),
            pokemonKnowledge = pokemonRepository
        )
        val session = DroidballSession(match)
        DroidballOverlayPresentation.clearOpponentSpecies()
        // A battle HUD is evidence before GO and remains evidence afterward. GO
        // changes the match boundary; it must not decide whether visible battle
        // information is preserved.
        val battleEvidenceLive: () -> Boolean = {
            session.phase.value == com.example.overdex.battle.observation.DroidballSessionPhase.COUNTDOWN ||
                session.phase.value == com.example.overdex.battle.observation.DroidballSessionPhase.BATTLE_ACTIVE ||
                session.battleSurfaceEstablished.value ||
                DroidballOverlayPresentation.mode.value == com.example.overdex.battle.observation.DroidballOverlayMode.BATTLE_HUD ||
                DroidballOverlayPresentation.mode.value == com.example.overdex.battle.observation.DroidballOverlayMode.BATTLE_LIVE
        }
        // The announcement carries entry species names before 3/2/1. The heavier
        // HUD witnesses still wait for the countdown boundary.
        val announcementEvidenceLive: () -> Boolean = {
            session.phase.value == com.example.overdex.battle.observation.DroidballSessionPhase.ARMED || battleEvidenceLive()
        }
        // HP evidence belongs to the live battle surface. Follow the same
        // surface signal that opens the overlay instead of waiting for the
        // later BATTLE_ACTIVE phase transition; that transition can arrive
        // after the first usable HP frames, or be missed by a session listener.
        val activeHpEvidenceLive: () -> Boolean = {
            battleEvidenceLive()
        }
        DroidballOverlayPresentation.showSessionPhase(session.phase.value)
        viewModelScope.launch {
            session.phase.collect(DroidballOverlayPresentation::showSessionPhase)
        }
        viewModelScope.launch {
            match.articles.collect { article ->
                if (article.payload is MatchEnded) retainAndArchiveMatch(match, "result witnessed")
            }
        }
        _activeMatch.value = match
        _droidballSession.value = session
        startMatchCheckpointing(match)
        match.custody.submitTestimony(
            sourceId = SourceId("DROIDBALL_SESSION"),
            payload = MatchRecordStarted,
            timestamp = System.currentTimeMillis(),
            confidence = null,
            evidenceReferences = emptyList(),
            monotonicTimeNanos = System.nanoTime()
        )
        _latestMatchArchiveSource.value =
            com.example.overdex.battle.archive.MatchArchiveSource(
                matchId = com.example.overdex.battle.observation.MatchId(match.matchId),
                realityTimeline = match.realityTimeline
            )
        _frameCount.value = 0
        Log.d("DEPLOY", "1 Match created")
        
        // Re-initialize dispatcher so each immutable Match owns its witnesses.
        observationDispatcher.stopAll()
        observationDispatcher = ObservationDispatcher()
        
        // Load calibration and register production observers
        val input = DroidballObservationInput()
        val calibrationManager = CalibrationManager(getApplication())
        val calibration = calibrationManager.load()
        val customCrops = com.example.overdex.data.CustomBattleCropManager.from(getApplication()).load()
        val cropArtifactStore = FileCropArtifactStore(getApplication<Application>().filesDir)
        val audioArtifactStore = FileAudioArtifactStore(getApplication<Application>().filesDir)
        // Brief battle phenomena need a dense sampling lane; stable panels use
        // the default five-fps cadence to leave capture capacity for them.
        val transientIntervalNanos = 33_333_333L // 30 fps
        // Full-width announcement and result strips remain transient evidence,
        // but they do not need a PNG on every display frame for the entire
        // match. Ten samples per second still catches brief text while keeping
        // PNG encoding and native bitmap churn bounded.
        val transientTextIntervalNanos = 100_000_000L // 10 fps
        val combatCadenceIntervalNanos = 50_000_000L // 20 fps
        // Live HP witnesses measure every usable frame. Keep only a sparse
        // forensic sample as PNG evidence for optional full exports.
        val hpArtifactIntervalNanos = 2_000_000_000L // one HP crop every 2 seconds
        val inactiveTimerIntervalNanos = 100_000_000L // 10 fps
        val playerHpFrameHub = LiveActiveHpBarFrameHub()
        val opponentHpFrameHub = LiveActiveHpBarFrameHub()
        val fastPlayerSpeciesOverlay = LiveOverlaySpeciesPipeline.player(cropArtifactStore)
        val fastOpponentSpeciesOverlay = LiveOverlaySpeciesPipeline.opponent(cropArtifactStore)
        PokemonGoTypeIconMatcher.initialize(getApplication())

        observationDispatcher.register(fastPlayerSpeciesOverlay)
        observationDispatcher.register(fastOpponentSpeciesOverlay)

        // Species identity is the first dependency for the battle HUD and
        // replay. Preserve these narrow badge strips continuously while the
        // field session is armed or live. The OCR worker still reads only the
        // short, cue-triggered windows from SpeciesCheckCoordinator; keeping
        // preservation independent means a cue can consume the very next badge
        // frame instead of waiting for a later recovery window.
        listOf(
            BattleWitnessContracts.playerActiveSpeciesTextCapture to "Player Active Species Text Capture Witness",
            BattleWitnessContracts.opponentActiveSpeciesTextCapture to "Opponent Active Species Text Capture Witness"
        ).forEach { (contract, name) ->
            observationDispatcher.register(CropCaptureWitness(
                input = input,
                calibration = calibration,
                contract = contract,
                artifactStore = cropArtifactStore,
                isEnabled = announcementEvidenceLive,
                captureIntervalNanos = com.example.overdex.battle.observation.SpeciesCheckCoordinator.SAMPLE_INTERVAL_NANOS,
                captureDispatcher = identityCaptureDispatcher,
                observerId = ObserverId(contract.witnessId, ObserverSource.SCREEN_CAPTURE),
                name = name,
                onCaptured = { captured, wallTimeMillis, monotonicTimeNanos ->
                    if (contract == BattleWitnessContracts.playerActiveSpeciesTextCapture) {
                        fastPlayerSpeciesOverlay.offer(captured, wallTimeMillis, monotonicTimeNanos)
                    } else {
                        fastOpponentSpeciesOverlay.offer(captured, wallTimeMillis, monotonicTimeNanos)
                    }
                }
            ))
        }
        observationDispatcher.register(PersistedSpeciesWitness.player(cropArtifactStore))
        observationDispatcher.register(PersistedSpeciesWitness.opponent(cropArtifactStore))

        customCrops.filter { it.enabled }.forEach { crop ->
            observationDispatcher.register(
                CustomCropCaptureWitness(
                    input = input,
                    definition = crop,
                    artifactStore = cropArtifactStore,
                    isEnabled = battleEvidenceLive,
                    observerId = ObserverId("CUSTOM_CROP_${crop.id}", ObserverSource.SCREEN_CAPTURE),
                    name = "Custom Crop: ${crop.name}"
                )
            )
        }
        if (startCaptureService) {
            nextMatchVsWatcher?.stop()
            nextMatchVsWatcher = NextMatchVsWatcher(
                calibration = calibration,
                awaitingNextMatch = {
                    val session = _droidballSession.value ?: return@NextMatchVsWatcher false
                    session.phase.value == com.example.overdex.battle.observation.DroidballSessionPhase.RESULT ||
                        (session.phase.value == com.example.overdex.battle.observation.DroidballSessionPhase.ARMED &&
                            session.hasCompletedMatch)
                },
                onVs = { crop, frame ->
                    val nextMatch = startFreshMatch(resultCode, data, startCaptureService = false)
                    val artifact = cropArtifactStore.preservePng(crop.bitmap)
                    crop.bitmap.recycle()
                    if (artifact != null) nextMatch.custody.submitTestimony(
                        SourceId(BattleWitnessContracts.countdownCropCapture.witnessId), CropCaptured(artifact, crop.provenance),
                        frame.capturedAtWallTimeMillis, null, emptyList(), frame.capturedAtMonotonicTimeNanos
                    )
                }
            ).also { it.start() }
        }
        
        Log.d("DEPLOY", "2 Registering observers")
        val teamSelectCalibration = TeamSelectCalibration.measured1080x2400
        listOf(
            TeamSelectCropContracts.leagueText to "Team Select League Text Capture Witness",
            TeamSelectCropContracts.playerRosterSlot1 to "Team Select Player Roster Slot 1 Capture Witness",
            TeamSelectCropContracts.playerRosterSlot2 to "Team Select Player Roster Slot 2 Capture Witness",
            TeamSelectCropContracts.playerRosterSlot3 to "Team Select Player Roster Slot 3 Capture Witness"
        ).forEach { (contract, name) ->
            observationDispatcher.register(TeamSelectCropCaptureWitness(
                input = input,
                calibration = teamSelectCalibration,
                contract = contract,
                artifactStore = cropArtifactStore,
                isEnabled = { session.phase.value == com.example.overdex.battle.observation.DroidballSessionPhase.ARMED },
                observerId = ObserverId("${contract.cropName}_CAPTURE", ObserverSource.SCREEN_CAPTURE),
                name = name
            ))
        }
        observationDispatcher.register(PersistedTeamSelectPartyWitness(cropArtifactStore))
        listOf(
            1 to TeamSelectCropContracts.playerRosterSlot1.cropName,
            2 to TeamSelectCropContracts.playerRosterSlot2.cropName,
            3 to TeamSelectCropContracts.playerRosterSlot3.cropName
        ).forEach { (slot, cropName) ->
            observationDispatcher.register(PersistedPlayerTeamRosterSlotWitness(cropArtifactStore, slot, cropName))
        }
        if (getApplication<Application>().checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            observationDispatcher.register(
                AudioCaptureWitness(
                    artifactStore = audioArtifactStore,
                    observerId = ObserverId("BATTLE_CRY_AUDIO_CAPTURE", ObserverSource.AUDIO_CAPTURE)
                )
            )
            observationDispatcher.register(PersistedBattleCryCandidateWitness(getApplication(), getApplication<Application>().filesDir))
            observationDispatcher.register(PersistedFastMoveSoundWitness(getApplication<Application>().filesDir))
        } else {
            // Coverage is itself evidence: no cry could have been heard without the microphone.
            match.custody.submitAvailability(
                sourceId = SourceId("BATTLE_CRY_AUDIO_CAPTURE"),
                available = false,
                timestamp = System.currentTimeMillis()
            )
        }
        listOf(
            BattleWitnessContracts.playerActiveTypeIconsCapture to "Player Active Type Icons Capture Witness",
            BattleWitnessContracts.opponentActiveTypeIconsCapture to "Opponent Active Type Icons Capture Witness"
        ).forEach { (contract, name) ->
            observationDispatcher.register(CropCaptureWitness(
                input, calibration, contract, cropArtifactStore, battleEvidenceLive,
                ObserverId(contract.witnessId, ObserverSource.SCREEN_CAPTURE), name
            ))
        }
        listOf(
            BattleWitnessContracts.opponentPokeBallsCapture to "Opponent Poké Balls Capture Witness",
            BattleWitnessContracts.opponentShieldsCapture to "Opponent Shields Capture Witness"
        ).forEach { (contract, name) ->
            observationDispatcher.register(CropCaptureWitness(
                input = input,
                calibration = calibration,
                contract = contract,
                artifactStore = cropArtifactStore,
                isEnabled = announcementEvidenceLive,
                observerId = ObserverId(contract.witnessId, ObserverSource.SCREEN_CAPTURE),
                name = name
            ))
        }
        observationDispatcher.register(PersistedActivePokemonTypeWitness.player(cropArtifactStore))
        observationDispatcher.register(PersistedActivePokemonTypeWitness.opponent(cropArtifactStore))
        observationDispatcher.register(
            CropCaptureWitness(
                input = input,
                calibration = calibration,
                contract = BattleWitnessContracts.countdownCropCapture,
                artifactStore = cropArtifactStore,
                isEnabled = { session.firstLiveCombatArticle.value == null && session.phase.value != com.example.overdex.battle.observation.DroidballSessionPhase.ENDED },
                captureIntervalNanos = transientIntervalNanos,
                captureDispatcher = auxiliaryCaptureDispatcher,
                observerId = ObserverId(BattleWitnessContracts.countdownCropCapture.witnessId, ObserverSource.SCREEN_CAPTURE),
                name = "Countdown Crop Capture Witness"
            )
        )
        observationDispatcher.register(PersistedCountdownGlyphWitness(cropArtifactStore))
        // Cry capture must not wait for OCR to decide whether the visible glyph
        // is 3, 2, 1, or GO. This witness uses the preserved visual crop only to
        // request the short audio window; interpretation stays downstream.
        observationDispatcher.register(PersistedCountdownCryCueWitness(cropArtifactStore))
        observationDispatcher.register(
            CropCaptureWitness(
                input = input,
                calibration = calibration,
                contract = BattleWitnessContracts.vsScreenCapture,
                artifactStore = cropArtifactStore,
                isEnabled = {
                        session.phase.value == com.example.overdex.battle.observation.DroidballSessionPhase.ARMED ||
                        session.phase.value == com.example.overdex.battle.observation.DroidballSessionPhase.CALIBRATING
                },
                captureIntervalNanos = transientIntervalNanos,
                captureDispatcher = auxiliaryCaptureDispatcher,
                observerId = ObserverId(BattleWitnessContracts.vsScreenCapture.witnessId, ObserverSource.SCREEN_CAPTURE),
                name = "VS Screen Crop Capture Witness"
            )
        )
        observationDispatcher.register(PersistedVsScreenWitness(cropArtifactStore))
        observationDispatcher.register(
            CropCaptureWitness(
                input = input,
                calibration = calibration,
                contract = BattleWitnessContracts.trainerInactiveTimerOverlayClearanceCapture,
                artifactStore = cropArtifactStore,
                isEnabled = { session.phase.value == com.example.overdex.battle.observation.DroidballSessionPhase.COUNTDOWN },
                captureIntervalNanos = transientIntervalNanos,
                captureDispatcher = auxiliaryCaptureDispatcher,
                observerId = ObserverId(BattleWitnessContracts.trainerInactiveTimerOverlayClearanceCapture.witnessId, ObserverSource.SCREEN_CAPTURE),
                name = "Trainer Inactive Timer Overlay Capture Witness"
            )
        )
        observationDispatcher.register(PersistedTrainerInactiveTimerOverlayClearanceWitness(cropArtifactStore))
        observationDispatcher.register(
            CropCaptureWitness(
                input = input,
                calibration = calibration,
                contract = BattleWitnessContracts.opponentHpEvidenceCapture,
                artifactStore = cropArtifactStore,
                isEnabled = activeHpEvidenceLive,
                captureIntervalNanos = hpArtifactIntervalNanos,
                captureDispatcher = combatCaptureDispatcher,
                observerId = ObserverId(BattleWitnessContracts.opponentHpEvidenceCapture.witnessId, ObserverSource.SCREEN_CAPTURE),
                name = "Opponent HP Evidence Capture Witness"
            )
        )
        observationDispatcher.register(FirstLiveCombatRouter(session))
        observationDispatcher.register(LiveActiveHpBarWitness.player(input, calibration, activeHpEvidenceLive, playerHpFrameHub))
        observationDispatcher.register(LiveActiveHpBarWitness.opponent(input, calibration, activeHpEvidenceLive, opponentHpFrameHub))
        observationDispatcher.register(PersistedActiveHpBarCadenceWitness.player())
        observationDispatcher.register(PersistedActiveHpBarCadenceWitness.opponent())
        observationDispatcher.register(LiveActiveHpBarBorderCadenceWitness.player(playerHpFrameHub))
        observationDispatcher.register(LiveActiveHpBarBorderCadenceWitness.opponent(opponentHpFrameHub))
        observationDispatcher.register(LiveActiveHpBarBorderPulseWitness.player(playerHpFrameHub))
        observationDispatcher.register(LiveActiveHpBarBorderPulseWitness.opponent(opponentHpFrameHub))
        listOf(
            BattleWitnessContracts.playerHpEvidenceCapture to "Player HP Evidence Capture Witness",
            BattleWitnessContracts.playerTeamStatusCapture to "Player Team Status Capture Witness",
            BattleWitnessContracts.opponentTeamStatusCapture to "Opponent Team Status Capture Witness",
            BattleWitnessContracts.chargeMoveExecutionCapture to "Charge Move Execution Capture Witness",
            BattleWitnessContracts.playerChargeMoveControlsCapture to "Player Charge Move Controls Capture Witness"
        ).forEach { (contract, name) ->
            observationDispatcher.register(CropCaptureWitness(input, calibration, contract, cropArtifactStore,
                isEnabled = if (contract == BattleWitnessContracts.playerHpEvidenceCapture) activeHpEvidenceLive else battleEvidenceLive,
                captureIntervalNanos = when (contract) {
                    BattleWitnessContracts.playerHpEvidenceCapture -> hpArtifactIntervalNanos
                    BattleWitnessContracts.chargeMoveExecutionCapture -> combatCadenceIntervalNanos
                    BattleWitnessContracts.playerChargeMoveControlsCapture -> inactiveTimerIntervalNanos
                    else -> 200_000_000L
                },
                captureDispatcher = if (contract == BattleWitnessContracts.playerHpEvidenceCapture) {
                    combatCaptureDispatcher
                } else {
                    auxiliaryCaptureDispatcher
                },
                observerId = ObserverId(contract.witnessId, ObserverSource.SCREEN_CAPTURE), name = name))
        }
        observationDispatcher.register(
            CropCaptureWitness(input, calibration, BattleWitnessContracts.announcementCropCapture, cropArtifactStore,
                isEnabled = announcementEvidenceLive,
                captureIntervalNanos = transientTextIntervalNanos,
                captureDispatcher = auxiliaryCaptureDispatcher,
                observerId = ObserverId(BattleWitnessContracts.announcementCropCapture.witnessId, ObserverSource.SCREEN_CAPTURE),
                name = "Announcement Crop Capture Witness")
        )
        observationDispatcher.register(PersistedAnnouncementWitness(cropArtifactStore))
        observationDispatcher.register(PersistedPlayerEntrySpeciesWitness())
        observationDispatcher.register(PersistedAnnouncementSpeciesWitness())
        observationDispatcher.register(PersistedAttackIncomingWitness(cropArtifactStore))
        observationDispatcher.register(PersistedAnnouncementPhraseWitness.getReady(cropArtifactStore))
        observationDispatcher.register(PersistedAnnouncementPhraseWitness.chargeMoveUsed(cropArtifactStore))
        listOf(
            BattleWitnessContracts.playerInactiveUpperSpeciesSpriteCapture to "Player Inactive Upper Species Sprite Capture Witness",
            BattleWitnessContracts.playerInactiveLowerSpeciesSpriteCapture to "Player Inactive Lower Species Sprite Capture Witness",
            BattleWitnessContracts.playerInactiveUpperHpBarCapture to "Player Inactive Upper HP Bar Capture Witness",
            BattleWitnessContracts.playerInactiveLowerHpBarCapture to "Player Inactive Lower HP Bar Capture Witness",
            BattleWitnessContracts.inactiveMatchStartTimerCapture to "Inactive Match Start Timer Capture Witness",
            BattleWitnessContracts.switchLockoutTimerCapture to "Switch Lockout Timer Capture Witness"
        ).forEach { (contract, name) ->
            observationDispatcher.register(
                CropCaptureWitness(
                    input = input,
                    calibration = calibration,
                    contract = contract,
                    artifactStore = cropArtifactStore,
                    isEnabled = battleEvidenceLive,
                    captureIntervalNanos = inactiveTimerIntervalNanos,
                    captureDispatcher = auxiliaryCaptureDispatcher,
                    observerId = ObserverId(contract.witnessId, ObserverSource.SCREEN_CAPTURE),
                    name = name
                )
            )
        }
        observationDispatcher.register(PersistedPlayerInactiveHpBarWitness.upper(cropArtifactStore))
        observationDispatcher.register(PersistedPlayerInactiveHpBarWitness.lower(cropArtifactStore))
        observationDispatcher.register(PersistedPlayerInactiveSpeciesSpriteWitness.upper(cropArtifactStore))
        observationDispatcher.register(PersistedPlayerInactiveSpeciesSpriteWitness.lower(cropArtifactStore))
        observationDispatcher.register(PersistedOutOfBattleMenuWitness(cropArtifactStore))
        observationDispatcher.register(CropCaptureWitness(input, calibration, BattleWitnessContracts.battlePartyTabsCapture, cropArtifactStore,
            isEnabled = { session.phase.value == com.example.overdex.battle.observation.DroidballSessionPhase.RESULT },
            observerId = ObserverId(BattleWitnessContracts.battlePartyTabsCapture.witnessId, ObserverSource.SCREEN_CAPTURE), name = "Battle Party Tabs Capture Witness"))
        observationDispatcher.register(CropCaptureWitness(input, calibration, BattleWitnessContracts.outOfBattleMenuCapture, cropArtifactStore,
            isEnabled = { session.phase.value == com.example.overdex.battle.observation.DroidballSessionPhase.RESULT },
            observerId = ObserverId(BattleWitnessContracts.outOfBattleMenuCapture.witnessId, ObserverSource.SCREEN_CAPTURE), name = "Out Of Battle Menu Capture Witness"))
        observationDispatcher.register(
            CropCaptureWitness(
                input = input,
                calibration = calibration,
                contract = BattleWitnessContracts.matchOutcomeCropCapture,
                artifactStore = cropArtifactStore,
                isEnabled = { battleEvidenceLive() },
                captureIntervalNanos = transientTextIntervalNanos,
                captureDispatcher = auxiliaryCaptureDispatcher,
                observerId = ObserverId(BattleWitnessContracts.matchOutcomeCropCapture.witnessId, ObserverSource.SCREEN_CAPTURE),
                name = "Match Outcome Crop Capture Witness"
            )
        )
        observationDispatcher.register(PersistedOutcomeTextWitness(cropArtifactStore))
        observationDispatcher.register(
            PersistedOutcomePhraseWitness(
                accepts = { text -> text.trim().uppercase() in setOf("YOU WIN!", "YOU WIN", "YOU WVIN!", "YOU WVIN") },
                observerId = ObserverId("YOU_WIN_WITNESS", ObserverSource.SCREEN_CAPTURE),
                name = "You Win Witness"
            )
        )
        observationDispatcher.register(
            PersistedOutcomePhraseWitness(
                accepts = { text -> text.trim().uppercase().contains("GOOD EFFORT") },
                observerId = ObserverId("GOOD_EFFORT_WITNESS", ObserverSource.SCREEN_CAPTURE),
                name = "Good Effort Witness"
            )
        )

        // The first Match starts capture; later field-session Matches reuse it.
        if (startCaptureService) {
            DroidballService.start(getApplication(), resultCode, data)
            startDroidBallService()
        }

        var battleHudOpenRecorded = false
        fun openBattleHud(reason: BattleOverlayOpenReason) {
            if (!battleHudOpenRecorded) {
                battleHudOpenRecorded = true
            val now = System.currentTimeMillis()
            match.custody.submitTestimony(
                sourceId = SourceId("DROIDBALL_OVERLAY"),
                payload = BattleOverlayOpened(reason),
                timestamp = now,
                confidence = null,
                evidenceReferences = emptyList(),
                monotonicTimeNanos = System.nanoTime()
            )
            }
            DroidballOverlayPresentation.showBattleHud()
        }

        // Listen for signals
        droidballSignalJob?.cancel()
        droidballSignalJob = viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            DroidballService.signals.collect { signal ->
                when (signal) {
                    is DroidballSignal.Started -> {
                        // Service is up, but no frames yet
                        val now = System.currentTimeMillis()
                        match.realityTimeline.append(
                            RealityArticle(
                                id = ArticleId(java.util.UUID.randomUUID().toString()),
                                perceivedAt = now,
                                recordedAt = now,
                                sourceId = SourceId("DroidballService"),
                                payload = RawTestimony("DroidballService started")
                            )
                        )
                    }
                    is DroidballSignal.FrameCaptured -> {
                        if (_deploymentState.value == InstrumentDeploymentState.DEPLOYING || _deploymentState.value == InstrumentDeploymentState.READY) {
                            _deploymentState.value = InstrumentDeploymentState.OBSERVING
                        }
                        match.incrementFrameCount()
                        _frameCount.value = match.frameCount
                    }
                    is DroidballSignal.Stopped -> {
                        _activeMatch.value?.let { retainAndArchiveMatch(it, "capture stopped") }
                        _deploymentState.value = InstrumentDeploymentState.IDLE
                    }
                    is DroidballSignal.Error -> {
                        Log.e("DROIDBALL_SERVICE", "Error: ${signal.message}")
                        stopObservation()
                    }
                    is DroidballSignal.VisualCaptureGap -> {
                        match.custody.submitTestimony(SourceId("VISUAL_CAPTURE_CLOCK"), VisualCaptureGapObserved(signal.durationNanos), System.currentTimeMillis(), null, emptyList(), System.nanoTime())
                    }
                    is DroidballSignal.CountdownWitnessed -> {
                        // Any accepted countdown glyph proves the VS transition has passed.
                        openBattleHud(BattleOverlayOpenReason.COUNTDOWN_GLYPH)
                        session.armCountdown()
                    }
                    is DroidballSignal.VsScreenWitnessed -> {
                        openBattleHud(BattleOverlayOpenReason.VS_SCREEN)
                        session.armCountdown()
                    }
                    is DroidballSignal.BattleHudWitnessed -> {
                        openBattleHud(BattleOverlayOpenReason.BATTLE_WITNESS)
                        session.armCountdown()
                    }
                    is DroidballSignal.OpenBattleHudRequested -> {
                        // Explicit user intent opens the presentation and enables the
                        // battle-facing crops without claiming that GO has occurred.
                        openBattleHud(BattleOverlayOpenReason.USER_REQUEST)
                    }
                    is DroidballSignal.BeginNextMatch -> {
                        if (session.phase.value == com.example.overdex.battle.observation.DroidballSessionPhase.RESULT) {
                            session.end()
                            match.release()
                            startFreshMatch(resultCode, data, startCaptureService = false)
                        }
                    }
                }
            }
        }

        // The listener must be live before the first observer can emit a
        // battle-entry signal. Otherwise an early “Go, Pokémon!” can be
        // accepted without opening the HUD that enables the remaining crops.
        Log.d("DEPLOY", "3 Starting observers")
        observationDispatcher.startAll(match)
        return match
    }

    /**
     * Retains the exact timeline gathered so far. A normal result marks the Match complete;
     * a stopped projection yields an intentionally incomplete record rather than discarding
     * its observed portion. Droidball can then be started again without confusing the two.
     */
    private fun startMatchCheckpointing(match: Match) {
        activeCheckpointJob?.cancel()
        activeCheckpointJob = archiveScope.launch {
            while (isActive) {
                delay(15_000L)
                if (_activeMatch.value?.matchId != match.matchId || retainedMatchIds.contains(match.matchId)) break
                runCatching {
                    archiveMutex.withLock {
                        if (archiveDirectoryManager.isFolderAvailable()) {
                            archiveDirectoryManager.exportMatchCheckpoint(
                                matchId = MatchId(match.matchId),
                                realityTimeline = match.realityTimeline
                            )
                        }
                    }
                }.onFailure { error ->
                    Log.w("MATCH_ARCHIVE", "Unable to checkpoint active match ${match.matchId}", error)
                }
            }
        }
    }

    private fun retainAndArchiveMatch(match: Match, reason: String) {
        if (!retainedMatchIds.add(match.matchId)) return
        activeCheckpointJob?.cancel()
        activeCheckpointJob = null
        match.speciesChecks.stop()
        val source = MatchArchiveSource(MatchId(match.matchId), match.realityTimeline)
        fieldMatchLedger.complete(source)
        _latestMatchArchiveSource.value = source
        archiveScope.launch {
            runCatching {
                archiveMutex.withLock {
                    if (archiveDirectoryManager.isFolderAvailable()) {
                        archiveDirectoryManager.exportMatch(
                            source.matchId,
                            source.realityTimeline,
                            MatchArchiveExportMode.COMPACT_CITED_EVIDENCE
                        )
                    } else null
                }
            }.onSuccess { uri ->
                if (uri != null) {
                    archiveDirectoryManager.deleteMatchCheckpoint(source.matchId)
                    Log.i("MATCH_ARCHIVE", "Auto-saved $reason match ${source.matchId.value}: $uri")
                } else {
                    Log.w("MATCH_ARCHIVE", "$reason match ${source.matchId.value} remains in the field ledger; no Archive Directory is configured.")
                }
            }.onFailure { error ->
                Log.e("MATCH_ARCHIVE", "Unable to auto-save $reason match ${source.matchId.value}", error)
            }
        }
    }

    fun stopObservation() {
        _deploymentState.value = InstrumentDeploymentState.RETURNING
        _activeMatch.value?.let { retainAndArchiveMatch(it, "Droidball stopped") }
        nextMatchVsWatcher?.stop()
        nextMatchVsWatcher = null
        DroidballService.stop(getApplication())
        stopDroidBallService()
        observationDispatcher.stopAll()
        _droidballSession.value?.end()
        _droidballSession.value = null
        DroidballOverlayPresentation.reset()
        _activeMatch.value = null
        _deploymentState.value = InstrumentDeploymentState.IDLE
    }

    override fun onCleared() {
        archiveScope.cancel()
        super.onCleared()
    }

    fun startDroidBallService() {
        setObservationSessionState(ObservationSessionState.SERVICE_ACTIVE)
    }

    fun stopDroidBallService() {
        if (_observationSessionState.value == ObservationSessionState.SERVICE_ACTIVE) {
            setObservationSessionState(ObservationSessionState.IDLE)
        }
    }

    fun setObservationSessionState(state: ObservationSessionState) {
        val oldState = _observationSessionState.value
        _observationSessionState.value = state
        if (state == ObservationSessionState.CALIBRATING) {
            _droidballSession.value?.beginCalibration()
        }
        
        // Lifecycle management for ObservationRecorder
        if (state == ObservationSessionState.SERVICE_ACTIVE && oldState != ObservationSessionState.SERVICE_ACTIVE) {
            // Droidball deployed - Start recording
            ObservationRecorder.startRecording(getApplication()) 
        } else if (state != ObservationSessionState.SERVICE_ACTIVE && oldState == ObservationSessionState.SERVICE_ACTIVE) {
            // Droidball docked - Stop recording
            val recording = ObservationRecorder.stopRecording()
            if (recording != null) {
                Log.d("OBSERVATION_RECORDER", "Match captured: ${recording.matchId} with ${recording.events.size} events")
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedPokemon: Flow<PagingData<Pokemon>> = createSearchFlow(_searchQuery, _searchRequest.map { it.type })

    /**
     * Creates an independent, paged search flow.
     * 
     * @param queryFlow A flow of search strings.
     * @param typeFlow A flow of optional type filters.
     * @return A flow of PagingData that maintains its own loading state.
     */
    //Design Note: PokedexViewModel provides a reusable mechanism for constructing independent search sessions. Individual workflows, such as Register Specimen, own their transient search state while sharing the same repository and paging infrastructure.
    @OptIn(ExperimentalCoroutinesApi::class)
    fun createSearchFlow(
        queryFlow: Flow<String>,
        typeFlow: Flow<PokemonType?> = flowOf(null)
    ): Flow<PagingData<Pokemon>> {
        return combine(queryFlow, typeFlow) { q, t -> q to t }
            .flatMapLatest { (q, t) ->
                Pager(
                    config = PagingConfig(pageSize = 50, enablePlaceholders = false),
                ) {
                    pokemonRepository.search(query = q, type = t)
                }.flow
            }

            .cachedIn(viewModelScope)
    }


    init {
        viewModelScope.launch {
            Log.d("STARTUP", "1: ViewModel init [BUILD AUG27_B]")

            val currentCount = pokemonDao.getCount()
            Log.d("STARTUP", "Current Pokemon count in DB: $currentCount")

            if (currentCount < 1025) {
                Log.d("STARTUP", "2: Database incomplete. Starting populateFullPokedex")
                populateFullPokedex()
            } else {
                Log.d("STARTUP", "2: Database already populated. Skipping import.")
            }

            Log.d("STARTUP", "3: Initialization complete")
        }
    }

    suspend fun populateFullPokedex() {
        try {
            val imported = pokemonLoader.loadPokemon()
            val importedMap = imported.pokemon.associateBy { it.id }
            val speciesLoader = SpeciesJsonLoader(getApplication())

            val speciesMap =
                speciesLoader.loadSpecies()
                    .associateBy { it.id }

            Log.d("SPECIES_TEST", "Species count = ${speciesMap.size}")
            Log.d("SPECIES_TEST", "Charizard = ${speciesMap[6]}")


            val pokemonEntities = mutableListOf<PokemonEntity>()
            
            // Populate up to 1025 pokemon
            for (id in 1..1025) {
                val importedPokemon = importedMap[id]

                if (id == 5) {
                    Log.d(
                        "EVO_TEST",
                        "name=${importedPokemon?.name} prev=${importedPokemon?.prev_evolution} next=${importedPokemon?.next_evolution}"
                    )
                }

                val speciesInfo = speciesMap[id]

                val gameMasterPokemon =
                    gameMasterLoader.getPokemonByDex(id)

                val name =
                    importedPokemon?.name
                        ?: gameMasterPokemon?.speciesName
                        ?: "Pokemon #$id"
                val rawTypes =
                    importedPokemon?.type
                        ?: gameMasterPokemon?.types
                            ?.asSequence()
                            ?.filter { it != "none" }
                            ?.map { it.replaceFirstChar(Char::uppercase) }
                            ?.toList()
                        ?: listOf("Normal")
                val spriteUrl = spriteProvider.getSpriteUrl(id = id)

                if ((id == 152) || (id == 249) || (id == 445) || (id == 1000)) {
                    Log.d(
                        "TYPE_DEBUG",
                        "#$id importedPokemon=${importedPokemon != null} rawTypes=$rawTypes"
                    )
                }
                if (id <= 3) {
                    Log.d("SPRITE_TEST", "ID=$id importedImg=${importedPokemon?.img}")
                    Log.d("SPRITE_TEST", "ID=$id spriteUrl=$spriteUrl")
                }
                val mappedTypes = rawTypes.mapNotNull { typeName ->
                    try {
                        PokemonType.valueOf(typeName.uppercase())
                    } catch (e: Exception) {
                        null
                    }
                }.ifEmpty { listOf(PokemonType.NORMAL) }

                val region = getRegionForId(id)



                if (id == 149) {
                    Log.d("OVERDEX", "Lookup result = ${gameMasterPokemon?.speciesName}")
                    Log.d("OVERDEX", "Fast = ${gameMasterPokemon?.fastMoves}")
                    Log.d("OVERDEX", "Charged = ${gameMasterPokemon?.chargedMoves}")

                    val bubble = gameMasterLoader.getMove("BUBBLE")

                    Log.d("OVERDEX", "Move Name = ${bubble?.name}")
                    Log.d("OVERDEX", "Move Type = ${bubble?.type}")
                    Log.d("OVERDEX", "Move Power = ${bubble?.power}")
                    Log.d("OVERDEX", "Move EnergyGain = ${bubble?.energyGain}")
                }

                val fastMoves = gameMasterPokemon?.fastMoves
                    ?.mapNotNull { moveId ->
                        gameMasterLoader.getMove(moveId)?.let { move ->
                            Move(
                                name = move.name,
                                type = try {
                                    PokemonType.valueOf(move.type.uppercase())
                                } catch (e: Exception) {
                                    PokemonType.NORMAL
                                },
                                damage = move.power,
                                energy = move.energyGain,
                                isFast = true,
                                turns = move.turns
                            )
                        }
                    }
                    ?: emptyList()

                val chargedMoves = gameMasterPokemon?.chargedMoves
                    ?.mapNotNull { moveId ->
                        gameMasterLoader.getMove(moveId)?.let { move ->
                            Move(
                                name = move.name,
                                type = try {
                                    PokemonType.valueOf(move.type.uppercase())
                                } catch (e: Exception) {
                                    PokemonType.NORMAL
                                },
                                damage = move.power,
                                energy = move.energy,
                                isFast = false
                            )
                        }
                    }
                    ?: emptyList()
                pokemonEntities.add(
                    PokemonEntity(
                        id = id,
                        name = name,
                        typesJson = Json.encodeToString(mappedTypes),
                        region = region,

                        genus = speciesInfo?.genus ?: "",

                        height = importedPokemon?.height ?: "",
                        weight = importedPokemon?.weight ?: "",

                        prevEvolutionsJson = Json.encodeToString(
                            importedPokemon?.prev_evolution ?: emptyList()
                        ),
                        nextEvolutionsJson = Json.encodeToString(
                            importedPokemon?.next_evolution ?: emptyList()
                        ),

                        baseAttack = gameMasterPokemon?.baseStats?.atk ?: 0,
                        baseDefense = gameMasterPokemon?.baseStats?.def ?: 0,
                        baseStamina = gameMasterPokemon?.baseStats?.hp ?: 0,

                        fastMovesJson = Json.encodeToString(fastMoves),
                        chargedMovesJson = Json.encodeToString(chargedMoves),

                        spriteUrl = spriteUrl,
                        cryUrl = "https://raw.githubusercontent.com/PokeAPI/cries/main/cries/pokemon/latest/$id.ogg",

                        description =
                            speciesInfo?.flavor_text
                                ?: "A ${mappedTypes.joinToString("/") { it.name.lowercase() }} type Pokémon from the $region region."
                    )
                )
                
                if (pokemonEntities.size >= 100) {
                    pokemonDao.insertAll(pokemonEntities)
                    pokemonEntities.clear()
                }
            }

            if (pokemonEntities.isNotEmpty()) {
                pokemonDao.insertAll(pokemonEntities)
            }
            Log.d("PokedexViewModel", "Successfully imported 1025 Pokemon into Room")
        } catch (e: Exception) {
            Log.e("PokedexViewModel", "Error populating pokedex", e)
        }
    }

    private fun getRegionForId(id: Int): String = when {
        id <= 151 -> "Kanto"
        id <= 251 -> "Johto"
        id <= 386 -> "Hoenn"
        id <= 493 -> "Sinnoh"
        id <= 649 -> "Unova"
        id <= 721 -> "Kalos"
        id <= 809 -> "Alola"
        id <= 898 -> "Galar"
        id <= 1025 -> "Paldea"
        else -> "Unknown"
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        _searchRequest.value =
            _searchRequest.value.copy(
                text = query,
                type = if (query.isBlank()) null else _searchRequest.value.type
            )
    }

    fun removeFilter(filter: SearchRequest.ActiveFilter) {
        when (filter.label) {
            _searchRequest.value.type?.name -> {
                _searchQuery.value = ""

                _searchRequest.value =
                    _searchRequest.value.copy(
                        text = "",
                        type = null
                    )
            }
        }
    }
    fun updateTypeFilter(type: PokemonType?) {
        _searchRequest.value =
            _searchRequest.value.copy(
                type = type
            )
    }

    private val pokemonNameCache = mutableMapOf<Int, String>()

    suspend fun getPokemonName(id: Int): String {
        return pokemonNameCache[id] ?: run {
            val name = pokemonRepository.getPokemonById(id)?.name ?: "Unknown"
            pokemonNameCache[id] = name
            name
        }
    }

    suspend fun getPokemonById(id: Int): Pokemon? {
        return pokemonRepository.getPokemonById(id)
    }

    suspend fun getPokemonByName(name: String): Pokemon? {
        return pokemonRepository.getPokemonByName(name)
    }

    suspend fun getAllSpeciesNames(): Set<String> = pokemonRepository.getAllSpeciesNames()
    /**
     * Resolves the entire evolution family (names) that a given species belongs to.
     */
    suspend fun getEvolutionFamily(name: String): List<String> {
        val base = getPokemonByName(name) ?: return emptyList()
        val family = mutableSetOf<String>()
        family.add(base.name)
        base.prevEvolutions.forEach { family.add(it.name) }
        base.nextEvolutions.forEach { family.add(it.name) }
        return family.toList()
    }
}
