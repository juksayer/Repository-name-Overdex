package com.example.overdex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.data.registration.GuidedRegistrationController
import com.example.overdex.data.registration.RegistrationFields

@Composable
fun RegistrationScanOverlay(
    controller: GuidedRegistrationController,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDrag: (Float, Float) -> Unit,
    onReview: () -> Unit,
) {
    val state by controller.state.collectAsState()
    val alignment by controller.alignment.state.collectAsState()
    val cry by controller.cry.collectAsState()
    val field by controller.fieldReview.collectAsState()
    var confirmNext by remember(state.draft?.cardId) { mutableStateOf(false) }
    val ink = Color(0xFF315E61)
    Column(Modifier.width(if (expanded) 176.dp else 108.dp).heightIn(max = 440.dp).verticalScroll(rememberScrollState())
        .background(Color(0xF2E8F5EE), RoundedCornerShape(10.dp)).padding(6.dp)) {
        Text("CARD SCAN", color = ink, fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).pointerInput(Unit) {
                detectDragGestures { change, drag -> change.consume(); onDrag(drag.x, drag.y) }
            })
        Text(state.resolution?.species?.name ?: state.resolution?.title ?: "Open detail page", color = ink, fontSize = 11.sp)
        TextButton(onClick = { onExpandedChange(!expanded) }) {
            Text(if (state.draft?.saved == true) "SAVED ✓" else if (expanded) "LESS" else "PROGRESS", fontSize = 11.sp, color = ink)
        }
        if (alignment.editing) {
            Text("Drag a box to ${if (alignment.resizing) "resize" else "move"} it. All fields share this layout.", color = ink, fontSize = 11.sp)
            TextButton(onClick = { controller.alignment.toggleResize() }) { Text(if (alignment.resizing) "MOVE" else "RESIZE", color = ink) }
            TextButton(onClick = { controller.alignment.save() }) { Text("SAVE BOXES", color = ink) }
            TextButton(onClick = { controller.alignment.cancelEdit() }) { Text("CANCEL", color = ink) }
            alignment.error?.let { Text(it, color = Color.Red, fontSize = 11.sp) }
        } else if (field != null) {
            val review = requireNotNull(field)
            Text(RegistrationFields.label(review.region.id), color = ink, fontSize = 12.sp)
            Text(review.message, color = ink, fontSize = 11.sp)
            review.read?.fields?.forEach { Text(it.value, color = ink, fontSize = 13.sp) }
            if (review.read?.fields.isNullOrEmpty() && review.rawText.isNotBlank()) Text("Read: ${review.rawText}", color = ink, fontSize = 10.sp)
            if (!review.busy) {
                if (!review.read?.fields.isNullOrEmpty()) {
                    val changes = state.draft?.identityChanges(requireNotNull(review.read)).orEmpty()
                    if (changes.isEmpty()) {
                        TextButton(onClick = { controller.confirmField() }) { Text("USE READING", color = ink, fontSize = 11.sp) }
                    } else {
                        Text("This differs from the current card. Update it, or start another Pokémon?", color = ink, fontSize = 11.sp)
                        TextButton(onClick = { controller.confirmField(updateExisting = true) }) { Text("UPDATE THIS CARD", color = ink, fontSize = 11.sp) }
                        TextButton(onClick = { controller.confirmField(startNewCard = true) }) { Text("NEW POKÉMON", color = ink, fontSize = 11.sp) }
                    }
                }
                RegistrationFields.choices(review.region.id).forEach { (label, value) ->
                    TextButton(onClick = { controller.confirmField(value) }) { Text(label, color = ink, fontSize = 11.sp) }
                }
                TextButton(onClick = { controller.dismissField(); controller.readField(review.region.id) }) { Text("RETRY", color = ink, fontSize = 11.sp) }
            }
            TextButton(onClick = { controller.dismissField() }) { Text("CANCEL", color = ink, fontSize = 11.sp) }
        } else {
            Text("Scroll between boxes. Tap a box to read its field.", color = ink, fontSize = 10.sp)
            if (expanded) {
                Text(state.message, color = ink, fontSize = 12.sp)
                state.resolution?.let { r ->
                    Text("CP ${r.cp ?: "…"}\n${r.fastMove ?: "Fast move…"}\n${r.chargedMoves.joinToString("\n")}", color = ink, fontSize = 11.sp)
                }
                if (state.resolution?.ready == true && state.draft?.saved != true)
                    TextButton(onClick = { controller.saveCard() }) { Text("SAVE CARD", color = ink, fontSize = 11.sp) }
                TextButton(onClick = onReview) { Text("REVIEW / FINISH", color = ink, fontSize = 11.sp) }
                if (state.draft?.saved != true) {
                    TextButton(onClick = { controller.alignment.edit() }) { Text("ADJUST BOXES", color = ink, fontSize = 11.sp) }
                    TextButton(onClick = { controller.listenForCry() }, enabled = !cry.busy) { Text("LISTEN FOR CRY", color = ink, fontSize = 11.sp) }
                    if (cry.message.isNotBlank()) Text(cry.message, color = ink, fontSize = 11.sp)
                    cry.evidence?.candidates?.forEach { candidate ->
                        TextButton(onClick = { controller.confirmCrySpecies(candidate.speciesId) }) { Text("CONFIRM ${candidate.speciesName}", color = ink, fontSize = 11.sp) }
                    }
                }
                if (state.draft?.saved == true || !state.draft?.evidence.isNullOrEmpty() || !state.draft?.cries.isNullOrEmpty() || state.draft?.confirmedSpeciesId != null) {
                    if (confirmNext) {
                        Text("This card is unfinished. Keep its draft and start another Pokémon?", color = ink, fontSize = 11.sp)
                        TextButton(onClick = { controller.newCard() }) { Text("START NEXT", color = ink, fontSize = 11.sp) }
                        TextButton(onClick = { confirmNext = false }) { Text("KEEP SCANNING", color = ink, fontSize = 11.sp) }
                    } else TextButton(onClick = {
                        if (state.draft?.saved == true) controller.newCard() else confirmNext = true
                    }) { Text("NEXT POKÉMON", color = ink, fontSize = 11.sp) }
                }
            }
        }
    }
}
