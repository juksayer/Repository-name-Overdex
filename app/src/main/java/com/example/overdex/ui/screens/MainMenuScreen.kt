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
import kotlinx.coroutines.delay

enum class MainMenuPhase {
    BOOT,
    READY
}

@Composable
fun MainMenuScreen(
    hasBootedInSession: Boolean,
    onBootComplete: () -> Unit,
    trainerIdentity: TrainerIdentity? = null,
    partnerIdentity: com.example.overdex.model.PartnerIdentity? = null,
    isResearcherUnlocked: Boolean = false,
    observationState: com.example.overdex.model.observation.ObservationSessionState = com.example.overdex.model.observation.ObservationSessionState.IDLE,
    onPhaseChange: (MainMenuPhase) -> Unit = {},
) {
    val scrollState = rememberScrollState()

    // Local state for the sequential lines
    var phase by remember(hasBootedInSession) {
        mutableStateOf(if (hasBootedInSession) MainMenuPhase.READY else MainMenuPhase.BOOT)
    }
    
    // Notify parent of initial phase
    SideEffect { onPhaseChange(phase) }

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

            // 2. READY Phase
            phase = MainMenuPhase.READY
            onPhaseChange(phase)
            onBootComplete()
        } else {
            // Already booted
            bootStep = bootLines.size
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
            InstrumentStatusView(
                trainerIdentity = trainerIdentity,
                partnerIdentity = partnerIdentity,
                isResearcherUnlocked = isResearcherUnlocked,
                observationState = observationState
            )
        }
    }
}
