package com.example.overdex.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.R
import com.example.overdex.battle.observation.CaptureDiagnostics
import com.example.overdex.battle.observation.DroidballOverlayMode
import com.example.overdex.battle.observation.DroidballOverlayPresentation
import com.example.overdex.battle.observation.OpponentMovePossibilities
import com.example.overdex.battle.observation.OverlayMovePossibility
import com.example.overdex.battle.observation.DroidballService

/**
 * Droidball's field body. Before battle it is a movable control; opening it
 * exposes the instrument panel between its two physical halves. Live battle
 * state opens that panel automatically because it now has decision-time work.
 */
@Composable
fun BattleOverlay(
    onDrag: (Float, Float) -> Unit = { _, _ -> },
    onDragFinished: () -> Unit = {}
) {
    val mode by DroidballOverlayPresentation.mode.collectAsState()
    val expanded by DroidballOverlayPresentation.expanded.collectAsState()
    val diagnostics by DroidballService.captureDiagnostics.collectAsState()
    val opponentSpecies by DroidballOverlayPresentation.opponentSpecies.collectAsState()
    val opponentMoves by DroidballOverlayPresentation.activeOpponentMovePossibilities.collectAsState()
    var arrived by remember { mutableStateOf(false) }
    val arrivalOffset by animateDpAsState(
        targetValue = if (arrived) 0.dp else 58.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "droidball field arrival"
    )
    LaunchedEffect(Unit) { arrived = true }

    val panelIsVisible = expanded || mode == DroidballOverlayMode.BATTLE_HUD
    val mayMove = mode != DroidballOverlayMode.BATTLE_HUD && mode != DroidballOverlayMode.RESULT
    val interactionModifier = if (mayMove) {
        Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragEnd = onDragFinished,
                onDrag = { change, amount ->
                    change.consume()
                    onDrag(amount.x, amount.y)
                }
            )
        }
    } else Modifier

    Column(
        modifier = interactionModifier
            .offset(x = arrivalOffset)
            .clickable { DroidballOverlayPresentation.toggleExpanded() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (panelIsVisible) {
            DroidballHalf(top = true, displaced = true)
            OverlayPanel(mode, diagnostics, opponentSpecies, opponentMoves)
            DroidballHalf(top = false, displaced = true)
        } else {
            Image(
                painter = painterResource(R.drawable.droidball),
                contentDescription = "Droidball. Tap to open assistance.",
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
private fun DroidballHalf(top: Boolean, displaced: Boolean) {
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(24.dp)
            .clipToBounds(),
        contentAlignment = if (top) Alignment.BottomCenter else Alignment.TopCenter
    ) {
        Image(
            painter = painterResource(R.drawable.droidball),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .offset(y = when {
                    top && displaced -> (-5).dp
                    !top && displaced -> (-19).dp
                    top -> 0.dp
                    else -> (-24).dp
                })
        )
    }
}

@Composable
private fun OverlayPanel(
    mode: DroidballOverlayMode,
    diagnostics: CaptureDiagnostics,
    opponentSpecies: List<String>,
    opponentMoves: OpponentMovePossibilities?
) {
    val isBattleHud = mode == DroidballOverlayMode.BATTLE_HUD || mode == DroidballOverlayMode.BATTLE_LIVE
    val heading = when (mode) {
        DroidballOverlayMode.PRE_BATTLE -> "ASSISTANCE ARMED"
        DroidballOverlayMode.CALIBRATING -> "CALIBRATION"
        DroidballOverlayMode.RESULT -> "RESULT OBSERVED"
        else -> "BATTLE HUD"
    }
    // Pokémon GO's Team Info panels measure 415 source pixels wide on the
    // calibrated 1080px capture. Resolve that physical width at runtime rather
    // than using an arbitrary dp panel size.
    val teamInfoWidth = with(LocalDensity.current) { 415.toDp() }
    val background = if (isBattleHud) Color(0xE8E0F2F1) else Color(0xE810231F)
    val foreground = if (isBattleHud) Color(0xFF004D40) else Color(0xFFD7FFF4)
    val muted = if (isBattleHud) Color(0xFF397D77) else Color(0xFFD7FFF4).copy(alpha = 0.82f)

    Column(
        modifier = Modifier
            .width(teamInfoWidth)
            .background(background, RoundedCornerShape(8.dp))
            .border(1.dp, foreground.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(heading, color = foreground, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        if (isBattleHud) {
            Text("OPPONENT TEAM", color = muted, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { index -> EmptySpeciesCell(opponentSpecies.getOrNull(index)) }
            }
            if (opponentMoves == null) {
                Text("Awaiting species evidence", color = muted, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            } else {
                MovePossibilityLine("POSSIBLE FAST", opponentMoves.fastMoves, muted)
                MovePossibilityLine("POSSIBLE CHARGED", opponentMoves.chargedMoves, muted)
            }
        } else {
            val message = when (mode) {
                DroidballOverlayMode.PRE_BATTLE -> "Start a Pokémon GO battle. VS opens the HUD."
                DroidballOverlayMode.CALIBRATING -> "Adjust crop boxes in Overdex."
                DroidballOverlayMode.RESULT -> "Outcome evidence is preserved. Tap to arm the next match."
                else -> captureLine(diagnostics)
            }
            Text(
                text = message,
                modifier = if (mode == DroidballOverlayMode.RESULT) Modifier.clickable {
                    DroidballService.emitSignal(com.example.overdex.battle.observation.DroidballSignal.BeginNextMatch)
                } else Modifier,
                color = foreground, fontSize = 9.sp, fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun RowScope.EmptySpeciesCell(speciesName: String?) {
    Box(
        modifier = Modifier.weight(1f).height(34.dp)
            .background(Color(0x18005E5B), RoundedCornerShape(7.dp))
            .border(1.dp, Color(0x55005E5B), RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            speciesName ?: "?",
            color = Color(0xFF397D77),
            fontSize = if (speciesName == null) 14.sp else 7.sp,
            fontWeight = FontWeight.Bold
        )
    }
}


@Composable
private fun MovePossibilityLine(label: String, moves: List<OverlayMovePossibility>, muted: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, color = muted, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
        if (moves.isEmpty()) {
            Text("UNAVAILABLE", color = muted, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
        } else {
            moves.forEach { move ->
                Text(
                    text = if (move.hazardous) "⚠ ${move.name}" else move.name,
                    color = if (move.hazardous) Color(0xFFC62828) else muted,
                    fontSize = 8.sp,
                    fontWeight = if (move.hazardous) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

private fun captureLine(diagnostics: CaptureDiagnostics): String {
    val dimensions = diagnostics.width?.let { width ->
        diagnostics.height?.let { height -> "${width}x${height}" }
    } ?: "awaiting frame"
    return "Capture: ${diagnostics.state.lowercase()} ($dimensions)"
}
