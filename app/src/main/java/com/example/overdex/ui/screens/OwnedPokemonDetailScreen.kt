package com.example.overdex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.overdex.model.Pokemon
import com.example.overdex.model.PokemonType
import com.example.overdex.ui.MyCollectionViewModel
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.*
import kotlinx.coroutines.launch

enum class DetailField {
    NICKNAME, CP, SHADOW, PURIFIED, SHINY, FAVORITE, SHARE, SAVE, DELETE
}

@Composable
fun OwnedPokemonDetailScreen(
    ownedId: String,
    pokedexViewModel: PokedexViewModel,
    collectionViewModel: MyCollectionViewModel,
    chatRepository: com.example.overdex.data.ChatRepository? = null,
    trainerIdentity: com.example.overdex.model.TrainerIdentity? = null,
    filterSettings: FilterSettings,
    onFilterSettingsChange: (FilterSettings) -> Unit,
    onDeleteSuccess: () -> Unit,
    onBack: () -> Unit,
    isServiceRunning: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val ownedPokemon by collectionViewModel.getOwnedPokemon(ownedId).collectAsState(initial = null)
    var species by remember { mutableStateOf<Pokemon?>(null) }
    
    // Focused field state
    var focusedField by remember { mutableStateOf(DetailField.NICKNAME) }
    
    // Mutable editing state
    var editingNickname by remember { mutableStateOf("") }
    var editingCP by remember { mutableStateOf("") }
    var isShadow by remember { mutableStateOf(false) }
    var isPurified by remember { mutableStateOf(false) }
    var isShiny by remember { mutableStateOf(false) }
    var isFavorite by remember { mutableStateOf(false) }
    
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Initialize editing state when data loads
    LaunchedEffect(ownedPokemon) {
        ownedPokemon?.let { owned ->
            editingNickname = owned.displayName ?: ""
            editingCP = owned.cp?.toString() ?: ""
            isShadow = owned.isShadow
            isPurified = owned.isPurified
            isShiny = owned.isShiny
            isFavorite = owned.isFavorite
            
            if (species == null) {
                species = pokedexViewModel.getPokemonById(owned.speciesId)
            }
        }
    }

    if (showDeleteConfirm) {
        TerminalDialog(
            title = "DELETE RECORD?",
            onConfirm = {
                collectionViewModel.removeOwnedPokemon(ownedId)
                onDeleteSuccess()
            },
            onDismiss = { showDeleteConfirm = false }
        )
        return
    }

    PokedexFrame(
        onUp = {
            focusedField = when (focusedField) {
                DetailField.NICKNAME -> DetailField.DELETE
                DetailField.CP -> DetailField.NICKNAME
                DetailField.SHADOW -> DetailField.CP
                DetailField.PURIFIED -> DetailField.SHADOW
                DetailField.SHINY -> DetailField.PURIFIED
                DetailField.FAVORITE -> DetailField.SHINY
                DetailField.SHARE -> DetailField.FAVORITE
                DetailField.SAVE -> DetailField.SHARE
                DetailField.DELETE -> DetailField.SAVE
            }
        },
        onDown = {
            focusedField = when (focusedField) {
                DetailField.NICKNAME -> DetailField.CP
                DetailField.CP -> DetailField.SHADOW
                DetailField.SHADOW -> DetailField.PURIFIED
                DetailField.PURIFIED -> DetailField.SHINY
                DetailField.SHINY -> DetailField.FAVORITE
                DetailField.FAVORITE -> DetailField.SHARE
                DetailField.SHARE -> DetailField.SAVE
                DetailField.SAVE -> DetailField.DELETE
                DetailField.DELETE -> DetailField.NICKNAME
            }
        },
        onA = {
            when (focusedField) {
                DetailField.SHADOW -> {
                    isShadow = !isShadow
                    if (isShadow) isPurified = false
                }
                DetailField.PURIFIED -> {
                    isPurified = !isPurified
                    if (isPurified) isShadow = false
                }
                DetailField.SHINY -> isShiny = !isShiny
                DetailField.FAVORITE -> isFavorite = !isFavorite
                DetailField.SHARE -> {
                    if (chatRepository != null && trainerIdentity != null && ownedPokemon != null && species != null) {
                        scope.launch {
                            chatRepository.sendPokemon(ownedPokemon!!, species!!, trainerIdentity)
                        }
                    }
                }
                DetailField.SAVE -> {
                    ownedPokemon?.let { original ->
                        val updated = original.copy(
                            displayName = editingNickname.ifBlank { null },
                            cp = editingCP.toIntOrNull(),
                            isShadow = isShadow,
                            isPurified = isPurified,
                            isShiny = isShiny,
                            isFavorite = isFavorite,
                            updatedAt = System.currentTimeMillis()
                        )
                        collectionViewModel.updateOwnedPokemon(updated)
                        onBack()
                    }
                }
                DetailField.DELETE -> showDeleteConfirm = true
                else -> {} // NICKNAME and CP handled by system keyboard focus
            }
        },
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
            // Identity Header with Sprite
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sprite
                val spriteUrl = pokedexViewModel.spriteProvider.getSpriteUrl(owned.speciesId)
                AsyncImage(
                    model = spriteUrl,
                    contentDescription = species?.name,
                    modifier = Modifier
                        .size(80.dp)
                        .background(TerminalGreen.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                        .border(1.dp, TerminalDimGreen.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    val speciesName = species?.name ?: "UNKNOWN"
                    Text(
                        text = speciesName.uppercase(),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isShiny) TerminalPurple else TerminalGreen,
                    )
                    
                    Text(
                        text = "OWNED ID: #${ownedId.takeLast(8).uppercase()}",
                        fontSize = 10.sp,
                        color = TerminalDimGreen
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row {
                        species?.types?.forEach { type ->
                            TypeBadge(
                                type = type,
                                style = TypeIconStyle.OVERDEX,
                                modifier = Modifier.padding(end = 4.dp).scale(0.8f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = TerminalDimGreen.copy(alpha = 0.2f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            // EDITABLE FIELDS
            TerminalEditField(
                label = "NICKNAME",
                value = editingNickname,
                onValueChange = { editingNickname = it },
                selected = focusedField == DetailField.NICKNAME
            )

            TerminalEditField(
                label = "COMBAT POWER",
                value = editingCP,
                onValueChange = { if (it.length <= 4) editingCP = it.filter { c -> c.isDigit() } },
                selected = focusedField == DetailField.CP,
                keyboardType = KeyboardType.Number
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = TerminalDimGreen.copy(alpha = 0.2f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // TOGGLES
            AttributeToggle(
                label = "Shadow Form",
                value = isShadow,
                selected = focusedField == DetailField.SHADOW,
                onClick = { 
                    isShadow = !isShadow
                    if (isShadow) isPurified = false
                    focusedField = DetailField.SHADOW
                }
            )

            AttributeToggle(
                label = "Purified Form",
                value = isPurified,
                selected = focusedField == DetailField.PURIFIED,
                onClick = { 
                    isPurified = !isPurified
                    if (isPurified) isShadow = false
                    focusedField = DetailField.PURIFIED
                }
            )

            AttributeToggle(
                label = "Shiny Variant",
                value = isShiny,
                selected = focusedField == DetailField.SHINY,
                onClick = { 
                    isShiny = !isShiny
                    focusedField = DetailField.SHINY
                }
            )

            AttributeToggle(
                label = "Favorite",
                value = isFavorite,
                selected = focusedField == DetailField.FAVORITE,
                onClick = { 
                    isFavorite = !isFavorite
                    focusedField = DetailField.FAVORITE
                }
            )

            TerminalMenuOption(
                label = "SHARE WITH PARTNER",
                selected = focusedField == DetailField.SHARE,
                onClick = {
                    focusedField = DetailField.SHARE
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
            
            // ACTION BUTTONS
            TerminalMenuOption(
                label = "SAVE RECORD",
                selected = focusedField == DetailField.SAVE,
                onClick = {
                    focusedField = DetailField.SAVE
                    // Trigger Save (onA handles this if triggered by Dpad)
                }
            )

            TerminalMenuOption(
                label = "DELETE RECORD",
                selected = focusedField == DetailField.DELETE,
                onClick = {
                    focusedField = DetailField.DELETE
                    showDeleteConfirm = true
                }
            )

            Spacer(modifier = Modifier.height(32.dp))
            Spacer(modifier = Modifier.weight(1f))
            
            // Footer guidance
            HorizontalDivider(color = TerminalDimGreen.copy(alpha = 0.2f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "A ${if (focusedField == DetailField.SAVE) "SAVE" else if (focusedField == DetailField.DELETE) "DELETE" else "TOGGLE"}",
                    color = TerminalGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "B BACK",
                    color = TerminalDimGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun TerminalEditField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    selected: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) TerminalGreen.copy(alpha = 0.1f) else Color.Transparent)
            .padding(vertical = 8.dp, horizontal = 8.dp)
    ) {
        Text(
            text = label,
            color = if (selected) TerminalGreen else TerminalDimGreen,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                color = if (selected) Color.White else TerminalGreen,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            ),
            cursorBrush = SolidColor(TerminalGreen),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = "EMPTY",
                            color = TerminalGreen.copy(alpha = 0.3f),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                    innerTextField()
                    
                    // Focus Indicator Line
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(TerminalGreen)
                        )
                    }
                }
            }
        )
    }
}

@Composable
fun TerminalDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(280.dp)
                .background(TerminalBlack, RoundedCornerShape(8.dp))
                .border(2.dp, TerminalGreen, RoundedCornerShape(8.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TerminalHeader(text = title)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TerminalButton(
                    text = "A YES",
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(16.dp))
                TerminalButton(
                    text = "B NO",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
