package com.example.overdex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.battle.observation.DroidballFact
import com.example.overdex.battle.observation.DroidballService
import com.example.overdex.model.observation.InstrumentDeploymentState
import com.example.overdex.ui.theme.TerminalGreen
import kotlinx.coroutines.flow.collect

/**
 * The floating field presentation of the Battle Observation Session.
 * 
 * This overlay mimics the Instrument LCD and provides real-time feedback
 * while the trainer is in the field (e.g., in Pokémon GO).
 */
@Composable
fun BattleOverlay() {
    // In a real implementation, this would observe the Coordinator's state.
    // For Git #197, we observe the service facts directly to prove the flow.
    var frameCount by remember { mutableLongStateOf(0) }
    var status by remember { mutableStateOf("DEPLOYING") }

    LaunchedEffect(Unit) {
        DroidballService.facts.collect { fact ->
            when (fact) {
                is DroidballFact.FrameCaptured -> {
                    frameCount++
                    status = "OBSERVING"
                }
                is DroidballFact.Started -> status = "READY"
                is DroidballFact.Stopped -> status = "RETURNING"
                else -> {}
            }
        }
    }

    Box(
        modifier = Modifier
            .size(120.dp, 80.dp)
            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF121510), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = status,
                color = TerminalGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "FRAMES: $frameCount",
                color = TerminalGreen.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )

            if (status == "OBSERVING") {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(4.dp)
                        .background(Color.Red, RoundedCornerShape(2.dp))
                )
            }
        }
    }
}
