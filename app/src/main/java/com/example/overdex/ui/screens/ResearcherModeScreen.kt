package com.example.overdex.ui.screens

import com.example.overdex.ui.components.rememberHandheldFocusManager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.components.AttributeToggle
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
    SCANLINES,
    CURVATURE,
    BLOOM,
    PHOSPHOR,
    PROBE,
    OBSERVATORY,
    MATCH_SIGHT,
    MATCH_CALIBRATION,
    DEBUG_OVERLAY,
    OCR_BOXES,
    EXPERIMENTAL,
    CLOSE
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
    val focusManager = rememberHandheldFocusManager(ResearcherFocus.SCANLINES)
    
    val visibleItems = remember {
        listOf(
            ResearcherFocus.SCANLINES,
            ResearcherFocus.CURVATURE,
            ResearcherFocus.BLOOM,
            ResearcherFocus.PHOSPHOR,
            ResearcherFocus.PROBE,
            ResearcherFocus.OBSERVATORY,
            ResearcherFocus.MATCH_SIGHT,
            ResearcherFocus.MATCH_CALIBRATION,
            ResearcherFocus.DEBUG_OVERLAY,
            ResearcherFocus.OCR_BOXES,
            ResearcherFocus.EXPERIMENTAL,
            ResearcherFocus.CLOSE
        )
    }

    LaunchedEffect(visibleItems) {
        focusManager.updateItems(visibleItems)
    }

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
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("RESEARCHER MODE", fontWeight = FontWeight.Bold, color = TerminalPurple, fontSize = 20.sp)
                val closeSelected = focusManager.currentItem == ResearcherFocus.CLOSE
                Text(
                    text = if (closeSelected) "[ CLOSE ]" else "  CLOSE  ",
                    color = if (closeSelected) TerminalGreen else TerminalDimGreen,
                    modifier = Modifier.padding(4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("ADVANCED SUBSYSTEMS", color = TerminalPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            FocusablePlaceholderSetting("CRT Scanline Intensity", focusManager.currentItem == ResearcherFocus.SCANLINES)
            FocusablePlaceholderSetting("CRT Curvature", focusManager.currentItem == ResearcherFocus.CURVATURE)
            FocusablePlaceholderSetting("Screen Bloom", focusManager.currentItem == ResearcherFocus.BLOOM)
            FocusablePlaceholderSetting("Phosphor Color", focusManager.currentItem == ResearcherFocus.PHOSPHOR)
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("SIGNAL OBSERVATORY", color = TerminalPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (focusManager.currentItem == ResearcherFocus.PROBE) TerminalGreen.copy(alpha = 0.1f) else Color.Transparent)
                    .border(1.dp, if (focusManager.currentItem == ResearcherFocus.PROBE) TerminalGreen else Color.Transparent)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("LAUNCH ACCESSIBILITY PROBE", color = if (focusManager.currentItem == ResearcherFocus.PROBE) TerminalGreen else TerminalDimGreen)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (focusManager.currentItem == ResearcherFocus.OBSERVATORY) TerminalGreen.copy(alpha = 0.1f) else Color.Transparent)
                    .border(1.dp, if (focusManager.currentItem == ResearcherFocus.OBSERVATORY) TerminalGreen else Color.Transparent)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("LAUNCH TIMELINE VIEWER", color = if (focusManager.currentItem == ResearcherFocus.OBSERVATORY) TerminalGreen else TerminalDimGreen)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (focusManager.currentItem == ResearcherFocus.MATCH_SIGHT) TerminalGreen.copy(alpha = 0.1f) else Color.Transparent)
                    .border(1.dp, if (focusManager.currentItem == ResearcherFocus.MATCH_SIGHT) TerminalGreen else Color.Transparent)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("LAUNCH MATCH SIGHT", color = if (focusManager.currentItem == ResearcherFocus.MATCH_SIGHT) TerminalGreen else TerminalDimGreen)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (focusManager.currentItem == ResearcherFocus.MATCH_CALIBRATION) TerminalGreen.copy(alpha = 0.1f) else Color.Transparent)
                    .border(1.dp, if (focusManager.currentItem == ResearcherFocus.MATCH_CALIBRATION) TerminalGreen else Color.Transparent)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("LAUNCH MATCH CALIBRATION", color = if (focusManager.currentItem == ResearcherFocus.MATCH_CALIBRATION) TerminalGreen else TerminalDimGreen)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("DEBUG TOOLS", color = TerminalPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            FocusablePlaceholderToggle("Debug Overlay", focusManager.currentItem == ResearcherFocus.DEBUG_OVERLAY)
            FocusablePlaceholderToggle("OCR Bounding Boxes", focusManager.currentItem == ResearcherFocus.OCR_BOXES)
            FocusablePlaceholderToggle("Experimental Features", focusManager.currentItem == ResearcherFocus.EXPERIMENTAL)
        }
    }
}

@Composable
private fun FocusablePlaceholderSetting(label: String, selected: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(if (selected) TerminalGreen.copy(alpha = 0.1f) else Color.Transparent)
            .border(1.dp, if (selected) TerminalGreen else Color.Transparent)
            .padding(8.dp)
    ) {
        Text(label, fontSize = 14.sp, color = if (selected) TerminalGreen else TerminalDimGreen)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .height(4.dp)
                .background(TerminalBlack)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .fillMaxHeight()
                    .background(if (selected) TerminalGreen else TerminalDimGreen)
            )
        }
    }
}

@Composable
private fun FocusablePlaceholderToggle(label: String, selected: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(if (selected) TerminalGreen.copy(alpha = 0.1f) else Color.Transparent)
            .border(1.dp, if (selected) TerminalGreen else Color.Transparent)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = if (selected) TerminalGreen else TerminalDimGreen)
        Text(
            text = "[   ]",
            color = if (selected) TerminalGreen else TerminalDimGreen,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}

