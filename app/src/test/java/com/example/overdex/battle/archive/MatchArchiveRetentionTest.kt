package com.example.overdex.battle.archive

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class MatchArchiveRetentionTest {
    @Test fun `retains newest 50 distinct matches by recorded time and preserves original bytes`() = inDirectory { dir ->
        val files = (1..52).map { write(dir, "record-$it.odxmatch.zip", "match-$it", it.toLong()) }
        files.first().setLastModified(System.currentTimeMillis() + 10000)
        val newest = files.takeLast(50).associateWith { it.readBytes() }
        val savedRecords = mutableListOf<String>()
        val result = MatchArchiveRetention(dir).trim(beforeDelete = { savedRecords += it.name })
        assertEquals(setOf("record-1.odxmatch.zip", "record-2.odxmatch.zip"), result.deletedNames.toSet())
        assertEquals(result.deletedNames, savedRecords)
        assertEquals(50, dir.listFiles()!!.size)
        newest.forEach { (file, original) -> assertArrayEquals(original, file.readBytes()) }
    }

    @Test fun `export copies of one match do not consume extra slots`() = inDirectory { dir ->
        write(dir, "old.odxmatch.zip", "old", 1)
        write(dir, "a-full.odxmatch.zip", "a", 2)
        write(dir, "a-compact.odxmatch.zip", "a", 2)
        write(dir, "b.odxmatch.zip", "b", 3)
        val result = MatchArchiveRetention(dir).trim(keepMatches = 2)
        assertEquals(listOf("old.odxmatch.zip"), result.deletedNames)
        assertEquals(3, dir.listFiles()!!.size)
    }

    @Test fun `active checkpoints corrupt files and files outside archive root are protected`() = inDirectory { dir ->
        write(dir, "old.odxmatch.zip", "old", 1)
        write(dir, "new.odxmatch.zip", "new", 2)
        val checkpoint = write(dir, "active-older.odxmatch.zip", "active", 0)
        val journal = File(dir, "active-journal.odxjournal").apply { writeText("working") }
        val corrupt = File(dir, "unreadable.odxmatch.zip").apply { writeText("broken") }
        val staged = File(dir, ".new.odxmatch.zip.tmp").apply { writeText("saving") }
        val result = MatchArchiveRetention(dir).trim(keepMatches = 1)
        assertEquals(listOf("old.odxmatch.zip"), result.deletedNames)
        assertTrue(result.failures.containsKey(corrupt.name))
        listOf(checkpoint, journal, corrupt, staged).forEach { assertTrue(it.exists()) }
    }

    @Test fun `failed statistics preservation and protected match prevent deletion`() = inDirectory { dir ->
        write(dir, "old.odxmatch.zip", "old", 1)
        write(dir, "protected.odxmatch.zip", "protected", 2)
        write(dir, "new.odxmatch.zip", "new", 3)
        val result = MatchArchiveRetention(dir).trim(keepMatches = 1, protectedMatchIds = setOf("protected")) {
            error("Statistics could not be saved")
        }
        assertTrue(result.deletedNames.isEmpty())
        assertEquals(setOf("old.odxmatch.zip"), result.failures.keys)
        assertEquals(3, dir.listFiles()!!.size)
    }

    @Test fun `adding a recording retires one more and refreshing a record does not change chronology`() = inDirectory { dir ->
        write(dir, "a.odxmatch.zip", "a", 1)
        write(dir, "b.odxmatch.zip", "b", 2)
        val retention = MatchArchiveRetention(dir)
        assertTrue(retention.trim(2).deletedNames.isEmpty())
        write(dir, "c.odxmatch.zip", "c", 3)
        assertEquals(listOf("a.odxmatch.zip"), retention.trim(2).deletedNames)
        write(dir, "b.odxmatch.zip", "b", 2, "later OCR")
        write(dir, "d.odxmatch.zip", "d", 4)
        assertEquals(listOf("b.odxmatch.zip"), retention.trim(2).deletedNames)
    }

    private fun write(dir: File, name: String, id: String, time: Long, text: String = "reading"): File =
        File(dir, name).also { file ->
            val archive = MatchArchive(matchId = id, articles = listOf(
                ArchivedRealityArticle("article-$id", id, time, time, "witness", ArchivedRawText(text), emptyList(), null, null, null)
            ))
            file.outputStream().use { MatchArchivePackageWriter.write(archive, it) }
        }

    private fun inDirectory(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("match-retention-").toFile()
        try { block(dir) } finally { dir.deleteRecursively() }
    }
}
