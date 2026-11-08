package com.example.overdex.model

import com.example.overdex.data.DroidballDexEntry
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class PersonalFieldNote(
    val id: String,
    val speciesId: Int,
    val speciesName: String,
    val title: String,
    val lines: List<String>,
    val lesson: String = "",
    val author: String = "Unknown Author",
    val revision: Int = 1,
    val createdAt: Long,
    val updatedAt: Long,
    val shareable: Boolean = false,
    val originTrainerId: String,
    val received: Boolean = false,
) {
    fun toJournalEntry() = FieldNoteJournalEntry(
        speciesId = speciesId,
        sourceSpeciesId = if (speciesId == DroidballDexEntry.ID) DroidballDexEntry.FIELD_NOTE_SOURCE_ID else speciesId,
        speciesName = speciesName,
        note = FieldNote(id, title, lines, lesson.takeIf { it.isNotBlank() },
            author.ifBlank { "Unknown Author" }, verified = false, revision = revision),
    )
}

@Serializable
data class FieldNoteDraft(
    val id: String = UUID.randomUUID().toString(),
    val editingNoteId: String? = null,
    val speciesId: Int,
    val speciesName: String,
    val title: String = "",
    val lines: List<String> = listOf("", "", ""),
    val lesson: String = "",
    val author: String = "Unknown Author",
)

@Serializable
data class SyllableCorrection(
    val id: String,
    val entryId: String,
    val entryRevision: Int,
    val lineIndex: Int,
    val proposedCount: Int?,
    val pronunciation: String,
    val replacementLine: String,
    val createdAt: Long,
    val originalLine: String = "",
    val author: String = "Unknown Author",
    val originTrainerId: String = "",
)

@Serializable
data class PersonalFieldNotesState(
    val notes: List<PersonalFieldNote> = emptyList(),
    val draft: FieldNoteDraft? = null,
    val corrections: List<SyllableCorrection> = emptyList(),
)
