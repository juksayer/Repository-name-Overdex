package com.example.overdex.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.BattleMemory
import com.example.overdex.model.BattleEvent
import com.example.overdex.model.BattleEventType
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.TerminalDimGreen
import java.util.Locale

@Composable
fun BattleTimelineScreen(
    battleMemory: BattleMemory,
    viewModel: PokedexViewModel,
    onBack: () -> Unit
) {
    val events = battleMemory.timeline.events
    val startTime = battleMemory.startTime

    TerminalScreen {
        TerminalHeader("battle.log")
        TerminalText(text = "session_history: active", color = TerminalDimGreen)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(events) { event ->
                BattleEventRow(event, startTime, viewModel)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TerminalButton(text = "back", onClick = onBack)
    }
}

@Composable
fun BattleEventRow(
    event: BattleEvent,
    battleStartTime: Long,
    viewModel: PokedexViewModel
) {
    val relativeMs = event.timestamp - battleStartTime
    val seconds = (relativeMs / 1000) % 60
    val minutes = (relativeMs / (1000 * 60)) % 60
    val timeStr = String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)

    var pokemonName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(event.pokemonId) {
        event.pokemonId?.let { id ->
            pokemonName = viewModel.getPokemonName(id)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            TerminalText(
                text = timeStr,
                color = TerminalDimGreen,
                fontSize = 12.sp,
                modifier = Modifier.width(48.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            val eventDesc = when (event.type) {
                BattleEventType.BATTLE_STARTED -> "Battle Started"
                BattleEventType.BATTLE_ENDED -> "Battle Ended"
                BattleEventType.POKEMON_IDENTIFIED -> "Enemy Identified"
                BattleEventType.POKEMON_SWITCHED -> if (event.actor == com.example.overdex.model.BattleActor.PLAYER) "Player Swapped" else "Enemy Switched"
                BattleEventType.POKEMON_FAINTED -> "Pokemon Fainted"
                BattleEventType.CHARGED_MOVE_THROWN -> "Charged Move Thrown"
                BattleEventType.SHIELD_USED -> "Shield Used"
                BattleEventType.ENERGY_UPDATED -> "Energy Updated"
            }
            
            TerminalText(text = eventDesc, fontSize = 14.sp)
        }
        
        if (pokemonName != null) {
            Row(modifier = Modifier.fillMaxWidth().padding(start = 56.dp)) {
                TerminalText(
                    text = pokemonName!!,
                    color = TerminalDimGreen,
                    fontSize = 12.sp
                )
            }
        }
    }
}
