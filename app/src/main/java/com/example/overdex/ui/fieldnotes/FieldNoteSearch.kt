package com.example.overdex.ui.fieldnotes

import com.example.overdex.model.FieldNoteJournalEntry

data class FieldNoteSearchResult(
    val entry: FieldNoteJournalEntry,
    val excerpt: String?,
    val excerptIsLesson: Boolean,
)

/** Keep catalogue order and original notes; an ID match never broadens into a partial ID search. */
fun searchFieldNotes(entries: List<FieldNoteJournalEntry>, query: String): List<FieldNoteSearchResult> {
    val literal = query.trim()
    if (literal.isEmpty()) return entries.map { FieldNoteSearchResult(it, it.note.lines.firstOrNull(), false) }
    val optionalId = literal.removePrefix("#")
    val speciesNumber = optionalId.takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' } }?.toIntOrNull()
    return entries.mapNotNull { entry ->
        val poemLine = entry.note.lines.firstOrNull { it.contains(literal, ignoreCase = true) }
        val lesson = entry.note.lesson?.takeIf { it.contains(literal, ignoreCase = true) }
        val stableIdMatches = entry.note.id.equals(literal, ignoreCase = true) ||
            (literal.startsWith('#') && entry.note.id.equals(optionalId, ignoreCase = true))
        val matches = speciesNumber == entry.speciesId || speciesNumber == entry.sourceSpeciesId || stableIdMatches ||
            entry.speciesName.contains(literal, ignoreCase = true) || entry.note.title.contains(literal, ignoreCase = true) ||
            poemLine != null || lesson != null
        if (!matches) return@mapNotNull null
        FieldNoteSearchResult(entry, poemLine ?: lesson ?: entry.note.lines.firstOrNull(),
            excerptIsLesson = poemLine == null && lesson != null)
    }
}

/** Literal, non-overlapping matches. Offsets address the unchanged String's UTF-16 code units. */
fun fieldNoteSearchRanges(text: String, query: String): List<IntRange> {
    val literal = query.trim()
    if (literal.isEmpty()) return emptyList()
    val ranges = mutableListOf<IntRange>()
    var from = 0
    while (from <= text.length - literal.length) {
        val at = text.indexOf(literal, startIndex = from, ignoreCase = true)
        if (at < 0) break
        ranges += at until at + literal.length
        from = at + literal.length
    }
    return ranges
}

/** Center a bounded preview on its first literal match without hiding a long matching phrase. */
fun fieldNoteSearchExcerpt(text: String, query: String, maxCharacters: Int = 110): String {
    val limit = maxCharacters.coerceAtLeast(1)
    if (text.length <= limit) return text
    val match = fieldNoteSearchRanges(text, query).firstOrNull()
    val contentBudget = (limit - 2).coerceAtLeast(1) // Reserve space for ellipses when both ends are cropped.
    val before = match?.let { minOf(24, (contentBudget - it.count()).coerceAtLeast(0)) } ?: 0
    var start = match?.let { (it.first - before).coerceAtLeast(0) } ?: 0
    var end = minOf(text.length, maxOf(start + contentBudget, match?.let { it.last + 1 } ?: 0))
    // Cropping must not leave half of an emoji or another supplementary character.
    if (start > 0 && text[start].isLowSurrogate() && text[start - 1].isHighSurrogate()) start--
    if (end < text.length && text[end].isLowSurrogate() && text[end - 1].isHighSurrogate()) end++
    return buildString {
        if (start > 0) append('…')
        append(text, start, end)
        if (end < text.length) append('…')
    }
}
