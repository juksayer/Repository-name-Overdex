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
    openSelected: Boolean = false,
    onLcdContentUpdate: ((@Composable () -> Unit)?) -> Unit = {}
) {
    val lastRecording = remember { ObservationRecorder.getLastRecording() }
    var selectedEvent by remember { mutableStateOf<RecordedEvent?>(null) }
    var activeFilters by remember { mutableStateOf(EvidenceSourceType.entries.toSet()) }

    PublishMatchLcd(onLcdContentUpdate) {
        MatchLcdColumn {
            exportMatchId?.let { MatchLcdText("ID: $it") }
            MatchLcdButton("OPEN MATCH ARCHIVE", onOpenMatch, selected = openSelected)
            if (exportMatchId != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MatchLcdButton("SAVE COMPACT", onExportCompact, Modifier.weight(1f), compactExportSelected, !exportInProgress)
                    MatchLcdButton("SAVE FULL", onExportFull, Modifier.weight(1f), fullExportSelected, !exportInProgress)
                }
                if (exportInProgress) androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                exportStatus?.let { MatchLcdText(it) }
            }
            MatchLcdButton("BACK", onBack, selected = !openSelected && !compactExportSelected && !fullExportSelected)
        }
    }

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

    }
}
