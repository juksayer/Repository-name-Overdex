package com.example.overdex.ui.screens

import com.example.overdex.ui.components.rememberHandheldFocusManager
import com.example.overdex.ui.components.HandheldFocusSync
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.example.overdex.ui.theme.TerminalDimGreen
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.ui.theme.TerminalBlack
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.ui.theme.TerminalPurple

enum class ResearcherFocus {
    PROBE,
    OBSERVATORY,
    MATCH_SIGHT,
    MATCH_CALIBRATION,
    CLOSE
}

@Composable
fun SignalObservatoryScreen(
    onLaunchProbe: () -> Unit = {},
    onLaunchObservatory: () -> Unit = {},
    onLaunchMatchSight: () -> Unit = {},
    onLaunchMatchCalibration: () -> Unit = {},
    onBack: () -> Unit = {},
    onUp: (() -> Unit) -> Unit = {},
    onDown: (() -> Unit) -> Unit = {},
    onLeft: (() -> Unit) -> Unit = {},
    onRight: (() -> Unit) -> Unit = {},
    onA: (() -> Unit) -> Unit = {},
    onB: (() -> Unit) -> Unit = {},
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = TerminalBlack,
        contentColor = TerminalGreen
    ) {
        ResearcherModeOverlay(
            onLaunchProbe = onLaunchProbe,
            onLaunchObservatory = onLaunchObservatory,
            onLaunchMatchSight = onLaunchMatchSight,
            onLaunchMatchCalibration = onLaunchMatchCalibration,
            onClose = onBack,
            onUp = onUp,
            onDown = onDown,
            onLeft = onLeft,
            onRight = onRight,
            onA = onA,
            onB = onB
        )
    }
}

@Composable
fun ResearcherModeOverlay(
    onLaunchProbe: () -> Unit = {},
    onLaunchObservatory: () -> Unit = {},
    onLaunchMatchSight: () -> Unit = {},
    onLaunchMatchCalibration: () -> Unit = {},
    onClose: () -> Unit,
    onUp: (() -> Unit) -> Unit = {},
    onDown: (() -> Unit) -> Unit = {},
    onLeft: (() -> Unit) -> Unit = {},
    onRight: (() -> Unit) -> Unit = {},
    onA: (() -> Unit) -> Unit = {},
    onB: (() -> Unit) -> Unit = {},
) {
    val focusManager = rememberHandheldFocusManager(ResearcherFocus.PROBE)
    
    val visibleItems = remember {
        listOf(
            ResearcherFocus.PROBE,
            ResearcherFocus.OBSERVATORY,
            ResearcherFocus.MATCH_SIGHT,
            ResearcherFocus.MATCH_CALIBRATION,
            ResearcherFocus.CLOSE
        )
    }

    LaunchedEffect(visibleItems) {
        focusManager.updateItems(visibleItems)
    }

    val bringIntoViewRequesters = remember {
        visibleItems.associateWith { BringIntoViewRequester() }
    }

    HandheldFocusSync(
        selectedIndex = visibleItems.indexOf(focusManager.currentItem),
        items = visibleItems,
        requesters = bringIntoViewRequesters
    )

    SideEffect {
        onUp { focusManager.moveUp() }
        onDown { focusManager.moveDown() }
        onA {
            when (focusManager.currentItem) {
                ResearcherFocus.PROBE -> onLaunchProbe()
                ResearcherFocus.OBSERVATORY -> onLaunchObservatory()
                ResearcherFocus.MATCH_SIGHT -> onLaunchMatchSight()
                ResearcherFocus.MATCH_CALIBRATION -> onLaunchMatchCalibration()
                ResearcherFocus.CLOSE -> onClose()
                else -> {}
            }
        }
        onB { onClose() }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = TerminalBlack.copy(alpha = 0.95f),
        contentColor = TerminalGreen
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SIGNAL OBSERVATORY", fontWeight = FontWeight.Bold, color = TerminalPurple, fontSize = 22.sp)
                //val closeSelected = focusManager.currentItem == ResearcherFocus.CLOSE
                //Text(
                   //text = if (closeSelected) "[ CLOSE ]" else "  CLOSE  ",
                   // color = if (closeSelected) TerminalGreen else TerminalDimGreen,
                   // modifier = Modifier
                   //     .padding(4.dp)
                   //     .bringIntoViewRequester(bringIntoViewRequesters[ResearcherFocus.CLOSE]!!)

            }

            Spacer(modifier = Modifier.height(26.dp))
           Text("LAUNCH", color = TerminalPurple, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (focusManager.currentItem == ResearcherFocus.PROBE) TerminalGreen.copy(alpha = 0.1f) else Color.Transparent)
                    .border(1.dp, if (focusManager.currentItem == ResearcherFocus.PROBE) TerminalGreen else Color.Transparent)
                    .bringIntoViewRequester(bringIntoViewRequesters[ResearcherFocus.PROBE]!!)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Accessibility Probe", color = if (focusManager.currentItem == ResearcherFocus.PROBE) TerminalGreen else TerminalDimGreen)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (focusManager.currentItem == ResearcherFocus.OBSERVATORY) TerminalGreen.copy(alpha = 0.1f) else Color.Transparent)
                    .border(1.dp, if (focusManager.currentItem == ResearcherFocus.OBSERVATORY) TerminalGreen else Color.Transparent)
                    .bringIntoViewRequester(bringIntoViewRequesters[ResearcherFocus.OBSERVATORY]!!)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Timeline Viewer", color = if (focusManager.currentItem == ResearcherFocus.OBSERVATORY) TerminalGreen else TerminalDimGreen)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (focusManager.currentItem == ResearcherFocus.MATCH_SIGHT) TerminalGreen.copy(alpha = 0.1f) else Color.Transparent)
                    .border(1.dp, if (focusManager.currentItem == ResearcherFocus.MATCH_SIGHT) TerminalGreen else Color.Transparent)
                    .bringIntoViewRequester(bringIntoViewRequesters[ResearcherFocus.MATCH_SIGHT]!!)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Match Sight", color = if (focusManager.currentItem == ResearcherFocus.MATCH_SIGHT) TerminalGreen else TerminalDimGreen)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (focusManager.currentItem == ResearcherFocus.MATCH_CALIBRATION) TerminalGreen.copy(alpha = 0.1f) else Color.Transparent)
                    .border(1.dp, if (focusManager.currentItem == ResearcherFocus.MATCH_CALIBRATION) TerminalGreen else Color.Transparent)
                    .bringIntoViewRequester(bringIntoViewRequesters[ResearcherFocus.MATCH_CALIBRATION]!!)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Match Calibration", color = if (focusManager.currentItem == ResearcherFocus.MATCH_CALIBRATION) TerminalGreen else TerminalDimGreen)
            }
        }
    }
}
