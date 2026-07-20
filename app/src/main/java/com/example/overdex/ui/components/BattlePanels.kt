package com.example.overdex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.presentation.*
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalGreen

/**
 * Canonical panel for enemy species identification.
 */
@Composable
fun EnemyDetailPanel(opponent: OpponentTeamPresentation) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TerminalGreen, RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        TerminalText(
            text = "ENEMY DETAIL",
            fontSize = 10.sp,
            color = TerminalDimGreen
        )
        Text(
            text = opponent.activeSpecies?.uppercase() ?: "UNKNOWN",
            color = TerminalGreen,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Canonical panel for fast and charged move analysis.
 */
@Composable
fun MoveAnalysisPanel(moves: List<SemanticMove>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TerminalDimGreen, RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        TerminalText(text = "MOVE ANALYSIS", fontSize = 10.sp, color = TerminalDimGreen)
        Spacer(modifier = Modifier.height(8.dp))
        
        moves.filter { it.isFast }.forEach { move ->
            MoveRow(move, "FAST")
        }
        
        HorizontalDivider(color = TerminalDimGreen.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))
        
        moves.filter { !it.isFast }.forEach { move ->
            MoveRow(move, "CHARGED")
        }
    }
}

@Composable
private fun MoveRow(move: SemanticMove, label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            TerminalText(text = label, fontSize = 8.sp, color = TerminalDimGreen)
            Text(text = move.name, color = TerminalGreen, fontSize = 16.sp)
        }
        Text(
            text = move.type.name,
            color = TerminalGreen.copy(alpha = 0.7f),
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterVertically)
        )
    }
}

/**
 * Canonical panel for instrument status tracking.
 */
@Composable
fun StatusPanel(observation: ObservationPresentation) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalGreen.copy(alpha = 0.1f))
            .border(1.dp, TerminalGreen.copy(alpha = 0.5f))
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TerminalText(text = "INSTRUMENT STATUS:", fontSize = 12.sp, color = TerminalDimGreen)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = observation.focus.name.replace("_", " "),
                color = TerminalGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Canonical panel for tactical recommendations and guidance.
 */
@Composable
fun TacticalRecommendationPanel(tactical: TacticalPresentation) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (tactical.shieldRequired) Color.Red.copy(alpha = 0.2f) else Color.Transparent)
            .border(
                width = 2.dp, 
                color = if (tactical.shieldRequired) Color.Red else TerminalGreen, 
                shape = RoundedCornerShape(8.dp)
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TerminalText(
            text = "TACTICAL RECOMMENDATION",
            fontSize = 12.sp,
            color = if (tactical.shieldRequired) Color.Red else TerminalDimGreen
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (tactical.shieldRequired) "SHIELD NEXT CHARGED MOVE" else tactical.primaryGuidance.name.replace("_", " "),
            color = if (tactical.shieldRequired) Color.Red else TerminalGreen,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 22.sp
        )
    }
}
