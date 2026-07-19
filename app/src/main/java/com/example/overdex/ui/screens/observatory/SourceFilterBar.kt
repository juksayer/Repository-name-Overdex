package com.example.overdex.ui.screens.observatory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.battle.debug.observatory.EvidenceSourceType
import com.example.overdex.ui.components.TerminalText
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalPurple

@Composable
fun SourceFilterBar(
    activeFilters: Set<EvidenceSourceType>,
    onToggleFilter: (EvidenceSourceType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EvidenceSourceType.entries.forEach { type ->
            val isActive = activeFilters.contains(type)
            Box(
                modifier = Modifier
                    .background(if (isActive) TerminalPurple.copy(alpha = 0.3f) else Color.Transparent)
                    .clickable { onToggleFilter(type) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                TerminalText(
                    text = type.name.take(3),
                    color = if (isActive) TerminalPurple else TerminalDimGreen,
                    fontSize = 10.sp,
                    fontWeight = if (isActive) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                )
            }
        }
    }
}
