package com.example.overdex.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.ui.components.PokedexFrame
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.components.FilterSettings
import com.example.overdex.ui.PokedexViewModel

@Composable
fun ReadmeScreen(
    filterSettings: FilterSettings,
    onFilterSettingsChange: (FilterSettings) -> Unit,
    onSelect: () -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
    viewModel: PokedexViewModel? = null
) {
    var currentPage by remember { mutableIntStateOf(0) }
    val pages = readmePages

    PokedexFrame(
        onUp = {}, // Scrolling disabled per requirements
        onDown = {},
        onLeft = { if (currentPage > 0) currentPage-- },
        onRight = { if (currentPage < pages.size - 1) currentPage++ },
        onB = onBack,
        onA = {},
        filterSettings = filterSettings,
        onFilterSettingsChange = onFilterSettingsChange,
        onSelect = onSelect,
        onStart = onStart,
        viewModel = viewModel,
        showBattleOverlay = false
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = pages[currentPage].title,
                    color = TerminalGreen,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = pages[currentPage].content,
                    color = TerminalGreen,
                    fontSize = 16.sp
                )
            }
            
            // Current page number display (e.g. 1/3)
            Text(
                text = "${currentPage + 1}/${pages.size}",
                color = TerminalDimGreen,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}
