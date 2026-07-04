package com.example.overdex.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.AnchorRegion
import com.example.overdex.CalibrationManager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.overdex.CalibrationRegion
import com.example.overdex.ui.components.CalibrationMode
import com.example.overdex.ui.components.DPad

@Composable
fun CalibrationScreen(
    calibrationManager: CalibrationManager
) {

    var selectedRegion by remember {
        mutableStateOf(CalibrationRegion.ENEMY_NAME)
    }

    var selectedMode by remember {
        mutableStateOf(CalibrationMode.POSITION)
    }

    var calibration by remember {
        mutableStateOf(calibrationManager.load())
    }

    var statusMessage by remember {
        mutableStateOf("Ready")
    }

    val activeRegion = when (selectedRegion) {
        CalibrationRegion.ENEMY_NAME -> calibration.enemyNameRegion
        CalibrationRegion.HP_BAR -> calibration.hpBarRegion
        CalibrationRegion.TEAM_ICONS -> calibration.teamIconsRegion
        CalibrationRegion.MOVE_BANNER -> calibration.moveBannerRegion
        CalibrationRegion.NONE -> calibration.enemyNameRegion
    }

    fun updateActiveRegion(transform: (AnchorRegion) -> AnchorRegion) {
        val updated = transform(activeRegion)
        calibration = when (selectedRegion) {
            CalibrationRegion.ENEMY_NAME -> calibration.copy(enemyNameRegion = updated)
            CalibrationRegion.HP_BAR -> calibration.copy(hpBarRegion = updated)
            CalibrationRegion.TEAM_ICONS -> calibration.copy(teamIconsRegion = updated)
            CalibrationRegion.MOVE_BANNER -> calibration.copy(moveBannerRegion = updated)
            else -> calibration.copy(enemyNameRegion = updated)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text("Calibration")

        Spacer(modifier = Modifier.height(16.dp))

        Text("Target: $selectedRegion")
        Text("Mode: ${if (selectedMode == CalibrationMode.POSITION) "Position" else "Size"}", fontSize = 14.sp)
        Text("Status: $statusMessage")

        Spacer(modifier = Modifier.height(16.dp))

        Text("X: ${activeRegion.x}")
        Text("Y: ${activeRegion.y}")
        Text("Width: ${activeRegion.width}")
        Text("Height: ${activeRegion.height}")

        Spacer(modifier = Modifier.height(16.dp))


        DPad(
            onUp = {
                if (selectedMode == CalibrationMode.POSITION) moveUp() else increaseHeight()
            },
            onDown = {
                if (selectedMode == CalibrationMode.POSITION) moveDown() else decreaseHeight()
            },
            onLeft = {
                if (selectedMode == CalibrationMode.POSITION) moveLeft() else decreaseWidth()
            },
            onRight = {
                if (selectedMode == CalibrationMode.POSITION) moveRight() else increaseWidth()
            }
        )

        Button(
            onClick = {
                selectedMode = if (selectedMode == CalibrationMode.POSITION) {
                    CalibrationMode.SIZE
                } else {
                    CalibrationMode.POSITION
                }
            }
        ) {
            Text("SELECT")
        }


        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { selectedRegion = CalibrationRegion.ENEMY_NAME }
        ) {
            Text("Enemy Name")
        }

        Button(
            onClick = { selectedRegion = CalibrationRegion.HP_BAR }
        ) {
            Text("HP Bar")
        }

        Button(
            onClick = { selectedRegion = CalibrationRegion.TEAM_ICONS }
        ) {
            Text("Team Icons")
        }

        Button(
            onClick = { selectedRegion = CalibrationRegion.MOVE_BANNER }
        ) {
            Text("Move Banner")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {

                calibration = when (selectedRegion) {

                    CalibrationRegion.ENEMY_NAME ->
                        calibration.copy(
                            enemyNameRegion = AnchorRegion(
                                x = 100f,
                                y = 200f,
                                width = 300f,
                                height = 50f
                            )
                        )

                    CalibrationRegion.HP_BAR ->
                        calibration.copy(
                            hpBarRegion = AnchorRegion(
                                x = 400f,
                                y = 100f,
                                width = 500f,
                                height = 25f
                            )
                        )

                    CalibrationRegion.TEAM_ICONS ->
                        calibration.copy(
                            teamIconsRegion = AnchorRegion(
                                x = 50f,
                                y = 500f,
                                width = 200f,
                                height = 100f
                            )
                        )

                    CalibrationRegion.MOVE_BANNER ->
                        calibration.copy(
                            moveBannerRegion = AnchorRegion(
                                x = 999f,
                                y = 999f,
                                width = 999f,
                                height = 999f
                            )
                        )

                    CalibrationRegion.NONE ->
                        calibration
                }
            }
        ) {
            Text("TEST REGION")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                calibrationManager.save(calibration)
                statusMessage = "Saved"
            }
        ) {
            Text("SAVE")
        }

        Button(
            onClick = {
                calibration = calibrationManager.load()
                statusMessage = "Loaded"
            }
        ) {
            Text("LOAD")
        }
    }
}
