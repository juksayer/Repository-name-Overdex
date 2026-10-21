package com.example.overdex.ui.screens.observatory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.battle.replay.ReplayChargedMoveSlot
import com.example.overdex.battle.replay.ReplayEnergyState
import com.example.overdex.ui.components.PokemonTypeIcon

/** Read-only GO-style charge buttons; fill uses the same approximate ledger as the badge. */
@Composable
internal fun ReplayChargeButtons(moves: List<ReplayChargedMoveSlot>, energy: ReplayEnergyState?, modifier: Modifier = Modifier) {
    val slots = if (moves.size == 1 && moves.single().slot == 1) listOf(1) else listOf(1, 2)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        slots.forEach { slot ->
            val move = moves.firstOrNull { it.slot == slot }
            val fill = if (move?.energyCost != null && energy != null)
                (energy.recordedEnergy.toFloat() / move.energyCost).coerceIn(0f, 1f) else 0f
            Column(Modifier.widthIn(max = 132.dp).semantics(mergeDescendants = true) {
                contentDescription = "Player charged move $slot: ${move?.moveName ?: "not recorded"}"
                stateDescription = if (move?.energyCost != null && energy != null)
                    "Recorded energy approximately ${energy.recordedEnergy}, cost ${move.energyCost}" else "Energy requirement unknown"
            }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(52.dp).clip(CircleShape)
                    .background(Color(0xB812211C))
                    .border(if (fill >= 1f) 2.dp else 1.dp,
                        if (fill >= 1f) Color.White else Color(0xFF8AA499), CircleShape),
                    contentAlignment = Alignment.Center) {
                    if (fill > 0f) Box(Modifier.fillMaxWidth().fillMaxHeight(fill).align(Alignment.BottomCenter)
                        .background((move?.type?.color ?: Color.LightGray).copy(alpha = 0.32f)))
                    move?.type?.let { PokemonTypeIcon(it, Modifier.size(34.dp)) }
                        ?: Text("?", color = Color(0xFF8AA499), fontSize = 28.sp)
                }
                Text(move?.moveName ?: "—", color = Color(0xFFDBEDE4), fontSize = 11.sp,
                    textAlign = TextAlign.Center, maxLines = 2, lineHeight = 13.sp,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
