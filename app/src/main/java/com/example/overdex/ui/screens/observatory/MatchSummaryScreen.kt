package com.example.overdex.ui.screens.observatory

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.battle.archive.*
import com.example.overdex.battle.history.BattleRecordStore
import com.example.overdex.ui.components.TerminalHeader
import com.example.overdex.ui.components.TerminalScreen
import com.example.overdex.ui.components.TerminalText
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalPurple
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Both an in-process ledger and a saved match lead to the same summary. No crop decoding. */
@Composable
fun MatchSummaryRoute(
    source: MatchArchiveSource?,
    directory: ArchiveDirectoryManager,
    onOpenMatches: () -> Unit,
    onInspect: (MatchArchive, Uri?) -> Unit,
    onBack: () -> Unit,
    onUp: (() -> Unit) -> Unit,
    onDown: (() -> Unit) -> Unit,
    onLeft: (() -> Unit) -> Unit,
    onRight: (() -> Unit) -> Unit,
    onA: (() -> Unit) -> Unit,
    onB: (() -> Unit) -> Unit,
    onLcdContentUpdate: ((@Composable () -> Unit)?) -> Unit,
    onOpenExportedMatch: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var archive by remember { mutableStateOf<MatchArchive?>(null) }
    var uri by remember { mutableStateOf<Uri?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var exportStatus by remember { mutableStateOf<String?>(null) }
    var exportProgress by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(source, directory) {
        loading = true
        error = null
        var lastSaved: ArchiveDirectoryManager.ArchiveEntry? = null
        while (isActive) {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    if (source != null) {
                        source.snapshot() to null
                    } else {
                        val newest = directory.listArchives().maxByOrNull { it.lastModified }
                        if (newest == null) null to null
                        else if (newest == lastSaved) archive to uri
                        else {
                            val saved = BattleRecordStore(context).readArchive(newest.uri)
                            lastSaved = newest
                            saved to newest.uri
                        }
                    }
                }
                archive = loaded.first
                uri = loaded.second
                error = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = "Unable to read the latest match: ${failure.message}"
            }
            loading = false
            delay(1_000)
        }
    }

    fun export(mode: MatchArchiveExportMode) {
        val snapshot = archive ?: return
        if (exporting) return
        exporting = true
        exportProgress = null
        exportStatus = "PREPARING ARCHIVE…"
        scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    directory.exportArchiveToDownloads(snapshot, mode) { completed, total ->
                        scope.launch {
                            exportStatus = "VERIFYING PRESERVED EVIDENCE $completed / $total"
                            exportProgress = if (total > 0) completed.toFloat() / total else null
                        }
                    }
                }
                exportStatus = if (saved == null) "SAVE FAILED" else "SAVED TO DOWNLOADS/ODXMATCHES"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                exportStatus = "SAVE FAILED: ${failure.message}"
            } finally {
                exporting = false
            }
        }
    }

    MatchSummaryScreen(archive, loading, error, source != null, exporting, exportStatus, exportProgress,
        onOpenMatches, { archive?.let { onInspect(it, uri) } },
        { export(MatchArchiveExportMode.COMPACT_CITED_EVIDENCE) },
        { export(MatchArchiveExportMode.FULL_FORENSIC) }, onBack,
        onUp, onDown, onLeft, onRight, onA, onB, onLcdContentUpdate,
        onExportReplayOnly = { export(MatchArchiveExportMode.TIMELINE_ONLY) },
        onOpenExportedMatch = onOpenExportedMatch)
}

private enum class SummaryAction { OPEN_MATCHES, INSPECT, REPLAY_ONLY, COMPACT, FULL, OPEN_EXPORTED, BACK }

