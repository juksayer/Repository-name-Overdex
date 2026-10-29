package com.example.overdex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.battle.observation.DroidballOverlayMode
import com.example.overdex.battle.observation.DroidballOverlayPresentation
import com.example.overdex.battle.observation.DroidballService
import com.example.overdex.battle.observation.DroidballSignal
import kotlin.math.roundToInt

/** Physical screenshot coordinates, including system bars, from the Pixel 4 reference. */
internal object FieldBattleMenuGeometry {
    fun widthPx(displayWidth: Int): Int = (displayWidth * 930f / 1080f).roundToInt()
    fun leftPx(displayWidth: Int): Int = (displayWidth - widthPx(displayWidth)) / 2
    fun topPx(displayHeight: Int): Int = (displayHeight * 1001f / 2280f).roundToInt()
    fun bottomPx(displayHeight: Int): Int = (displayHeight * 1485f / 2280f).roundToInt()
    fun heightPx(displayHeight: Int): Int = bottomPx(displayHeight) - topPx(displayHeight)
    fun windowTopPx(displayHeight: Int, bodyHeight: Int, windowScreenOffsetY: Int): Int =
        topPx(displayHeight) - bodyHeight - windowScreenOffsetY
}

// Sampled from the opaque interior of GO's Team Select card, rather than the shaded scene.
internal val FieldMenuPaper = Color(0xFFFBFEF5)
internal val FieldMenuInk = Color(0xFF315E63)

/** Placement first: two rows keep Start and Stop apart without burying either in a scroll. */
@Composable
internal fun FieldBattleMenu(
    mode: DroidballOverlayMode,
    inferredTeam: List<String?>,
    confirmed: Boolean,
    configuredTeam: List<String>,
    teamEditingAllowed: Boolean,
    widthPx: Int,
    heightPx: Int,
) {
    val width = with(LocalDensity.current) { widthPx.toDp() }
    val height = with(LocalDensity.current) { heightPx.toDp() }
    val ready = mode == DroidballOverlayMode.BATTLE_READY
    val needsConfirmation = mode == DroidballOverlayMode.TEAM_SELECT &&
        inferredTeam.size == 3 && inferredTeam.all { it != null } && !confirmed
    val heading = when (mode) {
        DroidballOverlayMode.BATTLE_READY -> "BATTLE HUD READY"
        DroidballOverlayMode.SEEKING_TEAM_SELECT -> "SCANNING TEAM"
        DroidballOverlayMode.TEAM_SELECT -> if (needsConfirmation) "IS THIS YOUR TEAM?" else "TEAM SELECT"
        DroidballOverlayMode.CALIBRATING -> "CALIBRATION"
        DroidballOverlayMode.RESULT -> "MATCH COMPLETE"
        else -> "BATTLE SETUP"
    }
    val detail = when {
        ready -> "Start the battle in Pokémon GO. The HUD will appear when the battle is visible."
        mode == DroidballOverlayMode.RESULT -> "Outcome saved. Ready for the next match."
        inferredTeam.any { it != null } -> inferredTeam.joinToString(" · ") { it ?: "?" }
        configuredTeam.size == 3 -> configuredTeam.joinToString(" · ")
        mode == DroidballOverlayMode.SEEKING_TEAM_SELECT -> "Keep Choose Your Party visible while your team is read."
        else -> "Scan your team, or open the battle HUD for observation."
    }
    Column(Modifier.width(width).height(height)
        .background(FieldMenuPaper, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(heading, color = FieldMenuInk, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = FieldMenuInk, fontSize = 10.sp, lineHeight = 12.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FieldMenuAction("TEAM / MOVES", Modifier.weight(1f), teamEditingAllowed) {
                DroidballService.emitSignal(DroidballSignal.EditTeamRequested)
            }
            FieldMenuAction("SCAN TEAM SELECT", Modifier.weight(1f), teamEditingAllowed) {
                DroidballService.emitSignal(DroidballSignal.ScanTeamSelectRequested)
            }
            when {
                mode == DroidballOverlayMode.RESULT -> FieldMenuAction("NEXT MATCH", Modifier.weight(1f)) {
                    DroidballService.emitSignal(DroidballSignal.BeginNextMatch)
                }
                ready -> FieldMenuAction("HIDE HUD MENU", Modifier.weight(1f)) {
                    DroidballOverlayPresentation.toggleExpanded()
                }
                else -> FieldMenuAction("START BATTLE HUD", Modifier.weight(1f)) {
                    DroidballService.emitSignal(DroidballSignal.OpenBattleHudRequested)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FieldMenuAction("STOP SESSION", Modifier.weight(1f)) {
                DroidballService.emitSignal(DroidballSignal.StopSessionRequested)
            }
            if (needsConfirmation) {
                FieldMenuAction("YES", Modifier.weight(1f)) {
                    DroidballService.emitSignal(DroidballSignal.ConfirmInferredPlayerTeamRequested)
                }
                FieldMenuAction("NO / RESCAN", Modifier.weight(1f)) {
                    DroidballService.emitSignal(DroidballSignal.RejectInferredPlayerTeamRequested)
                }
            } else {
                FieldMenuAction("HIDE", Modifier.weight(1f)) { DroidballOverlayPresentation.toggleExpanded() }
                if (mode == DroidballOverlayMode.SEEKING_TEAM_SELECT) {
                    FieldMenuAction("IGNORE SCREEN", Modifier.weight(1f)) {
                        DroidballService.emitSignal(DroidballSignal.IgnoreCurrentScreenRequested)
                    }
                } else {
                    FieldMenuAction("RESTART OBSERVATION", Modifier.weight(1f), teamEditingAllowed) {
                        DroidballService.emitSignal(DroidballSignal.RestartObservationRequested)
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldMenuAction(label: String, modifier: Modifier, enabled: Boolean = true, action: () -> Unit) {
    Box(modifier.heightIn(min = 48.dp).clickable(enabled = enabled, role = Role.Button, onClick = action)
        .padding(horizontal = 4.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(label, color = FieldMenuInk.copy(alpha = if (enabled) 1f else .4f),
            fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold)
    }
}
