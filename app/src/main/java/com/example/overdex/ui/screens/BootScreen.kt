package com.example.overdex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.overdex.ui.theme.TerminalBlack
import com.example.overdex.ui.theme.TerminalGreen

@Composable
fun BootScreen(
    trainerName: String,
    trainerStatus: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalBlack),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "OVERDEX TERMINAL 1.0",
            color = TerminalGreen,
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Birthday Edition",
            color = TerminalGreen,
            style = MaterialTheme.typography.bodyMedium
        )

        Text("")

        Text(
            text = "Loading Trainer Profile...",
            color = TerminalGreen
        )

        Text("")

        Text(
            text = "Trainer:",
            color = TerminalGreen
        )

        Text(
            text = trainerName.uppercase(),
            color = TerminalGreen,
            style = MaterialTheme.typography.headlineSmall
        )

        Text("")

        Text(
            text = "Status:",
            color = TerminalGreen
        )

        Text(
            text = trainerStatus,
            color = TerminalGreen
        )

        Text("")

        Text(
            text = "████████████████████",
            color = TerminalGreen
        )

        Text("")

        Text(
            text = "Welcome back, $trainerName.",
            color = TerminalGreen,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}