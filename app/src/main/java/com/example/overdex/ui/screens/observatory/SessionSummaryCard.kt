package com.example.overdex.ui.screens.observatory

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.battle.debug.observatory.EvidenceSourceType
import com.example.overdex.battle.debug.observatory.ObservationRecording
import com.example.overdex.ui.components.TerminalSection
import com.example.overdex.ui.components.TerminalText
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalPurple
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SessionSummaryCard(recording: ObservationRecording) {
    val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.ROOT)
    val durationMs = recording.endTime - recording.startTime
    val sourcesSeen = recording.events.map { it.sourceType }.distinct()

    Column {
        TerminalSection(title = "SESSION SUMMARY") {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    TerminalText(text = "ID: ${recording.sessionId.take(8)}...", color = TerminalPurple, fontSize = 12.sp)
                    TerminalText(text = "Duration: ${durationMs}ms", fontSize = 12.sp)
                    TerminalText(text = "Events: ${recording.events.size}", fontSize = 12.sp)
                    TerminalText(text = "Start: ${dateFormat.format(Date(recording.startTime))}", color = TerminalDimGreen, fontSize = 10.sp)
                    TerminalText(text = "End: ${dateFormat.format(Date(recording.endTime))}", color = TerminalDimGreen, fontSize = 10.sp)
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    recording.metadata?.let { meta ->
                        TerminalText(text = "Device: ${meta.deviceModel}", fontSize = 12.sp)
                        TerminalText(text = "Res: ${meta.screenResolution}", fontSize = 12.sp)
                        TerminalText(text = "Orientation: ${if (meta.orientation == 1) "PORT" else "LAND"}", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            TerminalText(text = "EVIDENCE SOURCES SEEN:", color = TerminalPurple, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                EvidenceSourceType.entries.forEach { type ->
                    val seen = sourcesSeen.contains(type)
                    TerminalText(
                        text = "${if (seen) "[X]" else "[ ]"} ${type.name.take(3)}",
                        color = if (seen) TerminalPurple else TerminalDimGreen,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
