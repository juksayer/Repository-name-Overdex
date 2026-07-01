package com.example.overdex.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.TerminalDimGreen
import kotlinx.coroutines.delay

@Composable
fun MainMenuScreen(
    isServiceRunning: Boolean,
    onModuleSelect: (String) -> Unit,
    onShutdown: () -> Unit,
) {
    var bootStep by remember { mutableIntStateOf(0) }
    val bootLines = listOf(
        "overdex boot sequence...",
        "version 1.0.8",
        "",
        "initializing confidence engine...",
        "checking local database.............. [ok]",
        "loading pokemon...................... [1025]",
        "loading move database................ [894]",
        "loading type effectiveness........... [ok]",
        "loading user profile................. [ok]",
        "confidence engine.................... [ready]",
        "confidence level..................... [high]"
    )

    LaunchedEffect(Unit) {
        bootLines.indices.forEach { index ->
            bootStep = index + 1
            // Variable delays to feel "authentic"
            val baseDelay = if (index < 2) 400L else 150L
            delay(baseDelay)
        }
    }

    val isBootComplete = bootStep >= bootLines.size

    TerminalScreen {
        Column {
            bootLines.take(bootStep).forEach { line ->
                TerminalText(
                    text = line,
                    color = TerminalDimGreen,
                    fontSize = 12.sp
                )
            }
        }

        if (isBootComplete) {
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
