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
import com.example.overdex.EnemyPokemonMemory
import com.example.overdex.model.Effectiveness
import com.example.overdex.model.Move
import com.example.overdex.model.Pokemon
import com.example.overdex.model.MatchupAnalysis
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.ui.theme.TerminalPurple

/**
 * A highly compressed move display focusing on type relationship and threat level.
 */
@Composable
fun LiveMovePanel(
    activePokemon: EnemyPokemonMemory?,
    viewModel: PokedexViewModel?,
    matchup: MatchupAnalysis? = null
) {
    var pokemonData by remember { mutableStateOf<Pokemon?>(null) }

    LaunchedEffect(activePokemon?.species) {
        if (viewModel != null && activePokemon != null) {
            pokemonData = viewModel.getPokemonByName(activePokemon.species)
        } else {
            pokemonData = null
        }
    }

    if (activePokemon == null || pokemonData == null) return

    Row(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Fast Move
        pokemonData!!.fastMoves.firstOrNull()?.let { move ->
            val effectiveness = matchup?.enemyFastMoveMatchup?.effectiveness ?: Effectiveness.NEUTRAL
            CompactMoveIcon(move, effectiveness)
        }
        
        // Vertical Divider
        Box(modifier = Modifier.width(0.5.dp).height(12.dp).background(TerminalDimGreen.copy(alpha = 0.3f)))

        // Charged Moves
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            pokemonData!!.chargedMoves.take(2).forEach { move ->
                val effectiveness = matchup?.enemyChargedMoveMatchups?.find { it.moveName == move.name }?.effectiveness 
                    ?: Effectiveness.NEUTRAL
                CompactMoveIcon(move, effectiveness)
            }
        }
    }
}

@Composable
fun CompactMoveIcon(move: Move, effectiveness: Effectiveness) {
    Box(
        modifier = Modifier
            .background(
                color = move.type.color.copy(alpha = 0.15f),
                shape = RoundedCornerShape(2.dp)
            )
            .border(
                width = if (effectiveness == Effectiveness.SUPER_EFFECTIVE) 1.dp else 0.5.dp,
                color = if (effectiveness == Effectiveness.SUPER_EFFECTIVE) Color.Red else move.type.color.copy(alpha = 0.4f),
                shape = RoundedCornerShape(2.dp)
            )
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = move.name.take(4).uppercase(),
                color = if (effectiveness == Effectiveness.SUPER_EFFECTIVE) Color.White else TerminalGreen,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold
            )
            
            if (effectiveness == Effectiveness.SUPER_EFFECTIVE) {
                Spacer(modifier = Modifier.width(2.dp))
                Box(modifier = Modifier.size(2.dp).background(Color.Red, RoundedCornerShape(1.dp)))
            }
        }
    }
}
