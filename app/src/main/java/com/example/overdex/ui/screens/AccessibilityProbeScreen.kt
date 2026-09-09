package com.example.overdex.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.battle.debug.accessibility.*
import com.example.overdex.battle.debug.observatory.AccessibilityProbeNode
import com.example.overdex.model.observation.InstrumentDeploymentState
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

sealed class AccessibilityProbeFocus {
    object ToggleRecording : AccessibilityProbeFocus()
    object ClearLog : AccessibilityProbeFocus()
    object ExportData : AccessibilityProbeFocus()
    data class Event(val sequenceNumber: Long) : AccessibilityProbeFocus()
    object EnableService : AccessibilityProbeFocus()
    object Back : AccessibilityProbeFocus()
}

@Composable
fun AccessibilityProbeScreen(
    onBack: () -> Unit,
    onUp: (() -> Unit) -> Unit = {},
    onDown: (() -> Unit) -> Unit = {},
    onA: (() -> Unit) -> Unit = {},
    onB: (() -> Unit) -> Unit = {},
    deploymentState: InstrumentDeploymentState = InstrumentDeploymentState.IDLE,
    onUpdateInfo: (String, String, List<String>) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(AccessibilityProbeManager.isActive()) }
    val events = remember { mutableStateListOf<AccessibilityProbeEvent>() }
    var summary by remember { mutableStateOf(AccessibilityProbeManager.getSummary()) }
    var selectedEvent by remember { mutableStateOf<AccessibilityProbeEvent?>(null) }

    val focusManager = rememberHandheldFocusManager<AccessibilityProbeFocus>(AccessibilityProbeFocus.ToggleRecording)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val visibleEvents = remember(events.size) { events.asReversed() }

    val currentTitle = if (selectedEvent != null) "INSPECT" else "PROBE"
    val currentBreadcrumb = "/signal_observatory/accessibility_probe/"
    
    val currentLcdLines = remember(isRecording, summary, selectedEvent, focusManager.currentItem, deploymentState) {
        val pathSegments = currentBreadcrumb.removePrefix("/").split("/").filter { it.isNotEmpty() }
        buildList {
            if (pathSegments.isEmpty()) {
                add("/")
            } else {
                add("/${pathSegments[0]}/")
                for (i in 1 until pathSegments.size) {
                    add("${pathSegments[i]}/")
                }
            }
            add("SYS: $deploymentState")
            add("PRB: ${if (isRecording) "RECORDING" else "IDLE"}")
            add("EVT: ${summary.totalEvents}  TXT: ${summary.nodesWithText}")
            add("DSC: ${summary.nodesWithContentDescription}")
            if (selectedEvent != null) {
                add("SEL # : ${selectedEvent!!.sequenceNumber}")
            } else if (focusManager.currentItem is AccessibilityProbeFocus.Event) {
                val focus = focusManager.currentItem as AccessibilityProbeFocus.Event
                add("FOC # : ${focus.sequenceNumber}")
            } else {
                add("")
            }
            add("U/D: MOVE  A: ACT  B: BACK")
        }
    }

    LaunchedEffect(currentTitle, currentBreadcrumb, currentLcdLines) {
        onUpdateInfo(currentTitle, currentBreadcrumb, currentLcdLines)
    }

    val focusableItems = remember(isRecording, visibleEvents) {
        buildList {
            add(AccessibilityProbeFocus.ToggleRecording)
            add(AccessibilityProbeFocus.ClearLog)
            add(AccessibilityProbeFocus.ExportData)
            visibleEvents.forEach { add(AccessibilityProbeFocus.Event(it.sequenceNumber)) }
            add(AccessibilityProbeFocus.EnableService)
            add(AccessibilityProbeFocus.Back)
        }
    }

    LaunchedEffect(focusableItems) {
        focusManager.updateItems(focusableItems)
    }

    // Event detail scroll state
    val detailScrollState = rememberScrollState()

    // Recording and data refresh
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                val newEvents = AccessibilityProbeManager.getEvents().filterIsInstance<AccessibilityProbeEvent>()
                if (newEvents.size != events.size) {
                    events.clear()
                    events.addAll(newEvents)
                }
                summary = AccessibilityProbeManager.getSummary()
                delay(500)
            }
        }
    }

    // Stable handler registration
    val currentIsRecording by rememberUpdatedState(isRecording)
    val currentSelectedEvent by rememberUpdatedState(selectedEvent)
    val currentFocus by rememberUpdatedState(focusManager.currentItem)
    val currentVisibleEvents by rememberUpdatedState(visibleEvents)

    DisposableEffect(Unit) {
        onUp {
            if (currentSelectedEvent != null) {
                scope.launch { detailScrollState.scrollBy(-100f) }
            } else {
                focusManager.moveUp()
            }
        }
        onDown {
            if (currentSelectedEvent != null) {
                scope.launch { detailScrollState.scrollBy(100f) }
            } else {
                focusManager.moveDown()
            }
        }
        onA {
            if (currentSelectedEvent != null) {
                // Already inspecting
            } else {
                when (val focus = currentFocus) {
                    AccessibilityProbeFocus.ToggleRecording -> {
                        if (currentIsRecording) {
                            AccessibilityProbeManager.stopRecording()
                        } else {
                            AccessibilityProbeManager.captureMetadata(context)
                            AccessibilityProbeManager.startRecording()
                        }
                        isRecording = AccessibilityProbeManager.isActive()
                    }
                    AccessibilityProbeFocus.ClearLog -> {
                        AccessibilityProbeManager.clear()
                        events.clear()
                        summary = AccessibilityProbeManager.getSummary()
                    }
                    AccessibilityProbeFocus.ExportData -> {
                        AccessibilityProbeManager.exportAsJson(context)
                    }
                    is AccessibilityProbeFocus.Event -> {
                        selectedEvent = currentVisibleEvents.find { it.sequenceNumber == focus.sequenceNumber }
                        scope.launch { detailScrollState.scrollTo(0) }
                    }
                    AccessibilityProbeFocus.EnableService -> {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                    AccessibilityProbeFocus.Back -> onBack()
                    null -> {}
                }
            }
        }
        onB {
            if (currentSelectedEvent != null) {
                selectedEvent = null
            } else {
                onBack()
            }
        }
        onDispose {
            onUp {}
            onDown {}
            onA {}
            onB {}
        }
    }

    // Sync list scrolling with focus
    val timelineStartIndex = 3 // Index of first event in focusableItems
    HandheldListSync(
        listState = listState,
        selectedIndex = focusableItems.indexOf(focusManager.currentItem),
        listIndexMapping = { focusIndex ->
            if (focusIndex >= timelineStartIndex && focusIndex < timelineStartIndex + visibleEvents.size) {
                focusIndex - timelineStartIndex
            } else null
        },
        totalItems = visibleEvents.size
    )

    TerminalScreen {
        if (selectedEvent != null) {
            EventInspectionView(
                event = selectedEvent!!,
                scrollState = detailScrollState,
                onBack = { selectedEvent = null }
            )
        } else {
            ProbeMainView(
                isRecording = isRecording,
                events = visibleEvents,
                focusManager = focusManager,
                listState = listState,
                onToggleRecording = {
                    if (isRecording) AccessibilityProbeManager.stopRecording()
                    else {
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
                onExport = { AccessibilityProbeManager.exportAsJson(context) },
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
    events: List<AccessibilityProbeEvent>,
    focusManager: HandheldFocusManager<AccessibilityProbeFocus>,
    listState: LazyListState,
    onToggleRecording: () -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit,
    onEventClick: (AccessibilityProbeEvent) -> Unit,
    onBack: () -> Unit,
    onEnableService: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Controls
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TerminalButton(
                text = if (isRecording) "STOP RECORDING" else "START RECORDING",
                onClick = onToggleRecording,
                selected = focusManager.currentItem == AccessibilityProbeFocus.ToggleRecording,
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TerminalButton(
                text = "CLEAR LOG", 
                onClick = onClear, 
                selected = focusManager.currentItem == AccessibilityProbeFocus.ClearLog,
                modifier = Modifier.weight(1f)
            )
            TerminalButton(
                text = "EXPORT DATA", 
                onClick = onExport, 
                selected = focusManager.currentItem == AccessibilityProbeFocus.ExportData,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Timeline
        TerminalHeader("TIMELINE")
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .background(Color.Black.copy(alpha = 0.3f))
                .padding(2.dp)
        ) {
            itemsIndexed(events) { _, event ->
                val isSelected = focusManager.currentItem is AccessibilityProbeFocus.Event && 
                    (focusManager.currentItem as AccessibilityProbeFocus.Event).sequenceNumber == event.sequenceNumber
                ProbeEventRow(event, isSelected = isSelected, onClick = { onEventClick(event) })
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TerminalButton(
                text = "SERVICE", 
                onClick = onEnableService, 
                selected = focusManager.currentItem == AccessibilityProbeFocus.EnableService,
                modifier = Modifier.weight(1f)
            )
            TerminalButton(
                text = "BACK", 
                onClick = onBack, 
                selected = focusManager.currentItem == AccessibilityProbeFocus.Back,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ProbeEventRow(
    event: AccessibilityProbeEvent,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val relativeSeconds = event.relativeTimestamp / 1000.0
    val timeStr = String.format(Locale.ROOT, "%.3fs", relativeSeconds)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) TerminalGreen.copy(alpha = 0.15f) else Color.Transparent)
            .border(1.dp, if (isSelected) TerminalGreen else Color.Transparent, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TerminalText(
                text = "#${String.format(Locale.ROOT, "%04d", event.sequenceNumber % 10000)}",
                color = if (isSelected) TerminalGreen else TerminalPurple,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            TerminalText(
                text = timeStr,
                color = TerminalDimGreen,
                fontSize = 11.sp
            )
        }
        
        TerminalText(
            text = event.eventType.substringAfter("TYPE_"), 
            fontSize = 11.sp, 
            color = if (isSelected) Color.White else TerminalGreen,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )

        if (event.text.isNotEmpty() || !event.contentDescription.isNullOrBlank()) {
            val preview = (event.text + listOfNotNull(event.contentDescription)).joinToString(" | ").take(80)
            TerminalText(
                text = "> $preview",
                color = TerminalDimGreen,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun EventInspectionView(
    event: AccessibilityProbeEvent,
    scrollState: androidx.compose.foundation.ScrollState,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TerminalText(text = "SEQUENCE: #${event.sequenceNumber}", color = TerminalPurple)
        TerminalText(text = "TYPE: ${event.eventType}")
        TerminalText(text = "PACKAGE: ${event.packageName}", color = TerminalDimGreen)
        
        Spacer(modifier = Modifier.height(8.dp))
        
        TerminalHeader("NODE TREE")
        Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(scrollState)) {
            event.nodeTree?.let { NodeTreeDump(it) } ?: TerminalText(text = "[ NO TREE CAPTURED ]", color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(16.dp))
        TerminalButton(text = "BACK TO TIMELINE", onClick = onBack, selected = true)
    }
}

@Composable
private fun NodeTreeDump(node: AccessibilityProbeNode, depth: Int = 0) {
    val maxVisualDepth = 10
    val visualDepth = minOf(depth, maxVisualDepth)
    val indent = (visualDepth * 6).dp
    
    val label = node.className?.substringAfterLast(".") ?: "Node"
    val text = node.text?.let { " \"$it\"" } ?: ""
    val desc = node.contentDescription?.let { " desc:($it)" } ?: ""
    val id = node.viewId?.let { " id:($it)" } ?: ""
    
    val color = if (text.isNotEmpty() || desc.isNotEmpty()) TerminalGreen else TerminalDimGreen
    val depthLabel = if (depth > maxVisualDepth) "($depth) " else ""

    TerminalText(
        text = "${if (depth > 0) "|-" else ""}$depthLabel$label$text$desc$id",
        fontSize = 11.sp,
        color = color,
        modifier = Modifier.padding(start = indent)
    )
    
    node.children.forEach { child ->
        NodeTreeDump(child, depth + 1)
    }
}
