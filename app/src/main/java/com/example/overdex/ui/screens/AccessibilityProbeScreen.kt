package com.example.overdex.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.battle.debug.accessibility.*
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.*
import java.util.Locale

@Composable
fun AccessibilityProbeScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(AccessibilityProbeManager.isActive()) }
    val events = remember { mutableStateListOf<AccessibilityProbeEvent>() }
    var summary by remember { mutableStateOf(AccessibilityProbeManager.getSummary()) }
    var selectedEvent by remember { mutableStateOf<AccessibilityProbeEvent?>(null) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                events.clear()
                events.addAll(AccessibilityProbeManager.getEvents().filterIsInstance<AccessibilityProbeEvent>())
                summary = AccessibilityProbeManager.getSummary()
                kotlinx.coroutines.delay(500)
            }
        }
    }

    TerminalScreen {
        TerminalPathIndicator(path = "/signal_observatory/accessibility_probe/")

        if (selectedEvent != null) {
            EventInspectionView(
                event = selectedEvent!!,
                onBack = { selectedEvent = null }
            )
        } else {
            ProbeMainView(
                isRecording = isRecording,
                summary = summary,
                events = events,
                onToggleRecording = {
                    if (isRecording) {
                        AccessibilityProbeManager.stopRecording()
                    } else {
                        AccessibilityProbeManager.captureMetadata(context)
                        AccessibilityProbeManager.startRecording()
                    }
                    isRecording = AccessibilityProbeManager.isActive()
                },
                onClear = {
                    AccessibilityProbeManager.clear()
                    events.clear()
                    summary = AccessibilityProbeManager.getSummary()
                },
                onExport = {
                    AccessibilityProbeManager.exportAsJson(context)
                },
                onEventClick = { selectedEvent = it },
                onBack = onBack,
                onEnableService = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            )
        }
    }
}

@Composable
private fun ProbeMainView(
    isRecording: Boolean,
    summary: ObservatorySummary,
    events: List<AccessibilityProbeEvent>,
    onToggleRecording: () -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit,
    onEventClick: (AccessibilityProbeEvent) -> Unit,
    onBack: () -> Unit,
    onEnableService: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Summary Card
        TerminalSection(title = "OBSERVATORY SUMMARY") {
            summary.metadata?.let { meta ->
                TerminalText(text = "device: ${meta.deviceModel}", color = TerminalDimGreen, fontSize = 12.sp)
                TerminalText(text = "display: ${meta.screenResolution} (${meta.displayDensity}x)", color = TerminalDimGreen, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryStat("EVENTS", summary.totalEvents.toString())
                SummaryStat("TEXT", summary.nodesWithText.toString())
                SummaryStat("DESC", summary.nodesWithContentDescription.toString())
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Controls
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TerminalButton(
                text = if (isRecording) "STOP RECORDING" else "START RECORDING",
                onClick = onToggleRecording,
                selected = isRecording,
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TerminalButton(text = "CLEAR LOG", onClick = onClear, modifier = Modifier.weight(1f))
            TerminalButton(text = "EXPORT DATA", onClick = onExport, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Timeline
        TerminalHeader("TIMELINE")
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .background(Color.Black.copy(alpha = 0.3f))
                .padding(4.dp)
        ) {
            items(events.asReversed()) { event ->
                ProbeEventRow(event, onClick = { onEventClick(event) })
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TerminalButton(text = "ENABLE SERVICE", onClick = onEnableService, modifier = Modifier.weight(1f))
            TerminalButton(text = "BACK", onClick = onBack, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Column {
        TerminalText(text = label, color = TerminalDimGreen, fontSize = 10.sp)
        TerminalText(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProbeEventRow(
    event: AccessibilityProbeEvent,
    onClick: () -> Unit
) {
    val relativeSeconds = event.relativeTimestamp / 1000.0
    val timeStr = String.format(Locale.ROOT, "+%.3fs", relativeSeconds)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            TerminalText(
                text = "#${String.format(Locale.ROOT, "%05d", event.sequenceNumber)}",
                color = TerminalPurple,
                fontSize = 12.sp,
                modifier = Modifier.width(60.dp)
            )
            TerminalText(
                text = timeStr,
                color = TerminalDimGreen,
                fontSize = 12.sp,
                modifier = Modifier.width(64.dp)
            )
            TerminalText(text = event.eventType.substringAfter("TYPE_"), fontSize = 12.sp, modifier = Modifier.weight(1f))
        }
        if (event.text.isNotEmpty() || !event.contentDescription.isNullOrBlank()) {
            val preview = (event.text + listOfNotNull(event.contentDescription)).joinToString(" | ").take(40)
            TerminalText(
                text = "  > $preview",
                color = TerminalDimGreen,
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 124.dp)
            )
        }
    }
}

@Composable
private fun EventInspectionView(
    event: AccessibilityProbeEvent,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TerminalHeader("EVENT INSPECTION")
        TerminalText(text = "SEQUENCE: #${event.sequenceNumber}", color = TerminalPurple)
        TerminalText(text = "TYPE: ${event.eventType}")
        TerminalText(text = "PACKAGE: ${event.packageName}", color = TerminalDimGreen)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TerminalHeader("NODE TREE")
        Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            event.nodeTree?.let { NodeTreeDump(it) } ?: TerminalText(text = "[ NO TREE CAPTURED ]", color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(16.dp))
        TerminalButton(text = "BACK TO TIMELINE", onClick = onBack, selected = true)
    }
}

@Composable
private fun NodeTreeDump(node: AccessibilityProbeNode, depth: Int = 0) {
    Column(modifier = Modifier.padding(start = (depth * 8).dp)) {
        val label = node.className?.substringAfterLast(".") ?: "Node"
        val text = node.text?.let { " \"$it\"" } ?: ""
        val desc = node.contentDescription?.let { " desc:($it)" } ?: ""
        val id = node.viewId?.let { " id:($it)" } ?: ""
        
        val color = if (text.isNotEmpty() || desc.isNotEmpty()) TerminalGreen else TerminalDimGreen
        
        TerminalText(
            text = "${if (depth > 0) "|-" else ""}$label$text$desc$id",
            fontSize = 12.sp,
            color = color
        )
        
        node.children.forEach { child ->
            NodeTreeDump(child, depth + 1)
        }
    }
}
