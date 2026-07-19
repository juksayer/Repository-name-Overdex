package com.example.overdex.ui.screens.observatory

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val eventCounts = recording.events.groupingBy { it.sourceType }.eachCount()

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
            TerminalText(text = "EVENT STATISTICS:", color = TerminalPurple, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                EvidenceSourceType.entries.forEach { type ->
                    val count = eventCounts[type] ?: 0
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        TerminalText(
                            text = type.name.take(3),
                            color = if (count > 0) TerminalPurple else TerminalDimGreen,
                            fontSize = 10.sp
                        )
                        TerminalText(
                            text = count.toString(),
                            color = if (count > 0) Color.White else TerminalDimGreen,
                            fontSize = 12.sp,
                            fontWeight = if (count > 0) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