/** CRT is read-only; every control is on the LCD and reachable by instrument buttons. */
@Composable
fun MatchSummaryScreen(
    archive: MatchArchive?,
    loading: Boolean = false,
    error: String? = null,
    fullExportAvailable: Boolean = false,
    exporting: Boolean = false,
    exportStatus: String? = null,
    exportProgress: Float? = null,
    onOpenMatches: () -> Unit = {},
    onInspect: () -> Unit = {},
    onExportCompact: () -> Unit = {},
    onExportFull: () -> Unit = {},
    onBack: () -> Unit = {},
    onUp: (() -> Unit) -> Unit = {},
    onDown: (() -> Unit) -> Unit = {},
    onLeft: (() -> Unit) -> Unit = {},
    onRight: (() -> Unit) -> Unit = {},
    onA: (() -> Unit) -> Unit = {},
    onB: (() -> Unit) -> Unit = {},
    onLcdContentUpdate: ((@Composable () -> Unit)?) -> Unit = {},
    onExportReplayOnly: () -> Unit = {},
    onOpenExportedMatch: () -> Unit = {},
) {
    val summary = remember(archive) { archive?.summarize() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val scrollStep = with(LocalDensity.current) { 120.dp.toPx() }
    val actions = buildList {
        add(SummaryAction.OPEN_MATCHES)
        if (archive != null) {
            add(SummaryAction.INSPECT)
            add(SummaryAction.REPLAY_ONLY)
            if (archive.mediaIncluded) {
                add(SummaryAction.COMPACT)
                if (fullExportAvailable) add(SummaryAction.FULL)
            }
        }
        add(SummaryAction.OPEN_EXPORTED)
        add(SummaryAction.BACK)
    }
    var selected by remember { mutableStateOf(SummaryAction.OPEN_MATCHES) }
    LaunchedEffect(actions) { if (selected !in actions) selected = actions.first() }
    fun move(delta: Int) {
        selected = actions[(actions.indexOf(selected).coerceAtLeast(0) + delta + actions.size) % actions.size]
    }
    fun activate(action: SummaryAction) {
        selected = action
        when (action) {
            SummaryAction.OPEN_MATCHES -> onOpenMatches()
            SummaryAction.INSPECT -> onInspect()
            SummaryAction.REPLAY_ONLY -> if (!exporting) onExportReplayOnly()
            SummaryAction.COMPACT -> if (!exporting) onExportCompact()
            SummaryAction.FULL -> if (!exporting) onExportFull()
            SummaryAction.OPEN_EXPORTED -> onOpenExportedMatch()
            SummaryAction.BACK -> onBack()
        }
    }
    BackHandler(onBack = onBack)
    SideEffect {
        onUp { scope.launch { scroll.scrollBy(-scrollStep) } }
        onDown { scope.launch { scroll.scrollBy(scrollStep) } }
        onLeft { move(-1) }
        onRight { move(1) }
        onA { activate(selected) }
        onB(onBack)
    }
    PublishMatchLcd(onLcdContentUpdate) {
        MatchLcdColumn {
            MatchLcdText("↑/↓ SCROLL   ←/→ ACTION   A SELECT")
            MatchLcdButton("OPEN RECORDED MATCHES", { activate(SummaryAction.OPEN_MATCHES) }, selected = selected == SummaryAction.OPEN_MATCHES)
            if (archive != null) {
                MatchLcdButton("INSPECT TIMELINE / REPLAY", { activate(SummaryAction.INSPECT) }, selected = selected == SummaryAction.INSPECT)
                MatchLcdButton("EXPORT REPLAY ONLY", { activate(SummaryAction.REPLAY_ONLY) },
                    selected = selected == SummaryAction.REPLAY_ONLY, enabled = !exporting)
                if (selected == SummaryAction.REPLAY_ONLY) MatchLcdText("ALL RECORDED READINGS · NO IMAGES / AUDIO")
                if (archive.mediaIncluded) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MatchLcdButton("EXPORT COMPACT", { activate(SummaryAction.COMPACT) }, Modifier.weight(1f), selected == SummaryAction.COMPACT, !exporting)
                    if (fullExportAvailable) MatchLcdButton("EXPORT FULL", { activate(SummaryAction.FULL) }, Modifier.weight(1f), selected == SummaryAction.FULL, !exporting)
                }
            }
            if (exporting) {
                if (exportProgress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { exportProgress }, modifier = Modifier.fillMaxWidth())
            }
            exportStatus?.let { MatchLcdText(it) }
            MatchLcdButton("OPEN EXPORTED MATCH", { activate(SummaryAction.OPEN_EXPORTED) },
                selected = selected == SummaryAction.OPEN_EXPORTED)
            MatchLcdButton("BACK", { activate(SummaryAction.BACK) }, selected = selected == SummaryAction.BACK)
            summary?.let { MatchLcdText("ID: ${it.matchId}\nEXPORTS: DOWNLOADS/ODXMATCHES") }
        }
    }
    TerminalScreen {
        TerminalHeader("MATCH SUMMARY")
        Column(Modifier.weight(1f).fillMaxWidth().testTag("match-summary-content").verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                loading -> {
                    TerminalText("READING MATCH SUMMARY…")
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                error != null -> TerminalText(error)
                summary == null -> TerminalText("No recorded matches yet. Open Recorded Matches to browse available archives.")
                else -> {
                    TerminalText(when (summary.result) {
                        "WIN" -> "YOU WIN"
                        "LOSS" -> "GOOD EFFORT"
                        "TIE" -> "TIE"
                        null -> "RESULT NOT RECORDED"
                        else -> summary.result
                    }, color = TerminalPurple, fontSize = 18.sp)
                    summary.recordedAt?.let {
                        TerminalText(SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(it)), fontSize = 12.sp)
                    }
                    TerminalText(summary.battleDurationMillis?.let {
                        "${if (summary.result == null) "Battle observed" else "Battle duration"}: ${summaryTime(it)}"
                    } ?: "Match start not recorded", fontSize = 13.sp)
                    SummarySide("PLAYER", summary.player)
                    SummarySide("OPPONENT", summary.opponent)
                    if (summary.playerInformation.isNotEmpty()) {
                        TerminalText("PLAYER INFORMATION", color = TerminalPurple, fontSize = 14.sp)
                        summary.playerInformation.forEach { check ->
                            val status = when (check.status) {
                                "DIFFERS" -> "Differs from saved: ${check.configuredValue}"
                                "CONFIRMED" -> "Matches saved information"
                                "COMPATIBLE" -> if (check.field == "Fast Move")
                                    "Timing fits saved move; name not independently confirmed"
                                else "Species name fits; form and size not independently confirmed"
                                else -> "Observed in this match"
                            }
                            TerminalText("${check.speciesName} · ${check.field}: ${check.observedValue}\n$status", fontSize = 12.sp)
                        }
                    }
                    summary.resultMusic?.let { TerminalText(it, fontSize = 13.sp) }
                    TerminalText("RECORDING", color = TerminalPurple, fontSize = 14.sp)
                    if (archive?.mediaIncluded == false) TerminalText("Replay only: recorded readings retained; original images and audio omitted.", color = TerminalDimGreen, fontSize = 12.sp)
                    TerminalText("${summary.articleCount} Timeline articles\nRecording length: ${summaryTime(summary.recordingDurationMillis)}", fontSize = 12.sp)
                    TerminalText("Counts reflect recorded observations; missed events are not added to the totals.", color = TerminalDimGreen, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SummarySide(label: String, side: MatchSideSummary) {
    TerminalText(label, color = TerminalPurple, fontSize = 14.sp)
    TerminalText(side.species.joinToString(" · ").ifBlank { "No species recorded" }, fontSize = 14.sp)
    TerminalText("Fast Moves: ${side.fastMoveUses}   Charged: ${side.chargedMoveUses}\nFaints recorded: ${side.faintCount}", fontSize = 12.sp)
    side.identifiedFastMoves.forEach { TerminalText(it, fontSize = 12.sp) }
    side.chargedMoves.forEach { TerminalText(it, fontSize = 12.sp) }
}

private fun summaryTime(millis: Long): String =
    String.format(Locale.ROOT, "%d:%02d", millis / 60_000, (millis / 1_000) % 60)
