package com.example.overdex.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.model.TrainerIdentity
import kotlinx.coroutines.delay

data class MenuOption(
    val label: String,
    val onActivate: () -> Unit,
    val isEnabled: Boolean = true
)

@Composable
fun MainMenuScreen(
    hasBootedInSession: Boolean,
    onBootComplete: () -> Unit,
    selectedIndex: Int = 0,
    options: List<MenuOption> = emptyList(),
    trainerIdentity: TrainerIdentity? = null
) {
    val scrollState = rememberScrollState()

    // Local state for the sequential lines
    var bootStep by remember(hasBootedInSession) { mutableIntStateOf(if (hasBootedInSession) 99 else 0) }

    val bootLines = remember(trainerIdentity) {
        listOf(
            "overdex boot sequence...",
            "version 1.0.8",
            "",
            "checking local database.............. [ok]",
            "loading pokemon...................... [1025]",
            "loading move database................ [335]",
            "loading type effectiveness........... [ok]",
            "overdex ready",
            "TRAINER: ${trainerIdentity?.displayName?.uppercase() ?: "TheRealestSquid"}",
            "ID: ${trainerIdentity?.trainerId?.toString()?.take(8) ?: "737032186666"}",
            "",
        )
    }

    LaunchedEffect(hasBootedInSession) {
        if (!hasBootedInSession) {
            // 1. Sequential Reveal
            for (i in 1..bootLines.size) {
                bootStep = i
                
                // Trigger onBootComplete immediately after the final line is appended
                if (i == bootLines.size) {
                    onBootComplete()
                }

                val baseDelay = if (i < 3) 400L else 100L
                delay(baseDelay)
            }
        } else {
            // If already booted, ensure menu is visible instantly
            bootStep = bootLines.size
        }
    }

    TerminalScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // SECTION 1: BOOT REPORT
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                bootLines.take(bootStep).forEach { line ->
                    TerminalText(
                        text = line,
                        color = TerminalDimGreen,
                        fontSize = 12.sp
                    )
                }

                if (bootStep >= bootLines.size) {
                    Spacer(modifier = Modifier.height(24.dp))
                    TerminalHeader(text = "system check")
                }
            }

            // SECTION 2: INTERACTIVE MENU (Appears as lines are appended)
            if (bootStep >= bootLines.size) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TerminalSection(title = "modules", spacing = 0) {
                        options.forEachIndexed { index, option ->
                            TerminalMenuOption(
                                label = option.label,
                                selected = selectedIndex == index
                            ) {
                                option.onActivate()
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
