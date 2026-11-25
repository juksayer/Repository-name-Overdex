package com.example.overdex.ui.screens.observatory

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.overdex.battle.archive.MatchArchive
import com.example.overdex.battle.replay.ReplayFastMoveAction
import com.example.overdex.battle.replay.ReplayChargedMoveAction
import com.example.overdex.battle.replay.ReplayChargePose
import com.example.overdex.battle.replay.ReplayChargePhase
import com.example.overdex.battle.replay.ReplayScene
import com.example.overdex.battle.replay.ReplayCropProgress
import com.example.overdex.battle.replay.MatchReplayModel
import com.example.overdex.battle.replay.ReplayCombatant
import com.example.overdex.battle.replay.ReplayIdentityObservation
import com.example.overdex.battle.replay.ReplayHpState
import com.example.overdex.battle.replay.ReplayCombatantEndPose
import com.example.overdex.battle.replay.ReplayAttackSounds
import com.example.overdex.battle.replay.replayAttackSoundsBetween
import com.example.overdex.battle.replay.ReplayTransportSounds
import com.example.overdex.battle.replay.ReplayPlaybackClock
import com.example.overdex.battle.replay.ReplayEventMarker
import com.example.overdex.battle.replay.ReplayEventMarkers
import com.example.overdex.battle.replay.ReplayAudioRecovery
import com.example.overdex.battle.replay.ReplayPreparationCoordinator
import com.example.overdex.battle.replay.ReplayPreparationState
import com.example.overdex.battle.replay.ReplayReferenceLookup
import com.example.overdex.battle.replay.ReplayCorrectionSide
import com.example.overdex.battle.replay.ReplayTrainerCorrection
import com.example.overdex.battle.replay.ReplayCorrectionKind
import com.example.overdex.battle.replay.ReplayTrainerProjection
import com.example.overdex.battle.replay.prepareReplay
import com.example.overdex.battle.replay.ReplayEventMarkerNavigation
import com.example.overdex.data.LocalSpriteProvider
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import com.example.overdex.ui.components.PokemonTypeIcon
import com.example.overdex.ui.components.TypeIconStyle
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.ui.theme.TerminalPurple
import kotlin.math.sin
import kotlin.math.pow
import kotlin.math.roundToInt
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.overdex.battle.replay.ReplayExcerptStore
import com.example.overdex.battle.replay.ReplayWindow

