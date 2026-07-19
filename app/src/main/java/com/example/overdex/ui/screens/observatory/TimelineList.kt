package com.example.overdex.ui.screens.observatory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.overdex.battle.debug.observatory.RecordedEvent
import com.example.overdex.ui.components.TerminalHeader

@Composable
fun TimelineList(
    events: List<RecordedEvent>,
    selectedEvent: RecordedEvent?,
    onEventSelected: (RecordedEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        TerminalHeader(text = "TIMELINE")
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.2f))
        ) {
            items(events) { event ->
                TimelineEventRow(
                    event = event,
                    isSelected = event == selectedEvent,
                    onClick = { onEventSelected(event) }
                )
            }
        }
    }
}
