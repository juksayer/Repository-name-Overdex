package com.example.overdex.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CalibrationScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Calibration")

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {}) {
            Text("Enemy Name")
        }

        Button(onClick = {}) {
            Text("HP Bar")
        }

        Button(onClick = {}) {
            Text("Team Icons")
        }

        Button(onClick = {}) {
            Text("Move Banner")
        }
    }
}