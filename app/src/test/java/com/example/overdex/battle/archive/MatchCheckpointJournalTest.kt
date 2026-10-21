package com.example.overdex.battle.archive

import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.*
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class MatchCheckpointJournalTest {
    private val id = MatchId("journal-match")
    private fun article(index: Int, match: MatchId? = id) = RealityArticle(
        ArticleId("article-$index"), 100L + index, 150L + index,
        SourceId("RAW"), RawTestimony("reading-$index"), emptyList(), matchId = match,
        monotonicTimeNanos = 1_000_000L + index)

    private fun withFile(test: (File) -> Unit) {
        val dir = Files.createTempDirectory("checkpoint-test").toFile()
        try { test(File(dir, "active.odxjournal")) } finally { dir.deleteRecursively() }
    }

    @Test fun `appends only the new tail without changing the durable prefix`() = withFile { file ->
        val timeline = InMemoryRealityTimeline()
        timeline.append(article(1))
        val journal = MatchCheckpointJournal(file, id)
        assertEquals(1, journal.append(timeline))
        val prefix = file.readBytes()
        assertEquals(0, journal.append(timeline))
        assertArrayEquals(prefix, file.readBytes())
        timeline.append(article(2))
        assertEquals(1, journal.append(timeline))
        assertArrayEquals(prefix, file.readBytes().take(prefix.size).toByteArray())
        val recovered = MatchCheckpointJournal.read(file)
        assertFalse(recovered.incompleteTail)
        assertEquals(timeline.getArticles().map(RealityArticleArchiveMapper::map), recovered.archive.articles)
    }

    @Test fun `each torn final append recovers the preceding committed batch`() = withFile { file ->
        val timeline = InMemoryRealityTimeline().apply { append(article(1)) }
        val journal = MatchCheckpointJournal(file, id)
        journal.append(timeline)
        val firstLength = file.length().toInt()
        timeline.append(article(2)); journal.append(timeline)
        val complete = file.readBytes()
        for (end in firstLength + 1 until complete.size) {
            file.writeBytes(complete.copyOf(end))
            val recovered = MatchCheckpointJournal.read(file)
            assertEquals(firstLength.toLong(), recovered.validLength)
            assertTrue(recovered.incompleteTail)
            assertEquals(listOf("article-1"), recovered.archive.articles.map { it.articleId })
        }
        // Restarting the writer repairs a partial tail, then resumes without duplicating it.
        assertEquals(1, MatchCheckpointJournal(file, id).append(timeline))
        assertEquals(2, MatchCheckpointJournal.read(file).archive.articles.size)
    }

    @Test fun `complete corruption is rejected and original bytes stay untouched`() = withFile { file ->
        MatchCheckpointJournal(file, id).append(InMemoryRealityTimeline().apply { append(article(1)) })
        val damaged = file.readBytes().also { it[20] = (it[20].toInt() xor 1).toByte() }
        file.writeBytes(damaged)
        assertTrue(runCatching { MatchCheckpointJournal.read(file) }.isFailure)
        assertArrayEquals(damaged, file.readBytes())
    }

    @Test fun `session and other match articles are excluded without losing the source cursor`() = withFile { file ->
        val timeline = InMemoryRealityTimeline().apply {
            append(article(0, null)); append(article(1)); append(article(2, MatchId("other")))
        }
        assertEquals(1, MatchCheckpointJournal(file, id).append(timeline))
        timeline.append(article(3))
        assertEquals(1, MatchCheckpointJournal(file, id).append(timeline))
        assertEquals(listOf("article-1", "article-3"), MatchCheckpointJournal.read(file).archive.articles.map { it.articleId })
        assertTrue(runCatching { MatchCheckpointJournal(file, MatchId("other")).append(timeline) }.isFailure)
    }
}
