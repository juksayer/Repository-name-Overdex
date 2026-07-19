package com.example.overdex.ui.components

import androidx.compose.foundation.background
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
import com.example.overdex.model.PokemonType
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.presentation.*

/**
 * A highly compressed move display focusing on type relationship and threat level.
 * Refactored to consume semantic [PresentationState] models.
 */
@Composable
fun LiveMovePanel(
    opponent: OpponentTeamPresentation,
    tactical: TacticalPresentation
) {
    if (opponent.activeSpecies == null) return

    Row(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Fast Move
        val fastMove = opponent.knownMoves.find { it.isFast }
        if (fastMove != null) {
            MoveIndicator(fastMove)
        }

        // Charged Moves
        val chargedMoves = opponent.knownMoves.filter { !it.isFast }
        chargedMoves.forEach { move ->
            MoveIndicator(move)
        }
        
        if (opponent.knownMoves.isEmpty()) {
            Text(
                text = "AWAITING MOVES",
                color = TerminalDimGreen,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun MoveIndicator(move: SemanticMove) {
    val color = when (move.effectiveness) {
        MoveEffectiveness.SUPER_EFFECTIVE -> Color.Red
        MoveEffectiveness.NEUTRAL -> Color.White
        MoveEffectiveness.NOT_VERY_EFFECTIVE -> TerminalGreen
        MoveEffectiveness.IMMUNE -> TerminalDimGreen
        MoveEffectiveness.UNKNOWN -> Color.Gray
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        // Simple dot for type
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(move.type.color, RoundedCornerShape(1.dp))
        )
        
        Spacer(modifier = Modifier.width(4.dp))
        
        Text(
            text = move.name.uppercase(),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}
