package com.example.overdex.ui.screens.observatory

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.battle.replay.ReplayEnergyState
import com.example.overdex.battle.replay.ReplayTeamResources
import com.example.overdex.model.PokemonType
import com.example.overdex.ui.components.PokemonTypeIcon

/** Team resources stay on the battlefield; the LCD remains transport and detail. */
@Composable
internal fun ReplayResourceBadge(resources: ReplayTeamResources, side: String, energy: ReplayEnergyState? = null,
                                mirrored: Boolean = false, types: List<PokemonType> = emptyList()) {
    Column(horizontalAlignment = if (mirrored) Alignment.End else Alignment.Start) {
        Canvas(Modifier.width(114.dp).height(20.dp).semantics {
            contentDescription = "$side: " +
                (resources.pokeBalls?.let { "$it of 3 Pokémon remaining" } ?: "Poké Balls not recorded") + ", " +
                (resources.shields?.let { "$it of 2 shields remaining" } ?: "shields not recorded")
        }) {
            val ink = Color(0xFF17272A)
            val unavailable = Color(0xFF78908B)
            fun x(position: Int): Float = position.dp.toPx().let { if (mirrored) size.width - it else it }
            repeat(3) { slot ->
                val center = Offset(x(8 + slot * 20), 10.dp.toPx())
                val radius = 7.dp.toPx()
                val remaining = resources.pokeBalls
                val alive = remaining != null && slot < remaining
                if (alive) {
                    drawCircle(Color.White, radius, center)
                    drawArc(Color(0xFFEF5350), 180f, 180f, true,
                        topLeft = center - Offset(radius, radius), size = Size(radius * 2, radius * 2))
                    drawLine(ink, center - Offset(radius, 0f), center + Offset(radius, 0f), 1.5.dp.toPx())
                    drawCircle(ink, 2.5.dp.toPx(), center)
                    drawCircle(Color.White, 1.2.dp.toPx(), center)
                } else if (remaining != null) {
                    drawCircle(ink, radius, center)
                    drawLine(unavailable, center - Offset(3.dp.toPx(), 3.dp.toPx()),
                        center + Offset(3.dp.toPx(), 3.dp.toPx()), 1.dp.toPx())
                    drawLine(unavailable, center + Offset(-3.dp.toPx(), 3.dp.toPx()),
                        center + Offset(3.dp.toPx(), -3.dp.toPx()), 1.dp.toPx())
                }
                drawCircle(if (alive) Color.White else unavailable, radius, center, style = Stroke(1.dp.toPx()))
            }
            repeat(2) { slot ->
                val x = x(79 + slot * 23)
                val y = 10.dp.toPx()
                val w = 7.dp.toPx()
                val h = 9.dp.toPx()
                val hexagon = Path().apply {
                    moveTo(x, y - h); lineTo(x + w, y - h / 2); lineTo(x + w, y + h / 2)
                    lineTo(x, y + h); lineTo(x - w, y + h / 2); lineTo(x - w, y - h / 2); close()
                }
                val remaining = resources.shields
                val intact = remaining != null && slot < remaining
                if (remaining != null) drawPath(hexagon, if (intact) Color(0xFFE9A9F4) else ink)
                drawPath(hexagon, if (intact) Color(0xFFFFDDFF) else unavailable, style = Stroke(1.dp.toPx()))
                if (intact) {
                    drawLine(Color(0xFFA06DBA), Offset(x - w, y - h / 2), Offset(x + w, y + h / 2), 1.dp.toPx())
                    drawLine(Color(0xFFA06DBA), Offset(x + w, y - h / 2), Offset(x - w, y + h / 2), 1.dp.toPx())
                }
            }
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (mirrored) ReplayEnergyLabel(energy, side)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.semantics {
                contentDescription = "$side active Pokémon types: ${types.joinToString { it.name.lowercase() }.ifEmpty { "unknown" }}"
            }) {
                types.take(2).forEach { PokemonTypeIcon(it, Modifier.size(18.dp)) }
            }
            if (!mirrored) ReplayEnergyLabel(energy, side)
        }
    }
}

@Composable
private fun ReplayEnergyLabel(energy: ReplayEnergyState?, side: String) {
    Text(energy?.let { "E ~${it.recordedEnergy}/100" } ?: "E —", color = Color(0xFFDBEDE4), fontSize = 12.sp,
        maxLines = 1, softWrap = false, modifier = Modifier.semantics {
            contentDescription = "$side energy"
            stateDescription = energy?.let { "Approximately ${it.recordedEnergy} of 100, from recorded moves" } ?: "Not recorded"
        })
}
