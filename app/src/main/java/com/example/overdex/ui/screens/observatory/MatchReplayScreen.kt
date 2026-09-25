package com.example.overdex.ui.screens.observatory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.overdex.battle.archive.MatchArchive
import com.example.overdex.battle.replay.MatchReplayModel
import com.example.overdex.battle.replay.ReplayCombatant
import com.example.overdex.battle.replay.ReplayTransportSounds
import com.example.overdex.data.LocalSpriteProvider
import com.example.overdex.ui.components.TerminalScreen
import com.example.overdex.ui.theme.TerminalGreen
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
    onLcdUpdate: (String, String) -> Unit = { _, _ -> }
) {
    val model = remember(archive) { MatchReplayModel(archive) }
    var cursor by remember(archive) { mutableLongStateOf(model.startNanos) }
    var playing by remember { mutableStateOf(false) }
    val scene = model.sceneAt(cursor)
    val duration = (model.endNanos - model.startNanos).coerceAtLeast(1L)
    val context = LocalContext.current
    val transportSounds = remember { ReplayTransportSounds(context) }

    DisposableEffect(transportSounds) {
        onDispose { transportSounds.release() }
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
            if (model.crossedArticleBoundary(previous, cursor)) transportSounds.tick()
        }
    }

    SideEffect {
        val fraction = ((cursor - model.startNanos).toFloat() / duration).coerceIn(0f, 1f)
        onLcdUpdate(
            "[${if (playing) "PLAY" else "PAUSE"}] ${formatReplayTime(cursor - model.startNanos)} ${replayScrubBar(fraction)}",
            "TAP PLAY  DRAG SCRUB  UP RESET  A PLAY  B BACK"
        )
    }

    LaunchedEffect(playing, cursor, model.endNanos) {
        while (playing && cursor < model.endNanos) {
            delay(33)
            val previous = cursor
            cursor = (cursor + 33_000_000L).coerceAtMost(model.endNanos)
            if (model.crossedArticleBoundary(previous, cursor)) transportSounds.tick()
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReplayCombatantSlot(
                    combatant = scene.player,
                    context = context,
                    mirrorSpriteHorizontally = true
                )
                ReplayCombatantSlot(
                    combatant = scene.opponent,
                    context = context,
                    mirrorSpriteHorizontally = false
                )
            }
        }
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
    mirrorSpriteHorizontally: Boolean
) {
    Box(
        modifier = Modifier.width(150.dp).size(150.dp),
        contentAlignment = Alignment.Center
    ) {
        combatant?.speciesId?.let { speciesId ->
            AsyncImage(
                model = LocalSpriteProvider(context.assets).getSpriteUrl(speciesId),
                contentDescription = combatant.speciesName,
                modifier = Modifier
                    .size(130.dp)
                    // Player is fixed on the left, so mirror its source sprite to face the center.
                    .graphicsLayer(scaleX = if (mirrorSpriteHorizontally) -1f else 1f),
                contentScale = ContentScale.Fit
            )
        }
    }
}