/** A clean CRT battle stage. Replay transport and diagnostics live in the ODX-Fi LCD. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MatchReplayScreen(
    archive: MatchArchive,
    onBack: () -> Unit,
    onUp: (() -> Unit) -> Unit = {},
    onDown: (() -> Unit) -> Unit = {},
    onA: (() -> Unit) -> Unit = {},
    onB: (() -> Unit) -> Unit = {},
    onLcdDrag: ((Offset) -> Unit) -> Unit = {},
    onLcdTap: (() -> Unit) -> Unit = {},
    onLcdUpdate: (String, String) -> Unit = { _, _ -> },
    onLcdContentUpdate: ((@Composable () -> Unit)?) -> Unit = {},
    resolveSpeciesId: suspend (String) -> Int? = { null },
    resolveSpeciesTypes: suspend (String) -> List<PokemonType> = { emptyList() },
    resolveFastMoveType: suspend (speciesName: String, moveName: String) -> PokemonType? = { _, _ -> null },
    resolveChargedMoveType: suspend (speciesName: String, moveName: String) -> PokemonType? = { _, _ -> null },
    resolveFastMoves: suspend (speciesName: String) -> List<Move> = { emptyList() },
    resolveChargedMoves: suspend (speciesName: String) -> List<Move> = { emptyList() },
    impactArchiveUri: android.net.Uri? = null,
    replayPreparation: ReplayPreparationCoordinator? = null,
    resolveSpeciesNames: suspend () -> Set<String> = { emptySet() },
    resolveArchivedAudio: (suspend (MatchArchive, List<Move>, Set<Int>) -> ReplayAudioRecovery)? = null,
    resolveArchivedSpeciesCrops: (suspend (MatchArchive, (ReplayCropProgress) -> Unit) -> List<ReplayIdentityObservation>)? = null,
    initialCursorNanos: Long? = null,
    playbackWindow: ReplayWindow? = null,
    autoplay: Boolean = false,
    playbackLabel: String? = null,
    sharedReplay: com.example.overdex.battle.replay.SharedReplay? = null,
    onPlaybackComplete: () -> Unit = {},
    onOpenEditor: (() -> Unit)? = null,
    allowClipSave: Boolean = true,
    onLeft: (() -> Unit) -> Unit = {},
    onRight: (() -> Unit) -> Unit = {},
    initialPlaybackSpeed: Float = 1f,
    onPlaybackSpeedChange: (Float) -> Unit = {},
    pairedReplayScene: ((com.example.overdex.battle.replay.ReplayScene, Long) -> com.example.overdex.battle.replay.ReplayScene)? = null,
) {
    val replayContext = LocalContext.current
    var preparation by remember(archive, impactArchiveUri, replayPreparation, sharedReplay) {
        mutableStateOf(ReplayPreparationState())
    }
    LaunchedEffect(archive, impactArchiveUri, replayPreparation, sharedReplay) {
        try {
            if (replayPreparation != null && impactArchiveUri != null && replayPreparation.owns(impactArchiveUri) &&
                resolveArchivedAudio == null && resolveArchivedSpeciesCrops == null) {
                replayPreparation.observe(impactArchiveUri).collect { preparation = it }
            } else {
                preparation = prepareReplay(
                    replayContext, archive, impactArchiveUri,
                    ReplayReferenceLookup(resolveSpeciesId, resolveSpeciesNames, resolveFastMoves,
                        resolveChargedMoves, resolveFastMoveType, resolveChargedMoveType),
                    { preparation = it }, resolveArchivedAudio, resolveArchivedSpeciesCrops,
                    sharedReadings = sharedReplay?.readings,
                )
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            preparation = preparation.copy(complete = true, error = error.message ?: "Replay preparation failed.")
        }
    }
    var pairedEvidence by remember(archive) { mutableStateOf<com.example.overdex.battle.replay.PairedReplayEvidence?>(null) }
    val baseModel = preparation.replay?.model
    val projectedModel by androidx.compose.runtime.key(baseModel, pairedEvidence) {
        produceState(baseModel) {
            val evidence = pairedEvidence
            if (baseModel != null && evidence != null) {
                value = withContext(Dispatchers.Default) { baseModel.pairedWith(evidence) }
            }
        }
    }
    val model = if (pairedEvidence == null) baseModel else projectedModel ?: baseModel
    val cropProgress = preparation.progress
    val cropError = preparation.error
    if (model == null) {
        BackHandler(onBack = onBack)
        SideEffect {
            onUp { }
            onDown { }
            onLeft { }
            onRight { }
            onA { onBack() }
            onB { onBack() }
            onLcdTap { }
            onLcdDrag { }
        }
        PublishMatchLcd(onLcdContentUpdate) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ReplayLcdText("Preparing Replay…", heading = true)
                ReplayLcdText(
                    cropProgress.stage +
                        if (cropProgress.total > 0) " ${cropProgress.completed}/${cropProgress.total}" else ""
                )
                if (cropProgress.total > 0) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { cropProgress.completed.toFloat() / cropProgress.total },
                        modifier = Modifier.fillMaxWidth(),
                        color = TerminalGreen,
                    )
                } else {
                    androidx.compose.material3.LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = TerminalGreen,
                    )
                }
                cropError?.let { ReplayLcdText(it) }
                ReplayLcdButton("Exit Replay", onBack, focused = true, revealFocus = false, compact = true)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF163721))
                .border(1.dp, TerminalGreen),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PREPARING REPLAY…", color = TerminalGreen, fontSize = 24.sp)
                Text(cropProgress.stage, color = TerminalGreen.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }
        return
    }
    val reviewed = requireNotNull(preparation.replay)
    val archivedCropIdentities = reviewed.identities
    val fastMovesBySpecies = reviewed.fastMoves
    val chargedMovesBySpecies = reviewed.chargedMoves
    val reviewComplete = preparation.complete
    val replayArchive = reviewed.archive
    val typeSpeciesNames = remember(replayArchive, archivedCropIdentities) {
        (MatchReplayModel.referencedSpeciesNames(replayArchive) + archivedCropIdentities.map { it.speciesName }).distinct()
    }
    val speciesTypes by produceState<Map<String, List<PokemonType>>>(emptyMap(), replayArchive, typeSpeciesNames) {
        value = typeSpeciesNames.associate { name ->
            name.uppercase().filter(Char::isLetterOrDigit) to resolveSpeciesTypes(name).distinct()
        }
    }
    val context = LocalContext.current
    val correctionStore = remember(context) { com.example.overdex.battle.replay.ReplayTrainerCorrectionStore(context.filesDir) }
    var corrections by remember(replayArchive) { mutableStateOf(if (sharedReplay == null) correctionStore.list(replayArchive.matchId) else emptyList()) }
    val trainerEnd = model.matchStartNanos?.let { start -> corrections.mapNotNull { entry ->
        runCatching { Math.addExact(Math.addExact(start, entry.matchOffsetNanos), 1_000_000_000L) }.getOrNull()
    }.maxOrNull() }
    val playbackEnd = playbackWindow?.endNanos ?: maxOf(model.endNanos, trainerEnd ?: model.endNanos)
    val playbackStart = com.example.overdex.battle.replay.replayDefaultPosition(
        playbackWindow?.startNanos ?: model.startNanos, playbackEnd, model.matchStartNanos, playbackWindow != null)

    val trainerProjection = remember(replayArchive, corrections, model.matchStartNanos, fastMovesBySpecies, chargedMovesBySpecies) {
        model.matchStartNanos?.let { ReplayTrainerProjection(replayArchive.matchId, it, corrections,
            (fastMovesBySpecies.keys + chargedMovesBySpecies.keys).associateWith { name ->
                fastMovesBySpecies[name].orEmpty() + chargedMovesBySpecies[name].orEmpty()
            }) }
    }
    val eventMarkers = remember(model, playbackStart, playbackEnd, trainerProjection) {
        ReplayEventMarkers.lcdMarkers((model.eventMarkers + trainerProjection?.markers().orEmpty())
            .filter { it.atNanos in playbackStart..playbackEnd }.sortedBy { it.atNanos })
    }
    var selectedMarkerId by remember(replayArchive.matchId, playbackWindow) { mutableStateOf<String?>(null) }
    var cursor by remember(replayArchive.matchId, initialCursorNanos, playbackWindow) {
        mutableLongStateOf((initialCursorNanos ?: playbackStart).coerceIn(playbackStart, playbackEnd))
    }
    var playing by remember(replayArchive.matchId, playbackWindow) { mutableStateOf(false) }
    var autoStarted by remember(replayArchive.matchId, playbackWindow) { mutableStateOf(false) }
    var playbackSpeed by remember(replayArchive.matchId, playbackWindow) { mutableFloatStateOf(initialPlaybackSpeed) }
    LaunchedEffect(autoplay, replayArchive.matchId, playbackWindow) {
        if (autoplay && !autoStarted) {
            autoStarted = true
            playing = true
        }
    }
    fun setSpeed(speed: Float) {
        playbackSpeed = speed
        onPlaybackSpeedChange(speed)
    }
    val currentModel by rememberUpdatedState(model)
    val pairScope = rememberCoroutineScope()
    var pendingPair by remember(replayArchive) { mutableStateOf<com.example.overdex.battle.replay.PairedReplayEvidence?>(null) }
    var pairStatus by remember(replayArchive) { mutableStateOf<String?>(null) }
    val pairFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pairScope.launch {
            try {
                val evidence = withContext(Dispatchers.IO) {
                    val stream = requireNotNull(context.contentResolver.openInputStream(uri))
                    val other = com.example.overdex.battle.archive.MatchArchivePackageReader.read(stream)
                    val anchors = com.example.overdex.battle.replay.PairedReplayEvidenceBuilder.proposedAnchors(replayArchive, other)
                    com.example.overdex.battle.replay.PairedReplayEvidenceBuilder.build(replayArchive, other, anchors)
                }
                pendingPair = evidence
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                pairStatus = "PAIR FAILED: ${error.message}"
            }
        }
    }
    pendingPair?.let { candidate ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingPair = null },
            title = { Text("Pair this match?") },
            text = { Text("Confirm this is the other trainer's recording of the same battle. Sides will be reversed. Timing-only matches remain uncertain.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                pairedEvidence = candidate; pendingPair = null; pairStatus = "PAIRED RECORD · TIMING APPROXIMATE"
            }) { Text("Pair") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { pendingPair = null }) { Text("Cancel") } },
        )
    }
    val pairedChargedTypes by produceState<Map<Pair<String, String>, PokemonType>>(emptyMap(), pairedEvidence) {
        value = pairedEvidence?.events.orEmpty().flatMap { it.observations }
            .filter { it.kind == com.example.overdex.battle.replay.PairedReplayObservation.Kind.CHARGED_MOVE }
            .mapNotNull { observation ->
                val species = observation.species ?: return@mapNotNull null
                val move = observation.moveName ?: return@mapNotNull null
                resolveChargedMoveType(species, move)?.let { (species.uppercase() to move.uppercase()) to it }
            }.toMap()
    }
    val pairedProjection = remember(pairedEvidence, model, pairedChargedTypes) {
        pairedEvidence?.takeIf { model !== baseModel }?.let { evidence -> model.matchStartNanos?.let {
            com.example.overdex.battle.replay.PairedReplayProjection(replayArchive.matchId, it, evidence, pairedChargedTypes, model)
        } }
    }
    val primaryScene = trainerProjection?.apply(model.sceneAt(cursor), cursor) ?: model.sceneAt(cursor)
    val pairedScene = pairedProjection?.apply(primaryScene, cursor) ?: primaryScene
    val scene = pairedReplayScene?.invoke(pairedScene, cursor) ?: pairedScene
    val attackSoundEvents = remember(model, trainerProjection) {
        trainerProjection?.soundEvents(model.attackSoundEvents, model::sceneAt) ?: model.attackSoundEvents
    }
    val currentAttackSoundEvents by rememberUpdatedState(attackSoundEvents)
    val duration = (playbackEnd - playbackStart).coerceAtLeast(1L)
    val displayedMatchStart = model.matchStartNanos ?: model.countdown?.goAtNanos
        ?.takeUnless { model.countdown?.timingBasis == com.example.overdex.battle.replay.ReplayCountdownTiming.PRESENTATION_ONLY }
    val matchStartFraction = displayedMatchStart?.takeIf { it in playbackStart..playbackEnd }?.let { matchStartNanos ->
        ((matchStartNanos - playbackStart).toFloat() / duration).coerceIn(0f, 1f)
    }
    val saveScope = rememberCoroutineScope()
    var addingCorrection by remember(replayArchive) { mutableStateOf(false) }
    var editingSpecies by remember(replayArchive) { mutableStateOf<ReplayTrainerCorrection?>(null) }
    var correctionStatus by remember(replayArchive) { mutableStateOf<String?>(null) }
    var correctionExport by remember { mutableStateOf<ByteArray?>(null) }
    var correctionImport by remember { mutableStateOf<ByteArray?>(null) }
    val exportCorrection = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val bytes = correctionExport
        correctionExport = null
        if (uri != null && bytes != null) runCatching {
            requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(bytes) }
        }.onSuccess { correctionStatus = "CORRECTION FILE SAVED" }
            .onFailure { correctionStatus = "COULD NOT SAVE CORRECTION FILE" }
    }
    val importCorrection = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            requireNotNull(context.contentResolver.openInputStream(uri)).use {
                com.example.overdex.battle.replay.ReplayCorrectionTransfer.readBounded(it)
            }.also { com.example.overdex.battle.replay.ReplayCorrectionTransfer.decode(it) }
        }.onSuccess { correctionImport = it }
            .onFailure { correctionStatus = "INVALID CORRECTION FILE: ${it.message}" }
    }
    fun exportEntries() {
        playing = false
        val selected = selectedMarkerId?.takeIf { id -> id.startsWith("trainer:") && eventMarkers.any { it.id == id && it.atNanos == cursor } }?.removePrefix("trainer:")
            ?.let { id -> corrections.singleOrNull { it.id == id } }
        val toExport = if (selected == null) corrections else
            listOfNotNull(selected.finishingMoveId?.let { id -> corrections.singleOrNull { it.id == id } }, selected).distinctBy { it.id }
        runCatching { com.example.overdex.battle.replay.ReplayCorrectionTransfer.encode(
            com.example.overdex.battle.replay.ReplayCorrectionFile(sourceMatchId = replayArchive.matchId, entries = toExport))
        }.onSuccess { correctionExport = it; exportCorrection.launch("overdex-correction.json") }
            .onFailure { correctionStatus = it.message ?: "NO ENTRIES TO EXPORT" }
    }
    fun importEntries() { playing = false; importCorrection.launch(arrayOf("application/json", "application/octet-stream")) }
    correctionImport?.let { bytes ->
        val source = remember(bytes) { com.example.overdex.battle.replay.ReplayCorrectionTransfer.decode(bytes) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { correctionImport = null },
            title = { Text("Import trainer entries?") },
            text = { Text("Confirm this is the same battle. ${source.entries.size} entries from ${source.entries.map { it.authorName }.distinct().joinToString()} will use their match-relative times. Names in a file are self-reported. Choose whether the other recording uses the opposite perspective.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                correctionImport = null
                saveScope.launch {
                    try { val count = withContext(Dispatchers.IO) { correctionStore.importFile(bytes, replayArchive.matchId, true) }
                        corrections = withContext(Dispatchers.IO) { correctionStore.list(replayArchive.matchId) }; correctionStatus = "IMPORTED $count ENTRIES · SIDES REVERSED"
                    } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; correctionStatus = "IMPORT FAILED: ${e.message}" }
                }
            }) { Text("Opposite sides") } },
            dismissButton = { Row {
                androidx.compose.material3.TextButton(onClick = {
                    correctionImport = null
                    saveScope.launch {
                        try { val count = withContext(Dispatchers.IO) { correctionStore.importFile(bytes, replayArchive.matchId, false) }
                            corrections = withContext(Dispatchers.IO) { correctionStore.list(replayArchive.matchId) }; correctionStatus = "IMPORTED $count ENTRIES"
                        } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; correctionStatus = "IMPORT FAILED: ${e.message}" }
                    }
                }) { Text("Same sides") }
                androidx.compose.material3.TextButton(onClick = { correctionImport = null }) { Text("Cancel") }
            } },
        )
    }
    var clipStart by remember(replayArchive) { mutableStateOf<Long?>(null) }
    var clipEnd by remember(replayArchive) { mutableStateOf<Long?>(null) }
    var savingClip by remember { mutableStateOf(false) }
    var clipStatus by remember { mutableStateOf<String?>(null) }
    var lcdView by remember(replayArchive.matchId, playbackWindow) { mutableStateOf(ReplayLcdView.Playback) }
    var lcdSection by remember(replayArchive.matchId, playbackWindow) { mutableStateOf<ReplayLcdSection?>(null) }
    var compactTabs by remember { mutableStateOf(false) }
    var viewChooserOpen by remember(replayArchive.matchId, playbackWindow) { mutableStateOf(false) }
    var focusedAction by remember(replayArchive.matchId, playbackWindow) { mutableStateOf("play") }
    var revealFocus by remember { mutableStateOf(false) }
    var eventScrollRevision by remember(replayArchive.matchId, playbackWindow) { mutableIntStateOf(0) }
    val completion by rememberUpdatedState(onPlaybackComplete)
    fun addEvent() {
        playing = false
        val start = model.matchStartNanos
        if (start == null || cursor < start) correctionStatus = "SEEK TO A RECORDED MATCH TIME FIRST"
        else addingCorrection = true
    }
    val editableCorrections = remember(corrections) { corrections.filter { it.kind != ReplayCorrectionKind.CONCESSION } }
    if (addingCorrection) ReplayCorrectionDialog(
        matchId = replayArchive.matchId,
        offsetNanos = cursor - requireNotNull(model.matchStartNanos),
        priorEntries = corrections,
        onEditSaved = if (editableCorrections.isEmpty()) null else ({
            addingCorrection = false
            if (editableCorrections.size == 1) editingSpecies = editableCorrections.single()
            else {
                lcdView = ReplayLcdView.Menu
                lcdSection = ReplayLcdSection.Corrections
                focusedAction = "edit-species-${editableCorrections.first().id}"
                revealFocus = true
            }
        }),
        onDismiss = { addingCorrection = false },
        onSave = { correction ->
            addingCorrection = false
            saveScope.launch {
                try {
                    withContext(Dispatchers.IO) { correctionStore.add(correction) }
                    corrections = withContext(Dispatchers.IO) { correctionStore.list(replayArchive.matchId) }
                    correctionStatus = "TRAINER ENTRY SAVED"
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    correctionStatus = "EVENT NOT SAVED: ${e.message}"
                }
            }
        },
    )
    editingSpecies?.let { entry ->
        var species by remember(entry) { mutableStateOf(entry.speciesName) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editingSpecies = null },
            title = { Text("Edit trainer entry") },
            text = { androidx.compose.material3.OutlinedTextField(species, { species = it },
                singleLine = true, label = { Text("Pokémon species") }) },
            confirmButton = { androidx.compose.material3.TextButton(enabled = species.trim().length in 1..100,
                onClick = {
                    editingSpecies = null
                    saveScope.launch {
                        try {
                            corrections = withContext(Dispatchers.IO) {
                                correctionStore.changeSpecies(entry, species)
                                correctionStore.list(entry.matchId)
                            }
                            correctionStatus = "SPECIES UPDATED"
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            correctionStatus = "EDIT FAILED: ${e.message}"
                        }
                    }
                }) { Text("Save") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { editingSpecies = null }) { Text("Cancel") } },
        )
    }
    fun markClipStart() {
        playing = false
        clipStart = cursor
        clipStatus = "START ${formatReplayTime(cursor - playbackStart)}"
    }
    fun markClipEnd() {
        playing = false
        if (clipStart == null) clipStatus = "MARK START FIRST"
        else if (cursor <= clipStart!!) clipStatus = "MOVE FORWARD TO SET THE END"
        else clipEnd = cursor
    }
    if (clipEnd != null) ReplayNameDialog(
        heading = "Save replay clip", initialTitle = "Clip", initialNotes = "",
        onDismiss = { clipEnd = null },
        onSave = { title, notes ->
            val start = clipStart!!
            val end = clipEnd!!
            clipEnd = null
            savingClip = true
            saveScope.launch {
                try {
                    val clip = withContext(Dispatchers.IO) {
                        ReplayExcerptStore(context.filesDir).saveRange(replayArchive, start, end, title, notes,
                            recoveredIdentities = archivedCropIdentities)
                    }
                    clipStatus = "SAVED: ${clip.title}"
                    clipStart = null
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    clipStatus = "NOT SAVED: ${e.message}"
                } finally { savingClip = false }
            }
        },
    )
    val replayPreferences = remember(context) { context.getSharedPreferences("match_replay", Context.MODE_PRIVATE) }
    var eventBlipsEnabled by remember(replayPreferences) {
        mutableStateOf(replayPreferences.getBoolean("event_blips_enabled", false))
    }
    var attackSfxEnabled by remember(replayPreferences) {
        mutableStateOf(replayPreferences.getBoolean("attack_sfx_enabled", true))
    }
    val currentAttackSfxEnabled by rememberUpdatedState(attackSfxEnabled)
    val attackSounds = remember(context) { ReplayAttackSounds(context) }
    LaunchedEffect(attackSoundEvents, attackSounds) { attackSounds.prepare(attackSoundEvents) }
    LaunchedEffect(playing, attackSfxEnabled, playbackSpeed) { attackSounds.stop() }
    val currentEventBlipsEnabled by rememberUpdatedState(eventBlipsEnabled)
    val transportSounds = remember { ReplayTransportSounds(context) }
    val replayHaptics = remember { ReplayHaptics(context) }

    DisposableEffect(transportSounds, replayHaptics, attackSounds) {
        onDispose {
            attackSounds.release()
            transportSounds.release()
            replayHaptics.release()
        }
    }

    LaunchedEffect(transportSounds) {
        transportSounds.insert()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        playing = false
        transportSounds.endScrub()
        attackSounds.stop()
        replayHaptics.release()
    }

    fun togglePlayback() {
        if (!playing && cursor >= playbackEnd) cursor = playbackStart
        playing = !playing
        if (playing) transportSounds.play() else transportSounds.pause()
    }

    fun scrubBy(deltaNanos: Long) {
        playing = false
        val previous = cursor
        cursor = (cursor + deltaNanos).coerceIn(playbackStart, playbackEnd)
        if (cursor != previous) transportSounds.scrub()
    }

    fun seekMarker(marker: ReplayEventMarker) {
        playing = false
        selectedMarkerId = marker.id
        cursor = marker.atNanos.coerceIn(playbackStart, playbackEnd)
        attackSounds.stop()
        transportSounds.scrub()
    }

    fun seekAdjacentMarker(forward: Boolean) {
        val index = if (forward) ReplayEventMarkerNavigation.nextIndex(eventMarkers, cursor, selectedMarkerId)
            else ReplayEventMarkerNavigation.previousIndex(eventMarkers, cursor, selectedMarkerId)
        index?.let { seekMarker(eventMarkers[it]) }
    }

    fun changeView(view: ReplayLcdView) {
        lcdView = view
        lcdSection = null
        viewChooserOpen = false
        revealFocus = false
        focusedAction = when (view) {
            ReplayLcdView.Playback -> "play"
            ReplayLcdView.Events -> "next"
            ReplayLcdView.Menu -> "settings"
        }
    }
    fun exitReplay() {
        if (!savingClip) {
            playing = false
            transportSounds.stop()
            onBack()
        }
    }
    fun returnFromLcd() {
        when {
            viewChooserOpen -> {
                viewChooserOpen = false
                focusedAction = "views"
            }
            lcdSection != null -> {
                focusedAction = lcdSection!!.id
                lcdSection = null
            }
            lcdView != ReplayLcdView.Playback -> changeView(ReplayLcdView.Playback)
            else -> exitReplay()
        }
    }
    fun seekEvent(forward: Boolean) {
        seekAdjacentMarker(forward)
        revealFocus = false
        eventScrollRevision++
    }
    fun changeSpeed(delta: Float) {
        val speed = ((playbackSpeed + delta) * 10f).roundToInt() / 10f
        setSpeed(speed.coerceIn(ReplayPlaybackClock.MIN_SPEED, ReplayPlaybackClock.MAX_SPEED))
    }
    fun openSection(section: ReplayLcdSection) {
        lcdSection = section
        focusedAction = "menu"
    }

    val tabs = ReplayLcdView.entries.map { view ->
        ReplayLcdAction("view-${view.name}", view.name, run = { changeView(view) })
    }
    val chooser = ReplayLcdAction("views", "Change view", run = {
        viewChooserOpen = true
        focusedAction = "view-${lcdView.name}"
    })
    val exit = ReplayLcdAction("exit", "Exit Replay", enabled = !savingClip, run = ::exitReplay)
    val play = ReplayLcdAction("play", if (playing) "Pause" else "Play", run = ::togglePlayback)
    val playbackActions = listOf(
        ReplayLcdAction("step-back", "−1 sec", "Step backward one second", run = { scrubBy(-SCRUB_STEP_NANOS) }),
        play,
        ReplayLcdAction("step-forward", "+1 sec", "Step forward one second", run = { scrubBy(SCRUB_STEP_NANOS) }),
    )
    val selectedDecision = if (lcdView == ReplayLcdView.Events && !viewChooserOpen) {
        val index = ReplayEventMarkerNavigation.currentIndex(eventMarkers, cursor, selectedMarkerId)
        eventMarkers.getOrNull(index)?.let(model::faintDecision)
    } else null
    var showDecisionSources by remember(model, selectedDecision?.markerId) { mutableStateOf(false) }
    val decisionSources = selectedDecision?.let {
        ReplayLcdAction("decision-sources", if (showDecisionSources) "Hide sources" else "Show sources",
            run = { showDecisionSources = !showDecisionSources })
    }
    val eventActions = listOf(
        ReplayLcdAction("previous", "Previous", "Previous replay event",
            ReplayEventMarkerNavigation.previousIndex(eventMarkers, cursor, selectedMarkerId) != null,
            run = { seekEvent(false) }),
        ReplayLcdAction("next", "Next", "Next replay event",
            ReplayEventMarkerNavigation.nextIndex(eventMarkers, cursor, selectedMarkerId) != null,
            run = { seekEvent(true) }),
    )
    val section = lcdSection
    val menuActions = when (section) {
        null -> buildList {
            ReplayLcdSection.entries.filter {
                when (it) {
                    ReplayLcdSection.Clips -> allowClipSave || onOpenEditor != null
                    ReplayLcdSection.Corrections -> sharedReplay == null
                    else -> true
                }
            }
                .forEach { entry -> add(ReplayLcdAction(entry.id, entry.title, run = { openSection(entry) })) }
            add(exit)
        }
        ReplayLcdSection.Settings -> listOf(
            ReplayLcdAction("slower", "Slower by 0.1×", enabled = playbackSpeed > ReplayPlaybackClock.MIN_SPEED,
                run = { changeSpeed(-0.1f) }),
            ReplayLcdAction("faster", "Faster by 0.1×", enabled = playbackSpeed < ReplayPlaybackClock.MAX_SPEED,
                run = { changeSpeed(0.1f) }),
            ReplayLcdAction("speed-reset", "Reset speed to 1×", "Reset replay speed to 1x", run = { setSpeed(1f) }),
            ReplayLcdAction("blips", "Event blips: ${if (eventBlipsEnabled) "On" else "Off"}", "Replay event blips",
                selected = eventBlipsEnabled, run = {
                    eventBlipsEnabled = !eventBlipsEnabled
                    replayPreferences.edit().putBoolean("event_blips_enabled", eventBlipsEnabled).apply()
                }),
            ReplayLcdAction("sounds", "Attack sounds: ${if (attackSfxEnabled) "On" else "Off"}", "Replay attack sounds",
                selected = attackSfxEnabled, run = {
                    attackSfxEnabled = !attackSfxEnabled
                    if (!attackSfxEnabled) attackSounds.stop()
                    replayPreferences.edit().putBoolean("attack_sfx_enabled", attackSfxEnabled).apply()
                }),
            ReplayLcdAction("restart", "Restart Replay", run = {
                cursor = playbackStart
                playing = false
                transportSounds.reset()
            }),
        )
        ReplayLcdSection.Corrections -> listOf(
            ReplayLcdAction("add-event", "Add event", run = ::addEvent),
            ReplayLcdAction("export", "Export corrections", run = ::exportEntries),
            ReplayLcdAction("import", "Import corrections", run = ::importEntries),
            ReplayLcdAction("pair", "Pair match", run = {
                pairFile.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
            }),
        ) + editableCorrections.map { entry ->
            ReplayLcdAction("edit-species-${entry.id}", "Edit ${entry.speciesName} · ${entry.side.name.lowercase()} · " +
                "${entry.moveName ?: "faint"} · ${formatReplayTime(entry.matchOffsetNanos)}", run = {
                playing = false
                editingSpecies = entry
            })
        }
        ReplayLcdSection.Clips -> buildList {
            if (allowClipSave) {
                add(ReplayLcdAction("mark-start", "Mark start", enabled = !savingClip, run = ::markClipStart))
                add(ReplayLcdAction("mark-end", "End and save", enabled = !savingClip, run = ::markClipEnd))
            }
            onOpenEditor?.let { open ->
                add(ReplayLcdAction("clip-library", "Clip library", enabled = !savingClip, run = {
                    playing = false
                    open()
                }))
            }
        }
        ReplayLcdSection.Details -> emptyList()
    }
    val menuBack = ReplayLcdAction("menu", "Back to Menu", run = ::returnFromLcd)
    val viewActions = when {
        viewChooserOpen -> tabs
        section != null -> listOf(menuBack) + menuActions
        else -> (if (compactTabs) listOf(chooser) else tabs) + when (lcdView) {
            ReplayLcdView.Playback -> playbackActions
            ReplayLcdView.Events -> eventActions + listOfNotNull(decisionSources)
            ReplayLcdView.Menu -> menuActions
        }
    }
    val enabledActions = viewActions.filter { it.enabled }
    val activeAction = enabledActions.firstOrNull { it.id == focusedAction }
        ?: enabledActions.firstOrNull { it.id == "previous" || it.id == "next" }
        ?: enabledActions.firstOrNull()
    fun moveFocus(delta: Int) {
        if (enabledActions.isEmpty()) return
        val current = enabledActions.indexOf(activeAction)
        focusedAction = enabledActions[Math.floorMod(current + delta, enabledActions.size)].id
        revealFocus = true
    }
    fun activate() {
        if (editingSpecies != null || addingCorrection || correctionImport != null || clipEnd != null || savingClip) return
        activeAction?.run?.invoke()
    }
    BackHandler(enabled = editingSpecies == null && !addingCorrection && correctionImport == null && clipEnd == null) { returnFromLcd() }
    SideEffect {
        onUp {
            when {
                viewChooserOpen -> moveFocus(-1)
                section == ReplayLcdSection.Clips -> scrubBy(-SCRUB_STEP_NANOS)
                lcdView == ReplayLcdView.Menu -> moveFocus(-1)
                lcdView == ReplayLcdView.Events -> seekEvent(false)
                else -> scrubBy(-SCRUB_STEP_NANOS)
            }
        }
        onDown {
            when {
                viewChooserOpen -> moveFocus(1)
                section == ReplayLcdSection.Clips -> scrubBy(SCRUB_STEP_NANOS)
                lcdView == ReplayLcdView.Menu -> moveFocus(1)
                lcdView == ReplayLcdView.Events -> seekEvent(true)
                else -> scrubBy(SCRUB_STEP_NANOS)
            }
        }
        onLeft { moveFocus(-1) }
        onRight { moveFocus(1) }
        onA { activate() }
        onB { returnFromLcd() }
        onLcdTap { togglePlayback() }
        onLcdDrag { delta -> scrubBy((duration * delta.x / LCD_SCRUB_WIDTH_PX).toLong()) }
    }

    // A first-pass scene can only show the result withdrawal while the ending sounds are pending.
    // Keep the selected seek position, but never play a provisional departure past the user.
    val pendingFinishStart = if (!reviewComplete && replayArchive.mediaIncluded && impactArchiveUri != null)
        model.matchEndNanos?.let { (it - 20_000_000_000L).coerceAtLeast(model.matchStartNanos ?: model.startNanos) }
            ?.takeIf { it <= playbackEnd }?.coerceAtLeast(playbackStart)
    else null
    PublishMatchLcd(onLcdContentUpdate) {
        val scroll = rememberScrollState()
        LaunchedEffect(lcdView, section, viewChooserOpen, eventScrollRevision) { scroll.scrollTo(0) }

        @Composable fun Action(
            action: ReplayLcdAction,
            modifier: Modifier = Modifier.fillMaxWidth(),
            alignedStart: Boolean = true,
        ) {
            ReplayLcdButton(action.label, {
                focusedAction = action.id
                revealFocus = true
                action.run()
            }, modifier.semantics { action.description?.let { contentDescription = it } },
                focused = activeAction?.id == action.id, enabled = action.enabled,
                selected = action.selected, alignedStart = alignedStart, revealFocus = revealFocus, compact = true)
        }

        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
            val stackControls = maxWidth < 184.dp || LocalDensity.current.fontScale > 1.35f
            Column(Modifier.fillMaxSize().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (section == null) {
                    ReplayLcdViewTabs(lcdView, viewChooserOpen, ReplayLcdView.entries.firstOrNull {
                        activeAction?.id == "view-${it.name}"
                    }, activeAction?.id == "views", chooser.run, ::changeView, { compactTabs = it })
                } else {
                    Action(menuBack)
                    ReplayLcdText(section.title, heading = true)
                }
                if (viewChooserOpen) return@Column
                when (lcdView) {
                    ReplayLcdView.Playback -> {
                        playbackLabel?.let { ReplayLcdText(it) }
                        ReplayLcdTimeline(cursor, playbackStart, duration, playing, eventMarkers, matchStartFraction,
                            waitingForReview = pendingFinishStart != null && cursor >= pendingFinishStart,
                            ::scrubBy, transportSounds::endScrub)
                        ReplayLcdActionRow(playbackActions, stackControls) { action, modifier ->
                            Action(action, modifier, alignedStart = stackControls)
                        }
                        ReplayLcdEventDetails(eventMarkers, cursor, selectedMarkerId, playbackStart,
                            model.matchStartNanos, model.startNanos, playbackSummary = true)
                    }
                    ReplayLcdView.Events -> {
                        ReplayLcdEventDetails(eventMarkers, cursor, selectedMarkerId, playbackStart,
                            model.matchStartNanos, model.startNanos)
                        ReplayLcdActionRow(eventActions, stackControls) { action, modifier ->
                            Action(action, modifier, alignedStart = stackControls)
                        }
                        selectedDecision?.let { record ->
                            ReplayLcdText("Faint decision", heading = true)
                            if (record.credit == null) ReplayLcdText("Finishing attack unresolved")
                            val origin = model.matchStartNanos ?: model.startNanos
                            for (entry in record.entries) {
                                val ms = kotlin.math.round((entry.atNanos - origin) / 1_000_000.0).toLong()
                                ReplayLcdText(String.format(Locale.ROOT, "%d:%02d.%03d · %s",
                                    ms / 60_000, ms / 1_000 % 60, ms % 1_000, entry.description))
                                if (showDecisionSources) entry.sourceArticleIds.forEach { ReplayLcdText(it) }
                            }
                            decisionSources?.let { Action(it) }
                        }
                    }
                    ReplayLcdView.Menu -> {
                        if (section == ReplayLcdSection.Settings) {
                            ReplayLcdText("Playback speed ${String.format(Locale.ROOT, "%.1f×", playbackSpeed)}")
                            Slider(
                                value = playbackSpeed,
                                onValueChange = { setSpeed((it * 10f).roundToInt() / 10f) },
                                valueRange = ReplayPlaybackClock.MIN_SPEED..ReplayPlaybackClock.MAX_SPEED,
                                steps = 48,
                                modifier = Modifier.fillMaxWidth().height(28.dp).semantics {
                                    contentDescription = "Replay speed"
                                    stateDescription = String.format(Locale.ROOT, "%.1f times", playbackSpeed)
                                },
                                colors = SliderDefaults.colors(thumbColor = TerminalGreen, activeTrackColor = TerminalGreen),
                                thumb = { Box(Modifier.size(12.dp).background(TerminalGreen, CircleShape)) },
                                track = { state ->
                                    SliderDefaults.Track(
                                        sliderState = state,
                                        modifier = Modifier.height(4.dp),
                                        colors = SliderDefaults.colors(
                                            activeTrackColor = TerminalGreen,
                                            inactiveTrackColor = TerminalGreen.copy(alpha = 0.25f),
                                        ),
                                        drawTick = { _, _ -> },
                                        drawStopIndicator = null,
                                        thumbTrackGapSize = 0.dp,
                                    )
                                },
                            )
                        }
                        if (section == ReplayLcdSection.Clips) {
                            ReplayLcdText("Clip at ${formatReplayTime(cursor - playbackStart)}")
                        }
                        menuActions.forEach { Action(it) }
                        when (section) {
                            ReplayLcdSection.Corrections -> {
                                ReplayLcdText("Select a trainer event to export it, or export all corrections.")
                                pairStatus?.let { ReplayLcdText(it) }
                                pairedEvidence?.let { paired ->
                                    ReplayLcdText("Paired candidates ${paired.events.count { it.ambiguous }} · Approximate timing")
                                }
                                correctionStatus?.let { ReplayLcdText(it) }
                                corrections.forEach { entry ->
                                    val credited = entry.finishingMoveId?.let { id -> corrections.firstOrNull { it.id == id } }
                                    ReplayLcdText("${formatReplayTime(entry.matchOffsetNanos)} · ${entry.side.name} · " +
                                        "${entry.speciesName} · ${entry.moveName ?: "Faint"} · ${entry.authorName}" +
                                        (credited?.let { " · KO credit ${it.moveName}" } ?: ""))
                                }
                            }
                            ReplayLcdSection.Clips -> {
                                if (savingClip) ReplayLcdText("Saving clip…")
                                clipStatus?.let { ReplayLcdText(it) }
                            }
                            ReplayLcdSection.Details -> {
                                scene.countdownFrame?.let { ReplayLcdText(it.lcdLabel) }
                                ReplayLcdText("Estimated energy", heading = true)
                                ReplayLcdText("Player ${energyLedger(scene.playerEnergy)}")
                                ReplayLcdText("Opponent ${energyLedger(scene.opponentEnergy)}")
                                scene.statChanges.filter { it.side == null }.forEach {
                                    ReplayLcdText("${it.label}${it.moveName?.let { move -> " · $move" }.orEmpty()} · Side unconfirmed")
                                }
                                ReplayLcdText(if (reviewComplete) "Review complete" else cropProgress.stage +
                                    if (cropProgress.total > 0) " ${cropProgress.completed}/${cropProgress.total}" else "")
                                cropError?.let { ReplayLcdText(it) }
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(playing, playbackSpeed, playbackEnd, pendingFinishStart) {
        // Cursor is deliberately not an effect key. A frame is a chance to draw,
        // not a fixed amount of Match time; slow frames must not slow the clock.
        val readyThrough = pendingFinishStart ?: playbackEnd
        if (pendingFinishStart != null && cursor >= readyThrough) return@LaunchedEffect
        val clock = ReplayPlaybackClock(cursor, readyThrough, playbackSpeed)
        while (playing && cursor < readyThrough) {
            val frameNanos = withFrameNanos { it }
            if (!playing) break
            val previous = cursor
            cursor = clock.positionAt(frameNanos)
            if (currentEventBlipsEnabled && currentModel.crossedArticleBoundary(previous, cursor)) transportSounds.tick()
            if (currentAttackSfxEnabled) {
                replayAttackSoundsBetween(currentAttackSoundEvents, previous, cursor).forEach {
                    attackSounds.play(it, playbackSpeed)
                }
            }
            currentModel.hapticEventsBetween(previous, cursor).forEach { event ->
                replayHaptics.play(event.pulseCount)
            }
        }
        if (pendingFinishStart == null && playing && cursor >= playbackEnd) {
            playing = false
            transportSounds.stop()
            completion()
        }
    }

    val playerTypeReference = remember(replayArchive, scene.player?.speciesName) {
        scene.player?.speciesName?.let { name ->
            com.example.overdex.data.PokemonFormReference.playerReferenceName(name,
                com.example.overdex.battle.replay.latestArchivedPlayerRoster(replayArchive.articles, Long.MAX_VALUE))
        }
    }
    if (pendingFinishStart != null && cursor >= pendingFinishStart) {
        Box(Modifier.fillMaxSize().background(Color(0xFF163721)), contentAlignment = Alignment.Center) {
            Text("Checking the finish…", color = TerminalGreen)
        }
    } else ReplayBattlefield(scene, cursor, context, speciesTypes, playerTypeReference)
}

@Composable
internal fun ReplayBattlefield(scene: ReplayScene, cursor: Long, context: Context,
                               speciesTypes: Map<String, List<PokemonType>> = emptyMap(),
                               playerTypeReference: String? = null) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(2.dp)
                .background(Color(0xFF163721))
                .border(1.dp, TerminalGreen),
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val travelHalfWidth = (maxWidth / 4).coerceAtLeast(0.dp)
                val spriteSize = minOf(130.dp * 1.15f, maxWidth / 2 + 18.dp)
                val chargePreparation = scene.chargeFocus
                val playerOffset = ReplayChargePose.qteSpriteTranslationXPixels(chargePreparation, cursor, player = true)
                val opponentOffset = ReplayChargePose.qteSpriteTranslationXPixels(chargePreparation, cursor, player = false)
                val opponentScale = ReplayChargePose.qteSpriteScale(chargePreparation, cursor, player = false)
                var opponentArt by remember(scene.opponent?.speciesId, scene.opponent?.speciesName) {
                    mutableStateOf<ReplaySpriteVisibleBounds?>(null)
                }
                val opponentFrontFraction = opponentArt?.let { art ->
                    val leadingEdge = if (scene.opponent?.speciesId == 776) 1f - art.right else art.left
                    // A small inset overlaps the facing silhouette instead of targeting
                    // transparent PNG margins or the middle of a wide sprite.
                    leadingEdge + (art.right - art.left) * .10f
                } ?: .22f
                val opponentForwardX = travelHalfWidth + spriteSize * 0.9f *
                    (opponentFrontFraction - .5f) * opponentScale
                val opponentForwardY = spriteSize * 0.9f * (opponentArt?.let { art ->
                    // Place the glyph on the visible upper body. A fixed shoulder height
                    // can float above short, wide art, especially with a head-anchored scale.
                    art.top + (art.bottom - art.top) * .45f * opponentScale - .5f
                } ?: -.12f)
                // Keep the far combatant behind the attack plane and the player's
                // rear-facing sprite in front. Opponent-side glyphs remain visible
                // over its forward shoulder without painting over the player.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp).align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f))
                    ReplayCombatantSlot(
                        combatant = scene.opponent ?: scene.heldOpponentSprite,
                        cursorNanos = cursor,
                        hp = scene.opponentHp,
                        context = context,
                        useBackSprite = false,
                        fastAction = scene.fastMoveActions.lastOrNull { scene.opponent != null && it.side == "OPPONENT" },
                        chargedAction = scene.chargedMoveActions.lastOrNull { scene.opponent != null && it.side == "OPPONENT" },
                        qteMilestone = scene.qteMilestone.takeIf { scene.opponent != null && scene.qteMilestoneSide == "OPPONENT" },
                        statChanges = scene.statChanges.filter { scene.opponent != null && it.side == "OPPONENT" },
                        spriteSize = spriteSize * 0.9f,
                        qteSpriteScale = opponentScale,
                        qteSpriteTranslationXPixels = opponentOffset,
                        onVisibleArt = { opponentArt = it },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Within the space between sprites, the near shield covers the projectile.
                Box(Modifier.fillMaxSize()) {
                    scene.protectShield?.let { shield ->
                        val playerDefends = shield.defendingSide == "PLAYER"
                        val x = shieldCenterX(playerDefends, travelHalfWidth, spriteSize * 1.15f, opponentForwardX)
                        ProtectShieldGraphic(shield, Modifier.align(Alignment.Center)
                            .zIndex(if (playerDefends) 1f else 0f)
                            .offset(x, if (playerDefends) 24.dp else opponentForwardY)
                            .graphicsLayer {
                                translationX = if (playerDefends) playerOffset else opponentOffset
                                translationY = if (playerDefends) 25f else 0f
                            }.size(spriteSize * .69f))
                    }
                    scene.fastMoveActions.forEach { action ->
                        FastMoveReplayIndicator(action, travelHalfWidth, opponentForwardX, opponentForwardY,
                            Modifier.align(Alignment.Center), playerOffset, opponentOffset)
                    }
                    scene.chargedMoveActions.forEach { action ->
                        val visible = if (action.side == "OPPONENT" && cursor < action.announcementAtNanos &&
                            !(action.hitsProtectShield && action.phase == ReplayChargePhase.STRIKING))
                            action.copy(moveName = null, type = null) else action
                        ChargedMoveReplayIndicator(visible, travelHalfWidth, spriteSize * 1.15f,
                            opponentForwardX, opponentForwardY, Modifier.align(Alignment.Center),
                            performerOffsetPixels = if (action.side == "PLAYER") playerOffset else opponentOffset,
                            recipientOffsetPixels = if (action.side == "PLAYER") opponentOffset else playerOffset)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp).align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ReplayCombatantSlot(
                        combatant = scene.player ?: scene.heldPlayerSprite,
                        cursorNanos = cursor,
                        hp = scene.playerHp,
                        context = context,
                        useBackSprite = true,
                        fastAction = scene.fastMoveActions.lastOrNull { scene.player != null && it.side == "PLAYER" },
                        chargedAction = scene.chargedMoveActions.lastOrNull { scene.player != null && it.side == "PLAYER" },
                        qteMilestone = scene.qteMilestone.takeIf { scene.player != null && scene.qteMilestoneSide == "PLAYER" },
                        statChanges = scene.statChanges.filter { scene.player != null && it.side == "PLAYER" },
                        spriteSize = spriteSize * 1.15f,
                        qteSpriteScale = ReplayChargePose.qteSpriteScale(chargePreparation, cursor, player = true),
                        qteSpriteTranslationXPixels = ReplayChargePose.qteSpriteTranslationXPixels(chargePreparation, cursor, player = true),
                        modifier = Modifier.weight(1f),
                    )
                    Box(Modifier.weight(1f))
                }
                Row(
                    Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(horizontal = 12.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (scene.player != null) ReplayResourceBadge(scene.playerResources, "Player", scene.playerEnergy,
                        types = speciesTypes[(playerTypeReference ?: scene.player.speciesName).uppercase().filter(Char::isLetterOrDigit)].orEmpty())
                    else Box(Modifier.width(114.dp))
                    if (scene.opponent != null) ReplayResourceBadge(scene.opponentResources, "Opponent", scene.opponentEnergy, mirrored = true,
                        types = speciesTypes[scene.opponent.speciesName.uppercase().filter(Char::isLetterOrDigit)].orEmpty())
                }
                if (scene.player != null && !scene.player.isFainted && !scene.combatEnded && scene.outcome == null && scene.trainerConcession == null &&
                    scene.decisionTimer == null && scene.chargedMoveActions.isEmpty()) {
                    ReplayChargeButtons(scene.playerChargedMoves, scene.playerEnergy,
                        Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 18.dp))
                }
                scene.countdownGlyph?.let { glyph ->
                    Text(
                        text = glyph,
                        color = Color.White,
                        fontSize = if (glyph == "GO") 72.sp else 88.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                scene.decisionTimer?.let { timer ->
                    Column(Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(44.dp).background(Color(0xD0233545), CircleShape)
                            .semantics {
                                contentDescription = timer.secondsRemaining?.let { "Decision timer: $it" }
                                    ?: "Decision timer: waiting for replacement"
                                stateDescription = timer.basis
                            }, contentAlignment = Alignment.Center) {
                            Canvas(Modifier.fillMaxSize().padding(2.dp)) {
                                drawCircle(Color.White.copy(alpha = .25f), style = Stroke(2.dp.toPx()))
                                drawArc(Color.White, -90f, 360f * timer.remainingFraction,
                                    useCenter = false, style = Stroke(2.dp.toPx()))
                            }
                            Text(timer.secondsRemaining?.toString() ?: "…", color = Color.White, fontSize = 24.sp)
                        }
                        timer.reason?.let { Text(it, color = Color.White, fontSize = 10.sp) }
                    }
                }
                scene.qteMilestone?.let { milestone ->
                    Text(
                        text = milestone,
                        color = when (milestone) {
                            "NICE" -> Color.White
                            "GREAT" -> Color(0xFFFFD54F)
                            else -> Color(0xFFFF8A65)
                        },
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center).offset(y = (-76).dp),
                    )
                }
                scene.announcement?.takeIf { scene.outcome == null && scene.trainerConcession == null }?.let { announcement ->
                    Text(announcement, color = Color.White, fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                            .background(Color(0xCC233545), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp))
                }
                val concession = scene.trainerConcession ?: ReplayCorrectionSide.PLAYER.takeIf { scene.inferredPlayerConcession }
                val recordedResult = scene.outcome?.let(::replayOutcomeLabel)
                val resultLabel = concession?.concessionLabel ?: recordedResult
                resultLabel?.let { label ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .background(Color(0xE6102418))
                            .border(2.dp, TerminalGreen)
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, color = Color.White, fontSize = if (concession == null) 40.sp else 22.sp,
                                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            if (concession != null) {
                                Text(if (scene.trainerConcession != null) "Trainer entry" else "Inferred from saved evidence",
                                    color = Color.White, fontSize = 14.sp)
                                val expected = if (concession == ReplayCorrectionSide.OPPONENT) "YOU WIN" else "GOOD EFFORT"
                                if (recordedResult != null && recordedResult != expected) {
                                    Text("Recorded result · $recordedResult", color = Color.White, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Replays accepted QTE vibration testimony only while the archive is playing forward. */
