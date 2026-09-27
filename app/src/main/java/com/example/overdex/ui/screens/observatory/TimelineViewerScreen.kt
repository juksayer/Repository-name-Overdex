package com.example.overdex.ui.screens.observatory

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.overdex.battle.debug.observatory.EvidenceSourceType
import com.example.overdex.battle.debug.observatory.ObservationRecorder
import com.example.overdex.battle.debug.observatory.RecordedEvent
import com.example.overdex.ui.components.TerminalButton
import com.example.overdex.ui.components.TerminalPathIndicator
import com.example.overdex.ui.components.TerminalScreen

@Composable
fun TimelineViewerScreen(
    onBack: () -> Unit,
    exportMatchId: String? = null,
    onExportCompact: () -> Unit = {},
    onExportFull: () -> Unit = {},
    compactExportSelected: Boolean = false,
    fullExportSelected: Boolean = false,
    exportInProgress: Boolean = false,
    exportStatus: String? = null,
    onOpenMatch: () -> Unit = {},
    openSelected: Boolean = false
) {
    val lastRecording = remember { ObservationRecorder.getLastRecording() }
    var selectedEvent by remember { mutableStateOf<RecordedEvent?>(null) }
    var activeFilters by remember { mutableStateOf(EvidenceSourceType.entries.toSet()) }

    TerminalScreen {
        TerminalPathIndicator(path = "/signal_observatory/timeline_viewer/")

        if (lastRecording == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                EmptyRecordingState()
            }
        } else {
            val filteredEvents = remember(lastRecording, activeFilters) {
                lastRecording.events.filter { activeFilters.contains(it.sourceType) }
            }

            Column(modifier = Modifier.weight(1f)) {
                MatchSummaryCard(recording = lastRecording)
                
                SourceFilterBar(
                    activeFilters = activeFilters,
                    onToggleFilter = { type ->
                        activeFilters = if (activeFilters.contains(type)) {
                            activeFilters - type
                        } else {
                            activeFilters + type
                        }
                    }
                )

                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    TimelineList(
                        events = filteredEvents,
                        selectedEvent = selectedEvent,
                        onEventSelected = { selectedEvent = it },
                        modifier = Modifier.weight(1f)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    EventInspectorPanel(
                        event = selectedEvent,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        TerminalButton(
            text = "OPEN MATCH ARCHIVE",
            onClick = onOpenMatch,
            selected = openSelected
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        if (exportMatchId != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TerminalButton(
                    text = if (exportInProgress && compactExportSelected) "SAVING…" else "SAVE COMPACT",
                    onClick = { if (!exportInProgress) onExportCompact() },
                    selected = compactExportSelected,
                    modifier = Modifier.weight(1f)
                )
                TerminalButton(
                    text = if (exportInProgress && fullExportSelected) "SAVING…" else "SAVE FULL",
                    onClick = { if (!exportInProgress) onExportFull() },
                    selected = fullExportSelected,
                    modifier = Modifier.weight(1f)
                )
            }
            exportStatus?.let { status ->
                androidx.compose.material3.Text(
                    text = status,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        val backSelected = !openSelected && !compactExportSelected && !fullExportSelected
        TerminalButton(text = "BACK", onClick = onBack, selected = backSelected)
    }
}
