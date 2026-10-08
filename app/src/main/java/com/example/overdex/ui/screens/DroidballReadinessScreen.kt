package com.example.overdex.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.FilterSettings
import com.example.overdex.ui.components.TerminalHeader
import com.example.overdex.ui.components.TerminalMenuOption
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalGreen

/** Pre-flight gate that keeps player move identity out of the live battle guesswork. */
@Composable
fun DroidballReadinessScreen(
    viewModel: PokedexViewModel,
    filterSettings: FilterSettings,
    onFilterSettingsChange: (FilterSettings) -> Unit,
    onLaunch: () -> Unit,
    onConfigureTeam: () -> Unit,
    onBack: () -> Unit,
) {
    val team by viewModel.currentBattleTeam.collectAsState()
    var selectedIndex by remember(team.members.size) {
        mutableIntStateOf(if (team.members.size == 3) 0 else 1)
    }

    fun activate() {
        if (selectedIndex == 0) onLaunch() else onConfigureTeam()
    }

    ODXFiShell(
        viewModel = viewModel,
        showBattleOverlay = false,
        filterSettings = filterSettings,
        onFilterSettingsChange = onFilterSettingsChange,
        lcdLines = listOf(
            "DROIDBALL PRE-FLIGHT",
            "TEAM ${team.members.size}/3",
            "[A] SELECT  [B] BACK",
        ),
        onUp = { selectedIndex = (selectedIndex - 1).coerceAtLeast(0) },
        onDown = { selectedIndex = (selectedIndex + 1).coerceAtMost(1) },
        onA = ::activate,
        onB = onBack,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TerminalHeader("droidball pre-flight")
            Text(
                "HAVE YOU SET YOUR TEAM AND ITS MOVES?",
                color = TerminalGreen,
                fontSize = 15.sp,
            )
            if (team.members.isEmpty()) {
                Text("NO CURRENT TEAM SAVED", color = TerminalDimGreen, fontSize = 12.sp)
            } else {
                team.members.sortedBy { it.slot }.forEach { member ->
                    Text(
                        "${member.slot}. ${member.speciesName}",
                        color = TerminalDimGreen,
                        fontSize = 11.sp,
                    )
                    Text(
                        "FAST ${member.fastMoveName}  /  CHARGED ${member.chargedMoveNames.joinToString(" + ")}",
                        color = TerminalDimGreen,
                        fontSize = 9.sp,
                    )
                }
            }
            TerminalMenuOption(
                label = "YES — LAUNCH POKEMON GO",
                selected = selectedIndex == 0,
                onClick = onLaunch,
            )
            TerminalMenuOption(
                label = "NO — SET TEAM & MOVES",
                selected = selectedIndex == 1,
                onClick = onConfigureTeam,
            )
            Text(
                if (team.members.size == 3) {
                    "At Team Select, tap Droidball and start the Battle HUD."
                } else {
                    "Complete all three slots before launching Droidball."
                },
                color = TerminalDimGreen,
                fontSize = 10.sp,
            )
        }
    }
}
