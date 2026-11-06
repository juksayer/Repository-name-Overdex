package com.example.overdex.ui.screens

import androidx.compose.foundation.layout.*
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
    onNodeSelected: (FlattenedNode) -> Unit = {}
) {
    // Local state for the sequential lines
    var phase by remember(hasBootedInSession) {
        mutableStateOf(if (hasBootedInSession) MainMenuPhase.READY else MainMenuPhase.BOOT)
    }
    
    // Observe the phase here as well as inside the CRT content, so the shell
    // receives every composed phase and enables controls after the reveal.
    val presentedPhase = phase
    SideEffect { onPhaseChange(presentedPhase) }

    var bootStep by remember(hasBootedInSession) { mutableIntStateOf(if (hasBootedInSession) 99 else 0) }
    var menuRevealCount by remember(hasBootedInSession) {
        mutableIntStateOf(
            if (hasBootedInSession) visibleNodes.size else 0
        )
    }

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

    val currentVisibleNodes by rememberUpdatedState(visibleNodes)
    val currentOnBootComplete by rememberUpdatedState(onBootComplete)

    // Resizing the CRT or refreshing directory contents must not restart boot.
    LaunchedEffect(hasBootedInSession) {
        if (hasBootedInSession) return@LaunchedEffect
        for (i in 1..bootLines.size) {
            bootStep = i
            val baseDelay = if (i < 3) 650L else 500L
            delay(baseDelay)
        }

        // Together with the final line delay, leave the completed boot text
        // and trainer details visible for three seconds, then clear the CRT.
        delay(2_500L)
        phase = MainMenuPhase.MENU_BUILD
        while (true) {
            delay(200L)
            if (menuRevealCount >= currentVisibleNodes.size) break
            menuRevealCount++
        }
        phase = MainMenuPhase.READY
        currentOnBootComplete()
    }

    TerminalScreen {
        if (phase == MainMenuPhase.BOOT) {
            Column(modifier = Modifier.fillMaxSize()) {
                bootLines.take(bootStep).forEach { line ->
                    TerminalText(
                        text = line,
                        color = TerminalDimGreen,
                        fontSize = 12.sp
                    )
                }

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
            val ready = phase == MainMenuPhase.READY
            DirectoryTree(
                visibleNodes = if (ready) visibleNodes else visibleNodes.take(menuRevealCount),
                selectedPath = if (ready) selectedPath else "",
                onNodeSelected = { if (ready) onNodeSelected(it) },
                scrollEnabled = ready,
            )
        }
    }
}
