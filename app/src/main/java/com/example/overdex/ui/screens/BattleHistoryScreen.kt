package com.example.overdex.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.data.BattleHistoryRepository
import com.example.overdex.model.ArchivedBattle
import com.example.overdex.model.getEnemyLeadId
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.TerminalButton
import com.example.overdex.ui.components.TerminalPathIndicator
import com.example.overdex.ui.components.TerminalScreen
import com.example.overdex.ui.components.TerminalText
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@Composable
fun BattleHistoryScreen(
    viewModel: PokedexViewModel,
    onBattleClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val repository = remember { BattleHistoryRepository() }
    val history = remember { repository.getHistory() }

    TerminalScreen {
        TerminalPathIndicator(path = "/battle/history/")
        
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

    val result = "Unknown"

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
                color = TerminalDimGreen,
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
