package com.example.overdex.battle.preview

import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.AnchorRegion
import org.junit.Assert.*
import org.junit.Test

class DirectoryCropCalibrationTest {
    private val calibration = BattleCalibration(
        playerSpeciesNameRegion = AnchorRegion(.01f, .02f, .21f, .03f),
        opponentSpeciesNameRegion = AnchorRegion(.71f, .03f, .22f, .04f),
        trainerHpRegion = AnchorRegion(.02f, .12f, .41f, .62f),
        opponentHpRegion = AnchorRegion(.54f, .13f, .43f, .63f),
        playerPokeBallsRegion = AnchorRegion(.03f, .08f, .15f, .05f),
        opponentPokeBallsRegion = AnchorRegion(.72f, .09f, .16f, .06f),
        announcementRegion = AnchorRegion(.04f, .24f, .92f, .07f),
        qteProgressTextRegion = AnchorRegion(.17f, .28f, .61f, .09f),
        qteTypeIconsRegion = AnchorRegion(.31f, .49f, .37f, .17f),
        exitIconRegion = AnchorRegion(.07f, .14f, .16f, .08f),
        opponentTeamInfoRegion = AnchorRegion(.57f, .06f, .39f, .09f),
        playerShieldsRegion = AnchorRegion(.02f, .11f, .09f, .02f),
    )

    @Test fun `seven LCD slots read their specific saved territories`() {
        assertEquals(listOf(calibration.playerSpeciesNameRegion, calibration.opponentSpeciesNameRegion,
            calibration.trainerHpRegion, calibration.opponentHpRegion, calibration.playerPokeBallsRegion,
            calibration.opponentPokeBallsRegion, calibration.announcementRegion),
            DirectoryCropSlot.entries.map { calibration.directoryCropRegion(it) })
    }

    @Test fun `each save changes exactly one territory and preserves the complete remaining profile`() {
        for (slot in DirectoryCropSlot.entries) {
            val original = calibration.directoryCropRegion(slot)
            val updated = original.copy(x = .05f, y = .15f)
            val expected = when (slot) {
                DirectoryCropSlot.PLAYER_SPECIES -> calibration.copy(playerSpeciesNameRegion = updated)
                DirectoryCropSlot.OPPONENT_SPECIES -> calibration.copy(opponentSpeciesNameRegion = updated)
                DirectoryCropSlot.PLAYER_HP -> calibration.copy(trainerHpRegion = updated)
                DirectoryCropSlot.OPPONENT_HP -> calibration.copy(opponentHpRegion = updated)
                DirectoryCropSlot.PLAYER_BALLS -> calibration.copy(playerPokeBallsRegion = updated)
                DirectoryCropSlot.OPPONENT_BALLS -> calibration.copy(opponentPokeBallsRegion = updated)
                DirectoryCropSlot.ANNOUNCEMENT -> calibration.copy(announcementRegion = updated)
            }
            val saved = calibration.withDirectoryCropRegion(slot, updated)
            assertEquals(slot.name, expected, saved)
            assertEquals(original.width, saved.directoryCropRegion(slot).width)
            assertEquals(original.height, saved.directoryCropRegion(slot).height)
            assertEquals(calibration.qteProgressTextRegion, saved.qteProgressTextRegion)
            assertEquals(calibration.qteTypeIconsRegion, saved.qteTypeIconsRegion)
            assertEquals(calibration.exitIconRegion, saved.exitIconRegion)
        }
    }

    @Test fun `position edits clamp inside the frame while keeping each original size`() {
        for (slot in DirectoryCropSlot.entries) {
            val original = calibration.directoryCropRegion(slot)
            val topLeft = calibration.withDirectoryCropRegion(slot, original.copy(x = -5f, y = -2f))
            assertEquals(original.copy(x = 0f, y = 0f), topLeft.directoryCropRegion(slot))
            val bottomRight = calibration.withDirectoryCropRegion(slot, original.copy(x = 8f, y = 3f))
            assertEquals(original.copy(x = 1f - original.width, y = 1f - original.height),
                bottomRight.directoryCropRegion(slot))
        }
    }

    @Test fun `resizing invalid geometry and nonfinite requests cannot overwrite a saved territory`() {
        for (slot in DirectoryCropSlot.entries) {
            val original = calibration.directoryCropRegion(slot)
            for (updated in listOf(original.copy(width = original.width / 2),
                original.copy(height = original.height / 2), original.copy(width = 0f),
                original.copy(height = -1f), original.copy(width = 2f), original.copy(x = Float.NaN),
                original.copy(y = Float.POSITIVE_INFINITY), original.copy(height = Float.NaN))) {
                assertSame(calibration, calibration.withDirectoryCropRegion(slot, updated))
            }
        }
    }

    @Test fun `pixel projection uses exclusive bounds and the source frame dimensions`() {
        val bounds = directoryCropPixelBounds(AnchorRegion(.101f, .202f, .205f, .307f), 100, 200)
        assertEquals(DirectoryCropPixelBounds(10, 40, 31, 102), bounds)
        assertEquals(21, bounds!!.width)
        assertEquals(62, bounds.height)
        assertEquals(DirectoryCropPixelBounds(0, 0, 1080, 2400),
            directoryCropPixelBounds(AnchorRegion(0f, 0f, 1f, 1f), 1080, 2400))
        assertEquals(DirectoryCropPixelBounds(99, 99, 100, 100),
            directoryCropPixelBounds(AnchorRegion(.99f, .99f, .01f, .01f), 100, 100))
    }

    @Test fun `pixel projection rejects missing dimensions empty pixels and out of source regions`() {
        val region = AnchorRegion(.1f, .2f, .3f, .4f)
        assertNull(directoryCropPixelBounds(region, 0, 100))
        assertNull(directoryCropPixelBounds(region, 100, -1))
        for (invalid in listOf(region.copy(x = -.1f), region.copy(y = .8f), region.copy(width = 0f),
            region.copy(x = Float.NaN), region.copy(height = Float.POSITIVE_INFINITY))) {
            assertNull(directoryCropPixelBounds(invalid, 1080, 2400))
        }
        assertNull(directoryCropPixelBounds(AnchorRegion(0f, 0f, .001f, .001f), 100, 100))
    }
}
