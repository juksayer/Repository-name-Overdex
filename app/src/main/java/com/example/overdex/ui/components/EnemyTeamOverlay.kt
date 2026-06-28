package com.example.overdex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.overdex.EnemyPokemonMemory
import com.example.overdex.model.DecisionAnalysis
import com.example.overdex.model.RecommendedAction
import com.example.overdex.ui.theme.TerminalBlack
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.ui.theme.TerminalPurple

/**
 * A horizontal row of enemy Pokémon sprites, serving as a persistent memory of the opponent's team.
 */
@Composable
fun EnemyTeamMemoryOverlay(
    enemyTeam: List<EnemyPokemonMemory>,
    decision: DecisionAnalysis? = null
) {
    Row(
        modifier = Modifier
            .padding(8.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .border(0.5.dp, TerminalDimGreen.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Strategic Indicator at the start
        if (decision != null) {
            DecisionIcon(decision)
        }
        
        // Enemy Pokémon Blocks
        enemyTeam.forEach { pokemon ->
            EnemyPokemonBlock(pokemon)
        }
    }
}

@Composable
fun DecisionIcon(decision: DecisionAnalysis) {
    val icon = when (decision.recommendedAction) {
        RecommendedAction.SWITCH_NOW -> Icons.Default.Sync
        RecommendedAction.FARM_ENERGY -> Icons.Default.Bolt
        RecommendedAction.SHIELD_LIKELY_REQUIRED -> Icons.Default.Shield
        RecommendedAction.STAY_AND_FIGHT -> Icons.Default.Check
        else -> Icons.Default.Info
    }
    
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (decision.isEnemyThreatening) Color.Red else TerminalGreen,
        modifier = Modifier.size(16.dp)
    )
}

@Composable
fun EnemyPokemonBlock(pokemon: EnemyPokemonMemory) {
    // Hardcoded sprite mapping for the prototype
    val spriteUrl = when (pokemon.species.lowercase()) {
        "swampert" -> "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/260.png"
        "talonflame" -> "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/663.png"
        "azumarill" -> "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/184.png"
        else -> "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/0.png"
    }

    val isFainted = !pokemon.alive
    val isActive = pokemon.isActive && !isFainted

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                when {
                    isActive -> TerminalGreen.copy(alpha = 0.15f)
                    isFainted -> Color.DarkGray.copy(alpha = 0.3f)
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (isActive) 1.5.dp else 0.5.dp,
                color = if (isActive) TerminalGreen else TerminalDimGreen.copy(alpha = 0.3f),
                shape = RoundedCornerShape(4.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        val colorFilter = if (isFainted) {
            ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        } else {
            null
        }

        AsyncImage(
            model = spriteUrl,
            contentDescription = null,
            modifier = Modifier
                .size(28.dp)
                .alpha(if (isFainted) 0.3f else 1.0f),
            contentScale = ContentScale.Fit,
            colorFilter = colorFilter
        )
        
        // Quiet Energy Bar at bottom
        if (!isFainted) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 1.dp)
                    .width(28.dp)
                    .height(2.dp)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((pokemon.estimatedEnergy.coerceIn(0, 100)) / 100f)
                        .fillMaxHeight()
                        .background(if (isActive) Color.White else TerminalGreen)
                )
            }
        }
    }
}
