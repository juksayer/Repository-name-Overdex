package com.example.overdex.ui.screens.observatory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.battle.archive.ComparedMatch
import com.example.overdex.battle.archive.MatchComparison
import com.example.overdex.battle.archive.MatchComparisonEntry
import com.example.overdex.ui.components.TerminalHeader
import com.example.overdex.ui.components.TerminalScreen
import com.example.overdex.ui.components.TerminalText
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.ui.theme.TerminalPurple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ComparisonAction { PREVIOUS, NEXT, OPEN, BACK }

/** The enclosing ODX-Fi shell owns GlassShield; all actions remain on its separate LCD. */
@Composable
fun MatchComparisonScreen(
    entries: List<MatchComparisonEntry>,
    loading: Boolean = false,
    onOpenMatch: (String) -> Unit = {},
    onBack: () -> Unit = {},
    onUp: (() -> Unit) -> Unit = {},
    onDown: (() -> Unit) -> Unit = {},
    onLeft: (() -> Unit) -> Unit = {},
    onRight: (() -> Unit) -> Unit = {},
    onA: (() -> Unit) -> Unit = {},
    onB: (() -> Unit) -> Unit = {},
    onLcdContentUpdate: ((@Composable () -> Unit)?) -> Unit = {},
) {
    val ids = remember(entries) { entries.map { it.matchId }.distinct() }
    val latestProjection by produceState<MatchComparison?>(null, entries) {
        value = withContext(Dispatchers.Default) { MatchComparison.project(entries) }
    }
    // Keep the visible cards while a late article refreshes this same selection.
    // A different batch must wait for its own projection rather than show old rows.
    val comparison = latestProjection?.takeIf { it.matches.map(ComparedMatch::matchId) == ids }
    var selectedId by rememberSaveable { mutableStateOf(ids.firstOrNull()) }
    var selectedAction by rememberSaveable { mutableStateOf(ComparisonAction.OPEN) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val scrollStep = with(LocalDensity.current) { 120.dp.toPx() }
    val requesters = remember(ids) { ids.associateWith { BringIntoViewRequester() } }
    LaunchedEffect(ids) {
        if (selectedId !in ids) selectedId = ids.firstOrNull()
    }
    val selectedRowAvailable = comparison?.matches?.any { it.matchId == selectedId } == true
    LaunchedEffect(selectedId, ids, selectedRowAvailable) {
        if (selectedRowAvailable) selectedId?.let { requesters[it]?.bringIntoView() }
    }
    val selectedIndex = ids.indexOf(selectedId)
    val selectedMatch = comparison?.matches?.firstOrNull { it.matchId == selectedId }
    val canOpen = selectedMatch?.summary != null
    val actions = ComparisonAction.entries
    fun selectMatch(delta: Int) {
        if (ids.isEmpty()) return
        selectedId = ids[(selectedIndex.coerceAtLeast(0) + delta + ids.size) % ids.size]
    }
    fun activate(action: ComparisonAction) {
        selectedAction = action
        when (action) {
            ComparisonAction.PREVIOUS -> selectMatch(-1)
            ComparisonAction.NEXT -> selectMatch(1)
            ComparisonAction.OPEN -> if (canOpen) selectedId?.let(onOpenMatch)
            ComparisonAction.BACK -> onBack()
        }
    }
    fun selectAction(delta: Int) {
        selectedAction = actions[(actions.indexOf(selectedAction) + delta + actions.size) % actions.size]
    }
    BackHandler(onBack = onBack)
    SideEffect {
        onUp { scope.launch { scroll.scrollBy(-scrollStep) } }
        onDown { scope.launch { scroll.scrollBy(scrollStep) } }
        onLeft { selectAction(-1) }
        onRight { selectAction(1) }
        onA { activate(selectedAction) }
        onB(onBack)
    }
    PublishMatchLcd(onLcdContentUpdate) {
        MatchLcdColumn {
            MatchLcdText(if (ids.isEmpty()) "MATCH COMPARISON" else
                "MATCH ${selectedIndex.coerceAtLeast(0) + 1} / ${ids.size} · ${comparisonResult(selectedMatch?.summary?.result)}")
            MatchLcdText("↑/↓ SCROLL   ←/→ ACTION   A SELECT")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MatchLcdButton("PREV MATCH", { activate(ComparisonAction.PREVIOUS) }, Modifier.weight(1f),
                    selectedAction == ComparisonAction.PREVIOUS, ids.size > 1)
                MatchLcdButton("NEXT MATCH", { activate(ComparisonAction.NEXT) }, Modifier.weight(1f),
                    selectedAction == ComparisonAction.NEXT, ids.size > 1)
            }
            MatchLcdButton("OPEN MATCH", { activate(ComparisonAction.OPEN) },
                selected = selectedAction == ComparisonAction.OPEN, enabled = canOpen)
            MatchLcdButton("BACK", { activate(ComparisonAction.BACK) }, selected = selectedAction == ComparisonAction.BACK)
        }
    }
    TerminalScreen {
        TerminalHeader("MATCH COMPARISON")
        Column(Modifier.weight(1f).fillMaxWidth().testTag("match-comparison-content").verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TerminalText("RECORDED PLAYER MOVE USES", color = TerminalPurple, fontSize = 13.sp)
            TerminalText("Fast and charged moves share the same scale across every match. " +
                "0 means no use was recorded; missed observations can leave gaps.",
                color = TerminalDimGreen, fontSize = 11.sp)
            if (loading || comparison == null) {
                TerminalText("READING SELECTED MATCHES…", fontSize = 12.sp)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            comparison?.let { projected ->
                TerminalText("ALL BARS: 0–${projected.maximumUses} RECORDED USES", fontSize = 11.sp)
                if (projected.matches.isEmpty() && !loading) TerminalText("No matches selected.")
                projected.matches.forEachIndexed { index, match ->
                    key(match.matchId) {
                        ComparisonCard(match, index + 1, projected.maximumUses, match.matchId == selectedId,
                            loading, Modifier.bringIntoViewRequester(requesters.getValue(match.matchId)))
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonCard(
    match: ComparedMatch, number: Int, maximum: Int, isSelected: Boolean, loading: Boolean, modifier: Modifier,
) {
    Column(modifier.fillMaxWidth().testTag("match-comparison-card-${match.matchId}")
        .semantics { selected = isSelected }
        .border(BorderStroke(1.dp, if (isSelected) TerminalGreen else TerminalDimGreen.copy(alpha = .4f)))
        .padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TerminalText("MATCH $number · ${comparisonResult(match.summary?.result)}", color = TerminalPurple, fontSize = 14.sp)
        val summary = match.summary
        match.error?.let { error ->
            TerminalText(if (summary == null) "Unable to read this match: $error" else
                "Latest refresh unavailable: $error. Showing the retained record.", fontSize = 12.sp)
        }
        if (summary == null) {
            if (match.error == null) TerminalText(if (loading) "Reading this match…" else "This match is unavailable.", fontSize = 12.sp)
        } else {
                summary.recordedAt?.let { at ->
                    val date = remember(at) { SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date(at)) }
                    TerminalText(date, color = TerminalDimGreen, fontSize = 11.sp)
                }
                TerminalText(summary.battleDurationMillis?.let { duration ->
                    "${if (summary.result == null) "Battle observed" else "Battle duration"}: ${comparisonDuration(duration)}"
                } ?: "Battle start not recorded", fontSize = 11.sp)
                TerminalText("PLAYER · ${summary.player.fastMoveUses} fast · ${summary.player.chargedMoveUses} charged · " +
                    "${summary.player.faintCount} faints recorded", fontSize = 11.sp)
                if (match.playerUses.isEmpty()) TerminalText("No player species recorded.", color = TerminalDimGreen, fontSize = 11.sp)
                match.playerUses.forEachIndexed { index, uses ->
                    TerminalText(uses.speciesName, fontSize = 12.sp)
                    UseBar("FAST", uses.fastMoveUses, maximum, TerminalGreen,
                        "comparison-${match.matchId}-player-$index-fast-bar")
                    UseBar("CHARGED", uses.chargedMoveUses, maximum, TerminalPurple,
                        "comparison-${match.matchId}-player-$index-charged-bar")
                }
                if (match.unattributedPlayerFastMoveUses > 0 || match.unattributedPlayerChargedMoveUses > 0) {
                    TerminalText("Player species not attributed", color = TerminalDimGreen, fontSize = 12.sp)
                    UseBar("FAST", match.unattributedPlayerFastMoveUses, maximum, TerminalGreen,
                        "comparison-${match.matchId}-unattributed-fast-bar")
                    UseBar("CHARGED", match.unattributedPlayerChargedMoveUses, maximum, TerminalPurple,
                        "comparison-${match.matchId}-unattributed-charged-bar")
                }
                TerminalText("OPPONENT · ${summary.opponent.fastMoveUses} fast · ${summary.opponent.chargedMoveUses} charged · " +
                    "${summary.opponent.faintCount} faints recorded", fontSize = 11.sp)
                TerminalText(summary.opponent.species.takeIf { it.isNotEmpty() }?.joinToString(" · ")
                    ?: "Opponent species not recorded", color = TerminalDimGreen, fontSize = 11.sp)
        }
    }
}

@Composable
private fun UseBar(label: String, uses: Int, maximum: Int, color: Color, tag: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TerminalText("$label $uses", Modifier.width(84.dp), color = color, fontSize = 10.sp)
        LinearProgressIndicator(progress = { uses.toFloat() / maximum },
            modifier = Modifier.weight(1f).height(6.dp).testTag(tag)
                .semantics { contentDescription = "$label: $uses recorded uses; scale 0 to $maximum" },
            color = color, trackColor = color.copy(alpha = .12f))
    }
}

private fun comparisonResult(result: String?): String = when (result?.uppercase(Locale.ROOT)) {
    "WIN" -> "WIN"
    "LOSS" -> "LOSS"
    "DRAW", "TIE" -> "TIE"
    else -> "RESULT NOT RECORDED"
}

private fun comparisonDuration(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1_000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
