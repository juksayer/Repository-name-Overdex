package com.example.overdex.ui.screens.observatory

import com.example.overdex.battle.archive.ArchivedMatchEnded
import com.example.overdex.battle.archive.ArchivedMatchRecordStarted
import com.example.overdex.battle.archive.ArchivedRealityArticle
import com.example.overdex.battle.archive.MatchArchive
import com.example.overdex.battle.archive.summarize
import org.junit.Assert.*
import org.junit.Test

class MatchHistoryModelTest {
    @Test fun `late export and recovery names do not reorder recorded matches`() {
        val older = item("older", 1_700_000_000_000, "2099-recovered.odxmatch.zip")
        val newer = item("newer", 1_800_000_000_000, "2000-completed.odxmatch.zip")
        val unreadable = MatchHistoryItem("broken", "latest-file.odxmatch.zip", null, "Missing timeline")
        val undated = MatchHistoryItem("undated", "undated.odxmatch.zip",
            MatchArchive(matchId = "undated", articles = emptyList()).summarize())

        assertEquals(listOf(newer, older, unreadable, undated),
            listOf(older, undated, unreadable, newer).newestMatchFirst())
    }

    @Test fun `refresh keeps selected match when newer match arrives`() {
        val older = item("older", 10)
        val newest = item("newest", 20)
        assertEquals("older", selectedMatchHistoryUri(listOf(newest, older), "older"))
        assertEquals("newest", selectedMatchHistoryUri(listOf(newest, older), null))
        assertEquals("newest", selectedMatchHistoryUri(listOf(newest, older), "removed"))
        assertNull(selectedMatchHistoryUri(emptyList(), "older"))
    }

    @Test fun `incomplete and unreadable archives remain distinct from observed result`() {
        val incomplete = item("interrupted", 10)
        val complete = item("completed", 20, result = "LOSS")
        val unreadable = MatchHistoryItem("broken", "broken.odxmatch.zip", null, "Missing manifest")
        assertEquals("INCOMPLETE · RESULT NOT RECORDED", incomplete.resultLabel)
        assertEquals("LOSS", complete.resultLabel)
        assertEquals("UNREADABLE RECORDING", unreadable.resultLabel)
    }

    @Test fun `active checkpoints stay outside saved match history`() {
        assertFalse(isSavedMatchHistoryArchive("active-running.odxmatch.zip"))
        assertFalse(isSavedMatchHistoryArchive("ACTIVE-old.odxmatch.zip"))
        assertTrue(isSavedMatchHistoryArchive("2026-11-05_completed.odxmatch.zip"))
        assertTrue(isSavedMatchHistoryArchive("2026-11-04_recovered.odxmatch.zip"))
    }

    @Test fun `duplicate packages keep richest match but never hide unreadable files`() {
        val original = item("same", 10).copy(sourceUri = "original")
        val richer = original.copy(sourceUri = "richer",
            summary = original.summary!!.copy(articleCount = original.summary.articleCount + 5))
        val tiedCopy = richer.copy(sourceUri = "z-recovered")
        val brokenOne = MatchHistoryItem("broken-one", "one.odxmatch.zip", null, "Missing manifest")
        val brokenTwo = MatchHistoryItem("broken-two", "two.odxmatch.zip", null, "Missing timeline")
        val distinct = listOf(tiedCopy, brokenOne, original, brokenTwo, richer).distinctRecordedMatches()

        assertEquals(setOf("richer", "broken-one", "broken-two"), distinct.map { it.sourceUri }.toSet())
        assertEquals("richer", selectedMatchHistoryUri(distinct, "original", "same"))
    }

    private fun item(id: String, at: Long, name: String = "$id.odxmatch.zip", result: String? = null): MatchHistoryItem {
        val first = ArchivedRealityArticle("start-$id", id, at, at + 50_000, "test", ArchivedMatchRecordStarted,
            emptyList(), null, null, null)
        val articles = listOfNotNull(first, result?.let {
            first.copy(articleId = "end-$id", perceivedAt = at + 10_000, payload = ArchivedMatchEnded(it))
        })
        return MatchHistoryItem(id, name, MatchArchive(matchId = id, articles = articles).summarize())
    }
}
