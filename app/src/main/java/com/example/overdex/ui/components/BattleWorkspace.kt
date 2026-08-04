package com.example.overdex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.model.observation.*
import com.example.overdex.ui.theme.TerminalBlack
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalPurple

@Composable
fun BattleWorkspaceViewer(
    history: Map<String, List<Observation>>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBlack.copy(alpha = 0.9f))
            .padding(16.dp)
    ) {
        TerminalHeader(text = "battle workspace", color = TerminalPurple)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            history.forEach { (regionId, observations) ->
                item {
                    TerminalText(text = regionId.uppercase(), color = TerminalPurple, fontSize = 10.sp)
                }
                items(observations) { obs ->
                    WorkspaceObservationRow(obs)
                }
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun WorkspaceObservationRow(obs: Observation) {
    val displayValue = when (obs) {
        is PokemonNameObservation -> obs.species
        is CombatPowerObservation -> obs.cp.toString()
        is FastMoveObservation -> obs.moveName
        is ChargedMoveObservation -> obs.moveName
        is ShadowStatusObservation -> "Shadow: ${obs.isShadow}"
        is EvolutionFamilyObservation -> obs.familySpecies
        is CountdownObservation -> "Countdown: ${obs.value}"
    }

    Column(modifier = Modifier.padding(start = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TerminalText(text = obs.observerId, color = TerminalDimGreen, fontSize = 9.sp)
            TerminalText(text = "CONF: ${(obs.confidence.score * 100).toInt()}%", color = TerminalDimGreen, fontSize = 9.sp)
        }
        TerminalText(
            text = displayValue,
            color = Color.White,
            fontSize = 12.sp
        )
    }
}
