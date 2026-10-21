package com.example.overdex.ui.screens.observatory

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Text entry uses a dialog outside the CRT's GlassShield. */
@Composable
internal fun ReplayNameDialog(
    heading: String,
    initialTitle: String,
    initialNotes: String? = null,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var notes by remember { mutableStateOf(initialNotes.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(heading) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it.take(80) }, Modifier.fillMaxWidth(),
                    label = { Text("Name") }, singleLine = true)
                if (initialNotes != null) OutlinedTextField(notes, { notes = it.take(2000) },
                    Modifier.fillMaxWidth().heightIn(max = 150.dp), label = { Text("Notes (optional)") })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title.trim(), notes.trim()) }, enabled = title.isNotBlank()) { Text("SAVE") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
    )
}
