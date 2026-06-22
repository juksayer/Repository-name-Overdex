package com.example.overdex.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.overdex.AnchorRegion
import com.example.overdex.CalibrationManager
import com.example.overdex.CalibrationMode
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun CalibrationScreen(
    calibrationManager: CalibrationManager
) {

    var calibrationMode by remember {
        mutableStateOf(CalibrationMode.ENEMY_NAME)
    }

    var calibration by remember {
        mutableStateOf(calibrationManager.load())
    }

    var statusMessage by remember {
        mutableStateOf("Ready")
    }

    val activeRegion = when (calibrationMode) {
        CalibrationMode.ENEMY_NAME -> calibration.enemyNameRegion
        CalibrationMode.HP_BAR -> calibration.hpBarRegion
        CalibrationMode.TEAM_ICONS -> calibration.teamIconsRegion
        CalibrationMode.MOVE_BANNER -> calibration.moveBannerRegion
        CalibrationMode.NONE -> calibration.enemyNameRegion
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text("Calibration")

        Spacer(modifier = Modifier.height(16.dp))

        Text("Current Mode: $calibrationMode")
        Text("Status: $statusMessage")

        Spacer(modifier = Modifier.height(16.dp))

        Text("X: ${activeRegion.x}")
        Text("Y: ${activeRegion.y}")
        Text("Width: ${activeRegion.width}")
        Text("Height: ${activeRegion.height}")

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                when (calibrationMode) {

                    CalibrationMode.ENEMY_NAME ->
                        calibration = calibration.copy(
                            enemyNameRegion = activeRegion.copy(
                                height = activeRegion.height + 10f
                            )
                        )

                    CalibrationMode.HP_BAR ->
                        calibration = calibration.copy(
                            hpBarRegion = activeRegion.copy(
                                height = activeRegion.height + 10f
                            )
                        )

                    else -> {}
                }
            }
        ) {
            Text("Height +")
        }

        Row {

            Button(
                onClick = {
                    when (calibrationMode) {

                        CalibrationMode.ENEMY_NAME ->
                            calibration = calibration.copy(
                                enemyNameRegion = activeRegion.copy(
                                    width = activeRegion.width - 10f
                                )
                            )

                        CalibrationMode.HP_BAR ->
                            calibration = calibration.copy(
                                hpBarRegion = activeRegion.copy(
                                    width = activeRegion.width - 10f
                                )
                            )

                        else -> {}
                    }
                }
            ) {
                Text("Width -")
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = {
                    when (calibrationMode) {

                        CalibrationMode.ENEMY_NAME ->
                            calibration = calibration.copy(
                                enemyNameRegion = activeRegion.copy(
                                    width = activeRegion.width + 10f
                                )
                            )

                        CalibrationMode.HP_BAR ->
                            calibration = calibration.copy(
                                hpBarRegion = activeRegion.copy(
                                    width = activeRegion.width + 10f
                                )
                            )

                        else -> {}
                    }
                }
            ) {
                Text("Width +")
            }
        }

        Button(
            onClick = {
                when (calibrationMode) {

                    CalibrationMode.ENEMY_NAME ->
                        calibration = calibration.copy(
                            enemyNameRegion = activeRegion.copy(
                                height = activeRegion.height - 10f
                            )
                        )

                    CalibrationMode.HP_BAR ->
                        calibration = calibration.copy(
                            hpBarRegion = activeRegion.copy(
                                height = activeRegion.height - 10f
                            )
                        )

                    else -> {}
                }
            }
        ) {
            Text("Height -")
        }
        Button(
            onClick = {
                when (calibrationMode) {

                    CalibrationMode.ENEMY_NAME ->
                        calibration = calibration.copy(
                            enemyNameRegion = activeRegion.copy(
                                y = activeRegion.y + 10f
                            )
                        )

                    CalibrationMode.HP_BAR ->
                        calibration = calibration.copy(
                            hpBarRegion = activeRegion.copy(
                                y = activeRegion.y + 10f
                            )
                        )

                    else -> {}
                }
            }
        ) {
            Text("↑")
        }

        Row {


            Button(
                onClick = {
                    when (calibrationMode) {

                        CalibrationMode.ENEMY_NAME ->
                            calibration = calibration.copy(
                                enemyNameRegion = activeRegion.copy(
                                    x = activeRegion.x - 10f
                                )
                            )

                        CalibrationMode.HP_BAR ->
                            calibration = calibration.copy(
                                hpBarRegion = activeRegion.copy(
                                    x = activeRegion.x - 10f
                                )
                            )

                        else -> {}
                    }
                }
            ) {
                Text("←")
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = {
                    when (calibrationMode) {

                        CalibrationMode.ENEMY_NAME ->
                            calibration = calibration.copy(
                                enemyNameRegion = activeRegion.copy(
                                    x = activeRegion.x + 10f
                                )
                            )

                        CalibrationMode.HP_BAR ->
                            calibration = calibration.copy(
                                hpBarRegion = activeRegion.copy(
                                    x = activeRegion.x + 10f
                                )
                            )

                        else -> {}
                    }
                }
            ) {
                Text("→")
            }
        }

        Button(
            onClick = {
                when (calibrationMode) {

                    CalibrationMode.ENEMY_NAME ->
                        calibration = calibration.copy(
                            enemyNameRegion = activeRegion.copy(
                                y = activeRegion.y - 10f
                            )
                        )

                    CalibrationMode.HP_BAR ->
                        calibration = calibration.copy(
                            hpBarRegion = activeRegion.copy(
                                y = activeRegion.y - 10f
                            )
                        )

                    else -> {}
                }
            }
        ) {
            Text("↓")
        }


        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { calibrationMode = CalibrationMode.ENEMY_NAME }
        ) {
            Text("Enemy Name")
        }

        Button(
            onClick = { calibrationMode = CalibrationMode.HP_BAR }
        ) {
            Text("HP Bar")
        }

        Button(
            onClick = { calibrationMode = CalibrationMode.TEAM_ICONS }
        ) {
            Text("Team Icons")
        }

        Button(
            onClick = { calibrationMode = CalibrationMode.MOVE_BANNER }
        ) {
            Text("Move Banner")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {

                calibration = when (calibrationMode) {

                    CalibrationMode.ENEMY_NAME ->
                        calibration.copy(
                            enemyNameRegion = AnchorRegion(
                                x = 100f,
                                y = 200f,
                                width = 300f,
                                height = 50f
                            )
                        )

                    CalibrationMode.HP_BAR ->
                        calibration.copy(
                            hpBarRegion = AnchorRegion(
                                x = 400f,
                                y = 100f,
                                width = 500f,
                                height = 25f
                            )
                        )

                    CalibrationMode.TEAM_ICONS ->
                        calibration.copy(
                            teamIconsRegion = AnchorRegion(
                                x = 50f,
                                y = 500f,
                                width = 200f,
                                height = 100f
                            )
                        )

                    CalibrationMode.MOVE_BANNER ->
                        calibration.copy(
                            moveBannerRegion = AnchorRegion(
                                x = 999f,
                                y = 999f,
                                width = 999f,
                                height = 999f
                            )
                        )

                    CalibrationMode.NONE ->
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