package com.example.overdex.battle.replay

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

class ReplayTrainerCorrectionStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private fun correction(match: String = "match-a") = ReplayTrainerCorrection(
        id = UUID.randomUUID().toString(), matchId = match, matchOffsetNanos = 10,
        kind = ReplayCorrectionKind.FAST_MOVE, side = ReplayCorrectionSide.PLAYER,
        speciesName = "Camerupt", moveName = "Incinerate", authorName = "Trainer",
        createdAtMillis = 42,
    )

    @Test fun statementsAreIndependentAndDoNotModifyArchive() {
        val root = folder.newFolder()
        val archive = File(root, "original.odxmatch.zip").apply { writeText("original evidence") }
        val first = correction()
        val second = correction()
        val other = correction("match-b")
        val store = ReplayTrainerCorrectionStore(root)
        listOf(first, second, other).forEach(store::add)
        assertEquals(setOf(first, second), store.list("match-a").toSet())
        assertEquals(listOf(other), store.list("match-b"))
        assertEquals("original evidence", archive.readText())
        assertThrows(IllegalStateException::class.java) { store.add(first) }
    }

    @Test fun invalidRecordsAreRejected() {
        val store = ReplayTrainerCorrectionStore(folder.newFolder())
        val item = correction()
        assertThrows(IllegalArgumentException::class.java) { store.add(item.copy(matchOffsetNanos = -1)) }
        assertThrows(IllegalArgumentException::class.java) { store.add(item.copy(moveName = null)) }
        assertThrows(IllegalArgumentException::class.java) { store.add(item.copy(id = "../invalid")) }
        assertThrows(IllegalArgumentException::class.java) { store.add(item.copy(kind = ReplayCorrectionKind.FAINT)) }
    }
}
