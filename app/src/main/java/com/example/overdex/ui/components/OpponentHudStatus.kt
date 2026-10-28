package com.example.overdex.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/** Speculative energy stays labeled; resources remain in the native badge location. */
@Composable
internal fun OpponentHudStatus(estimate: OpponentEnergyEstimate?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically) {
        if (estimate != null) {
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
