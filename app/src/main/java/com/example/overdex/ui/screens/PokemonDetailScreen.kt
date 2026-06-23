package com.example.overdex.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.overdex.model.Move
import com.example.overdex.model.Pokemon
import com.example.overdex.ui.components.FilterSettings
import com.example.overdex.ui.components.PokedexFrame
import com.example.overdex.ui.components.TypeBadge
import com.example.overdex.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun PokemonDetailScreen(
    pokemon: Pokemon,
    filterSettings: FilterSettings,
    onFilterSettingsChange: (FilterSettings) -> Unit,
    onSelect: () -> Unit,
    onBackClick: () -> Unit,
    onPlayCry: (String) -> Unit,
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    PokedexFrame(
        onUp = {
            scope.launch {
                scrollState.animateScrollBy(-500f)
            }
        },
        onDown = {
            scope.launch {
                scrollState.animateScrollBy(500f)
            }
        },
        onB = onBackClick,
        onLeft = onBackClick,
        onA = { onPlayCry(pokemon.cryUrl) },
        filterSettings = filterSettings,
        onFilterSettingsChange = onFilterSettingsChange,
        onSelect = onSelect,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TerminalGreen
                    )
                }
                IconButton(onClick = { onPlayCry(pokemon.cryUrl) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Play Cry",
                        tint = TerminalGreen
                    )
                }
            }
            
            // Sprite
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = pokemon.spriteUrl,
                    contentDescription = pokemon.name,
                    modifier = Modifier.size(180.dp),
                    contentScale = ContentScale.Fit,
                )
            }

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(text = pokemon.formattedId, fontSize = 16.sp, color = TerminalDimGreen)
                    Text(
                        text = pokemon.name,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalGreen
                    )
                }
                Row {
                    pokemon.types.forEach { TypeBadge(it) }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Description
            Text(
                text = pokemon.description,
                fontSize = 14.sp,
                color = TerminalGreen,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))



            Spacer(modifier = Modifier.height(16.dp))

// Effectiveness
            SectionTitle("Weaknesses")
            EffectivenessRow(pokemon.getWeaknesses().filter { it.value > 1.0 })
            
            SectionTitle("Resistances")
            EffectivenessRow(pokemon.getWeaknesses().filter { it.value < 1.0 })
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Moves
            SectionTitle("Fast Moves")
            pokemon.fastMoves.forEach { MoveRow(it) }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SectionTitle("Charged Moves")
            pokemon.chargedMoves.forEach { MoveRow(it) }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = TerminalPurple,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
fun EffectivenessRow(multipliers: Map<com.example.overdex.model.PokemonType, Double>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        multipliers.forEach { (type, mult) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TypeBadge(type)
                Text(
                    text = "%.2fx".format(mult),
                    fontSize = 16.sp,
                    color = TerminalGreen
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
    ) {
        content()
    }
}

@Composable
fun MoveRow(move: Move) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, TerminalDimGreen, CardDefaults.shape),
        colors = CardDefaults.cardColors(
            containerColor = TerminalBlack,
            contentColor = TerminalGreen
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TypeBadge(move.type)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = move.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Text(
                    text = if (move.isFast) "FAST" else "CHARGE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalPurple,
                    modifier = Modifier
                        .border(1.dp, TerminalPurple, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("Damage", "${move.damage}")
                if (move.isFast) {
                    StatItem("Energy Gain", "+${move.energy}")
                    StatItem("Turns", "${move.turns ?: 0}")
                } else {
                    StatItem("Energy Cost", "-${move.energy}")
                    StatItem("DPE", String.format(Locale.ROOT, "%.2f", move.dpe ?: 0.0))
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 10.sp, color = TerminalDimGreen)
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TerminalGreen)
    }
}
