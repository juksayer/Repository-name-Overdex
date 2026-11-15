package com.example.overdex.battle.replay

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.UUID

class ReplayCorrectionTransferTest {
    @get:Rule val folder = TemporaryFolder()
    private fun entry(kind: ReplayCorrectionKind, side: ReplayCorrectionSide, time: Long, move: String?, link: String? = null) =
        ReplayTrainerCorrection(UUID.randomUUID().toString(), "source", time, kind, side,
            if (side == ReplayCorrectionSide.PLAYER) "Camerupt" else "Vaporeon", move,
            authorName = "Trainer", createdAtMillis = 1, finishingMoveId = link)

    @Test fun fileSurvivesCommsAndExplicitPerspectiveMapping() {
        val move = entry(ReplayCorrectionKind.FAST_MOVE, ReplayCorrectionSide.PLAYER, 10, "Incinerate")
        val faint = entry(ReplayCorrectionKind.FAINT, ReplayCorrectionSide.OPPONENT, 15, null, move.id)
        val file = ReplayCorrectionFile(sourceMatchId = "source", entries = listOf(move, faint))
        val bytes = ReplayCorrectionTransfer.encode(file)
        val received = ReplayCorrectionTransfer.fromMessage(ReplayCorrectionTransfer.toMessage(bytes))!!
        assertArrayEquals(bytes, received)
        val store = ReplayTrainerCorrectionStore(folder.newFolder())
        assertEquals(2, store.importFile(received, "other-phone", true))
        assertEquals(0, store.importFile(received, "other-phone", true))
        val imported = store.list("other-phone").associateBy { it.id }
        assertEquals(ReplayCorrectionSide.OPPONENT, imported[move.id]!!.side)
        assertEquals(ReplayCorrectionSide.PLAYER, imported[faint.id]!!.side)
        assertEquals("source", imported[move.id]!!.originMatchId)
        assertEquals(move.id, imported[faint.id]!!.finishingMoveId)
        assertEquals(10L, imported[move.id]!!.matchOffsetNanos)
    }

    @Test fun rejectsOversizedAndUnlinkedFiles() {
        val faint = entry(ReplayCorrectionKind.FAINT, ReplayCorrectionSide.OPPONENT, 10, null, UUID.randomUUID().toString())
        assertThrows(IllegalArgumentException::class.java) { ReplayCorrectionTransfer.encode(ReplayCorrectionFile(sourceMatchId = "source", entries = listOf(faint))) }
        assertThrows(IllegalArgumentException::class.java) { ReplayCorrectionTransfer.decode(ByteArray(2901)) }
        assertNull(ReplayCorrectionTransfer.fromMessage("ordinary chat"))
    }
}
