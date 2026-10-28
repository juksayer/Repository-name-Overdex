package com.example.overdex.ui.screens.observatory

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.overdex.battle.archive.MatchArchive
import com.example.overdex.battle.archive.ArchivedFastMoveIdentified
import com.example.overdex.battle.archive.ArchivedChargedMoveEnergySpent
import com.example.overdex.battle.replay.ReplayFastMoveAction
import com.example.overdex.battle.replay.ReplayChargedMoveAction
import com.example.overdex.battle.replay.ReplayChargePhase
import com.example.overdex.battle.replay.ReplayChargePose
import com.example.overdex.battle.replay.ReplayScene
import com.example.overdex.battle.replay.ReplayCropProgress
import com.example.overdex.battle.replay.MatchReplayModel
import com.example.overdex.battle.replay.ReplayCombatant
import com.example.overdex.battle.replay.ReplayIdentityObservation
import com.example.overdex.battle.replay.ReplayHpState
import com.example.overdex.battle.replay.ReplayFaintPose
import com.example.overdex.battle.replay.ReplayAttackSounds
import com.example.overdex.battle.replay.replayAttackSoundsBetween
import com.example.overdex.battle.replay.ReplayTransportSounds
import com.example.overdex.battle.replay.ReplayPlaybackClock
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
    resolveArchivedSpeciesCrops: suspend (MatchArchive, (ReplayCropProgress) -> Unit) -> List<ReplayIdentityObservation> = { _, _ -> emptyList() },
    initialCursorNanos: Long? = null,
    playbackWindow: ReplayWindow? = null,
    autoplay: Boolean = false,
    playbackLabel: String? = null,
    onPlaybackComplete: () -> Unit = {},
    onOpenEditor: (() -> Unit)? = null,
    allowClipSave: Boolean = true,
    onLeft: (() -> Unit) -> Unit = {},
    onRight: (() -> Unit) -> Unit = {},
    initialPlaybackSpeed: Float = 1f,
    onPlaybackSpeedChange: (Float) -> Unit = {},
) {
    val referencedNames = remember(archive) { MatchReplayModel.referencedSpeciesNames(archive) }
    var speciesResolved by remember(archive) { mutableStateOf(false) }
    var fastMovesResolved by remember(archive) { mutableStateOf(false) }
    var chargedMovesResolved by remember(archive) { mutableStateOf(false) }
    val speciesIds by produceState<Map<String, Int>>(emptyMap(), archive) {
        value = referencedNames.mapNotNull { name -> resolveSpeciesId(name)?.let { name to it } }.toMap()
        speciesResolved = true
    }
    var cropProgress by remember(archive) { mutableStateOf(ReplayCropProgress("READING ARCHIVE")) }
    var cropError by remember(archive) { mutableStateOf<String?>(null) }
    val archivedCropIdentities by produceState<List<ReplayIdentityObservation>?>(null, archive) {
        value = try {
            resolveArchivedSpeciesCrops(archive) { cropProgress = it }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            cropError = "Crop verification failed: ${error.message}"
            emptyList()
        }
    }
    val typeSpeciesNames = remember(referencedNames, archivedCropIdentities) {
        (referencedNames + archivedCropIdentities.orEmpty().map { it.speciesName }).distinct()
    }
    val speciesTypes by produceState<Map<String, List<PokemonType>>>(emptyMap(), archive, typeSpeciesNames) {
        value = typeSpeciesNames.associate { name ->
            name.uppercase().filter(Char::isLetterOrDigit) to resolveSpeciesTypes(name).distinct()
        }
    }
    val fastMoveTypes by produceState<Map<String, PokemonType>>(emptyMap(), archive) {
        value = archive.articles.mapNotNull { article ->
            val identified = article.payload as? ArchivedFastMoveIdentified ?: return@mapNotNull null
            resolveFastMoveType(identified.speciesName, identified.moveName)
                ?.let { identified.moveName.uppercase().filter(Char::isLetterOrDigit) to it }
        }.toMap()
    }
    val fastMovesBySpecies by produceState<Map<String, List<Move>>>(emptyMap(), archive, referencedNames) {
        value = referencedNames.associateWith { resolveFastMoves(it) }
        fastMovesResolved = true
    }
    val chargedMovesBySpecies by produceState<Map<String, List<Move>>>(emptyMap(), archive, referencedNames) {
        value = referencedNames.associateWith { resolveChargedMoves(it) }
        chargedMovesResolved = true
    }
    val chargedMoveTypes by produceState<Map<String, PokemonType>>(emptyMap(), archive) {
        value = archive.articles.mapNotNull { it.payload as? ArchivedChargedMoveEnergySpent }
            .distinctBy { it.speciesName to it.moveName }.mapNotNull { move ->
                resolveChargedMoveType(move.speciesName, move.moveName)?.let {
                    move.moveName.uppercase().filter(Char::isLetterOrDigit) to it
                }
            }.toMap()
    }
    val model = remember(archive, speciesIds, archivedCropIdentities, fastMoveTypes, fastMovesBySpecies, chargedMoveTypes, chargedMovesBySpecies) {
        MatchReplayModel(
            archive,
            speciesIds,
            archivedCropIdentities.orEmpty(),
            fastMoveTypes,
            fastMovesBySpecies,
            chargedMoveTypes,
            chargedMovesBySpecies,
        )
    }
    val playbackStart = playbackWindow?.startNanos ?: model.startNanos
    val playbackEnd = playbackWindow?.endNanos ?: model.endNanos
    var cursor by remember(archive, initialCursorNanos, playbackWindow) {
        mutableLongStateOf((initialCursorNanos ?: playbackStart).coerceIn(playbackStart, playbackEnd))
    }
    var playing by remember(archive, playbackWindow) { mutableStateOf(false) }
    var autoStarted by remember(archive, playbackWindow) { mutableStateOf(false) }
    var playbackSpeed by remember(archive, playbackWindow) { mutableFloatStateOf(initialPlaybackSpeed) }
    LaunchedEffect(autoplay, speciesResolved, fastMovesResolved, chargedMovesResolved, archivedCropIdentities) {
        if (autoplay && !autoStarted && speciesResolved && fastMovesResolved && chargedMovesResolved &&
            archivedCropIdentities != null) {
            autoStarted = true
            playing = true
        }
    }
    fun setSpeed(speed: Float) {
        playbackSpeed = speed
        onPlaybackSpeedChange(speed)
    }
    val currentModel by rememberUpdatedState(model)
    val scene = model.sceneAt(cursor)
    val duration = (playbackEnd - playbackStart).coerceAtLeast(1L)
    val displayedMatchStart = model.matchStartNanos ?: model.countdown?.goAtNanos
        ?.takeUnless { model.countdown?.timingBasis == com.example.overdex.battle.replay.ReplayCountdownTiming.PRESENTATION_ONLY }
    val matchStartFraction = displayedMatchStart?.takeIf { it in playbackStart..playbackEnd }?.let { matchStartNanos ->
        ((matchStartNanos - playbackStart).toFloat() / duration).coerceIn(0f, 1f)
    }
    val context = LocalContext.current
    val saveScope = rememberCoroutineScope()
    var clipStart by remember(archive) { mutableStateOf<Long?>(null) }
    var clipEnd by remember(archive) { mutableStateOf<Long?>(null) }
    var savingClip by remember { mutableStateOf(false) }
    var clipStatus by remember { mutableStateOf<String?>(null) }
    var editorAction by remember { mutableIntStateOf(0) }
    val editorActionCount = if (onOpenEditor != null) 4 else 3
    val completion by rememberUpdatedState(onPlaybackComplete)
    fun markClipStart() {
        playing = false
        clipStart = cursor
        clipStatus = "START ${formatReplayTime(cursor - model.startNanos)}"
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
                        ReplayExcerptStore(context.filesDir).saveRange(archive, start, end, title, notes,
                            recoveredIdentities = archivedCropIdentities.orEmpty())
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
        mutableStateOf(replayPreferences.getBoolean("event_blips_enabled", true))
    }
    var attackSfxEnabled by remember(replayPreferences) {
        mutableStateOf(replayPreferences.getBoolean("attack_sfx_enabled", true))
    }
    val currentAttackSfxEnabled by rememberUpdatedState(attackSfxEnabled)
    val attackSounds = remember(context) { ReplayAttackSounds(context) }
    LaunchedEffect(model, attackSounds) { attackSounds.prepare(model.attackSoundEvents) }
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

    SideEffect {
        onUp { scrubBy(-SCRUB_STEP_NANOS) }
        onDown { scrubBy(SCRUB_STEP_NANOS) }
        onLeft { if (allowClipSave) editorAction = (editorAction + editorActionCount - 1) % editorActionCount }
        onRight { if (allowClipSave) editorAction = (editorAction + 1) % editorActionCount }
        onA {
            if (!savingClip && clipEnd == null) when (editorAction) {
                1 -> markClipStart()
                2 -> markClipEnd()
                3 -> { playing = false; onOpenEditor?.invoke() }
                else -> togglePlayback()
            }
        }
        onB { if (!savingClip) { playing = false; transportSounds.stop(); onBack() } }
        onLcdTap { togglePlayback() }
        onLcdDrag { delta ->
            // A complete lower-LCD-width sweep spans the complete match record.
            scrubBy((duration * (delta.x / LCD_SCRUB_WIDTH_PX)).toLong())
        }
    }

    PublishMatchLcd(onLcdContentUpdate) {
        var scrubWidth by remember { mutableIntStateOf(1) }
        val lcdScroll = rememberScrollState()
        val marksRequester = remember { BringIntoViewRequester() }
        LaunchedEffect(editorAction, clipStatus, savingClip) {
            if (allowClipSave) when (editorAction) {
                0 -> lcdScroll.animateScrollTo(0)
                1, 2 -> marksRequester.bringIntoView()
            }
        }
        val drag by rememberUpdatedState<(Offset) -> Unit>({ delta ->
            scrubBy((duration * delta.x / scrubWidth).toLong())
        })
        val toggle by rememberUpdatedState<() -> Unit>({ togglePlayback() })
        // Only the time display/scrubber and hint handle drag-to-seek. Keeping
        // these gestures off the parent lets the speed slider own its drags.
        val scrubGesture = Modifier
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { transportSounds.endScrub() },
                    onDragCancel = { transportSounds.endScrub() },
                ) { change, delta -> change.consume(); drag(Offset(delta, 0f)) }
            }
            .pointerInput(Unit) { detectTapGestures { toggle() } }
        Column(Modifier.fillMaxSize().onSizeChanged { scrubWidth = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) { detectTapGestures { toggle() } }
            .verticalScroll(lcdScroll),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            playbackLabel?.let { MatchLcdText(it) }
            Column(Modifier.fillMaxWidth().then(scrubGesture)) {
                MatchLcdText("${if (playing) "PLAY" else "PAUSE"}  ${formatReplayTime(cursor - playbackStart)} / ${formatReplayTime(duration)}")
                BoxWithConstraints(Modifier.fillMaxWidth().height(16.dp)) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { ((cursor - playbackStart).toFloat() / duration).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).align(Alignment.Center),
                        color = TerminalGreen,
                    )
                    matchStartFraction?.let { fraction ->
                        Box(
                            Modifier.align(Alignment.CenterStart)
                                .offset(x = (maxWidth - 3.dp) * fraction)
                                .width(3.dp).height(14.dp).background(TerminalPurple)
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MatchLcdTextButton("BACK", { scrubBy(-SCRUB_STEP_NANOS) },
                    Modifier.weight(1f).semantics { contentDescription = "Step backward one second" })
                Text("TAP ▶/Ⅱ  DRAG >>", color = TerminalGreen, maxLines = 1, softWrap = false,
                    fontSize = 10.sp, lineHeight = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(3.5f).then(scrubGesture).padding(vertical = 4.dp))
                MatchLcdTextButton("FWD", { scrubBy(SCRUB_STEP_NANOS) },
                    Modifier.weight(1f).semantics { contentDescription = "Step forward one second" })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MatchLcdTextButton(String.format(Locale.ROOT, "%.1f×", playbackSpeed), { setSpeed(1f) },
                    Modifier.width(40.dp).semantics { contentDescription = "Reset replay speed to 1x" })
                Slider(
                    value = playbackSpeed,
                    onValueChange = { setSpeed((it * 10f).roundToInt() / 10f) },
                    valueRange = ReplayPlaybackClock.MIN_SPEED..ReplayPlaybackClock.MAX_SPEED,
                    steps = 48,
                    modifier = Modifier.weight(1f).height(28.dp).semantics {
                        contentDescription = "Replay speed"
                        stateDescription = String.format(Locale.ROOT, "%.1f times", playbackSpeed)
                    },
                    colors = SliderDefaults.colors(thumbColor = TerminalGreen, activeTrackColor = TerminalGreen),
                    thumb = { Box(Modifier.size(12.dp).background(TerminalGreen, CircleShape)) },
                    track = { slider ->
                        SliderDefaults.Track(
                            sliderState = slider,
                            modifier = Modifier.height(4.dp),
                            colors = SliderDefaults.colors(activeTrackColor = TerminalGreen,
                                inactiveTrackColor = TerminalGreen.copy(alpha = 0.25f)),
                            drawTick = { _, _ -> },
                            drawStopIndicator = null,
                            thumbTrackGapSize = 0.dp,
                        )
                    },
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                MatchLcdTextButton("RESET", { cursor = playbackStart; playing = false; transportSounds.reset() }, Modifier.weight(1f))
                MatchLcdTextButton(if (playing) "PAUSE" else "PLAY", toggle, Modifier.weight(1f))
                MatchLcdTextButton(
                    label = "BLIPS ${if (eventBlipsEnabled) "ON" else "OFF"}",
                    onClick = {
                        eventBlipsEnabled = !eventBlipsEnabled
                        replayPreferences.edit().putBoolean("event_blips_enabled", eventBlipsEnabled).apply()
                    },
                    modifier = Modifier.weight(1.5f).semantics { contentDescription = "Replay event blips" },
                    selected = eventBlipsEnabled,
                )
                MatchLcdTextButton(
                    label = "SFX ${if (attackSfxEnabled) "ON" else "OFF"}",
                    onClick = {
                        attackSfxEnabled = !attackSfxEnabled
                        if (!attackSfxEnabled) attackSounds.stop()
                        replayPreferences.edit().putBoolean("attack_sfx_enabled", attackSfxEnabled).apply()
                    },
                    modifier = Modifier.weight(1.25f).semantics { contentDescription = "Replay attack sounds" },
                    selected = attackSfxEnabled,
                )
                MatchLcdTextButton("BACK", { if (!savingClip) { playing = false; transportSounds.stop(); onBack() } }, Modifier.weight(1f))
            }
            if (allowClipSave) {
                MatchLcdText("←/→ ${listOf("PLAY / PAUSE", "MARK START", "MARK END + SAVE", "CLIP LIBRARY")[editorAction]}   A SELECT")
                Column(Modifier.fillMaxWidth().bringIntoViewRequester(marksRequester)) {
                    MatchLcdText("CLIP AT ${formatReplayTime(cursor - model.startNanos)}   ↑/↓ STEP")
                    Row(Modifier.fillMaxWidth()) {
                        MatchLcdTextButton("MARK START", { if (!savingClip) { editorAction = 1; markClipStart() } }, Modifier.weight(1f), editorAction == 1)
                        MatchLcdTextButton("END + SAVE", { if (!savingClip) { editorAction = 2; markClipEnd() } }, Modifier.weight(1f), editorAction == 2)
                    }
                    if (savingClip) MatchLcdText("SAVING CLIP…")
                    clipStatus?.let { MatchLcdText(it) }
                }
                onOpenEditor?.let { open ->
                    MatchLcdButton("CLIP LIBRARY", { playing = false; open() }, selected = editorAction == 3, enabled = !savingClip)
                }
            }
            if (archivedCropIdentities == null) {
                MatchLcdText(cropProgress.stage + if (cropProgress.total > 0) " ${cropProgress.completed}/${cropProgress.total}" else "")
                if (cropProgress.total > 0) androidx.compose.material3.LinearProgressIndicator(
                    progress = { cropProgress.completed.toFloat() / cropProgress.total }, modifier = Modifier.fillMaxWidth(), color = TerminalGreen)
                else androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = TerminalGreen)
            }
            cropError?.let { MatchLcdText(it) }
            scene.countdownFrame?.let { MatchLcdText(it.lcdLabel) }
            MatchLcdText("RECORDED ENERGY\nP ${energyLedger(scene.playerEnergy)}  O ${energyLedger(scene.opponentEnergy)}")
            scene.statChanges.filter { it.side == null }.forEach {
                MatchLcdText("${it.label} · SIDE UNCONFIRMED")
            }
        }
    }

    LaunchedEffect(playing, playbackSpeed, playbackEnd) {
        // Cursor is deliberately not an effect key. A frame is a chance to draw,
        // not a fixed amount of Match time; slow frames must not slow the clock.
        val clock = ReplayPlaybackClock(cursor, playbackEnd, playbackSpeed)
        while (playing && cursor < playbackEnd) {
            val frameNanos = withFrameNanos { it }
            if (!playing) break
            val previous = cursor
            cursor = clock.positionAt(frameNanos)
            if (currentEventBlipsEnabled && currentModel.crossedArticleBoundary(previous, cursor)) transportSounds.tick()
            if (currentAttackSfxEnabled) {
                replayAttackSoundsBetween(currentModel.attackSoundEvents, previous, cursor).forEach {
                    attackSounds.play(it, playbackSpeed)
                }
            }
            currentModel.hapticEventsBetween(previous, cursor).forEach { event ->
                replayHaptics.play(event.pulseCount)
            }
        }
        if (playing && cursor >= playbackEnd) {
            playing = false
            transportSounds.stop()
            completion()
        }
    }

    val playerTypeReference = remember(archive, scene.player?.speciesName) {
        scene.player?.speciesName?.let { name ->
            com.example.overdex.data.PokemonFormReference.playerReferenceName(name,
                com.example.overdex.battle.replay.latestArchivedPlayerRoster(archive.articles, Long.MAX_VALUE))
        }
    }
    ReplayBattlefield(scene, cursor, context, speciesTypes, playerTypeReference)
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
                val playerQte = scene.chargedMoveActions.lastOrNull {
                    it.side == "PLAYER" && it.phase == ReplayChargePhase.CHARGING
                }
                // Build the icon toward the opponent, partly behind its performer,
                // then draw it above the
                // scene while in flight. It never becomes a separate spinner.
                scene.chargedMoveActions.filter { ReplayChargePose.at(it, it.side == "PLAYER").projectileProgress == 0f }
                    .forEach { action ->
                        ChargedMoveReplayIndicator(action, travelHalfWidth, spriteSize * 1.15f,
                            spriteSize * 0.9f, Modifier.align(Alignment.Center))
                    }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp).align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ReplayCombatantSlot(
                        combatant = scene.player,
                        cursorNanos = cursor,
                        hp = scene.playerHp,
                        context = context,
                        useBackSprite = true,
                        fastAction = scene.fastMoveActions.lastOrNull { it.side == "PLAYER" },
                        chargedAction = scene.chargedMoveActions.lastOrNull { it.side == "PLAYER" },
                        qteMilestone = scene.qteMilestone.takeIf { scene.qteMilestoneSide == "PLAYER" },
                        statChanges = scene.statChanges.filter { it.side == "PLAYER" },
                        spriteSize = spriteSize * 1.15f,
                        qteSpriteScale = ReplayChargePose.qteSpriteScale(playerQte, cursor, player = true),
                        modifier = Modifier.weight(1f),
                    )
                    ReplayCombatantSlot(
                        combatant = scene.opponent,
                        cursorNanos = cursor,
                        hp = scene.opponentHp,
                        context = context,
                        useBackSprite = false,
                        fastAction = scene.fastMoveActions.lastOrNull { it.side == "OPPONENT" },
                        chargedAction = scene.chargedMoveActions.lastOrNull { it.side == "OPPONENT" },
                        qteMilestone = scene.qteMilestone.takeIf { scene.qteMilestoneSide == "OPPONENT" },
                        statChanges = scene.statChanges.filter { it.side == "OPPONENT" },
                        spriteSize = spriteSize * 0.9f,
                        qteSpriteScale = ReplayChargePose.qteSpriteScale(playerQte, cursor, player = false),
                        modifier = Modifier.weight(1f),
                    )
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
                if (scene.player != null && !scene.player.isFainted && scene.outcome == null &&
                    scene.decisionTimer == null && scene.chargedMoveActions.isEmpty()) {
                    ReplayChargeButtons(scene.playerChargedMoves, scene.playerEnergy,
                        Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 18.dp))
                }
                scene.fastMoveActions.forEach { action ->
                    FastMoveReplayIndicator(action, travelHalfWidth, Modifier.align(Alignment.Center))
                }
                scene.chargedMoveActions.filter { ReplayChargePose.at(it, it.side == "PLAYER").projectileProgress > 0f }
                    .forEach { action ->
                        ChargedMoveReplayIndicator(action, travelHalfWidth, spriteSize * 1.15f,
                            spriteSize * 0.9f, Modifier.align(Alignment.Center))
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
                scene.announcement?.takeIf { scene.outcome == null }?.let { announcement ->
                    Text(announcement, color = Color.White, fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                            .background(Color(0xCC233545), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp))
                }
                scene.outcome?.let { outcome ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .background(Color(0xE6102418))
                            .border(2.dp, TerminalGreen)
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = replayOutcomeLabel(outcome),
                            color = Color.White,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                        )
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
    state?.let { "~${it.recordedEnergy}/100" } ?: "—"

private fun replayOutcomeLabel(outcome: String): String = when (
    outcome.uppercase().filter(Char::isLetterOrDigit)
) {
    "WIN", "WON", "VICTORY", "PLAYERWIN" -> "YOU WIN"
    "LOSS", "LOSE", "DEFEAT", "OPPONENTWIN" -> "GOOD EFFORT"
    "TIE", "DRAW" -> "TIE"
    else -> outcome.replace('_', ' ').uppercase()
}

@Composable
private fun FastMoveReplayIndicator(action: ReplayFastMoveAction, travelHalfWidth: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val movement = ((action.progress.coerceIn(0f, 1f) * 2f) - 1f)
    val direction = if (action.side == "PLAYER") 1f else -1f
    val indicatorColor = action.type?.color ?: Color(0xFF88AFA0)
    Box(
        modifier = modifier
            .semantics {
                contentDescription = "${action.side} ${action.moveName ?: "Fast Move"} projectile"
                stateDescription = if (action.progress >= 1f) "Impact" else "In flight"
            }
            .offset(x = travelHalfWidth * (movement * direction))
            .graphicsLayer { translationY = if (action.side == "PLAYER") 25f else -25f }
            .size(34.dp)
            .background(indicatorColor.copy(alpha = 0.18f), CircleShape)
            .border(1.dp, indicatorColor, CircleShape),
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

private const val SCRUB_STEP_NANOS = 1_000_000_000L
private const val LCD_SCRUB_WIDTH_PX = 600f

private fun formatReplayTime(nanos: Long): String {
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
) {
    val faintPose = combatant?.let {
        ReplayFaintPose.at(it.knockoutPresentationAtNanos ?: it.faintedAtNanos ?: it.defeatedAtNanos,
            cursorNanos, useBackSprite, it.knockoutFinishByNanos)
    }
    val down = faintPose != null
    Box(
        modifier = modifier.height(150.dp),
        contentAlignment = Alignment.Center
    ) {
        combatant?.speciesId?.let { speciesId ->
            AsyncImage(
                model = LocalSpriteProvider(context.assets).let { sprites ->
                    sprites.getReplaySpriteUrl(speciesId, combatant.speciesName, useBackSprite)
                },
                contentDescription = if (combatant.isFainted) "${combatant.speciesName}, fainted"
                    else if (down) "${combatant.speciesName}, defeated" else combatant.speciesName,
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
                        val focusScale = if (down) 1f else qteSpriteScale
                        scaleX = focusScale * if (speciesId == 776) -1f else 1f
                        scaleY = focusScale
                        // Keep fading on the transformed layer so a separate
                        // opacity layer cannot clip the falling sprite.
                        alpha = faintPose?.opacity ?: if (down) 0.6f else 1f
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
                        translationX = faintPose?.shakePixels ?: 0f
                        rotationZ = faintPose?.rotationDegrees ?: 0f
                        translationY = (if (down) faintPose?.dropPixels ?: 0f else attackOffset) +
                            if (useBackSprite) 25f else 0f
                    },
                contentScale = ContentScale.Fit,
                colorFilter = if (down) {
                    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(faintPose?.saturation ?: 1f) })
                } else null
            )
        }
        if (combatant != null && hp != null) {
            ReplayHpBar(
                hp,
                "${if (useBackSprite) "Player" else "Opponent"} ${combatant.speciesName} HP",
                Modifier.align(Alignment.TopCenter).offset(y = if (useBackSprite) 4.dp else (-20).dp),
            )
        }
        if (combatant != null) {
            Column(Modifier.align(Alignment.TopCenter).offset(y = if (useBackSprite) 20.dp else (-4).dp)) {
                statChanges.forEach { change ->
                    Text(change.label,
                        color = if (change.direction > 0) Color(0xFF9AFFCC) else Color(0xFFFFB27C),
                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.graphicsLayer {
                            translationY = -change.direction * change.progress * 24f
                            alpha = ((1f - change.progress) * 3f).coerceIn(0f, 1f)
                        }.background(Color(0xD0163721), RoundedCornerShape(5.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
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
    opponentSpriteSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val player = action.side == "PLAYER"
    val attributed = action.side == "PLAYER" || action.side == "OPPONENT"
    val pose = ReplayChargePose.at(action, player)
    val flight = pose.projectileProgress
    val direction = if (player) 1f else -1f
    val spriteSize = if (player) playerSpriteSize else opponentSpriteSize
    // Place the growing glyph at the performer's forward shoulder. The sprite
    // is drawn afterward, masking the part of the glyph that overlaps its body.
    val originX = -travelHalfWidth * direction + spriteSize * 0.28f * direction
    val originY = (if (player) 24.dp else 0.dp) - spriteSize * 0.12f
    val targetY = if (player) 0.dp else 24.dp
    val color = action.type?.color ?: Color(0xFF88AFA0)
    Box(modifier
        .offset(x = if (attributed) originX * (1f - flight) + travelHalfWidth * direction * flight else 0.dp,
            y = if (attributed) originY * (1f - flight) + targetY * flight else 0.dp)
        .graphicsLayer {
            translationY = if (attributed) {
                (pose.risePixels + if (player) 25f else 0f) * (1f - flight) +
                    (if (player) 0f else 25f) * flight - 18f * sin(flight * Math.PI).toFloat()
            } else 0f
            alpha = pose.iconAlpha
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
private fun ReplayHpBar(hp: ReplayHpState, label: String, modifier: Modifier = Modifier) {
    val fraction = hp.filledFraction
    val fillColor = when {
        fraction == null -> Color.Transparent
        fraction <= 0.2f -> Color(0xFFEF5350)
        fraction <= 0.5f -> Color(0xFFFFDF4F)
        else -> Color(0xFF00DFAC)
    }
    val orange = Color(0xFFFFA000)
    Canvas(modifier.width(132.dp).height(11.dp).semantics {
        contentDescription = label
        stateDescription = if (hp.assumedInitialFull) "Full HP assumed on first entry"
            else fraction?.let { "${(it * 100).toInt()} percent" } ?: "HP not recorded"
    }) {
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
        } else {
            // A small dash distinguishes an unmeasured bar from a witnessed empty bar.
            drawLine(Color.White.copy(alpha = 0.55f),
                Offset(size.width * 0.46f, size.height / 2),
                Offset(size.width * 0.54f, size.height / 2), 1.dp.toPx())
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

private const val REPLAY_BOB_DURATION_FRACTION = 0.6f
private const val FAST_MOVE_RISE_FRACTION = 0.72f
