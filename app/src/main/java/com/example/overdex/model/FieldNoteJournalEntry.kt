package com.example.overdex.model

/** A view of the canonical note, with its source number separate from the app's catalogue ID. */
data class FieldNoteJournalEntry(
    val speciesId: Int,
    val sourceSpeciesId: Int,
    val speciesName: String,
    val note: FieldNote,
)
