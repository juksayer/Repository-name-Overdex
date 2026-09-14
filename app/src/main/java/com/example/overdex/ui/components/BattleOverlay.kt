package com.example.overdex.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.R
import com.example.overdex.battle.observation.CaptureDiagnostics
import com.example.overdex.battle.observation.DroidballOverlayMode
import com.example.overdex.battle.observation.DroidballOverlayPresentation
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

    val panelIsVisible = expanded || mode == DroidballOverlayMode.BATTLE_LIVE
    val mayMove = mode != DroidballOverlayMode.BATTLE_LIVE && mode != DroidballOverlayMode.RESULT
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
        horizontalAlignment = Alignment.End
    ) {
        if (panelIsVisible) {
            DroidballHalf(top = true, displaced = true)
            OverlayPanel(mode, diagnostics)
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
private fun OverlayPanel(mode: DroidballOverlayMode, diagnostics: CaptureDiagnostics) {
    val lines = when (mode) {
        DroidballOverlayMode.PRE_BATTLE -> listOf(
            "ASSISTANCE ARMED",
            "Start a battle and choose a team.",
            "GO begins battle-live routing.",
            "Pre-battle evidence is recording."
        )
        DroidballOverlayMode.CALIBRATING -> listOf(
            "CALIBRATION ACTIVE",
            "Adjust crop boxes in Overdex.",
            "GO begins battle-live routing."
        )
        DroidballOverlayMode.COUNTDOWN -> listOf(
            "COUNTDOWN OBSERVED",
            "GO establishes the live battle boundary.",
            "Countdown evidence is recording."
        )
        DroidballOverlayMode.BATTLE_LIVE -> listOf(
            "BATTLE LIVE",
            captureLine(diagnostics),
            "Observed evidence is on the Timeline.",
            "Gaps remain visible; none are invented."
        )
        DroidballOverlayMode.RESULT -> listOf(
            "RESULT OBSERVED",
            "Outcome evidence is preserved.",
            "Tap here to arm the next match."
        )
    }

    val background = when (mode) {
        DroidballOverlayMode.BATTLE_LIVE -> Color(0xFF052E2B).copy(alpha = 0.92f)
        DroidballOverlayMode.RESULT -> Color(0xFF1B263B).copy(alpha = 0.92f)
        else -> Color(0xFF10231F).copy(alpha = 0.92f)
    }
    Column(
        modifier = Modifier
            .width(270.dp)
            .background(background, RoundedCornerShape(7.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = "DROIDBALL / ${lines.first()}",
            color = Color(0xFF8DF7D4),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        lines.drop(1).forEach { line ->
            Text(
                text = line,
                modifier = if (mode == DroidballOverlayMode.RESULT && line.contains("Tap here")) Modifier.clickable { DroidballService.emitSignal(com.example.overdex.battle.observation.DroidballSignal.BeginNextMatch) } else Modifier,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun captureLine(diagnostics: CaptureDiagnostics): String {
    val dimensions = diagnostics.width?.let { width ->
        diagnostics.height?.let { height -> "${width}x${height}" }
    } ?: "awaiting frame"
    return "Capture: ${diagnostics.state.lowercase()} ($dimensions)"
}
