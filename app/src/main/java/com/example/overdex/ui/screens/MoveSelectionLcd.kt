package com.example.overdex.ui.screens

import com.example.overdex.model.Move
import java.util.Locale

/** Compact competitive move data for the ODX-Fi LCD during move selection. */
internal fun moveSelectionLcdLines(
    move: Move?,
    selected: Boolean = false,
): List<String> {
    if (move == null) return listOf("MOVE DATA", "NO MOVE HIGHLIGHTED")

    return buildList {
        add(move.name.uppercase(Locale.ROOT))
        add("${move.type.name} / ${if (move.isFast) "FAST" else "CHARGED"}")
        add("DMG ${move.damage}  ENERGY ${if (move.isFast) "+" else "-"}${move.energy}")
        if (move.isFast) {
            move.turns?.takeIf { it > 0 }?.let { turns ->
                val seconds = turns * 0.5
                add("$turns ${if (turns == 1) "TURN" else "TURNS"}  ${decimal(seconds)} SEC")
                add(
                    "DPT ${decimal(move.damage.toDouble() / turns)}  " +
                        "EPT ${decimal(move.energy.toDouble() / turns)}"
                )
            }
        } else {
            add("DPE ${String.format(Locale.ROOT, "%.2f", move.dpe ?: 0.0)}")
        }
        if (selected) add("[ SELECTED ]")
    }
}

private fun decimal(value: Double): String = String.format(Locale.ROOT, "%.1f", value)
