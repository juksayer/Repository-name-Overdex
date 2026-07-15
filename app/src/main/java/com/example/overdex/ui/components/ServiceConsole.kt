package com.example.overdex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.model.observation.*
import com.example.overdex.ui.screens.ServiceObservation
import com.example.overdex.ui.screens.ServicePanelState
import com.example.overdex.ui.theme.*

@Composable
fun ServiceConsole(
    panelState: ServicePanelState,
    modifier: Modifier = Modifier,
    onManualSelect: () -> Unit = {}
) {
    android.util.Log.d("PIPELINE_INSTRUMENTATION", "ServiceConsole Rendered | Observations: ${panelState.observations.size}")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBlack)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TerminalHeader(text = "service console", color = TerminalPurple)
            
            val confidence = (panelState.assessment.confidence * 100).toInt()
            TerminalText(
                text = "CONFIDENCE: $confidence%",
                color = if (confidence > 75) TerminalGreen else if (confidence > 30) Color.Yellow else Color.Red,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(panelState.observations) { obs ->
                ServiceObservationRow(obs)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action Area
        val assessment = panelState.assessment
        val actionText = when (assessment.recommendedAction) {
            RegistrationAction.REGISTER -> "A: REGISTER SPECIMEN"
            RegistrationAction.SELECT_SPECIES -> "A: SELECT SPECIES MANUALLY"
            RegistrationAction.CAPTURE_SECOND_SCREEN -> "A: CAPTURE MOVES"
            RegistrationAction.VERIFY_CP -> "A: VERIFY CP"
            RegistrationAction.NONE -> "AWAITING DATA"
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(TerminalPurple.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                .border(1.dp, TerminalPurple, RoundedCornerShape(4.dp))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            TerminalText(
                text = actionText,
                color = TerminalPurple,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        
        if (assessment.conflictingObservations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            TerminalText(
                text = "CONFLICT: ${assessment.conflictingObservations.first()}",
                color = Color.Red,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun ServiceObservationRow(obs: ServiceObservation<*>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            TerminalText(text = obs.label.uppercase(), color = TerminalDimGreen, fontSize = 9.sp)
            TerminalText(
                text = obs.value?.toString() ?: "MISSING",
                color = when (obs.status) {
                    ObservationStatus.CONFIRMED -> TerminalGreen
                    ObservationStatus.RECOGNIZED -> Color.White
                    ObservationStatus.OBSERVED -> Color.Yellow
                    ObservationStatus.CONFLICT -> Color.Red
                    ObservationStatus.MISSING -> Color.Gray
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        TerminalText(
            text = obs.status.name,
            color = TerminalDimGreen.copy(alpha = 0.5f),
            fontSize = 9.sp
        )
    }
}
