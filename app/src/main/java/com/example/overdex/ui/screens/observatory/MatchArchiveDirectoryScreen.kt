package com.example.overdex.ui.screens.observatory

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.battle.archive.ArchiveDirectoryManager
import com.example.overdex.battle.archive.summarize
import com.example.overdex.battle.history.BattleRecordStore
import com.example.overdex.ui.components.TerminalHeader
import com.example.overdex.ui.components.TerminalPathIndicator
import com.example.overdex.ui.components.TerminalScreen
import com.example.overdex.ui.components.TerminalText
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.ui.theme.TerminalPurple
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MatchArchiveDirectoryScreen(
    archiveDirectoryManager: ArchiveDirectoryManager,
    onBack: () -> Unit,
    onOpenArchive: (Uri) -> Unit,
    isLoading: Boolean = false,
    onUp: (() -> Unit) -> Unit = {},
    onDown: (() -> Unit) -> Unit = {},
    onA: (() -> Unit) -> Unit = {},
    onB: (() -> Unit) -> Unit = {},
    onLcdUpdate: (String?, String?) -> Unit = { _, _ -> },
    onLcdContentUpdate: ((@Composable () -> Unit)?) -> Unit = {},
) {
    val context = LocalContext.current
    val reader = remember(context) { BattleRecordStore(context.applicationContext) }
    var selectedUri by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedMatchId by rememberSaveable { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var initialLoading by remember { mutableStateOf(true) }
    var matches by remember { mutableStateOf(emptyList<MatchHistoryItem>()) }
    val listState = rememberLazyListState()
    val latestBack by rememberUpdatedState(onBack)
    val latestOpen by rememberUpdatedState(onOpenArchive)

    // Internal publication/recovery may finish while History remains open. Cache
    // unchanged metadata, read only references on I/O, and never decode a crop.
    LaunchedEffect(archiveDirectoryManager, reader) {
        val cache = mutableMapOf<String, Pair<ArchiveDirectoryManager.ArchiveEntry, MatchHistoryItem>>()
        while (isActive) {
            try {
                val current = withContext(Dispatchers.IO) {
                    val entries = archiveDirectoryManager.listArchives()
                        .filter { isSavedMatchHistoryArchive(it.name) }
                    val presentUris = entries.mapTo(hashSetOf()) { it.uri.toString() }
                    cache.keys.retainAll(presentUris)
                    entries.map { entry ->
                        ensureActive()
                        val uri = entry.uri.toString()
                        cache[uri]?.takeIf { it.first == entry }?.second ?: run {
                            val item = try {
                                MatchHistoryItem(uri, entry.name, reader.readArchive(entry.uri).summarize())
                            } catch (error: Exception) {
                                if (error is CancellationException) throw error
                                MatchHistoryItem(uri, entry.name, null,
                                    error.message ?: "This recording could not be read.")
                            }
                            cache[uri] = entry to item
                            item
                        }
                    }.distinctRecordedMatches().newestMatchFirst()
                }
                matches = current
                selectedUri = selectedMatchHistoryUri(current, selectedUri, selectedMatchId)
                selectedMatchId = current.firstOrNull { it.sourceUri == selectedUri }?.summary?.matchId
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                errorMessage = "Unable to read Match History: ${error.message ?: "storage unavailable"}"
            } finally {
                initialLoading = false
            }
            delay(1_000L)
        }
    }

    val selectedIndex = matches.indexOfFirst { it.sourceUri == selectedUri }
    val selectedEntry = matches.getOrNull(selectedIndex)
    val busy = isLoading || initialLoading
    fun dateLabel(entry: MatchHistoryItem): String = entry.summary?.recordedAt?.let {
        SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(it))
    } ?: "Date unavailable"
    fun teamLabel(species: List<String>?): String = species?.takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: "Not observed"
    fun openSelected() {
        if (busy) return
        errorMessage = null
        val entry = selectedEntry ?: return
        if (entry.readError != null) {
            errorMessage = "Cannot open ${entry.archiveName}: ${entry.readError}"
        } else {
            try { latestOpen(Uri.parse(entry.sourceUri)) }
            catch (error: Exception) {
                errorMessage = "Cannot open this match: ${error.message ?: "recording unavailable"}"
            }
        }
    }

    LaunchedEffect(selectedUri, matches) {
        if (selectedIndex >= 0) {
            val visible = listState.layoutInfo.visibleItemsInfo.map { it.index }
            if (selectedIndex !in visible) listState.scrollToItem(selectedIndex)
        }
    }
    LaunchedEffect(selectedEntry, busy, errorMessage) {
        onLcdUpdate(selectedEntry?.let(::dateLabel) ?: "MATCH HISTORY",
            errorMessage ?: if (busy) "READING SAVED MATCHES…" else selectedEntry?.resultLabel ?: "NO SAVED MATCHES")
    }
    BackHandler { latestBack() }
    SideEffect {
        onUp {
            if (!busy && matches.isNotEmpty()) {
                selectedUri = matches[(selectedIndex - 1).coerceAtLeast(0)].sourceUri
                selectedMatchId = matches.firstOrNull { it.sourceUri == selectedUri }?.summary?.matchId
                errorMessage = null
            }
        }
        onDown {
            if (!busy && matches.isNotEmpty()) {
                selectedUri = matches[(selectedIndex + 1).coerceAtMost(matches.lastIndex)].sourceUri
                selectedMatchId = matches.firstOrNull { it.sourceUri == selectedUri }?.summary?.matchId
                errorMessage = null
            }
        }
        onA { openSelected() }
        onB { latestBack() }
    }

    PublishMatchLcd(onLcdContentUpdate) {
        MatchLcdColumn {
            when {
                busy -> MatchLcdText("READING SAVED MATCHES…")
                selectedEntry != null -> {
                    MatchLcdText("${dateLabel(selectedEntry)}\n${selectedEntry.resultLabel}")
                    MatchLcdText("YOU: ${teamLabel(selectedEntry.summary?.player?.species)}\nOPPONENT: ${teamLabel(selectedEntry.summary?.opponent?.species)}")
                }
                else -> MatchLcdText("No saved matches yet.")
            }
            errorMessage?.let { MatchLcdText(it) }
            MatchLcdButton("OPEN SUMMARY", ::openSelected, selected = selectedEntry != null,
                enabled = !busy && selectedEntry != null)
            MatchLcdButton("BACK", { latestBack() })
        }
    }

    TerminalScreen {
        TerminalPathIndicator(path = "/battle/match_history/")
        Column(Modifier.fillMaxSize()) {
            TerminalHeader(text = "MATCH HISTORY")
            TerminalText("SAVED MATCHES · NEWEST FIRST", color = TerminalPurple, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            if (matches.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (initialLoading) CircularProgressIndicator(color = TerminalGreen)
                    else TerminalText("No saved matches yet.", color = Color.Gray)
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    itemsIndexed(matches, key = { _, entry -> entry.sourceUri }) { index, entry ->
                        val isSelected = index == selectedIndex
                        Column(Modifier.fillMaxWidth()
                            .testTag("match-history-row-${entry.summary?.matchId ?: entry.sourceUri}")
                            .semantics { selected = isSelected }
                            .background(if (isSelected) TerminalGreen.copy(alpha = 0.16f) else Color.Transparent)
                            .border(1.dp, if (isSelected) TerminalGreen else Color.Transparent, RoundedCornerShape(2.dp))
                            .padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            TerminalText("${if (isSelected) "> " else ""}${dateLabel(entry)}", fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            TerminalText(entry.resultLabel, color = TerminalPurple, fontSize = 12.sp)
                            TerminalText("YOU: ${teamLabel(entry.summary?.player?.species)}", fontSize = 12.sp)
                            TerminalText("OPPONENT: ${teamLabel(entry.summary?.opponent?.species)}", fontSize = 12.sp)
                            if (entry.summary == null) TerminalText(entry.archiveName, color = TerminalDimGreen, fontSize = 10.sp)
                        }
                    }
                }
            }
            if (isLoading) {
                Spacer(Modifier.height(4.dp))
                TerminalText("READING MATCH SUMMARY…", color = TerminalGreen, fontSize = 11.sp)
            }
            errorMessage?.let { TerminalText(it, color = Color.Red, fontSize = 11.sp) }
            Spacer(Modifier.height(6.dp))
            TerminalText(if (busy) "READING SAVED MATCHES…" else "↑/↓ MATCH   A SUMMARY   B BACK",
                color = TerminalDimGreen, fontSize = 11.sp)
        }
    }
}
