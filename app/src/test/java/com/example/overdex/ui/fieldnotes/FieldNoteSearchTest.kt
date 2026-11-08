package com.example.overdex.ui.fieldnotes

import com.example.overdex.model.FieldNote
import com.example.overdex.model.FieldNoteJournalEntry
import org.junit.Assert.*
import org.junit.Test

class FieldNoteSearchTest {
    @Test fun `lesson only matches expose the original matching lesson and handle absent lessons`() {
        val lesson = "Remain a LIGHT when the harbor grows quiet."
        val target = entry("181-002", "Ampharos", listOf("A steady lantern", "Waits beside the sea"), lesson)
        val withoutLesson = entry("001-001", "Bulbasaur", listOf("A quiet seed"))
        val result = searchFieldNotes(listOf(withoutLesson, target), "  light  ").single()
        assertSame(target, result.entry)
        assertEquals(lesson, result.excerpt)
        assertTrue(result.excerptIsLesson)
        assertEquals(listOf(9..13), fieldNoteSearchRanges(requireNotNull(result.excerpt), "light"))
        assertTrue(searchFieldNotes(listOf(withoutLesson), "light").isEmpty())
    }

    @Test fun `first matching poem line takes precedence and metadata only matches fall back to the first line`() {
        val target = entry("181-001", "Ampharos", listOf("A steady lantern", "LIGHT over water", "Another light"),
            "Light is a lesson.", title = "The Lighthouse")
        val byPoem = searchFieldNotes(listOf(target), "light").single()
        assertEquals("LIGHT over water", byPoem.excerpt)
        assertFalse(byPoem.excerptIsLesson)
        val byName = searchFieldNotes(listOf(target), "ampharos").single()
        assertEquals("A steady lantern", byName.excerpt)
        assertFalse(byName.excerptIsLesson)
        val byTitle = searchFieldNotes(listOf(target), "lighthouse").single()
        assertEquals("A steady lantern", byTitle.excerpt)
    }

    @Test fun `stable sibling note IDs and both Droidball numbers are exact without changing source order`() {
        val second = entry("181-002", "Ampharos", listOf("The same first line"))
        val first = entry("181-001", "Ampharos", listOf("The same first line"))
        val mascot = entry("1026-004", "Droidball", listOf("A little light"), speciesId = 10001, sourceId = 1026)
        val entries = listOf(second, mascot, first)
        assertEquals(listOf(second), searchFieldNotes(entries, "#181-002").map { it.entry })
        assertEquals(listOf(first), searchFieldNotes(entries, "181-001").map { it.entry })
        assertTrue(searchFieldNotes(entries, "181-00").isEmpty())
        assertEquals(listOf(second, first), searchFieldNotes(entries, "#0181").map { it.entry })
        assertEquals(listOf(mascot), searchFieldNotes(entries, "1026").map { it.entry })
        assertEquals(listOf(mascot), searchFieldNotes(entries, "#10001").map { it.entry })
        assertTrue("Numeric species search must not match partial numbers", searchFieldNotes(entries, "18").isEmpty())
        assertSame(second.note, searchFieldNotes(entries, "#181-002").single().entry.note)
    }

    @Test fun `blank and filtered searches retain the supplied order and leave complete excerpts untouched`() {
        val longLine = "A quiet seed " + "keeps growing ".repeat(20)
        val later = entry("003-001", "Venusaur", listOf(longLine), "A lesson about roots")
        val earlier = entry("001-001", "Bulbasaur", emptyList(), "Another roots lesson")
        val other = entry("002-001", "Ivysaur", listOf("A different line"))
        val entries = listOf(later, other, earlier)
        val all = searchFieldNotes(entries, " \t ")
        assertEquals(entries, all.map { it.entry })
        assertEquals(longLine, all.first().excerpt)
        assertNull(all.last().excerpt)
        assertTrue(all.none { it.excerptIsLesson })
        assertEquals(listOf(later, earlier), searchFieldNotes(entries, "roots").map { it.entry })
        assertEquals(longLine, searchFieldNotes(entries, "growing").single().excerpt)
    }

    @Test fun `literal punctuation and hashtags never become patterns or unrelated text searches`() {
        val plain = entry("001-001", "Bulbasaur", listOf("Fire and a pattern"))
        val literal = entry("002-001", "Ivysaur", listOf("Keep #fire beside [a+b].* and [A+B].*"))
        assertEquals(listOf(literal), searchFieldNotes(listOf(plain, literal), "#fire").map { it.entry })
        assertEquals(listOf(literal), searchFieldNotes(listOf(plain, literal), "[a+b].*").map { it.entry })
        val text = requireNotNull(searchFieldNotes(listOf(literal), "[a+b].*").single().excerpt)
        assertEquals(listOf("[a+b].*", "[A+B].*"), fieldNoteSearchRanges(text, "[a+b].*").map { text.substring(it) })
        assertTrue(fieldNoteSearchRanges("fire", "#fire").isEmpty())
    }

    @Test fun `highlight offsets address original UTF16 text and repeated matches do not overlap`() {
        assertEquals(listOf(3..7, 9..13), fieldNoteSearchRanges("🐉 Ember ember", "  EMber  "))
        assertEquals(listOf(0..1, 2..3), fieldNoteSearchRanges("aaaaa", "aa"))
        assertTrue(fieldNoteSearchRanges("Anything", " ").isEmpty())
        assertTrue(fieldNoteSearchRanges("Short", "A longer query").isEmpty())
    }

    @Test fun `long lesson previews keep a match near the end in view rather than showing only the beginning`() {
        val lesson = "An opening that cannot answer the search. " + "Quiet water. ".repeat(35) +
            "The patient lighthouse keeps its promise."
        val result = searchFieldNotes(listOf(entry("181-002", "Ampharos", listOf("A steady light"), lesson)), "lighthouse").single()
        assertEquals("The search result retains the full canonical lesson", lesson, result.excerpt)
        val excerpt = fieldNoteSearchExcerpt(requireNotNull(result.excerpt), "lighthouse", maxCharacters = 80)
        assertTrue(excerpt.startsWith("…"))
        assertFalse(excerpt.endsWith("…"))
        assertTrue(excerpt.contains("lighthouse"))
        assertTrue(excerpt.length <= 80)
        assertTrue("The match should be visible near the start of the preview",
            fieldNoteSearchRanges(excerpt, "lighthouse").single().first <= 25)
    }

    @Test fun `preview preserves whole long matches and only adds ellipses where text was cropped`() {
        assertEquals("Short text", fieldNoteSearchExcerpt("Short text", "short", 20))
        val phrase = "A literal phrase ".repeat(9)
        val text = "Before context. $phrase After context."
        val excerpt = fieldNoteSearchExcerpt(text, phrase, maxCharacters = 40)
        assertTrue(excerpt.contains(phrase.trim()))
        assertTrue(excerpt.startsWith("…"))
        assertTrue(excerpt.endsWith("…"))
        val beginning = fieldNoteSearchExcerpt("Match " + "after ".repeat(30), "match", 45)
        assertFalse(beginning.startsWith("…"))
        assertTrue(beginning.endsWith("…"))
    }

    private fun entry(id: String, name: String, lines: List<String>, lesson: String? = null,
        title: String = "Field Note", speciesId: Int = id.substringBefore('-').toInt(), sourceId: Int = speciesId) =
        FieldNoteJournalEntry(speciesId, sourceId, name, FieldNote(id, title, lines, lesson, "Unknown Author"))
}
