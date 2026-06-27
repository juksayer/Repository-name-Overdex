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
import com.example.overdex.model.Move
import com.example.overdex.model.Pokemon
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.ui.theme.TerminalPurple

@Composable
fun LiveMovePanel(
    activePokemon: EnemyPokemonMemory?,
    viewModel: PokedexViewModel?
) {
    var pokemonData by remember { mutableStateOf<Pokemon?>(null) }

    LaunchedEffect(activePokemon?.species) {
        if (viewModel != null && activePokemon != null) {
            pokemonData = viewModel.getPokemonByName(activePokemon.species)
        } else {
            pokemonData = null
        }
    }

    Column(
        modifier = Modifier
            .padding(top = 4.dp)
            .width(120.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .border(1.dp, TerminalDimGreen.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (activePokemon == null) {
            Text(
                text = "NO ACTIVE",
                color = TerminalDimGreen,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Text(
                text = activePokemon.species.uppercase(),
                color = TerminalPurple,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )

            if (pokemonData == null) {
                Text(
                    text = "NO DATA",
                    color = TerminalDimGreen,
                    fontSize = 7.sp
                )
            } else {
                MoveSection("FAST", pokemonData!!.fastMoves)
                MoveSection("CHARGED", pokemonData!!.chargedMoves)
            }
        }
    }
}

@Composable
fun MoveSection(title: String, moves: List<Move>) {
    Column {
        Text(
            text = title,
            color = TerminalDimGreen,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
        )
        moves.forEach { move ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = move.name.uppercase(),
                    color = TerminalGreen,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                TypeBadgeTiny(move.type)
            }
        }
    }
}

@Composable
fun TypeBadgeTiny(type: com.example.overdex.model.PokemonType) {
    Box(
        modifier = Modifier
            .background(type.color.copy(alpha = 0.2f), RoundedCornerShape(1.dp))
            .border(0.5.dp, type.color.copy(alpha = 0.5f), RoundedCornerShape(1.dp))
            .padding(horizontal = 2.dp, vertical = 0.5.dp)
    ) {
        Text(
            text = type.name.take(1),
            color = type.color,
            fontSize = 6.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
