package com.example.overdex.ui.screens

import com.example.overdex.ui.components.rememberHandheldFocusManager
import com.example.overdex.ui.components.HandheldFocusSync
import com.example.overdex.ui.components.FilterSettings
import com.example.overdex.ui.components.SettingToggle
import com.example.overdex.ui.components.SettingSlider
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

enum class SignalObservatoryFocus {
    PROBE,
    OBSERVATORY,
    MATCH_SIGHT,
    MATCH_CALIBRATION,
    FILTERS_ENABLED,
    SCANLINES,
    CURVATURE,
    NOISE,
    CLOSE
}

enum class ResearcherFocus {
    PROBE,
    OBSERVATORY,
    MATCH_SIGHT,
    MATCH_CALIBRATION,
    CLOSE
}

@Composable
fun SignalObservatoryScreen(
    filterSettings: FilterSettings,
    onFilterSettingsChange: (FilterSettings) -> Unit,
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
    val focusManager = rememberHandheldFocusManager(SignalObservatoryFocus.PROBE)

    val visibleItems = remember {
        listOf(
            SignalObservatoryFocus.PROBE,
            SignalObservatoryFocus.OBSERVATORY,
            SignalObservatoryFocus.MATCH_SIGHT,
            SignalObservatoryFocus.MATCH_CALIBRATION,
            SignalObservatoryFocus.FILTERS_ENABLED,
            SignalObservatoryFocus.SCANLINES,
            SignalObservatoryFocus.CURVATURE,
            SignalObservatoryFocus.NOISE,
            SignalObservatoryFocus.CLOSE
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
        onLeft {
            when (focusManager.currentItem) {
                SignalObservatoryFocus.SCANLINES -> onFilterSettingsChange(
                    filterSettings.copy(scanlineIntensity = (filterSettings.scanlineIntensity - 0.05f).coerceIn(0f, 1f))
                )
                SignalObservatoryFocus.CURVATURE -> onFilterSettingsChange(
                    filterSettings.copy(crtCurvature = (filterSettings.crtCurvature - 0.05f).coerceIn(0f, 0.5f))
                )
                SignalObservatoryFocus.NOISE -> onFilterSettingsChange(
                    filterSettings.copy(noiseIntensity = (filterSettings.noiseIntensity - 0.05f).coerceIn(0f, 0.5f))
                )
                else -> {}
            }
        }
        onRight {
            when (focusManager.currentItem) {
                SignalObservatoryFocus.SCANLINES -> onFilterSettingsChange(
                    filterSettings.copy(scanlineIntensity = (filterSettings.scanlineIntensity + 0.05f).coerceIn(0f, 1f))
                )
                SignalObservatoryFocus.CURVATURE -> onFilterSettingsChange(
                    filterSettings.copy(crtCurvature = (filterSettings.crtCurvature + 0.05f).coerceIn(0f, 0.5f))
                )
                SignalObservatoryFocus.NOISE -> onFilterSettingsChange(
                    filterSettings.copy(noiseIntensity = (filterSettings.noiseIntensity + 0.05f).coerceIn(0f, 0.5f))
                )
                else -> {}
            }
        }
        onA {
            when (focusManager.currentItem) {
                SignalObservatoryFocus.PROBE -> onLaunchProbe()
                SignalObservatoryFocus.OBSERVATORY -> onLaunchObservatory()
                SignalObservatoryFocus.MATCH_SIGHT -> onLaunchMatchSight()
                SignalObservatoryFocus.MATCH_CALIBRATION -> onLaunchMatchCalibration()
                SignalObservatoryFocus.FILTERS_ENABLED -> onFilterSettingsChange(
                    filterSettings.copy(isEnabled = !filterSettings.isEnabled)
                )
                SignalObservatoryFocus.CLOSE -> onBack()
                else -> {}
            }
        }
        onB { onBack() }
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
            Text("SIGNAL OBSERVATORY", fontWeight = FontWeight.Bold, color = TerminalPurple, fontSize = 22.sp)

            Spacer(modifier = Modifier.height(26.dp))
            Text("LAUNCH", color = TerminalPurple, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            LauncherBox(
                label = "Accessibility Probe",
                selected = focusManager.currentItem == SignalObservatoryFocus.PROBE,
                requester = bringIntoViewRequesters[SignalObservatoryFocus.PROBE]!!
            )
            Spacer(modifier = Modifier.height(8.dp))

            LauncherBox(
                label = "Timeline Viewer",
                selected = focusManager.currentItem == SignalObservatoryFocus.OBSERVATORY,
                requester = bringIntoViewRequesters[SignalObservatoryFocus.OBSERVATORY]!!
            )
            Spacer(modifier = Modifier.height(8.dp))

            LauncherBox(
                label = "Match Sight",
                selected = focusManager.currentItem == SignalObservatoryFocus.MATCH_SIGHT,
                requester = bringIntoViewRequesters[SignalObservatoryFocus.MATCH_SIGHT]!!
            )
            Spacer(modifier = Modifier.height(8.dp))

            LauncherBox(
                label = "Match Calibration",
                selected = focusManager.currentItem == SignalObservatoryFocus.MATCH_CALIBRATION,
                requester = bringIntoViewRequesters[SignalObservatoryFocus.MATCH_CALIBRATION]!!
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text("DISPLAY", color = TerminalPurple, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.bringIntoViewRequester(bringIntoViewRequesters[SignalObservatoryFocus.FILTERS_ENABLED]!!)) {
                SettingToggle(
                    label = "FILTERS ENABLED",
                    value = filterSettings.isEnabled,
                    selected = focusManager.currentItem == SignalObservatoryFocus.FILTERS_ENABLED,
                    onValueChange = { onFilterSettingsChange(filterSettings.copy(isEnabled = it)) }
                )
            }

            Box(modifier = Modifier.bringIntoViewRequester(bringIntoViewRequesters[SignalObservatoryFocus.SCANLINES]!!)) {
                SettingSlider(
                    label = "SCANLINE INTENSITY",
                    value = filterSettings.scanlineIntensity,
                    min = 0f,
                    max = 1f,
                    selected = focusManager.currentItem == SignalObservatoryFocus.SCANLINES,
                    onValueChange = { onFilterSettingsChange(filterSettings.copy(scanlineIntensity = it)) }
                )
            }

            Box(modifier = Modifier.bringIntoViewRequester(bringIntoViewRequesters[SignalObservatoryFocus.CURVATURE]!!)) {
                SettingSlider(
                    label = "CRT CURVATURE",
                    value = filterSettings.crtCurvature,
                    min = 0f,
                    max = 0.5f,
                    selected = focusManager.currentItem == SignalObservatoryFocus.CURVATURE,
                    onValueChange = { onFilterSettingsChange(filterSettings.copy(crtCurvature = it)) }
                )
            }

            Box(modifier = Modifier.bringIntoViewRequester(bringIntoViewRequesters[SignalObservatoryFocus.NOISE]!!)) {
                SettingSlider(
                    label = "NOISE INTENSITY",
                    value = filterSettings.noiseIntensity,
                    min = 0f,
                    max = 0.5f,
                    selected = focusManager.currentItem == SignalObservatoryFocus.NOISE,
                    onValueChange = { onFilterSettingsChange(filterSettings.copy(noiseIntensity = it)) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val closeSelected = focusManager.currentItem == SignalObservatoryFocus.CLOSE
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (closeSelected) TerminalGreen.copy(alpha = 0.1f) else Color.Transparent)
                    .border(1.dp, if (closeSelected) TerminalGreen else Color.Transparent)
                    .bringIntoViewRequester(bringIntoViewRequesters[SignalObservatoryFocus.CLOSE]!!)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (closeSelected) "[ CLOSE ]" else "  CLOSE  ",
                    color = if (closeSelected) TerminalGreen else TerminalDimGreen
                )
            }
        }
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
            Text("SIGNAL OBSERVATORY", fontWeight = FontWeight.Bold, color = TerminalPurple, fontSize = 22.sp)

            Spacer(modifier = Modifier.height(26.dp))
            Text("LAUNCH", color = TerminalPurple, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            LauncherBox(
                label = "Accessibility Probe",
                selected = focusManager.currentItem == ResearcherFocus.PROBE,
                requester = bringIntoViewRequesters[ResearcherFocus.PROBE]!!
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            LauncherBox(
                label = "Timeline Viewer",
                selected = focusManager.currentItem == ResearcherFocus.OBSERVATORY,
                requester = bringIntoViewRequesters[ResearcherFocus.OBSERVATORY]!!
            )
            Spacer(modifier = Modifier.height(8.dp))

            LauncherBox(
                label = "Match Sight",
                selected = focusManager.currentItem == ResearcherFocus.MATCH_SIGHT,
                requester = bringIntoViewRequesters[ResearcherFocus.MATCH_SIGHT]!!
            )
            Spacer(modifier = Modifier.height(8.dp))

            LauncherBox(
                label = "Match Calibration",
                selected = focusManager.currentItem == ResearcherFocus.MATCH_CALIBRATION,
                requester = bringIntoViewRequesters[ResearcherFocus.MATCH_CALIBRATION]!!
            )
        }
    }
}

@Composable
private fun LauncherBox(
    label: String,
    selected: Boolean,
    requester: BringIntoViewRequester
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) TerminalGreen.copy(alpha = 0.1f) else Color.Transparent)
            .border(1.dp, if (selected) TerminalGreen else Color.Transparent)
            .bringIntoViewRequester(requester)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) TerminalGreen else TerminalDimGreen)
    }
}
