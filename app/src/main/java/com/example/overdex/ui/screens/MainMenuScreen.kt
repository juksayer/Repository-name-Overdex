package com.example.overdex.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.TerminalDimGreen

@Composable
fun MainMenuScreen(
    isServiceRunning: Boolean,
    onModuleSelect: (String) -> Unit,
    onShutdown: () -> Unit
) {
    TerminalScreen {
        TerminalText(
            text = "overdex boot sequence...",
            color = TerminalDimGreen,
            fontSize = 12.sp
        )
        TerminalText(
            text = "version 1.0.8 - advice_ai active",
            color = TerminalDimGreen,
            fontSize = 12.sp
        )
        
        TerminalSection(title = "system check") {
            TerminalMenuOption(label = "overlay_permission", status = "ok") {}
            TerminalMenuOption(label = "battery_optimization", status = "ok") {}
            TerminalMenuOption(label = "pogo_data_status", status = "ok") {}
        }
        
        TerminalSection(title = "modules") {
            TerminalMenuOption(label = "overdex") { onModuleSelect("overdex") }
            
            if (!isServiceRunning) {
                TerminalMenuOption(label = "start.service") { onModuleSelect("start.service") }
            } else {
                TerminalMenuOption(label = "stop.service") { onModuleSelect("stop.service") }
            }

            TerminalMenuOption(label = "review.kit") { onModuleSelect("review.kit") }
            TerminalMenuOption(label = "battle.log") { onModuleSelect("battle.log") }
            TerminalMenuOption(label = "more.info") { onModuleSelect("more.info") }
            TerminalMenuOption(label = "readme.txt") { onModuleSelect("readme.txt") }
            TerminalMenuOption(label = "settings") { onModuleSelect("settings") }
        }
        
        TerminalSection(title = "session") {
            TerminalMenuOption(label = "shutdown.droidball") { onShutdown() }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        TerminalText(
            text = "built for SMNAPvP",
            color = TerminalDimGreen,
            fontSize = 12.sp
        )
        TerminalText(
            text = "(c) 2026 som_labs",
            color = TerminalDimGreen,
            fontSize = 12.sp
        )
    }
}
