package com.example.overdex.ui.screens.observatory

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.overdex.battle.replay.ReplayCorrectionKind
import com.example.overdex.battle.replay.ReplayCorrectionSide
import com.example.overdex.battle.replay.ReplayTrainerCorrection
import java.util.UUID

@Composable
internal fun ReplayCorrectionDialog(
    matchId: String,
    offsetNanos: Long,
    priorEntries: List<ReplayTrainerCorrection>,
    onDismiss: () -> Unit,
    onSave: (ReplayTrainerCorrection) -> Unit,
) {
    var side by remember { mutableStateOf(ReplayCorrectionSide.PLAYER) }
    var kind by remember { mutableStateOf(ReplayCorrectionKind.FAST_MOVE) }
    var species by remember { mutableStateOf("") }
    var move by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var finishingMoveId by remember { mutableStateOf<String?>(null) }
    val valid = species.isNotBlank() && species.length <= 100 && author.isNotBlank() && author.length <= 100 &&
        note.length <= 2000 && (kind == ReplayCorrectionKind.FAINT || (move.isNotBlank() && move.length <= 100))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a missing event") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Place the cursor at the impact or faint. Your entry is saved separately from the recording.")
                Row {
                    TextButton(onClick = { side = ReplayCorrectionSide.PLAYER }) { Text(if (side == ReplayCorrectionSide.PLAYER) "✓ Your team" else "Your team") }
                    TextButton(onClick = { side = ReplayCorrectionSide.OPPONENT }) { Text(if (side == ReplayCorrectionSide.OPPONENT) "✓ Opponent" else "Opponent") }
                }
                Row {
                    ReplayCorrectionKind.entries.forEach { option ->
                        TextButton(onClick = { kind = option }) {
                            Text((if (kind == option) "✓ " else "") + when (option) {
                                ReplayCorrectionKind.FAST_MOVE -> "Fast"
                                ReplayCorrectionKind.CHARGED_MOVE -> "Charged"
                                ReplayCorrectionKind.FAINT -> "Faint"
                            })
                        }
                    }
                }
                OutlinedTextField(species, { species = it }, label = { Text("Pokémon") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (kind != ReplayCorrectionKind.FAINT) OutlinedTextField(move, { move = it }, label = { Text("Move") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (kind == ReplayCorrectionKind.FAINT) {
                    Text("Credit a move only if you know it caused this faint:")
                    TextButton(onClick = { finishingMoveId = null }) { Text(if (finishingMoveId == null) "✓ Cause unknown" else "Cause unknown") }
                    priorEntries.filter { it.kind != ReplayCorrectionKind.FAINT && it.side != side && it.matchOffsetNanos <= offsetNanos }
                        .sortedByDescending { it.matchOffsetNanos }.forEach { candidate ->
                            TextButton(onClick = { finishingMoveId = candidate.id }) {
                                Text((if (finishingMoveId == candidate.id) "✓ " else "") + "${formatReplayTime(candidate.matchOffsetNanos)} · ${candidate.speciesName} · ${candidate.moveName}")
                            }
                        }
                }
                OutlinedTextField(author, { author = it }, label = { Text("Your trainer name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = {
            onSave(ReplayTrainerCorrection(UUID.randomUUID().toString(), matchId, offsetNanos, kind, side,
                species.trim(), move.trim().takeIf { kind != ReplayCorrectionKind.FAINT }, note.trim(), author.trim(), System.currentTimeMillis(),
                finishingMoveId = finishingMoveId.takeIf { id -> kind == ReplayCorrectionKind.FAINT && priorEntries.any {
                    it.id == id && it.kind != ReplayCorrectionKind.FAINT && it.side != side && it.matchOffsetNanos <= offsetNanos
                } }))
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
