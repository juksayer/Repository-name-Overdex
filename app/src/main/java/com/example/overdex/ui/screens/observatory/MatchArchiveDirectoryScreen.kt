package com.example.overdex.ui.screens.observatory

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.battle.archive.ArchiveDirectoryManager
import com.example.overdex.ui.components.TerminalHeader
import com.example.overdex.ui.components.TerminalPathIndicator
import com.example.overdex.ui.components.TerminalScreen
import com.example.overdex.ui.components.TerminalText
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.ui.theme.TerminalPurple
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MatchArchiveDirectoryScreen(
    archiveDirectoryManager: ArchiveDirectoryManager,
    onBack: () -> Unit,
    onOpenArchive: (android.net.Uri) -> Unit,
    onRequestFolderConfigure: () -> Unit,
    isLoading: Boolean = false,
    onUp: (() -> Unit) -> Unit = {},
    onDown: (() -> Unit) -> Unit = {},
    onA: (() -> Unit) -> Unit = {},
    onB: (() -> Unit) -> Unit = {},
    onLcdUpdate: (String?, String?) -> Unit = { _, _ -> }
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val isAvailable = remember(archiveDirectoryManager) { archiveDirectoryManager.isFolderAvailable() }
    val folderDisplayName = remember(isAvailable) { archiveDirectoryManager.getFolderDisplayName() }
    val archives = remember(isAvailable) {
        if (isAvailable) archiveDirectoryManager.listArchives() else emptyList()
    }

    val selectedEntry = archives.getOrNull(selectedIndex)

    LaunchedEffect(selectedEntry, isLoading) {
        val entry = selectedEntry
        onLcdUpdate(
            entry?.name?.removeSuffix(".odxmatch.zip") ?: "ARCHIVE DIRECTORY",
            entry?.let {
                val size = if (it.byteCount >= 1_000_000L) "${it.byteCount / 1_000_000L} MB" else "${it.byteCount / 1_000L} KB"
                "$size  ${SimpleDateFormat("MMM d HH:mm", Locale.US).format(Date(it.lastModified))}"
            } ?: if (isLoading) "READING MATCH ARCHIVE" else "NO ARCHIVE SELECTED"
        )
    }

    BackHandler {
        onBack()
    }

    SideEffect {
        onUp {
            if (!isLoading && isAvailable && archives.isNotEmpty()) {
                selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                scope.launch { listState.animateScrollToItem(selectedIndex) }
            }
        }
        onDown {
            if (!isLoading && isAvailable && archives.isNotEmpty()) {
                selectedIndex = (selectedIndex + 1).coerceAtMost(archives.size - 1)
                scope.launch { listState.animateScrollToItem(selectedIndex) }
            }
        }
        onA {
            if (isLoading) return@onA
            errorMessage = null
            if (!isAvailable) {
                onRequestFolderConfigure()
            } else if (selectedEntry != null) {
                try {
                    onOpenArchive(selectedEntry.uri)
                } catch (e: Exception) {
                    errorMessage = "Failed to open ${selectedEntry.name}: ${e.message}"
                }
            }
        }
        onB {
            onBack()
        }
    }

    TerminalScreen {
        TerminalPathIndicator(path = "/signal_observatory/archive_directory/")

        Column(modifier = Modifier.fillMaxSize()) {
            TerminalHeader(text = "ARCHIVE DIRECTORY")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TerminalText(
                    text = "FOLDER: $folderDisplayName",
                    color = TerminalPurple,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                androidx.compose.material3.TextButton(onClick = onRequestFolderConfigure) {
                    TerminalText(text = "[ CHANGE FOLDER ]", color = TerminalGreen, fontSize = 10.sp)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (!isAvailable) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TerminalText(text = "Archive folder unavailable.", color = Color.Red, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        TerminalText(text = "Press [A] or click CHANGE FOLDER to select again.", color = TerminalDimGreen, fontSize = 11.sp)
                    }
                }
            } else if (archives.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TerminalText(text = "No match archives found.", color = Color.Gray)
                }
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(archives) { index, entry ->
                            val isSelected = index == selectedIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isSelected) TerminalGreen.copy(alpha = 0.2f) else Color.Transparent)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) TerminalGreen else Color.Transparent,
                                        shape = RoundedCornerShape(2.dp)
                                    )
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TerminalText(
                                    text = entry.name,
                                    color = if (isSelected) TerminalGreen else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                TerminalText(
                                    text = if (isSelected) "[SELECTED]" else "",
                                    color = TerminalDimGreen,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = TerminalGreen,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    TerminalText(
                        text = "READING MATCH ARCHIVE…",
                        color = TerminalGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                TerminalText(text = errorMessage!!, color = Color.Red, fontSize = 11.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))
            TerminalText(
                text = when {
                    isLoading -> "LOADING — PLEASE WAIT"
                    !isAvailable -> "[A] SELECT FOLDER  [B] BACK"
                    else -> "[A] OPEN ARCHIVE  [B] BACK"
                },
                color = TerminalDimGreen,
                fontSize = 11.sp
            )
        }
    }
}
