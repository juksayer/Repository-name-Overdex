package com.example.overdex.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.*
import com.example.overdex.model.TrainerIdentity
import com.example.overdex.model.navigation.*
import kotlinx.coroutines.delay

enum class MainMenuPhase {
    BOOT,
    MENU_BUILD,
    READY
}

@Composable
fun MainMenuScreen(
    hasBootedInSession: Boolean,
    onBootComplete: () -> Unit,
    visibleNodes: List<FlattenedNode> = emptyList(),
    selectedPath: String = "",
    trainerIdentity: TrainerIdentity? = null,
    onPhaseChange: (MainMenuPhase) -> Unit = {},
    onNodeSelected: (TreeNode) -> Unit = {}
) {
    val scrollState = rememberScrollState()

    // Local state for the sequential lines
    var phase by remember(hasBootedInSession) {
        mutableStateOf(if (hasBootedInSession) MainMenuPhase.READY else MainMenuPhase.BOOT)
    }
    
    // Notify parent of initial phase
    SideEffect { onPhaseChange(phase) }

    var bootStep by remember(hasBootedInSession) { mutableIntStateOf(if (hasBootedInSession) 99 else 0) }
    var menuRevealCount by remember(hasBootedInSession) { mutableIntStateOf(if (hasBootedInSession) visibleNodes.size else 0) }

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
        )
    }

    LaunchedEffect(hasBootedInSession) {
        if (!hasBootedInSession) {
            // 1. BOOT Phase
            phase = MainMenuPhase.BOOT
            onPhaseChange(phase)
            for (i in 1..bootLines.size) {
                bootStep = i
                val baseDelay = if (i < 3) 400L else 100L
                delay(baseDelay)
            }

            // Short pause on "OVERDEX READY"
            delay(200L)

            // 2. CLEAR Transition & MENU_BUILD Phase
            phase = MainMenuPhase.MENU_BUILD
            onPhaseChange(phase)
            
            // Sequential menu reveal
            for (i in 1..visibleNodes.size) {
                menuRevealCount = i
                val revealDelay = if (i == 1) 120L else 70L
                delay(revealDelay)
            }

            // Final intentional pause before READY
            delay(200L)
            phase = MainMenuPhase.READY
            onPhaseChange(phase)
            onBootComplete()
        } else {
            // Already booted
            bootStep = bootLines.size
            menuRevealCount = visibleNodes.size
            phase = MainMenuPhase.READY
            onPhaseChange(phase)
        }
    }

    TerminalScreen {
        if (phase == MainMenuPhase.BOOT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                bootLines.take(bootStep).forEach { line ->
                    TerminalText(
                        text = line,
                        color = TerminalDimGreen,
                        fontSize = 12.sp
                    )
                }
                
                // Show trainer info only during boot diagnostics
                if (bootStep >= bootLines.size) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TerminalText(
                        text = "TRAINER: ${trainerIdentity?.displayName?.uppercase() ?: "THEREALESTSQUID"}",
                        color = TerminalDimGreen,
                        fontSize = 12.sp
                    )
                    TerminalText(
                        text = "ID: ${trainerIdentity?.trainerId?.toString()?.take(8) ?: "737032186666"}",
                        color = TerminalDimGreen,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            // OPERATIONAL Workspace (MENU_BUILD or READY)
            DirectoryTree(
                visibleNodes = visibleNodes.take(menuRevealCount),
                selectedPath = if (phase == MainMenuPhase.READY) selectedPath else "",
                onNodeSelected = { node ->
                    if (phase == MainMenuPhase.READY) {
                        onNodeSelected(node)
                    }
                }
            )
        }
    }
}
