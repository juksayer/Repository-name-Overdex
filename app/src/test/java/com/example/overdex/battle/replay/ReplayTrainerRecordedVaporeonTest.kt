package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.MatchArchiveSerializer
import com.example.overdex.battle.archive.ArchivedCropCaptured
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
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
        // The two original native badge crops at 85.412 s were independently
        // reviewed. A late OCR completion time cannot confirm either appearance.
        fun badge(hash: String, side: String, name: String, id: Int): ReplayIdentityObservation {
            val row = archive.articles.single { (it.payload as? ArchivedCropCaptured)?.sha256 == hash }
            return ReplayIdentityObservation(side, name, id, row.monotonicTimeNanos!!,
                "RECONSTRUCTED FROM ARCHIVED SPECIES CROP", sourceId = row.sourceId,
                articleId = row.articleId, ocrMatchBasis = "EXACT_CATALOGUE_TEXT")
        }
        val model = MatchReplayModel(archive, archivedCropIdentities = listOf(
            badge("86dee6ba678c8de408b17e6e96733772f6bf94e1e6ded13d14d2e71da8cb3447", "PLAYER", "Turtonator", 776),
            badge("4262c2c6be569dc9b6b1d5296c87860a38337544a6656b3a0594df616c6fcf6d", "OPPONENT", "Vaporeon", 134)
        ), chargedMovesBySpeciesName = mapOf("Vaporeon" to listOf(Move("Water Pulse", PokemonType.WATER, 0, 0, false))))
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
        val blockedAt = archive.articles.first { it.sourceId == "MATCH_OUTCOME_TEXT_WITNESS" &&
            (it.payload as? com.example.overdex.battle.archive.ArchivedRawText)?.value == "BLOCKED!" }.monotonicTimeNanos!!
        val block = model.sceneAt(blockedAt)
        val water = block.chargedMoveActions.single { it.hitsProtectShield }
        assertEquals("Water Pulse", water.moveName)
        assertEquals("OPPONENT", water.side)
        assertNull(water.finishingAssociation)
        assertEquals(1f, ReplayChargePose.at(water, false).projectileProgress, 0f)
        assertEquals(ReplayProtectShield("PLAYER", 0), block.protectShield)
        assertEquals("Blocked!", block.announcement)
        assertEquals(block, model.sceneAt(blockedAt))
        assertNull(impact.protectShield)
        val end = projection.apply(model.sceneAt(start + faint.matchOffsetNanos), start + faint.matchOffsetNanos)
        assertTrue(end.opponent!!.isFainted)
        assertEquals("Incinerate", end.opponent!!.trainerFinishingMove)
        assertEquals(before, MatchArchiveSerializer.serialize(archive))
        assertEquals(hash, ReplayRetainedAudioRecovery.sha(file.readBytes()))
    }
}