private class ReplayHaptics(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun play(pulseCount: Int) {
        val pulses = pulseCount.coerceIn(1, 4)
        val timings = LongArray(pulses * 2) { index -> if (index % 2 == 0) 0L else 36L }
        for (index in 2 until timings.size step 2) timings[index] = 54L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(timings, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(timings, -1)
        }
    }

    fun release() {
        vibrator?.cancel()
    }
}

private fun energyLedger(state: com.example.overdex.battle.replay.ReplayEnergyState?): String =
    state?.let { "~${it.balanceLabel}/100" } ?: "—"

private fun replayOutcomeLabel(outcome: String): String = when (
    outcome.uppercase().filter(Char::isLetterOrDigit)
) {
    "WIN", "WON", "VICTORY", "PLAYERWIN" -> "YOU WIN"
    "LOSS", "LOSE", "DEFEAT", "OPPONENTWIN" -> "GOOD EFFORT"
    "TIE", "DRAW" -> "TIE"
    else -> outcome.replace('_', ' ').uppercase()
}

@Composable
private fun FastMoveReplayIndicator(
    action: ReplayFastMoveAction,
    travelHalfWidth: androidx.compose.ui.unit.Dp,
    opponentForwardX: androidx.compose.ui.unit.Dp,
    opponentForwardY: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    playerOffsetPixels: Float = 0f,
    opponentOffsetPixels: Float = 0f,
) {
    val progress = action.progress.coerceIn(0f, 1f)
    val player = action.side == "PLAYER"
    val originX = if (player) -travelHalfWidth else opponentForwardX
    val targetX = if (player) opponentForwardX else -travelHalfWidth
    val originY = if (player) 24.dp else opponentForwardY
    val targetY = if (player) opponentForwardY else 24.dp
    val indicatorColor = action.type?.color ?: Color(0xFF88AFA0)
    Box(
        modifier = modifier
            .offset(x = originX * (1f - progress) + targetX * progress,
                y = originY * (1f - progress) + targetY * progress)
            .graphicsLayer {
                // Finish at the recipient's actual sprite plane, including the
                // player's lower rear-view placement, rather than above its head.
                translationY = 25f * if (player) 1f - progress else progress
                translationX = if (player) playerOffsetPixels * (1f - progress) + opponentOffsetPixels * progress
                    else opponentOffsetPixels * (1f - progress) + playerOffsetPixels * progress
            }
            .size(34.dp)
            .background(indicatorColor.copy(alpha = 0.18f), CircleShape)
            .border(1.dp, indicatorColor, CircleShape)
            .semantics {
                contentDescription = "${action.side} ${action.moveName ?: "Fast Move"} projectile"
                stateDescription = if (action.progress >= 1f) "Impact" else "In flight"
            },
        contentAlignment = Alignment.Center
    ) {
        action.type?.let { type ->
            PokemonTypeIcon(type, style = TypeIconStyle.OVERDEX, modifier = Modifier.size(26.dp))
        } ?: Box(
            Modifier
                .size(12.dp)
                .border(1.dp, indicatorColor, CircleShape)
        )
    }
}

