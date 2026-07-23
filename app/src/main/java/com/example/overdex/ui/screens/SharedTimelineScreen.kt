package com.example.overdex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.model.PartnerIdentity
import com.example.overdex.model.SharedEvent
import com.example.overdex.model.SharedEventType
import com.example.overdex.model.MilestoneType
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SharedTimelineScreen(
    partnerIdentity: PartnerIdentity?,
    events: List<SharedEvent>,
    onBack: () -> Unit
) {
    val groupedEvents = remember(events) {
        events.groupBy { 
            it.timestamp.atZone(ZoneId.systemDefault()).toLocalDate() 
        }
    }

    ODXFiShell(
        onB = onBack
    ) { _ ->
        TerminalScreen {
            Column(modifier = Modifier.fillMaxSize()) {
                TerminalPathIndicator(path = "/trainer/timeline")
                
                Spacer(modifier = Modifier.height(16.dp))

                if (events.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TerminalText(text = "NO SHARED MOMENTS YET", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        groupedEvents.forEach { (date, dayEvents) ->
                            item {
                                TimelineDateHeader(date)
                            }
                            
                            items(dayEvents) { event ->
                                TimelineEventRow(event)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                TerminalButton(text = "back", onClick = onBack)
            }
        }
    }
}

@Composable
fun TimelineDateHeader(date: LocalDate) {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    
    val label = when (date) {
        today -> "TODAY"
        yesterday -> "YESTERDAY"
        else -> date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ROOT))
    }

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        TerminalText(text = label, color = TerminalDimGreen, fontSize = 12.sp)
        HorizontalDivider(
            color = TerminalDimGreen.copy(alpha = 0.3f),
            thickness = 1.dp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun TimelineEventRow(event: SharedEvent) {
    if (event.type == SharedEventType.MILESTONE && event.milestone != null) {
        MilestoneEventRow(event)
    } else {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            val description = when (event.type) {
                SharedEventType.LINKED -> event.payload
                SharedEventType.DISPLAY_NAME_CHANGED -> event.payload
                SharedEventType.MILESTONE -> event.payload
                SharedEventType.NOTE -> event.payload
            }
            
            TerminalText(text = description, fontSize = 15.sp, color = Color.White)
        }
    }
}

@Composable
fun MilestoneEventRow(event: SharedEvent) {
    val milestone = event.milestone!!
    val icon = when (milestone.type) {
        MilestoneType.FIRST_SHINY -> "✨"
        MilestoneType.POKEDEX_COMPLETE -> "🏆"
        else -> "⭐"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(TerminalGreen.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
            .border(0.5.dp, TerminalGreen.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TerminalText(text = "$icon MILESTONE", color = TerminalGreen, fontSize = 11.sp)
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        TerminalText(text = event.payload, fontSize = 16.sp, color = Color.White)
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.clickable { /* Future: Congratulate */ },
            verticalAlignment = Alignment.CenterVertically
        ) {
            TerminalText(text = " CONGRATULATE", color = TerminalPurple, fontSize = 12.sp)
        }
    }
}
