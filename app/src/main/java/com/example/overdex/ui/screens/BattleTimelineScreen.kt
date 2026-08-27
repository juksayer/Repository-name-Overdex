package com.example.overdex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.BattleMemory
import com.example.overdex.model.BattleEvent
import com.example.overdex.model.BattleEventType
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalGreen
import java.util.Locale

@Composable
fun BattleTimelineScreen(
    battleMemory: BattleMemory,
    viewModel: PokedexViewModel,
    onBack: () -> Unit,
    onLcdDrag: ((Offset) -> Unit) -> Unit = {},
    onLcdTap: (() -> Unit) -> Unit = {}
) {
    val events = battleMemory.timeline.events
    val startTime = battleMemory.startTime

    val listState = rememberLazyListState()
    val nav = rememberHandheldNavigationController(
        itemCount = { events.size }
    )

    HandheldListSync(
        listState = listState,
        selectedIndex = nav.selectedIndex,
        totalItems = events.size
    )

    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    val dragThreshold = 40f // Pixels of LCD drag to move one item

    SideEffect {
        onLcdDrag { delta ->
            dragAccumulator += delta.y
            if (dragAccumulator > dragThreshold) {
                nav.moveDown()
                dragAccumulator = 0f
            } else if (dragAccumulator < -dragThreshold) {
                nav.moveUp()
                dragAccumulator = 0f
            }
        }
        onLcdTap {
            // No activation action currently defined for timeline events
        }
    }

    TerminalScreen {
        TerminalPathIndicator(path = "/battle/logs/")
        TerminalText(text = "session_history: active", color = TerminalDimGreen)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            itemsIndexed(events) { index, event ->
                BattleEventRow(
                    event = event,
                    battleStartTime = startTime,
                    viewModel = viewModel,
                    isSelected = index == nav.selectedIndex
                )
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
    viewModel: PokedexViewModel,
    isSelected: Boolean = false
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

    val backgroundColor = if (isSelected) TerminalGreen.copy(alpha = 0.1f) else Color.Transparent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(vertical = 4.dp, horizontal = 8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            TerminalText(
                text = timeStr,
                color = if (isSelected) TerminalGreen else TerminalDimGreen,
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
            
            TerminalText(
                text = eventDesc,
                fontSize = 14.sp,
                color = if (isSelected) TerminalGreen else Color.Unspecified
            )
        }
        
        if (pokemonName != null) {
            Row(modifier = Modifier.fillMaxWidth().padding(start = 56.dp)) {
                TerminalText(
                    text = pokemonName!!,
                    color = if (isSelected) TerminalGreen.copy(alpha = 0.8f) else TerminalDimGreen,
                    fontSize = 12.sp
                )
            }
        }
    }
}