private enum class ReplayLcdSection(val id: String, val title: String) {
    Settings("settings", "Playback settings"),
    Corrections("corrections", "Trainer corrections"),
    Clips("clips", "Clips"),
    Details("details", "Match details"),
}

private data class ReplayLcdAction(
    val id: String,
    val label: String,
    val description: String? = null,
    val enabled: Boolean = true,
    val selected: Boolean? = null,
    val run: () -> Unit,
)

@Composable
private fun ReplayLcdActionRow(
    actions: List<ReplayLcdAction>,
    stacked: Boolean,
    content: @Composable (ReplayLcdAction, Modifier) -> Unit,
) {
    if (stacked) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            actions.forEach { content(it, Modifier.fillMaxWidth()) }
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            actions.forEach { content(it, Modifier.weight(1f)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReplayLcdTimeline(
    cursor: Long,
    start: Long,
    duration: Long,
    playing: Boolean,
    markers: List<ReplayEventMarker>,
    goFraction: Float?,
    waitingForReview: Boolean,
    scrubBy: (Long) -> Unit,
    onScrubFinished: () -> Unit,
) {
    val scrub by rememberUpdatedState(scrubBy)
    val finish by rememberUpdatedState(onScrubFinished)
    val elapsed = (cursor - start).coerceIn(0L, duration)
    val currentElapsed by rememberUpdatedState(elapsed)
    val fraction = (elapsed.toDouble() / duration).toFloat()
    Column(Modifier.fillMaxWidth().semantics {
        contentDescription = "Replay timeline"
        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
        setProgress { progress ->
            scrubBy((duration * progress.coerceIn(0f, 1f)).toLong() - elapsed)
            onScrubFinished()
            true
        }
    }.pointerInput(duration) {
        detectHorizontalDragGestures(onDragEnd = { finish() }, onDragCancel = { finish() }) { change, delta ->
            change.consume()
            scrub((duration * delta / size.width.coerceAtLeast(1)).toLong())
        }
    }.pointerInput(duration) {
        detectTapGestures { offset ->
            val target = (duration * (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)).toLong()
            scrub(target - currentElapsed)
            finish()
        }
    }, verticalArrangement = Arrangement.Center) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically) {
            ReplayLcdText("${formatReplayTime(elapsed)} / ${formatReplayTime(duration)}", heading = true)
            ReplayLcdText(when {
                playing && waitingForReview -> "Reviewing ending…"
                playing -> "Playing"
                else -> "Paused"
            })
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(9.dp)) {
            androidx.compose.material3.LinearProgressIndicator(progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.Center), color = TerminalGreen)
            ReplayEventMarkerStrip(markers, start, start + duration, Modifier.fillMaxSize())
            goFraction?.let {
                Box(Modifier.offset(x = (maxWidth - 3.dp) * it).width(3.dp).height(9.dp).background(TerminalPurple))
            }
        }
    }
}

