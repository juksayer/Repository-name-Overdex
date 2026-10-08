package com.example.overdex.ui.screens.observatory

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.example.overdex.battle.replay.ReplayTransportSounds
import com.example.overdex.data.LocalSpriteProvider
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import com.example.overdex.ui.components.PokemonTypeIcon
import com.example.overdex.ui.components.TypeIconStyle
import com.example.overdex.ui.components.TerminalScreen
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.ui.theme.TerminalPurple
import kotlin.math.sin
import kotlin.math.pow
import kotlinx.coroutines.delay

/** A clean CRT battle stage. Replay transport and diagnostics live in the ODX-Fi LCD. */
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
    var playing by remember { mutableStateOf(false) }
    val scene = model.sceneAt(cursor)
    val duration = (model.endNanos - model.startNanos).coerceAtLeast(1L)
    val matchStartFraction = model.matchStartNanos?.let { matchStartNanos ->
        ((matchStartNanos - model.startNanos).toFloat() / duration).coerceIn(0f, 1f)
    }
    val context = LocalContext.current
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

    SideEffect {
        onUp {
            cursor = model.startNanos
            playing = false
            transportSounds.reset()
        }
        onDown { cursor = (cursor + SCRUB_STEP_NANOS).coerceAtMost(model.endNanos) }
        onA {
            playing = !playing
            if (playing) transportSounds.play() else transportSounds.pause()
        }
        onB { playing = false; transportSounds.stop(); onBack() }
        onLcdTap {
            playing = !playing
            if (playing) transportSounds.play() else transportSounds.pause()
        }
        onLcdDrag { delta ->
            playing = false
            val previous = cursor
            // A complete lower-LCD-width sweep spans the complete match record.
            val deltaNanos = (duration * (delta.x / LCD_SCRUB_WIDTH_PX)).toLong()
            cursor = (cursor + deltaNanos).coerceIn(model.startNanos, model.endNanos)
            if (model.crossedArticleBoundary(previous, cursor)) transportSounds.scrub()
        }
    }

    PublishMatchLcd(onLcdContentUpdate) {
        var scrubWidth by remember { mutableIntStateOf(1) }
        val drag by rememberUpdatedState<(Offset) -> Unit>({ delta ->
            playing = false
            val previous = cursor
            cursor = (cursor + (duration * delta.x / scrubWidth).toLong()).coerceIn(model.startNanos, model.endNanos)
            if (model.crossedArticleBoundary(previous, cursor)) transportSounds.scrub()
        })
        val toggle by rememberUpdatedState<() -> Unit>({
            playing = !playing
            if (playing) transportSounds.play() else transportSounds.pause()
        })
        Column(Modifier.fillMaxSize().onSizeChanged { scrubWidth = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) { detectHorizontalDragGestures { change, delta -> change.consume(); drag(Offset(delta, 0f)) } }
            .pointerInput(Unit) { detectTapGestures { toggle() } }
            .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MatchLcdText("${if (playing) "PLAY" else "PAUSE"}  ${formatReplayTime(cursor - model.startNanos)} / ${formatReplayTime(duration)}")
            BoxWithConstraints(Modifier.fillMaxWidth().height(14.dp)) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { ((cursor - model.startNanos).toFloat() / duration).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).align(Alignment.Center),
                    color = TerminalGreen,
                )
                matchStartFraction?.let { fraction ->
                    Box(
                        Modifier
                            .offset(x = (maxWidth - 3.dp) * fraction)
                            .width(3.dp)
                            .height(14.dp)
                            .background(TerminalPurple)
                    )
                }
            }
            androidx.compose.material3.Text(
                "TAP ▶/Ⅱ   DRAG >>",
                color = TerminalGreen,
                maxLines = 1,
                softWrap = false,
                fontSize = 10.sp,
            )
            if (archivedCropIdentities == null) {
                MatchLcdText(cropProgress.stage + if (cropProgress.total > 0) " ${cropProgress.completed}/${cropProgress.total}" else "")
                if (cropProgress.total > 0) androidx.compose.material3.LinearProgressIndicator(
                    progress = { cropProgress.completed.toFloat() / cropProgress.total }, modifier = Modifier.fillMaxWidth(), color = TerminalGreen)
                else androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = TerminalGreen)
            }
            cropError?.let { MatchLcdText(it) }
            MatchLcdText("P ${energyLedger(scene.playerGeneratedEnergy, scene.playerSpentEnergy)}\nO ${energyLedger(scene.opponentGeneratedEnergy, scene.opponentSpentEnergy)}")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                MatchLcdButton("RESET", { cursor = model.startNanos; playing = false; transportSounds.reset() }, Modifier.weight(1f))
                MatchLcdButton(if (playing) "PAUSE" else "PLAY", toggle, Modifier.weight(1f))
                MatchLcdButton("BACK", { playing = false; transportSounds.stop(); onBack() }, Modifier.weight(1f))
            }
        }
    }

    LaunchedEffect(playing, cursor, model.endNanos) {
        while (playing && cursor < model.endNanos) {
            delay(33)
            val previous = cursor
            cursor = (cursor + 33_000_000L).coerceAtMost(model.endNanos)
            if (model.crossedArticleBoundary(previous, cursor)) transportSounds.tick()
            model.hapticEventsBetween(previous, cursor).forEach { event ->
                replayHaptics.play(event.pulseCount)
            }
        }
        if (playing && cursor >= model.endNanos) {
            playing = false
            transportSounds.stop()
        }
    }

    TerminalScreen {
        Box(
            Modifier
                .fillMaxSize()
                .padding(10.dp)
                .background(Color(0xFF163721))
                .border(1.dp, TerminalGreen),
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val travelHalfWidth = ((maxWidth - 36.dp - 150.dp) / 2).coerceAtLeast(0.dp)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).align(Alignment.Center),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ReplayCombatantSlot(
                        combatant = scene.player,
                        context = context,
                        useBackSprite = true,
                        fastAction = scene.fastMoveActions.lastOrNull { it.side == "PLAYER" },
                        chargedAction = scene.chargedMoveActions.lastOrNull { it.side == "PLAYER" },
                        qteMilestone = scene.qteMilestone.takeIf { scene.qteMilestoneSide == "PLAYER" },
                    )
                    ReplayCombatantSlot(
                        combatant = scene.opponent,
                        context = context,
                        useBackSprite = false,
                        fastAction = scene.fastMoveActions.lastOrNull { it.side == "OPPONENT" },
                        chargedAction = scene.chargedMoveActions.lastOrNull { it.side == "OPPONENT" },
                        qteMilestone = scene.qteMilestone.takeIf { scene.qteMilestoneSide == "OPPONENT" },
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
private const val LCD_SCRUB_STEPS = 12

private fun formatReplayTime(nanos: Long): String {
    val deciseconds = (nanos / 100_000_000L).coerceAtLeast(0L)
    val minutes = deciseconds / 600L
    val seconds = (deciseconds % 600L) / 10L
    return "%02d:%02d.%d".format(minutes, seconds, deciseconds % 10L)
}

private fun replayScrubBar(fraction: Float): String {
    val cursor = (fraction * LCD_SCRUB_STEPS).toInt().coerceIn(0, LCD_SCRUB_STEPS - 1)
    return buildString(LCD_SCRUB_STEPS + 2) {
        append('[')
        repeat(LCD_SCRUB_STEPS) { index ->
            append(if (index == cursor) 'o' else if (index < cursor) '=' else '-')
        }
        append(']')
    }
}

@Composable
private fun ReplayCombatantSlot(
    combatant: ReplayCombatant?,
    context: android.content.Context,
    useBackSprite: Boolean,
    fastAction: ReplayFastMoveAction?,
    chargedAction: ReplayChargedMoveAction?,
    qteMilestone: String?,
) {
    Box(
        modifier = Modifier.width(150.dp).size(150.dp),
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
                    .size(130.dp)
                    .alpha(if (combatant.isFainted) 0.35f else 1f)
                    // Pokémon GO places the player's back-facing combatant
                    // lower in the field. A small downward offset aligns its
                    // head with the opponent while preserving the left/right
                    // battle orientation.
                    .offset(y = if (useBackSprite) 24.dp else 0.dp)
                    .graphicsLayer {
                        // Cursor-driven pixels: pausing and scrubbing preserve the pose.
                        translationY = chargedAction?.progress?.coerceIn(0f, 1f)?.let { progress ->
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
    }
}

private const val REPLAY_BOB_DURATION_FRACTION = 0.6f
private const val FAST_MOVE_RISE_FRACTION = 0.72f
private const val CHARGED_MOVE_RISE_FRACTION = 0.78f
