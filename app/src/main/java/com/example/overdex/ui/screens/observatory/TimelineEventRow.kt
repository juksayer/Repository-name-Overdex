package com.example.overdex.ui.screens.observatory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.battle.debug.observatory.EvidenceSourceType
import com.example.overdex.battle.debug.observatory.RecordedEvent
import com.example.overdex.ui.components.TerminalText
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.ui.theme.TerminalPurple
import java.util.*

@Composable
fun TimelineEventRow(
    event: RecordedEvent,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val timeStr = String.format(Locale.ROOT, "+%04d ms", event.relativeTimestamp)
    
    val (badgeText, badgeColor) = when (event.sourceType) {
        EvidenceSourceType.ACCESSIBILITY -> "[A]" to TerminalGreen
        EvidenceSourceType.VISION -> "[V]" to TerminalPurple
        EvidenceSourceType.SYSTEM -> "[S]" to Color.White
        EvidenceSourceType.TIMING -> "[T]" to Color.Yellow
        EvidenceSourceType.MANUAL -> "[M]" to Color.Cyan
        else -> "[?]" to Color.Gray
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) TerminalPurple.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TerminalText(
            text = String.format(Locale.ROOT, "%06d", event.sequenceNumber),
            color = TerminalPurple,
            fontSize = 10.sp,
            modifier = Modifier.width(54.dp)
        )
        
        TerminalText(
            text = timeStr,
            color = TerminalDimGreen,
            fontSize = 10.sp,
            modifier = Modifier.width(64.dp)
        )
        
        TerminalText(
            text = badgeText,
            color = badgeColor,
            fontSize = 10.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.width(32.dp)
        )
        
        TerminalText(
            text = event.payload::class.java.simpleName.removeSuffix("Payload"),
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
