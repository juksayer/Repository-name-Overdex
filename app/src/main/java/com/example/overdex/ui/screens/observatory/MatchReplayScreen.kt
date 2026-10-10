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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
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
import com.example.overdex.battle.replay.ReplayFastMoveAction
import com.example.overdex.battle.replay.ReplayChargedMoveAction
import com.example.overdex.battle.replay.ReplayCropProgress
import com.example.overdex.battle.replay.MatchReplayModel
import com.example.overdex.battle.replay.ReplayCombatant
import com.example.overdex.battle.replay.ReplayIdentityObservation
import com.example.overdex.battle.replay.ReplayHpState
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
    resolveFastMoveType: suspend (speciesName: String, moveName: String) -> PokemonType? = { _, _ -> null },
    resolveFastMoves: suspend (speciesName: String) -> List<Move> = { emptyList() },
    resolveArchivedSpeciesCrops: suspend (MatchArchive, (ReplayCropProgress) -> Unit) -> List<ReplayIdentityObservation> = { _, _ -> emptyList() }
) {
    val referencedNames = remember(archive) { MatchReplayModel.referencedSpeciesNames(archive) }
    val speciesIds by produceState<Map<String, Int>>(emptyMap(), archive) {
        value = referencedNames.mapNotNull { name -> resolveSpeciesId(name)?.let { name to it } }.toMap()
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
    val fastMoveTypes by produceState<Map<String, PokemonType>>(emptyMap(), archive) {
        value = archive.articles.mapNotNull { article ->
            val identified = article.payload as? ArchivedFastMoveIdentified ?: return@mapNotNull null
            resolveFastMoveType(identified.speciesName, identified.moveName)
                ?.let { identified.moveName.uppercase().filter(Char::isLetterOrDigit) to it }
        }.toMap()
    }
    val fastMovesBySpecies by produceState<Map<String, List<Move>>>(emptyMap(), archive, referencedNames) {
        value = referencedNames.associateWith { resolveFastMoves(it) }
    }
    val model = remember(archive, speciesIds, archivedCropIdentities, fastMoveTypes, fastMovesBySpecies) {
        MatchReplayModel(
            archive,
            speciesIds,
            archivedCropIdentities.orEmpty(),
            fastMoveTypes,
            fastMovesBySpecies
        )
    }
    var cursor by remember(archive) { mutableLongStateOf(model.startNanos) }
    var playing by remember(archive) { mutableStateOf(false) }
    var playbackSpeed by remember(archive) { mutableFloatStateOf(1f) }
    val currentModel by rememberUpdatedState(model)
    val scene = model.sceneAt(cursor)
    val duration = (model.endNanos - model.startNanos).coerceAtLeast(1L)
    val matchStartFraction = model.matchStartNanos?.let { matchStartNanos ->
        ((matchStartNanos - model.startNanos).toFloat() / duration).coerceIn(0f, 1f)
    }
    val context = LocalContext.current
    val replayPreferences = remember(context) { context.getSharedPreferences("match_replay", Context.MODE_PRIVATE) }
    var eventBlipsEnabled by remember(replayPreferences) {
        mutableStateOf(replayPreferences.getBoolean("event_blips_enabled", true))
    }
    val currentEventBlipsEnabled by rememberUpdatedState(eventBlipsEnabled)
    val transportSounds = remember { ReplayTransportSounds(context) }
    val replayHaptics = remember { ReplayHaptics(context) }

    DisposableEffect(transportSounds, replayHaptics) {
        onDispose {
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
        replayHaptics.release()
    }

    fun togglePlayback() {
        if (!playing && cursor >= model.endNanos) cursor = model.startNanos
        playing = !playing
        if (playing) transportSounds.play() else transportSounds.pause()
    }

    fun scrubBy(deltaNanos: Long) {
        playing = false
        val previous = cursor
        cursor = (cursor + deltaNanos).coerceIn(model.startNanos, model.endNanos)
        if (cursor != previous) transportSounds.scrub()
    }

    SideEffect {
        onUp { scrubBy(-SCRUB_STEP_NANOS) }
        onDown { scrubBy(SCRUB_STEP_NANOS) }
        onA { togglePlayback() }
        onB { playing = false; transportSounds.stop(); onBack() }
        onLcdTap { togglePlayback() }
        onLcdDrag { delta ->
            // A complete lower-LCD-width sweep spans the complete match record.
            scrubBy((duration * (delta.x / LCD_SCRUB_WIDTH_PX)).toLong())
        }
    }

    PublishMatchLcd(onLcdContentUpdate) {
        var scrubWidth by remember { mutableIntStateOf(1) }
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
            .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.fillMaxWidth().then(scrubGesture)) {
                MatchLcdText("${if (playing) "PLAY" else "PAUSE"}  ${formatReplayTime(cursor - model.startNanos)} / ${formatReplayTime(duration)}")
                BoxWithConstraints(Modifier.fillMaxWidth().height(16.dp)) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { ((cursor - model.startNanos).toFloat() / duration).coerceIn(0f, 1f) },
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
                MatchLcdTextButton(String.format(Locale.ROOT, "%.1f×", playbackSpeed), { playbackSpeed = 1f },
                    Modifier.width(40.dp).semantics { contentDescription = "Reset replay speed to 1x" })
                Slider(
                    value = playbackSpeed,
                    onValueChange = { playbackSpeed = (it * 10f).roundToInt() / 10f },
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
                MatchLcdTextButton("RESET", { cursor = model.startNanos; playing = false; transportSounds.reset() }, Modifier.weight(1f))
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
                MatchLcdTextButton("BACK", { playing = false; transportSounds.stop(); onBack() }, Modifier.weight(1f))
            }
            if (archivedCropIdentities == null) {
                MatchLcdText(cropProgress.stage + if (cropProgress.total > 0) " ${cropProgress.completed}/${cropProgress.total}" else "")
                if (cropProgress.total > 0) androidx.compose.material3.LinearProgressIndicator(
                    progress = { cropProgress.completed.toFloat() / cropProgress.total }, modifier = Modifier.fillMaxWidth(), color = TerminalGreen)
                else androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = TerminalGreen)
            }
            cropError?.let { MatchLcdText(it) }
            MatchLcdText("P ${energyLedger(scene.playerGeneratedEnergy, scene.playerSpentEnergy)}\nO ${energyLedger(scene.opponentGeneratedEnergy, scene.opponentSpentEnergy)}")
        }
    }

    LaunchedEffect(playing, playbackSpeed, model.endNanos) {
        // Cursor is deliberately not an effect key. A frame is a chance to draw,
        // not a fixed amount of Match time; slow frames must not slow the clock.
        val clock = ReplayPlaybackClock(cursor, model.endNanos, playbackSpeed)
        while (playing && cursor < model.endNanos) {
            val frameNanos = withFrameNanos { it }
            if (!playing) break
            val previous = cursor
            cursor = clock.positionAt(frameNanos)
            if (currentEventBlipsEnabled && currentModel.crossedArticleBoundary(previous, cursor)) transportSounds.tick()
            currentModel.hapticEventsBetween(previous, cursor).forEach { event ->
                replayHaptics.play(event.pulseCount)
            }
        }
        if (playing && cursor >= model.endNanos) {
            playing = false
            transportSounds.stop()
        }
    }

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
                val travelHalfWidth = ((maxWidth - 36.dp) / 4).coerceAtLeast(0.dp)
                val spriteSize = minOf(130.dp * 1.15f, maxWidth / 2 + 18.dp)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).align(Alignment.Center),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ReplayCombatantSlot(
                        combatant = scene.player,
                        hp = scene.playerHp,
                        context = context,
                        useBackSprite = true,
                        fastAction = scene.fastMoveActions.lastOrNull { it.side == "PLAYER" },
                        chargedAction = scene.chargedMoveActions.lastOrNull { it.side == "PLAYER" },
                        qteMilestone = scene.qteMilestone.takeIf { scene.qteMilestoneSide == "PLAYER" },
                        spriteSize = spriteSize * 1.15f,
                        modifier = Modifier.weight(1f),
                    )
                    ReplayCombatantSlot(
                        combatant = scene.opponent,
                        hp = scene.opponentHp,
                        context = context,
                        useBackSprite = false,
                        fastAction = scene.fastMoveActions.lastOrNull { it.side == "OPPONENT" },
                        chargedAction = scene.chargedMoveActions.lastOrNull { it.side == "OPPONENT" },
                        qteMilestone = scene.qteMilestone.takeIf { scene.qteMilestoneSide == "OPPONENT" },
                        spriteSize = spriteSize,
                        modifier = Modifier.weight(1f),
                    )
                }
                scene.fastMoveActions.forEach { action ->
                    FastMoveReplayIndicator(action, travelHalfWidth, Modifier.align(Alignment.Center))
                }
                scene.countdownGlyph?.let { glyph ->
                    Text(
                        text = glyph,
                        color = Color.White,
                        fontSize = if (glyph == "GO") 72.sp else 88.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
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
                scene.outcome?.let { outcome ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
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

private fun energyLedger(generated: Int, spent: Int): String =
    "E ${generated - spent} (+$generated/-$spent)"

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
private fun ReplayCombatantSlot(
    combatant: ReplayCombatant?,
    hp: ReplayHpState?,
    context: android.content.Context,
    useBackSprite: Boolean,
    fastAction: ReplayFastMoveAction?,
    chargedAction: ReplayChargedMoveAction?,
    qteMilestone: String?,
    spriteSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.height(150.dp),
        contentAlignment = Alignment.Center
    ) {
        val qteLevel = when (qteMilestone) {
            "NICE" -> 1
            "GREAT" -> 2
            "EXCELLENT" -> 3
            else -> 0
        }
        if (qteLevel > 0) {
            val glowColor = when (qteLevel) {
                1 -> Color.White
                2 -> Color(0xFFFFD54F)
                else -> Color(0xFFFF8A65)
            }
            Box(
                Modifier
                    .size((132 + qteLevel * 8).dp)
                    .alpha(0.18f + qteLevel * 0.08f)
                    .background(glowColor.copy(alpha = 0.22f), CircleShape)
                    .border(qteLevel.dp, glowColor.copy(alpha = 0.72f), CircleShape)
            )
        }
        combatant?.speciesId?.let { speciesId ->
            AsyncImage(
                model = LocalSpriteProvider(context.assets).let { sprites ->
                    if (useBackSprite) sprites.getBackSpriteUrl(speciesId) else sprites.getSpriteUrl(speciesId)
                },
                contentDescription = combatant.speciesName,
                modifier = Modifier
                    .requiredSize(spriteSize)
                    .alpha(if (combatant.isFainted) 0.35f else 1f)
                    // Pokémon GO places the player's back-facing combatant
                    // lower in the field. A small downward offset aligns its
                    // head with the opponent while preserving the left/right
                    // battle orientation.
                    .offset(y = if (useBackSprite) 24.dp else 0.dp)
                    .graphicsLayer {
                        // Cursor-driven pixels: pausing and scrubbing preserve the pose.
                        val attackOffset = chargedAction?.progress?.coerceIn(0f, 1f)?.let { progress ->
                            val fastMoveAmplitudePixels = if (useBackSprite) 16f else 8f
                            val amplitudePixels = fastMoveAmplitudePixels * 3f
                            if (progress <= CHARGED_MOVE_RISE_FRACTION) {
                                val rise = progress / CHARGED_MOVE_RISE_FRACTION
                                val easedRise = rise * rise * (3f - 2f * rise)
                                -amplitudePixels * easedRise
                            } else {
                                val drop = (progress - CHARGED_MOVE_RISE_FRACTION) /
                                    (1f - CHARGED_MOVE_RISE_FRACTION)
                                // Spend most of the gesture rising, then cover
                                // the return distance immediately so landing
                                // reads as an impact instead of a float.
                                -amplitudePixels * (1f - drop).pow(3)
                            }
                        } ?: fastAction?.progress
                            ?.div(REPLAY_BOB_DURATION_FRACTION)
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
                        translationY = attackOffset + if (useBackSprite) 25f else 0f
                    },
                contentScale = ContentScale.Fit,
                colorFilter = if (combatant.isFainted) {
                    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                } else null
            )
            if (combatant.isFainted) {
                Text("×", color = Color(0xFFD32F2F), fontSize = 72.sp)
            }
        }
        if (combatant != null && hp != null) {
            ReplayHpBar(
                hp,
                "${if (useBackSprite) "Player" else "Opponent"} ${combatant.speciesName} HP",
                Modifier.align(Alignment.TopCenter).offset(y = if (useBackSprite) 4.dp else (-20).dp),
            )
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
        stateDescription = fraction?.let { "${(it * 100).toInt()} percent" } ?: "HP not recorded"
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
private const val CHARGED_MOVE_RISE_FRACTION = 0.78f
