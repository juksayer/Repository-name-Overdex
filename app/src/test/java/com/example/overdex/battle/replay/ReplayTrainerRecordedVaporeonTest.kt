package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.MatchArchiveSerializer
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

/** Optional regression over the trainer's preserved recording, never modified by this test. */
class ReplayTrainerRecordedVaporeonTest {
    @Test fun trainerStatementReplaysRecordedShieldThenFaint() {
        val path = System.getenv("OVERDEX_VAPOREON_ARCHIVE")
        assumeTrue(!path.isNullOrBlank())
        val file = File(requireNotNull(path))
        val hash = ReplayRetainedAudioRecovery.sha(file.readBytes())
        assertEquals("005e78ef51a4f272a89b7a555cb80241703b57770a21b9f3966f34efb797253f", hash)
        val archive = ZipFile(file).use { zip -> MatchArchiveSerializer.deserialize(
            zip.getInputStream(zip.getEntry("timeline.json")).bufferedReader().readText()) }
        val before = MatchArchiveSerializer.serialize(archive)
        val model = MatchReplayModel(archive)
        val start = requireNotNull(model.matchStartNanos)
        val hit = ReplayTrainerCorrection(UUID.randomUUID().toString(), archive.matchId, 86_430_000_000L,
            ReplayCorrectionKind.FAST_MOVE, ReplayCorrectionSide.PLAYER, "Turtonator", "Incinerate", authorName = "Trainer", createdAtMillis = 1)
        val faint = ReplayTrainerCorrection(UUID.randomUUID().toString(), archive.matchId, 96_000_000_000L,
            ReplayCorrectionKind.FAINT, ReplayCorrectionSide.OPPONENT, "Vaporeon", authorName = "Trainer", createdAtMillis = 1,
            finishingMoveId = hit.id)
        val projection = ReplayTrainerProjection(archive.matchId, start, listOf(hit, faint))
        val impact = projection.apply(model.sceneAt(start + hit.matchOffsetNanos), start + hit.matchOffsetNanos)
        assertTrue(impact.fastMoveActions.any { it.side == "PLAYER" && it.moveName == "Incinerate" && it.progress == 1f })
        assertFalse(impact.opponent!!.isFainted)
        val end = projection.apply(model.sceneAt(start + faint.matchOffsetNanos), start + faint.matchOffsetNanos)
        assertTrue(end.opponent!!.isFainted)
        assertEquals("Incinerate", end.opponent!!.trainerFinishingMove)
        assertEquals(before, MatchArchiveSerializer.serialize(archive))
        assertEquals(hash, ReplayRetainedAudioRecovery.sha(file.readBytes()))
    }
}
