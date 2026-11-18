package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import javax.imageio.ImageIO

/** Tests the reviewed projection separately from automatic attribution of the untouched ZIP. */
class RecordedDragoniteFinishingReviewTest {
    @Test fun recoveredRockTombAndReviewedFaintRenderAsOneFinish() {
        val directory = System.getenv("OVERDEX_DRAGONITE_FINISH_REVIEW")
        assumeTrue(!directory.isNullOrBlank())
        val root = File(requireNotNull(directory))
        val input = File(root, "inputs").listFiles()!!.single { it.extension == "zip" }
        val before = ReplayRetainedAudioRecovery.sha(input.readBytes())
        assertEquals("1a896bc59d99d136c569f2127b568e22a6a7efb8f27e3aae238d19aa9d5761a3", before)
        val archive = ZipFile(input).use { zip ->
            MatchArchiveSerializer.deserialize(zip.getInputStream(zip.getEntry("timeline.json")).bufferedReader().readText())
        }
        val original = MatchArchiveSerializer.serialize(archive)
        val sourceFrameHash = "1859e8f3b5120d2f15669b5776d3572c1c846658cdc6441c6fc1446bc1720a58"
        val sourceFrame = File(root, "dragonite/loose-frames/$sourceFrameHash.png")
        assertEquals(sourceFrameHash, ReplayRetainedAudioRecovery.sha(sourceFrame.readBytes()))
        val announced = archive.article("8c4a0246-fa1f-4eb3-be06-54d1a118193b")
        val retained = announced.payload as ArchivedCropCaptured
        val full = ImageIO.read(sourceFrame)
        ZipFile(input).use { zip ->
            val crop = ImageIO.read(zip.getInputStream(zip.getEntry(retained.artifactPath)))
            val w = retained.cropRight - retained.cropLeft
            val h = retained.cropBottom - retained.cropTop
            assertEquals(w, crop.width)
            assertEquals(h, crop.height)
            // Verify the reconstructed announcement's source time from identical pixels.
            assertArrayEquals(crop.getRGB(0, 0, w, h, null, 0, w),
                full.getRGB(retained.cropLeft, retained.cropTop, w, h, null, 0, w))
        }
        val moveAt = requireNotNull(announced.monotonicTimeNanos)
        val impact = archive.article("d57cf089-d966-404c-9413-b793302a84ea")
        val cleared = archive.article("f11360d9-99e1-4d15-96f0-ece3b8ccd9a7")
        val landing = requireNotNull(impact.monotonicTimeNanos)
        val faintAt = requireNotNull(cleared.monotonicTimeNanos)
        assertTrue(moveAt < landing && landing < faintAt)

        val baseline = MatchReplayModel(archive)
        assertTrue(baseline.finishingAttributions.none { it.recipientSpeciesName == "Dragonite" })
        assertTrue(baseline.sceneAt(landing).chargedMoveActions.none { it.moveName == "Rock Tomb" })
        val start = requireNotNull(baseline.matchStartNanos)
        assertEquals(102_916_409_002L, moveAt - start)

        // Review observations established from the full-frame badge and banner,
        // the retained impact/clear images, the matched cry, and the native win.
        // Apply them through the existing review layer, outside the source archive.
        val model = MatchReplayModel(archive, archivedCropIdentities = listOf(
            ReplayIdentityObservation("PLAYER", "Camerupt", 323, moveAt, "REVIEWED SOURCE FRAME",
                articleId = announced.articleId, evidenceArticleIds = listOf(announced.articleId))))
        val move = ReplayTrainerCorrection(UUID.nameUUIDFromBytes("dragonite-reviewed-move".toByteArray()).toString(),
            archive.matchId, landing - start, ReplayCorrectionKind.CHARGED_MOVE, ReplayCorrectionSide.PLAYER,
            "Camerupt", "Rock Tomb", note = "Source frame $sourceFrameHash. Presentation landing from first orange impact.",
            authorName = "Recorded evidence review", createdAtMillis = announced.perceivedAt)
        val faint = ReplayTrainerCorrection(UUID.nameUUIDFromBytes("dragonite-reviewed-faint".toByteArray()).toString(),
            archive.matchId, faintAt - start, ReplayCorrectionKind.FAINT, ReplayCorrectionSide.OPPONENT,
            "Dragonite", note = "Orange HP, independently matched cry, departure burst and win.",
            authorName = "Recorded evidence review", createdAtMillis = cleared.perceivedAt,
            finishingMoveId = move.id)
        val projection = ReplayTrainerProjection(archive.matchId, start, listOf(move, faint),
            mapOf("Camerupt" to listOf(Move("Rock Tomb", PokemonType.ROCK, 0, 0, isFast = false))))
        val landingScene = projection.apply(model.sceneAt(landing), landing)
        assertEquals("Camerupt", landingScene.player?.speciesName)
        assertEquals("Dragonite", landingScene.opponent?.speciesName)
        val projectile = landingScene.chargedMoveActions.single { it.moveName == "Rock Tomb" }
        assertEquals(1f, ReplayChargePose.at(projectile, true).projectileProgress, 0f)
        val faintScene = projection.apply(model.sceneAt(faintAt), faintAt)
        assertEquals("Dragonite", faintScene.opponent?.speciesName)
        assertTrue(faintScene.opponent!!.isFainted)
        assertEquals(move, projection.creditedMove(faint))
        assertEquals("Rock Tomb", faintScene.opponent.trainerFinishingMove)
        assertFalse(landingScene.opponent!!.isFainted)
        assertEquals(landingScene, projection.apply(model.sceneAt(landing), landing))

        assertEquals(original, MatchArchiveSerializer.serialize(archive))
        assertEquals(before, ReplayRetainedAudioRecovery.sha(input.readBytes()))
        assertEquals(sourceFrameHash, ReplayRetainedAudioRecovery.sha(sourceFrame.readBytes()))
    }

    private fun MatchArchive.article(id: String) = articles.single { it.articleId == id }
}
