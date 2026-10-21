package com.example.overdex.battle.archive

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Real fsync, process-style reopen and archive packaging, isolated from the user's saved Matches. */
class CheckpointRecoveryDeviceTest {
    @Test fun incrementalRecoveryHandlesTornTailAndLegacyZipWithoutChangingTestimony() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(app.cacheDir, "checkpoint-recovery-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences("checkpoint-test-$name", mode)
        }
        try {
            val manager = ArchiveDirectoryManager(context)
            val id = MatchId("recovery-test")
            val timeline = InMemoryRealityTimeline()
            fun append(index: Int) = timeline.append(RealityArticle(ArticleId("a-$index"), 1_000L + index,
                1_500L + index, SourceId("FIXTURE"), RawTestimony("reading $index"), emptyList(),
                matchId = id, monotonicTimeNanos = 1_000_000L + index))
            repeat(200, ::append)
            assertEquals(200, manager.exportMatchCheckpoint(id, timeline))
            (200..499).forEach(::append)
            assertEquals(300, manager.exportMatchCheckpoint(id, timeline))
            assertEquals(0, manager.exportMatchCheckpoint(id, timeline))
            val journal = manager.interruptedCheckpointFiles().single()
            val complete = journal.readBytes()
            val expected = MatchArchiveSource(id, timeline).snapshot()
            val recovery = ArchiveDirectoryManager(context).recoverInterruptedMatchCheckpoints()
            assertTrue(recovery.failures.toString(), recovery.failures.isEmpty())
            assertEquals(1, recovery.recoveredNames.size)
            val recoveredFile = File(context.filesDir, "match_archives/${recovery.recoveredNames.single()}")
            assertEquals(expected, recoveredFile.inputStream().use { MatchArchivePackageReader.read(it) })
            assertFalse(journal.exists())

            // Simulate a crash immediately after publication but before journal removal.
            journal.writeBytes(complete)
            assertEquals(recovery.recoveredNames, ArchiveDirectoryManager(context).recoverInterruptedMatchCheckpoints().recoveredNames)
            assertFalse(journal.exists())

            // A torn final write is preserved for diagnosis; the committed prefix is recovered.
            journal.writeBytes(complete + byteArrayOf(0x4f, 0x44, 0x58))
            val torn = ArchiveDirectoryManager(context).recoverInterruptedMatchCheckpoints()
            assertTrue(torn.failures.toString(), torn.failures.isEmpty())
            assertTrue(File(journal.path + ".recovered").exists())
            assertEquals(expected, recoveredFile.inputStream().use { MatchArchivePackageReader.read(it) })

            val legacy = File(context.filesDir, "match_archives/active-legacy.odxmatch.zip")
            legacy.outputStream().use { MatchArchivePackageWriter.write(expected, it) }
            assertEquals(1, ArchiveDirectoryManager(context).recoverInterruptedMatchCheckpoints().recoveredNames.size)
            assertFalse(legacy.exists())

            // Corruption cannot silently turn into a partial successful Match.
            journal.writeBytes(complete.also { it[30] = (it[30].toInt() xor 1).toByte() })
            val broken = journal.readBytes()
            assertEquals(1, ArchiveDirectoryManager(context).recoverInterruptedMatchCheckpoints().failures.size)
            assertArrayEquals(broken, journal.readBytes())
        } finally { root.deleteRecursively() }
    }
}
