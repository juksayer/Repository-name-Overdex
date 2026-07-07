package com.example.overdex.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.model.Pokemon
import com.example.overdex.ui.MyCollectionViewModel
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.*

@Composable
fun OwnedPokemonDetailScreen(
    ownedId: String,
    pokedexViewModel: PokedexViewModel,
    collectionViewModel: MyCollectionViewModel,
    filterSettings: FilterSettings,
    onFilterSettingsChange: (FilterSettings) -> Unit,
    onEditClick: () -> Unit,
    onDeleteSuccess: () -> Unit, // Reserved for future use
    onBack: () -> Unit,
    isServiceRunning: Boolean = false,
) {
    val ownedPokemon by collectionViewModel.getOwnedPokemon(ownedId).collectAsState(initial = null)
    var species by remember { mutableStateOf<Pokemon?>(null) }
    
    LaunchedEffect(ownedPokemon?.speciesId) {
        ownedPokemon?.speciesId?.let { id ->
            species = pokedexViewModel.getPokemonById(id)
        }
    }

    PokedexFrame(
        onA = onEditClick,
        onB = onBack,
        filterSettings = filterSettings,
        onFilterSettingsChange = onFilterSettingsChange,
        isServiceRunning = isServiceRunning,
        viewModel = pokedexViewModel,
    ) { _ ->
        val owned = ownedPokemon
        if (owned == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TerminalText(text = "RECORD NOT FOUND")
            }
            return@PokedexFrame
        }

        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // Identity Header
            val hasNickname = !owned.displayName.isNullOrBlank()
            val speciesName = species?.name ?: "UNKNOWN"
            val identity = if (hasNickname) {
                owned.displayName
            } else {
                val prefix = if (owned.isShadow) "SHADOW " else if (owned.isPurified) "PURIFIED " else ""
                "$prefix$speciesName"
            }

            Text(
                text = identity.uppercase(),
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = if (owned.isShiny) TerminalPurple else TerminalGreen,
                lineHeight = 32.sp
            )
            
            if (hasNickname) {
                Text(
                    text = "($speciesName)",
                    fontSize = 18.sp,
                    color = TerminalDimGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Text(
                text = "OWNED ID: #${ownedId.takeLast(8).uppercase()}",
                fontSize = 12.sp,
                color = TerminalDimGreen,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Type Icons (Scaled for emphasis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                species?.types?.forEach { type ->
                    TypeBadge(
                        type = type,
                        style = TypeIconStyle.OVERDEX,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .scale(1.1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = TerminalDimGreen.copy(alpha = 0.2f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(24.dp))

            // Attributes
            OwnedAttribute(label = "COMBAT POWER", value = owned.cp?.toString() ?: "???")
            
            if (owned.isShadow) {
                OwnedAttribute(label = "CONDITION", value = "SHADOW", color = Color(0xFFBC13FE))
            } else if (owned.isPurified) {
                OwnedAttribute(label = "CONDITION", value = "PURIFIED", color = Color(0xFF00E5FF))
            }

            if (owned.isShiny) {
                OwnedAttribute(label = "VARIANT", value = "SHINY ✨", color = TerminalPurple)
            }

            // Fill space to ensure scrollability is felt and for future metadata
            Spacer(modifier = Modifier.height(64.dp))
            Spacer(modifier = Modifier.weight(1f))
            
            // Firmware Footer
            HorizontalDivider(color = TerminalDimGreen.copy(alpha = 0.2f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "A EDIT", 
                    color = TerminalGreen, 
                    fontSize = 14.sp, 
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "B BACK", 
                    color = TerminalDimGreen, 
                    fontSize = 14.sp, 
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun OwnedAttribute(label: String, value: String, color: Color = TerminalGreen) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = label, 
            color = TerminalDimGreen, 
            fontSize = 10.sp, 
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            text = value.uppercase(), 
            color = color, 
            fontSize = 24.sp, 
            fontWeight = FontWeight.Black
        )
    }
}
