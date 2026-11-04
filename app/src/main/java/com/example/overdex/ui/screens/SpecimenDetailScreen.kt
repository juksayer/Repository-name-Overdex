package com.example.overdex.ui.screens

import androidx.activity.compose.BackHandler
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
import com.example.overdex.model.OwnedPokemon
import com.example.overdex.model.Pokemon
import com.example.overdex.ui.MyCollectionViewModel
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SpecimenDetailScreen(
    ownedId: String,
    pokedexViewModel: PokedexViewModel,
    collectionViewModel: MyCollectionViewModel,
    onEdit: (String) -> Unit,
    onRecords: (String) -> Unit = {},
    onDeleteSuccess: () -> Unit = {},
    onBack: () -> Unit
) {
    val ownedPokemon by collectionViewModel.getOwnedPokemon(ownedId).collectAsState(initial = null)
    var species by remember { mutableStateOf<Pokemon?>(null) }
    LaunchedEffect(ownedPokemon) {
        ownedPokemon?.let { species = pokedexViewModel.getPokemonById(it.speciesId) }
    }
    val spriteUrl = ownedPokemon?.let { owned ->
        pokedexViewModel.spriteProvider.getSpriteUrl(id = owned.speciesId, isShiny = owned.isShiny,
            isShadow = owned.isShadow, isPurified = owned.isPurified)
    }
    OwnedPokemonCardDetailScreen(ownedId, ownedPokemon, species, spriteUrl,
        deleteOwnedCard = collectionViewModel::deleteOwnedPokemon,
        onEdit = onEdit, onRecords = onRecords, onDeleteSuccess = onDeleteSuccess,
        onBack = onBack, pokedexViewModel = pokedexViewModel)
}

/** Shared production detail and its LCD/physical controls, independently exercisable with isolated storage. */
@Composable
internal fun OwnedPokemonCardDetailScreen(
    ownedId: String,
    ownedPokemon: OwnedPokemon?,
    species: Pokemon?,
    spriteUrl: String?,
    deleteOwnedCard: suspend (String) -> Unit,
    onEdit: (String) -> Unit,
    onRecords: (String) -> Unit,
    onDeleteSuccess: () -> Unit,
    onBack: () -> Unit,
    pokedexViewModel: PokedexViewModel? = null,
) {
    
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember(ownedId) { mutableStateOf(false) }
    var confirmDeleteSelected by remember(ownedId) { mutableStateOf(false) }
    var deleting by remember(ownedId) { mutableStateOf(false) }
    var deleteError by remember(ownedId) { mutableStateOf<String?>(null) }

    fun openDeleteConfirmation() {
        if (ownedPokemon == null || deleting) return
        confirmDeleteSelected = false
        deleteError = null
        showDeleteConfirm = true
    }

    fun cancelDelete() {
        if (!deleting) showDeleteConfirm = false
    }

    fun deleteCard() {
        if (!showDeleteConfirm || deleting || ownedPokemon == null) return
        deleting = true
        deleteError = null
        scope.launch {
            try {
                deleteOwnedCard(ownedId)
                onDeleteSuccess()
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                deleteError = "Could not delete this card. Please retry."
            } finally {
                deleting = false
            }
        }
    }

    BackHandler(enabled = showDeleteConfirm) { cancelDelete() }

    ODXFiShell(
        onUp = {
            if (showDeleteConfirm) {
                if (!deleting) confirmDeleteSelected = false
            } else scope.launch {
                scrollState.animateScrollBy(-500f)
            }
        },
        onDown = {
            if (showDeleteConfirm) {
                if (!deleting) confirmDeleteSelected = true
            } else scope.launch {
                scrollState.animateScrollBy(500f)
            }
        },
        onStart = { if (!showDeleteConfirm && !deleting) onEdit(ownedId) },
        onSelect = { if (!showDeleteConfirm) openDeleteConfirmation() },
        onA = {
            if (showDeleteConfirm) {
                if (confirmDeleteSelected) deleteCard() else cancelDelete()
            } else if (ownedPokemon != null) onRecords(ownedId)
        },
        onB = { if (showDeleteConfirm) cancelDelete() else onBack() },
        viewModel = pokedexViewModel,
        showBattleOverlay = false,
        lcdContent = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (showDeleteConfirm) {
                    TerminalButton(text = "KEEP CARD", selected = !confirmDeleteSelected,
                        onClick = if (deleting) null else ::cancelDelete)
                    TerminalButton(text = if (deleting) "DELETING…" else "DELETE CARD",
                        selected = confirmDeleteSelected,
                        onClick = if (deleting) null else ::deleteCard)
                    TerminalText("[↑/↓] CHOOSE · [A] CONFIRM · [B] CANCEL", fontSize = 11.sp)
                } else if (ownedPokemon != null) {
                    TerminalButton(text = "BATTLE RECORDS", onClick = { onRecords(ownedId) })
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TerminalButton(text = "EDIT CARD", modifier = Modifier.weight(1f), onClick = { onEdit(ownedId) })
                        TerminalButton(text = "DELETE CARD", modifier = Modifier.weight(1f), onClick = ::openDeleteConfirmation)
                    }
                    TerminalText("[A] RECORDS · [START] EDIT · [SELECT] DELETE · [B] BACK", fontSize = 11.sp)
                }
            }
        },
    ) { _ ->
        val owned = ownedPokemon
        if (owned == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TerminalText(text = "SPECIMEN NOT FOUND")
            }
            return@ODXFiShell
        }

        if (showDeleteConfirm) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TerminalHeader("delete card?")
                TerminalText(owned.displayName ?: species?.name ?: owned.speciesName ?: "Pokémon card", fontSize = 24.sp)
                TerminalText("${species?.name ?: owned.speciesName.orEmpty()} · CP ${owned.cp ?: "---"}")
                TerminalText("ID: ${owned.id.takeLast(8).uppercase()}", fontSize = 11.sp)
                TerminalText("Delete this card from all binders? Recorded matches and saved teams will stay.")
                TerminalText("This cannot be undone. Your other cards are kept.")
                deleteError?.let { TerminalText(it, color = TerminalPurple) }
                TerminalText(if (deleting) "Deleting card…" else "Choose KEEP CARD or DELETE CARD on the LCD.")
            }
            return@ODXFiShell
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
                    owned.displayName?.let { nickname ->
                        TypeIconText(nickname, color = TerminalGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    
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
                    if (owned.size != com.example.overdex.model.PokemonSize.UNKNOWN) StatusBadge(text = owned.size.label, color = TerminalGreen)
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
            TerminalText("[A] Open this card's recorded moves, faints and supported knockouts.")

            BinderSectionTitle("TIMELINE")
            PlaceholderContent()

            BinderSectionTitle("TEAMS")
            TerminalText(owned.teams.joinToString(", ").ifBlank { "No teams assigned — START to edit" })

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
