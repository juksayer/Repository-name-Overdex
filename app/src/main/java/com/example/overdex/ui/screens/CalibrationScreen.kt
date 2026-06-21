package com.example.overdex.ui.screens

import androidx.compose.runtime.*
import com.example.overdex.CalibrationMode
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CalibrationScreen() {

    var calibrationMode by remember {
        mutableStateOf(CalibrationMode.NONE)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text("Calibration")

        Spacer(modifier = Modifier.height(16.dp))

        Text("Current Mode: $calibrationMode")

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                calibrationMode = CalibrationMode.ENEMY_NAME
            }
        ) {
            Text("Enemy Name")
        }

        Button(
            onClick = {
                calibrationMode = CalibrationMode.HP_BAR
            }
        ) {
            Text("HP Bar")
        }

        Button(
            onClick = {
                calibrationMode = CalibrationMode.TEAM_ICONS
            }
        ) {
            Text("Team Icons")
        }

        Button(
            onClick = {
                calibrationMode = CalibrationMode.MOVE_BANNER
            }
        ) {
            Text("Move Banner")
        }
    }
}