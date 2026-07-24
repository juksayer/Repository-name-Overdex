package com.example.overdex.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.example.overdex.data.ChatRepository
import com.example.overdex.model.ChatMessage
import com.example.overdex.model.ChatMessageType
import com.example.overdex.model.PartnerIdentity
import com.example.overdex.model.PokemonType
import com.example.overdex.model.TrainerIdentity
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.MyCollectionViewModel
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.*
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ChatScreen(
    trainerIdentity: TrainerIdentity?,
    partnerIdentity: PartnerIdentity?,
    messages: List<ChatMessage>,
    chatRepository: ChatRepository,
    pokedexViewModel: PokedexViewModel,
    collectionViewModel: MyCollectionViewModel,
    onPokemonClick: (Int) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    ODXFiShell(
        viewModel = pokedexViewModel,
        onB = onBack,
        onA = {
            if (inputText.isNotBlank() && trainerIdentity != null) {
                scope.launch {
                    chatRepository.send(inputText, trainerIdentity)
                    inputText = ""
                }
            }
        }
    ) { _ ->
        TerminalScreen {
            Column(modifier = Modifier.fillMaxSize()) {
                TerminalPathIndicator(path = "/trainer/chat")
                
                Spacer(modifier = Modifier.height(8.dp))

                // Messages List
                Box(modifier = Modifier.weight(1f)) {
                    if (messages.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            TerminalText(text = "NO MESSAGES YET", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(messages) { message ->
                                val isMe = message.senderTrainerId == trainerIdentity?.trainerId?.toString()
                                ChatMessageRow(
                                    message = message,
                                    isMe = isMe,
                                    senderName = if (isMe) "YOU" else partnerIdentity?.displayName ?: "PARTNER",
                                    pokedexViewModel = pokedexViewModel,
                                    collectionViewModel = collectionViewModel,
                                    onPokemonClick = onPokemonClick
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Input Area
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("TYPE MESSAGE...", color = TerminalDimGreen, fontSize = 12.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = TerminalBlack,
                            unfocusedContainerColor = TerminalBlack,
                            focusedTextColor = TerminalGreen,
                            unfocusedTextColor = TerminalGreen,
                            focusedIndicatorColor = TerminalGreen,
                            unfocusedIndicatorColor = TerminalDimGreen
                        ),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    TerminalButton(
                        text = "SEND",
                        onClick = {
                            if (inputText.isNotBlank() && trainerIdentity != null) {
                                scope.launch {
                                    chatRepository.send(inputText, trainerIdentity)
                                    inputText = ""
                                }
                            }
                        },
                        modifier = Modifier.width(80.dp)
                    )
                }

                TerminalButton(text = "back", onClick = onBack)
            }
        }
    }
}

@Composable
fun ChatMessageRow(
    message: ChatMessage,
    isMe: Boolean,
    senderName: String,
    pokedexViewModel: PokedexViewModel,
    collectionViewModel: MyCollectionViewModel,
    onPokemonClick: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TerminalText(
                text = senderName,
                color = if (isMe) TerminalPurple else TerminalGreen,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            val timeStr = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
                .withZone(ZoneId.systemDefault())
                .format(message.sentAt)
            TerminalText(text = timeStr, color = Color.Gray, fontSize = 9.sp)
        }
        
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .background(
                    color = if (isMe) TerminalPurple.copy(alpha = 0.1f) else TerminalGreen.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(if (isMe) 8.dp else 4.dp)
                )
                .border(
                    width = 0.5.dp,
                    color = if (isMe) TerminalPurple.copy(alpha = 0.3f) else TerminalGreen.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(if (isMe) 8.dp else 4.dp)
                )
                .clickable(enabled = message.type == ChatMessageType.POKEMON) {
                    if (message.type == ChatMessageType.POKEMON) {
                        message.sharedPokemon?.let { sp ->
                            scope.launch {
                                val pokemon = pokedexViewModel.getPokemonByName(sp.speciesName)
                                if (pokemon != null) {
                                    onPokemonClick(pokemon.id)
                                } else {
                                    Toast.makeText(context, "Unable to open Pokémon.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
                .padding(8.dp)
        ) {
            if (message.type == ChatMessageType.POKEMON) {
                val sp = message.sharedPokemon
                if (sp != null) {
                    var resolvedPokemon by remember(sp.speciesName) { mutableStateOf<com.example.overdex.model.Pokemon?>(null) }
                    LaunchedEffect(sp.speciesName) {
                        resolvedPokemon = pokedexViewModel.getPokemonByName(sp.speciesName)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Sprite resolution through unified pipeline
                        val spriteUrl = resolvedPokemon?.let { 
                            pokedexViewModel.spriteProvider.getSpriteUrl(
                                id = it.id,
                                isShiny = sp.isShiny,
                                isShadow = sp.isShadow,
                                isPurified = sp.isPurified
                            )
                        }
                        var spriteLoaded by remember { mutableStateOf(false) }

                        if (spriteUrl != null) {
                            AsyncImage(
                                model = spriteUrl,
                                contentDescription = sp.speciesName,
                                onState = { state ->
                                    if (state is AsyncImagePainter.State.Success) spriteLoaded = true
                                },
                                modifier = Modifier
                                    .size(if (spriteLoaded) 64.dp else 0.dp)
                                    .alpha(if (spriteLoaded) 1f else 0f)
                                    .background(TerminalGreen.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                                    .border(if (spriteLoaded) 1.dp else 0.dp, TerminalDimGreen.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            )

                            if (spriteLoaded) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        val prefix = if (sp.isShadow) "SHADOW " else if (sp.isPurified) "PURIFIED " else ""
                        val shinySuffix = if (sp.isShiny) " ✨" else ""
                        TerminalText(
                            text = "$prefix${sp.speciesName}$shinySuffix".uppercase(),
                            color = if (sp.isShiny) TerminalPurple else TerminalGreen
                        )
                        
                        if (sp.cp != null) {
                            TerminalText(text = "CP ${sp.cp}", fontSize = 12.sp)
                        }
                        
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            val primaryEnum = try { PokemonType.valueOf(sp.primaryType.uppercase()) } catch (e: Exception) { null }
                            if (primaryEnum != null) {
                                TypeBadge(type = primaryEnum, style = TypeIconStyle.OVERDEX, modifier = Modifier.scale(0.8f))
                            } else {
                                TerminalText(text = sp.primaryType.uppercase(), fontSize = 11.sp, color = TerminalDimGreen)
                            }
                            
                            sp.secondaryType?.let { secondary ->
                                Spacer(modifier = Modifier.width(4.dp))
                                val secondaryEnum = try { PokemonType.valueOf(secondary.uppercase()) } catch (e: Exception) { null }
                                if (secondaryEnum != null) {
                                    TypeBadge(type = secondaryEnum, style = TypeIconStyle.OVERDEX, modifier = Modifier.scale(0.8f))
                                } else {
                                    TerminalText(text = secondary.uppercase(), fontSize = 11.sp, color = TerminalDimGreen)
                                }
                            }
                        }
                    }
                } else {
                    Column {
                        TerminalText(text = "[Unknown Pokémon]", color = Color.Red)
                        TerminalText(text = "This Pokémon is no longer available.", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            } else {
                TerminalText(
                    text = message.text ?: "",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }
    }
}
