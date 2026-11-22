package com.example.overdex.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.battle.forecast.OpponentEnergyEstimate
import com.example.overdex.battle.BattleSpeciesDisplay
import com.example.overdex.battle.observation.DroidballOverlayPresentation
import kotlinx.coroutines.delay

/** Speculative energy stays labeled; resources remain in the native badge location. */
@Composable
internal fun OpponentHudStatus(estimate: OpponentEnergyEstimate?) {
    val credit by DroidballOverlayPresentation.finishingCredit.collectAsState()
    val current = credit
    var showCredit by remember(current) { mutableStateOf(current != null && System.nanoTime() < current.showUntilNanos) }
    LaunchedEffect(current) {
        if (current != null) {
            delay(((current.showUntilNanos - System.nanoTime()) / 1_000_000L).coerceAtLeast(0L))
            showCredit = false
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically) {
        if (current != null && showCredit) {
            val hit = current.decision
            Text("${BattleSpeciesDisplay.name(hit.attackerSpeciesName)} · ${hit.moveName} KO’d ${BattleSpeciesDisplay.name(hit.recipientSpeciesName)}",
                color = Color.White, fontSize = 11.sp, lineHeight = 13.sp,
                modifier = Modifier.background(Color(0xDC141918)).padding(horizontal = 3.dp))
        } else if (estimate != null) {
            Text("≈${estimate.energy} E", fontSize = 8.sp, lineHeight = 10.sp,
                style = TextStyle(shadow = BattleHudBadgeStyle.textShadow(LocalDensity.current)),
                fontFamily = FontFamily.Monospace, color = Color(0xFF275F6D),
                modifier = Modifier.padding(end = 4.dp).semantics {
                    contentDescription = "Estimated energy ${estimate.energy}, assuming ${estimate.assumedMoveName}; " +
                        "${estimate.expectedUses} expected uses, ${estimate.observedUses} observed; " +
                        "${if (estimate.paused) "clock paused" else "perfect active play"}" +
                        if (estimate.unexplainedSpend > 0) "; ${estimate.unexplainedSpend} energy unaccounted for" else ""
                })
        }
    }
}
