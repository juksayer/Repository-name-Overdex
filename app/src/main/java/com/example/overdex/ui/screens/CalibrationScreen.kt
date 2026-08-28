package com.example.overdex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.ui.theme.TerminalPurple
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.model.AnchorRegion
import com.example.overdex.CalibrationManager
import com.example.overdex.ui.components.CalibrationRegion
import com.example.overdex.ui.components.CalibrationMode
import com.example.overdex.ui.components.rememberHandheldFocusManager
import com.example.overdex.ui.components.*

enum class CalibrationFocus {
    TARGET,
    MODE,
    X_AXIS,
    Y_AXIS,
    TEST,
    SAVE,
    LOAD
}

@Composable
fun CalibrationScreen(
    calibrationManager: CalibrationManager,
    onUp: (() -> Unit) -> Unit = {},
    onDown: (() -> Unit) -> Unit = {},
    onLeft: (() -> Unit) -> Unit = {},
    onRight: (() -> Unit) -> Unit = {},
    onA: (() -> Unit) -> Unit = {},
    onB: (() -> Unit) -> Unit = {}
) {
    val focusManager = rememberHandheldFocusManager(CalibrationFocus.TARGET)
    
    val visibleItems = remember {
        listOf(
            CalibrationFocus.TARGET,
            CalibrationFocus.MODE,
            CalibrationFocus.X_AXIS,
            CalibrationFocus.Y_AXIS,
            CalibrationFocus.TEST,
            CalibrationFocus.SAVE,
            CalibrationFocus.LOAD
        )
    }

    LaunchedEffect(visibleItems) {
        focusManager.updateItems(visibleItems)
    }

    var selectedRegion by remember { mutableStateOf(CalibrationRegion.ENEMY_NAME) }
    var selectedMode by remember { mutableStateOf(CalibrationMode.POSITION) }
    var calibration by remember { mutableStateOf(calibrationManager.load()) }
    var statusMessage by remember { mutableStateOf("Ready") }

    val regions = listOf(
        CalibrationRegion.ENEMY_NAME,
        CalibrationRegion.HP_BAR,
        CalibrationRegion.TEAM_ICONS,
        CalibrationRegion.MOVE_BANNER,
        CalibrationRegion.COUNTDOWN

    )
    var regionIndex by remember { mutableStateOf(0) }

    val activeRegion = when (selectedRegion) {
        CalibrationRegion.NONE -> calibration.enemyNameRegion
        CalibrationRegion.ENEMY_NAME -> calibration.enemyNameRegion
        CalibrationRegion.HP_BAR -> calibration.hpBarRegion
        CalibrationRegion.TEAM_ICONS -> calibration.teamIconsRegion
        CalibrationRegion.MOVE_BANNER -> calibration.moveBannerRegion
        CalibrationRegion.COUNTDOWN -> calibration.countdownRegion
    }

    fun updateActiveRegion(transform: (AnchorRegion) -> AnchorRegion) {
        val updated = transform(activeRegion)
        calibration = when (selectedRegion) {
            CalibrationRegion.NONE -> calibration.copy(enemyNameRegion = updated)
            CalibrationRegion.ENEMY_NAME -> calibration.copy(enemyNameRegion = updated)
            CalibrationRegion.HP_BAR -> calibration.copy(hpBarRegion = updated)
            CalibrationRegion.TEAM_ICONS -> calibration.copy(teamIconsRegion = updated)
            CalibrationRegion.MOVE_BANNER -> calibration.copy(moveBannerRegion = updated)
            CalibrationRegion.COUNTDOWN -> calibration.copy(countdownRegion = updated)
        }
    }

    fun moveUp() = updateActiveRegion { it.copy(y = it.y + 10f) }
    fun moveDown() = updateActiveRegion { it.copy(y = it.y - 10f) }
    fun moveLeft() = updateActiveRegion { it.copy(x = it.x - 10f) }
    fun moveRight() = updateActiveRegion { it.copy(x = it.x + 10f) }
    fun increaseWidth() = updateActiveRegion { it.copy(width = it.width + 10f) }
    fun decreaseWidth() = updateActiveRegion { it.copy(width = it.width - 10f) }
    fun increaseHeight() = updateActiveRegion { it.copy(height = it.height + 10f) }
    fun decreaseHeight() = updateActiveRegion { it.copy(height = it.height - 10f) }

    TerminalScreen {
        TerminalPathIndicator(path = "/OBSERVE")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            TerminalHeader("CALIBRATION MODULE")
            Spacer(modifier = Modifier.height(16.dp))

            // 0: Target Region
            TerminalMenuOption(
                label = "TARGET: ${selectedRegion.name}",
                selected = focusManager.currentItem == CalibrationFocus.TARGET,
                onClick = {}
            )

            // 1: Mode
            TerminalMenuOption(
                label = "MODE: ${if (selectedMode == CalibrationMode.POSITION) "POSITION" else "SIZE"}",
                selected = focusManager.currentItem == CalibrationFocus.MODE,
                onClick = {}
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 2: Horizontal Axis (X or Width)
            TerminalMenuOption(
                label = if (selectedMode == CalibrationMode.POSITION) "X-POS: ${activeRegion.x}" else "WIDTH: ${activeRegion.width}",
                selected = focusManager.currentItem == CalibrationFocus.X_AXIS,
                onClick = {}
            )

            // 3: Vertical Axis (Y or Height)
            TerminalMenuOption(
                label = if (selectedMode == CalibrationMode.POSITION) "Y-POS: ${activeRegion.y}" else "HEIGHT: ${activeRegion.height}",
                selected = focusManager.currentItem == CalibrationFocus.Y_AXIS,
                onClick = {}
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4: Test
            TerminalMenuOption(
                label = "TEST REGION",
                selected = focusManager.currentItem == CalibrationFocus.TEST,
                onClick = {}
            )

            // 5: Save
            TerminalMenuOption(
                label = "SAVE CONFIGURATION",
                selected = focusManager.currentItem == CalibrationFocus.SAVE,
                onClick = {}
            )

            // 6: Load
            TerminalMenuOption(
                label = "LOAD CONFIGURATION",
                selected = focusManager.currentItem == CalibrationFocus.LOAD,
                onClick = {}
            )

            Spacer(modifier = Modifier.weight(1f))

            TerminalText(
                text = "STATUS: $statusMessage",
                color = TerminalPurple,
                fontSize = 12.sp
            )
        }
    }

    // Input Handling
    SideEffect {
        onUp { focusManager.moveUp() }
        onDown { focusManager.moveDown() }
        onLeft {
            when (focusManager.currentItem) {
                CalibrationFocus.TARGET -> {
                    regionIndex = (regionIndex - 1 + regions.size) % regions.size
                    selectedRegion = regions[regionIndex]
                }
                CalibrationFocus.MODE -> selectedMode = if (selectedMode == CalibrationMode.POSITION) CalibrationMode.SIZE else CalibrationMode.POSITION
                CalibrationFocus.X_AXIS -> if (selectedMode == CalibrationMode.POSITION) moveLeft() else decreaseWidth()
                CalibrationFocus.Y_AXIS -> if (selectedMode == CalibrationMode.POSITION) moveDown() else decreaseHeight()
                else -> {}
            }
        }
        onRight {
            when (focusManager.currentItem) {
                CalibrationFocus.TARGET -> {
                    regionIndex = (regionIndex + 1) % regions.size
                    selectedRegion = regions[regionIndex]
                }
                CalibrationFocus.MODE -> selectedMode = if (selectedMode == CalibrationMode.POSITION) CalibrationMode.SIZE else CalibrationMode.POSITION
                CalibrationFocus.X_AXIS -> if (selectedMode == CalibrationMode.POSITION) moveRight() else increaseWidth()
                CalibrationFocus.Y_AXIS -> if (selectedMode == CalibrationMode.POSITION) moveUp() else increaseHeight()
                else -> {}
            }
        }
        onA {
            when (focusManager.currentItem) {
                CalibrationFocus.TEST -> {
                    val activeRegion = when (selectedRegion) {
                        CalibrationRegion.NONE -> calibration.enemyNameRegion
                        CalibrationRegion.ENEMY_NAME -> calibration.enemyNameRegion
                        CalibrationRegion.HP_BAR -> calibration.hpBarRegion
                        CalibrationRegion.TEAM_ICONS -> calibration.teamIconsRegion
                        CalibrationRegion.MOVE_BANNER -> calibration.moveBannerRegion
                        CalibrationRegion.COUNTDOWN -> calibration.countdownRegion
                    }

                }
                CalibrationFocus.SAVE -> {
                    calibrationManager.save(calibration)
                    statusMessage = "Saved"
                }
                CalibrationFocus.LOAD -> {
                    calibration = calibrationManager.load()
                    statusMessage = "Loaded"
                }
                else -> {}
            }
        }
    }
}
