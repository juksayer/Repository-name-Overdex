package com.example.overdex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.overdex.ui.theme.TerminalBlack
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.ui.theme.TerminalPurple

@Composable
fun EnemyTeamMemoryOverlay(enemyTeam: List<EnemyPokemonMemory>) {
    Column(
        modifier = Modifier
            .padding(8.dp)
            .width(120.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .border(1.dp, TerminalDimGreen.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "ENEMY TEAM",
            color = TerminalPurple,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        
        enemyTeam.forEach { pokemon ->
            EnemyPokemonRow(pokemon)
        }
    }
}

@Composable
fun EnemyPokemonRow(pokemon: EnemyPokemonMemory) {
    // Hardcoded sprite mapping for the prototype
    val spriteUrl = when (pokemon.species.lowercase()) {
        "swampert" -> "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/260.png"
        "talonflame" -> "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/663.png"
        "azumarill" -> "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/184.png"
        else -> "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/0.png"
    }

    val isFainted = !pokemon.alive
    val isActive = pokemon.isActive && !isFainted

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(
                when {
                    isActive -> TerminalGreen.copy(alpha = 0.15f)
                    isFainted -> Color.DarkGray.copy(alpha = 0.2f)
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (isActive) 1.5.dp else 0.dp,
                color = if (isActive) TerminalGreen else Color.Transparent,
                shape = RoundedCornerShape(2.dp)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Desaturate and reduce opacity if fainted
        val colorFilter = if (isFainted) {
            ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        } else {
            null
        }

        AsyncImage(
            model = spriteUrl,
            contentDescription = pokemon.species,
            modifier = Modifier
                .size(32.dp)
                .alpha(if (isFainted) 0.5f else 1.0f),
            contentScale = ContentScale.Fit,
            colorFilter = colorFilter
        )
        
        Spacer(modifier = Modifier.width(4.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pokemon.species.uppercase(),
                color = when {
                    isActive -> Color.White
                    isFainted -> TerminalDimGreen.copy(alpha = 0.6f)
                    else -> TerminalGreen
                },
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            
            // Energy Bar - Hidden if fainted
            if (!isFainted) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(TerminalBlack)
                        .border(0.5.dp, if (isActive) TerminalGreen else TerminalDimGreen, RoundedCornerShape(1.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((pokemon.estimatedEnergy.coerceIn(0, 100)) / 100f)
                            .fillMaxHeight()
                            .background(if (isActive) TerminalGreen else TerminalDimGreen)
                    )
                }
            } else {
                // Empty space to maintain vertical height consistency
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
