package com.example.overdex.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.TerminalDimGreen

@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    TerminalScreen {
        TerminalHeader("settings")
        TerminalText(text = "configuration_service: active", color = TerminalDimGreen)
        
        TerminalSection(title = "display filters") {
            TerminalMenuOption(label = "crt_flicker", status = "on") {}
            TerminalMenuOption(label = "scanlines", status = "on") {}
            TerminalMenuOption(label = "diagnostic_view", status = "on") {}
        }
        
        TerminalSection(title = "overlay parameters") {
            TerminalText(text = "hud_opacity: 100%")
            TerminalText(text = "-10%          +10%", color = TerminalDimGreen)
        }
        
        TerminalSection(title = "scan calibration [ fine-tune ]") {
            TerminalText(text = "faint_sensor_y_offset: 24px")
            TerminalText(text = "-4px          +4px", color = TerminalDimGreen)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            TerminalText(text = "enemy_scan_x_threshold: 60%")
            TerminalText(text = "-5%           +5%", color = TerminalDimGreen)
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        TerminalButton(text = "back", onClick = onBack)
    }
}