private const val SCRUB_STEP_NANOS = 1_000_000_000L
private const val LCD_SCRUB_WIDTH_PX = 600f

internal fun formatReplayTime(nanos: Long): String {
    val deciseconds = (nanos / 100_000_000L).coerceAtLeast(0L)
    val minutes = deciseconds / 600L
    val seconds = (deciseconds % 600L) / 10L
    return "%02d:%02d.%d".format(minutes, seconds, deciseconds % 10L)
}

@Composable
internal fun ReplayCombatantSlot(
    combatant: ReplayCombatant?,
    cursorNanos: Long,
    hp: ReplayHpState?,
    context: android.content.Context,
    useBackSprite: Boolean,
    fastAction: ReplayFastMoveAction?,
    chargedAction: ReplayChargedMoveAction?,
    qteMilestone: String?,
    spriteSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    statChanges: List<com.example.overdex.battle.replay.ReplayStatChangeAction> = emptyList(),
    qteSpriteScale: Float = 1f,
    qteSpriteTranslationXPixels: Float = 0f,
    onVisibleArt: (ReplaySpriteVisibleBounds) -> Unit = {},
) {
    val endPose = ReplayCombatantEndPose.at(combatant, cursorNanos, useBackSprite)
    val inactive = endPose != null
    Box(
        modifier = modifier.height(150.dp),
        contentAlignment = Alignment.Center
    ) {
        combatant?.speciesId?.let { speciesId ->
            val spriteUrl = remember(context, speciesId, combatant.speciesName, useBackSprite) {
                LocalSpriteProvider(context.assets).getReplaySpriteUrl(speciesId, combatant.speciesName, useBackSprite)
            }
            var visibleTop by remember(spriteUrl) { mutableFloatStateOf(0f) }
            val spriteRequest = remember(context, spriteUrl) {
                ImageRequest.Builder(context).data(spriteUrl).allowHardware(false).build()
            }
            AsyncImage(
                model = spriteRequest,
                onSuccess = { state ->
                    // Measure visible art once per decoded sprite, including rear art.
                    // Set the pivot with image success to avoid an initial wrong-anchor frame.
                    val art = (state.result.drawable as? BitmapDrawable)?.bitmap
                        ?.let(::replaySpriteVisibleBounds) ?: ReplaySpriteVisibleBounds(0f, 0f, 1f, 1f)
                    visibleTop = art.top
                    onVisibleArt(art)
                },
                contentDescription = if (combatant.isFainted) "${combatant.displayName}, fainted"
                    else if (inactive) "${combatant.displayName}, defeated" else combatant.displayName,
                modifier = Modifier
                    .requiredSize(spriteSize)
                    // Pokémon GO places the player's back-facing combatant
                    // lower in the field. A small downward offset aligns its
                    // head with the opponent while preserving the left/right
                    // battle orientation.
                    .offset(y = if (useBackSprite) 24.dp else 0.dp)
                    .graphicsLayer {
                        // The bundled Turtonator front/rear art faces opposite
                        // to the other battle sprites. Face the other combatant
                        // before applying the KO rotation; keep the PNG intact.
                        val focusScale = if (inactive) 1f else qteSpriteScale
                        scaleX = focusScale * if (speciesId == 776) -1f else 1f
                        scaleY = focusScale
                        // Fit artwork can contain transparent top padding and letterboxing.
                        // Pivot on the visible head, preserving its top as the body shrinks.
                        transformOrigin = if (inactive) TransformOrigin.Center
                            else TransformOrigin(0.5f, visibleTop)
                        // Keep fading on the transformed layer so a separate
                        // opacity layer cannot clip the falling sprite.
                        alpha = endPose?.opacity ?: 1f
                        // Cursor-driven pixels: pausing and scrubbing preserve the pose.
                        val attackOffset = chargedAction?.let { ReplayChargePose.at(it, useBackSprite).risePixels }
                            ?: fastAction?.let {
                                // Keep the short, sharp sprite bounce while the
                                // complete attack cycle uses its reference duration.
                                it.progress * it.durationNanos / (450_000_000L * REPLAY_BOB_DURATION_FRACTION)
                            }
                            ?.coerceIn(0f, 1f)
                            ?.let { quickProgress ->
                                val amplitudePixels = if (useBackSprite) 16f else 8f
                                if (quickProgress <= FAST_MOVE_RISE_FRACTION) {
                                    val rise = quickProgress / FAST_MOVE_RISE_FRACTION
                                    -amplitudePixels * sin(rise * Math.PI / 2.0).toFloat()
                                } else {
                                    val drop = (quickProgress - FAST_MOVE_RISE_FRACTION) /
                                        (1f - FAST_MOVE_RISE_FRACTION)
                                    -amplitudePixels * (1f - drop).pow(3)
                                }
                            }
                            ?: 0f
                        // Fine positioning uses physical pixels, independent of display density.
                        translationX = (endPose?.shakePixels ?: 0f) + (endPose?.withdrawalPixels ?: 0f) +
                            if (inactive) 0f else qteSpriteTranslationXPixels
                        rotationZ = endPose?.rotationDegrees ?: 0f
                        translationY = (if (inactive) endPose?.dropPixels ?: 0f else attackOffset) +
                            if (useBackSprite) 25f else 0f
                    },
                contentScale = ContentScale.Fit,
                colorFilter = if (inactive) {
                    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(endPose?.saturation ?: 1f) })
                } else null
            )
        }
        if (combatant != null && hp != null) {
            ReplayHpBar(
                hp,
                "${if (useBackSprite) "Player" else "Opponent"} ${combatant.displayName} HP",
                Modifier.align(Alignment.TopCenter).offset(y = if (useBackSprite) 4.dp else (-20).dp),
            )
        }
        if (combatant != null) {
            Column(Modifier.align(Alignment.TopCenter).offset(y = if (useBackSprite) 20.dp else (-4).dp)) {
                statChanges.forEach { change ->
                    val color = if (change.direction > 0) Color(0xFF9AFFCC) else Color(0xFFFFB27C)
                    Column(Modifier.graphicsLayer {
                            translationY = -change.direction * change.progress * 24f
                            alpha = ((1f - change.progress) * 3f).coerceIn(0f, 1f)
                        }.background(Color(0xD0163721), RoundedCornerShape(5.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(change.label, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        change.moveName?.let { move -> Text(move, color = color, fontSize = 11.sp) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChargedMoveReplayIndicator(
    action: ReplayChargedMoveAction,
    travelHalfWidth: androidx.compose.ui.unit.Dp,
    playerSpriteSize: androidx.compose.ui.unit.Dp,
    opponentForwardX: androidx.compose.ui.unit.Dp,
    opponentForwardY: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    performerOffsetPixels: Float = 0f,
    recipientOffsetPixels: Float = 0f,
) {
    val player = action.side == "PLAYER"
    val attributed = action.side == "PLAYER" || action.side == "OPPONENT"
    val pose = ReplayChargePose.at(action, player)
    val flight = pose.projectileProgress
    // The opponent's front shoulder is both its launch point and the player's
    // landing point. Its glyph is drawn above that sprite on the attack plane.
    val originX = if (player) -travelHalfWidth + playerSpriteSize * 0.28f else opponentForwardX
    val targetX = if (action.hitsProtectShield) shieldCenterX(!player, travelHalfWidth, playerSpriteSize, opponentForwardX)
        else if (player) opponentForwardX else -travelHalfWidth
    val originY = if (player) 24.dp - playerSpriteSize * 0.12f else opponentForwardY
    val targetY = if (player) opponentForwardY else 24.dp
    val color = action.type?.color ?: Color(0xFF88AFA0)
    Box(modifier
        .offset(x = if (attributed) originX * (1f - flight) + targetX * flight else 0.dp,
            y = if (attributed) originY * (1f - flight) + targetY * flight else 0.dp)
        .graphicsLayer {
            translationY = if (attributed) {
                (pose.projectileRisePixels + if (player) 25f else 0f) * (1f - flight) +
                    (if (player) 0f else 25f) * flight - 18f * sin(flight * Math.PI).toFloat()
            } else 0f
            translationX = if (attributed) performerOffsetPixels * (1f - flight) + recipientOffsetPixels * flight else 0f
            alpha = if (action.hitsProtectShield) pose.iconAlpha * ((1f - flight) * 10f).coerceIn(0f, 1f)
                else pose.iconAlpha
            scaleX = pose.iconScale
            scaleY = pose.iconScale
        }
        .size(54.dp)
        .semantics(mergeDescendants = true) {
            contentDescription = if (flight > 0f) "Charged move projectile" else "Charging move"
            stateDescription = "${action.side}: ${action.moveName ?: "Move unconfirmed"}; " +
                "${action.type?.name ?: "type unconfirmed"}; QTE level ${action.qteLevel}"
        }, contentAlignment = Alignment.Center) {
        if (action.type != null) {
            PokemonTypeIcon(action.type, modifier = Modifier.fillMaxSize(), style = TypeIconStyle.OVERDEX)
        } else {
            // Missing identity never borrows the combatant's own type.
            Box(Modifier.size(42.dp).border(2.dp, color, CircleShape))
        }
    }
}

@Composable
internal fun ReplayHpBar(hp: ReplayHpState, label: String, modifier: Modifier = Modifier) {
    // Display the existing per-Pokémon reconstructed bar continuously. Freshness
    // remains separate evidence and must not turn this display into a survival veto.
    val fraction = hp.filledFraction
    val lastSeen = fraction != null && !hp.currentHpConfirmed && !hp.assumedInitialFull && fraction != 0f
    val fillColor = when {
        fraction == null -> Color.Transparent
        fraction <= 0.2f -> Color(0xFFEF5350)
        fraction <= 0.5f -> Color(0xFFFFDF4F)
        else -> Color(0xFF00DFAC)
    }
    val orange = Color(0xFFFFA000)
    Box(modifier.width(132.dp).height(11.dp).semantics {
        contentDescription = label
        stateDescription = if (hp.assumedInitialFull) "Full HP assumed on first entry"
            else if (hp.estimatedBetweenMeasurements && fraction != null) "Estimated ${(fraction * 100).toInt()} percent between HP readings"
            else if (lastSeen) "Last seen ${(fraction!! * 100).toInt()} percent; current HP unknown"
            else fraction?.let { "${(it * 100).toInt()} percent" } ?: "HP not recorded"
    }) {
      Canvas(Modifier.fillMaxSize()) {
        val inset = 2.dp.toPx()
        val innerWidth = (size.width - inset * 2).coerceAtLeast(0f)
        val innerHeight = (size.height - inset * 2).coerceAtLeast(0f)
        drawRoundRect(Color.Black.copy(alpha = 0.55f), cornerRadius = CornerRadius(inset))
        if (fraction != null) {
            drawRect(fillColor, Offset(inset, inset), Size(innerWidth * fraction, innerHeight))
            hp.damageTrailFraction?.let { trail ->
                drawRect(orange, Offset(inset + innerWidth * fraction, inset),
                    Size(innerWidth * (trail - fraction).coerceAtLeast(0f), innerHeight))
            }
        }
        drawRoundRect(
            if (hp.borderPulse) orange else Color.White,
            topLeft = Offset(0.75.dp.toPx(), 0.75.dp.toPx()),
            size = Size(size.width - 1.5.dp.toPx(), size.height - 1.5.dp.toPx()),
            cornerRadius = CornerRadius(inset),
            style = Stroke(1.5.dp.toPx()),
        )
      }
    }
}

private const val REPLAY_BOB_DURATION_FRACTION = 0.6f
private const val FAST_MOVE_RISE_FRACTION = 0.72f
