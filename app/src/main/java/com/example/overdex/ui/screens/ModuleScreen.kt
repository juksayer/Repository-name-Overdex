package com.example.overdex.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.TerminalDimGreen

enum class ModuleStatus(val label: String) {
    UNAVAILABLE("unavailable"),
    OFFLINE("offline"),
    LOCKED("locked"),
    EXPERIMENTAL("experimental"),
    RESEARCHER_ONLY("researcher_only")
}

@Composable
fun ModuleScreen(
    title: String,
    status: ModuleStatus,
    description: String,
    onBack: () -> Unit,
    viewModel: com.example.overdex.ui.PokedexViewModel? = null,
    filterSettings: FilterSettings = FilterSettings(),
    onFilterSettingsChange: (FilterSettings) -> Unit = {},
    onStart: () -> Unit = {},
    onSelect: () -> Unit = {}
) {
    ODXFiShell(
        onA = onBack,
        onB = onBack,
        onStart = onStart,
        onSelect = onSelect,
        viewModel = viewModel,
        filterSettings = filterSettings,
        onFilterSettingsChange = onFilterSettingsChange,
        showBattleOverlay = false
    ) {
        TerminalScreen {
            TerminalHeader(title)
            TerminalText(text = "module_status: ${status.label}", color = TerminalDimGreen)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            TerminalText(text = description)
            
            Spacer(modifier = Modifier.weight(1f))
            
            TerminalButton(text = "back", onClick = onBack, selected = true)
        }
    }
}
