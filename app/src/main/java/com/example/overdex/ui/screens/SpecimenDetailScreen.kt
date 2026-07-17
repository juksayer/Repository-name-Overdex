package com.example.overdex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.overdex.model.Pokemon
import com.example.overdex.ui.MyCollectionViewModel
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SpecimenDetailScreen(
    ownedId: String,
    pokedexViewModel: PokedexViewModel,
    collectionViewModel: MyCollectionViewModel,
    onEdit: (String) -> Unit,
    onBack: () -> Unit
) {
    val ownedPokemon by collectionViewModel.getOwnedPokemon(ownedId).collectAsState(initial = null)
    var species by remember { mutableStateOf<Pokemon?>(null) }
    
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(ownedPokemon) {
        ownedPokemon?.let {
            if (species == null) {
                species = pokedexViewModel.getPokemonById(it.speciesId)
            }
        }
    }
    
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
        onStart = { onEdit(ownedId) },
        onB = onBack,
        viewModel = pokedexViewModel,
        showBattleOverlay = false
    ) { _ ->
        val owned = ownedPokemon
        if (owned == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TerminalText(text = "SPECIMEN NOT FOUND")
            }
            return@PokedexFrame
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // Header: Species Name
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = (species?.name ?: "UNKNOWN").uppercase(),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = if (owned.isShiny) TerminalPurple else TerminalGreen,
                    )
                    
                    // Reduced prominence Specimen ID
                    Text(
                        text = "ID: ${owned.id.takeLast(8).uppercase()}",
                        fontSize = 9.sp,
                        color = TerminalDimGreen.copy(alpha = 0.5f)
                    )
                }

                TerminalText(text = "START: EDIT", color = TerminalDimGreen, fontSize = 9.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sprite and Primary Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val spriteUrl = pokedexViewModel.spriteProvider.getSpriteUrl(
                    id = owned.speciesId,
                    isShiny = owned.isShiny,
                    isShadow = owned.isShadow,
                    isPurified = owned.isPurified
                )
                AsyncImage(
                    model = spriteUrl,
                    contentDescription = species?.name,
                    modifier = Modifier
                        .size(160.dp) // Increased size
                        .background(TerminalGreen.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                        .border(1.dp, TerminalDimGreen.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    val cpText = if (owned.cp != null && owned.cp != 0) "CP ${owned.cp}" else "CP ---"
                    Text(
                        text = cpText,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = TerminalGreen
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (owned.isShiny) {
                        StatusBadge(text = "SHINY", color = TerminalPurple)
                    }
                    if (owned.isShadow) {
                        StatusBadge(text = "SHADOW", color = Color(0xFFBC13FE))
                    }
                    if (owned.isPurified) {
                        StatusBadge(text = "PURIFIED", color = Color(0xFF00E5FF))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = TerminalDimGreen.copy(alpha = 0.2f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            // Moves Section
            BinderSectionTitle("MOVES")
            
            MoveDisplay(label = "FAST", moveName = owned.fastMove)
            MoveDisplay(label = "CHARGED 1", moveName = owned.chargedMove1)
            MoveDisplay(label = "CHARGED 2", moveName = owned.chargedMove2)

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = TerminalDimGreen.copy(alpha = 0.2f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            // Reserved Sections (Placeholders)
            BinderSectionTitle("BATTLE HISTORY")
            PlaceholderContent()

            BinderSectionTitle("TIMELINE")
            PlaceholderContent()

            BinderSectionTitle("TEAMS")
            PlaceholderContent()

            BinderSectionTitle("LEAGUE ELIGIBILITY")
            PlaceholderContent()

            BinderSectionTitle("NOTES")
            PlaceholderContent()

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun StatusBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier
            .padding(vertical = 2.dp)
            .border(1.dp, color, RoundedCornerShape(4.dp))
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun BinderSectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = TerminalPurple,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun MoveDisplay(label: String, moveName: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TerminalDimGreen,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(70.dp)
        )
        Text(
            text = moveName?.uppercase() ?: "---",
            color = TerminalGreen,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PlaceholderContent() {
    Text(
        text = "NO DATA AVAILABLE",
        color = TerminalDimGreen.copy(alpha = 0.5f),
        fontSize = 12.sp,
        modifier = Modifier.padding(bottom = 16.dp)
    )
}
