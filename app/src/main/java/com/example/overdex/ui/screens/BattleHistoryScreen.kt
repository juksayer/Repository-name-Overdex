package com.example.overdex.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.data.BattleHistoryRepository
import com.example.overdex.model.ArchivedBattle
import com.example.overdex.model.getEnemyLeadId
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.ui.theme.TerminalPurple
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BattleHistoryScreen(
    viewModel: PokedexViewModel,
    onBattleClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val repository = remember { BattleHistoryRepository() }
    val history = remember { repository.getHistory() }

    TerminalScreen {
        TerminalHeader("battle.history")
        
        if (history.isEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            TerminalText(text = "No Battles Yet", color = TerminalGreen)
            Spacer(modifier = Modifier.height(8.dp))
            TerminalText(
                text = "Your Battle History will begin after your first recorded battle.",
                color = TerminalDimGreen
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(history) { battle ->
                    BattleHistoryItem(battle, viewModel) {
                        onBattleClick(battle.id)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        TerminalButton(text = "back", onClick = onBack)
    }
}

@Composable
fun BattleHistoryItem(
    battle: ArchivedBattle,
    viewModel: PokedexViewModel,
    onClick: () -> Unit
) {
    val dateStr = remember(battle.startTime) {
        val sdf = SimpleDateFormat("MMM dd HH:mm", Locale.ROOT)
        sdf.format(Date(battle.startTime))
    }

    var enemyLeadName by remember { mutableStateOf<String?>(null) }
    val enemyLeadId = battle.getEnemyLeadId()

    LaunchedEffect(enemyLeadId) {
        if (enemyLeadId != null) {
            enemyLeadName = viewModel.getPokemonName(enemyLeadId)
        }
    }

    // Determine placeholder result for prototype
    // In a real analyzer, this would be computed
    val result = if (battle.timeline.any { it.type == com.example.overdex.model.BattleEventType.POKEMON_FAINTED }) 
        "Victory" else "Defeat"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TerminalText(
                text = result.uppercase(),
                color = if (result == "Victory") TerminalGreen else TerminalPurple,
                fontSize = 16.sp
            )
            TerminalText(
                text = dateStr,
                color = TerminalDimGreen,
                fontSize = 12.sp
            )
        }
        
        TerminalText(
            text = "vs ${enemyLeadName ?: "Unknown"}",
            color = TerminalGreen,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        TerminalText(
            text = "--------------------------------",
            color = TerminalDimGreen.copy(alpha = 0.3f),
            fontSize = 10.sp
        )
    }
}
