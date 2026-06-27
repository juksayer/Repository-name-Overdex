package com.example.overdex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.overdex.ui.theme.TerminalBlack
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.ui.theme.TerminalPurple

data class EnemyPokemonMemoryPrototype(
    val name: String,
    val spriteUrl: String,
    val energy: Int,
    val isFainted: Boolean = false
)

@Composable
fun EnemyTeamMemoryOverlay() {
    val testTeam = listOf(
        EnemyPokemonMemoryPrototype(
            name = "Swampert",
            spriteUrl = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/260.png",
            energy = 45
        ),
        EnemyPokemonMemoryPrototype(
            name = "Talonflame",
            spriteUrl = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/663.png",
            energy = 20
        ),
        EnemyPokemonMemoryPrototype(
            name = "Azumarill",
            spriteUrl = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/184.png",
            energy = 80
        )
    )

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
        
        testTeam.forEach { pokemon ->
            EnemyPokemonRow(pokemon)
        }
    }
}

@Composable
fun EnemyPokemonRow(pokemon: EnemyPokemonMemoryPrototype) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(if (pokemon.isFainted) Color.DarkGray else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = pokemon.spriteUrl,
            contentDescription = pokemon.name,
            modifier = Modifier.size(32.dp),
            contentScale = ContentScale.Fit
        )
        
        Spacer(modifier = Modifier.width(4.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pokemon.name.uppercase(),
                color = if (pokemon.isFainted) Color.Gray else TerminalGreen,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            
            // Energy Bar Placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(TerminalBlack)
                    .border(0.5.dp, TerminalDimGreen, RoundedCornerShape(1.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(pokemon.energy / 100f)
                        .fillMaxHeight()
                        .background(TerminalGreen)
                )
            }
        }
    }
}
