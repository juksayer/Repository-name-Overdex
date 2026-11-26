package com.example.overdex.ui.components

import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.AnchorRegion
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class BattleHudOverlayGeometryTest {
    @Test
    fun `matches measured opponent badge on reference display`() {
        assertEquals(645, BattleHudOverlayGeometry.panelLeftPx(1080))
        assertEquals(350, BattleHudOverlayGeometry.panelTopPx(2400))
        assertEquals(350, BattleHudOverlayGeometry.attachedPanelWindowTopPx(2400, 0))
        assertEquals(250, BattleHudOverlayGeometry.attachedPanelWindowTopPx(2400, 100))
        assertEquals(415, BattleHudOverlayGeometry.panelWidthPx(1080))
    }

    @Test
    fun `scales badge anchor and width together`() {
        assertEquals(1290, BattleHudOverlayGeometry.panelLeftPx(2160))
        assertEquals(700, BattleHudOverlayGeometry.panelTopPx(4800))
        assertEquals(700, BattleHudOverlayGeometry.attachedPanelWindowTopPx(4800, 0))
        assertEquals(500, BattleHudOverlayGeometry.attachedPanelWindowTopPx(4800, 200))
        assertEquals(830, BattleHudOverlayGeometry.panelWidthPx(2160))
    }

    @Test
    fun `badge seam is a default anchor rather than the editor ceiling`() {
        assertEquals(0, BattleHudOverlayGeometry.minimumEditableWindowTopPx(100))
        assertEquals(100, BattleHudOverlayGeometry.minimumEditableWindowTopPx(-100))
    }

    @Test fun `complementary corners preserve saved seam through repeated editing and reloads`() {
        assertEquals(27, BattleHudOverlayGeometry.jointDepthPx(1080))
        assertEquals(54, BattleHudOverlayGeometry.jointDepthPx(2160))
        for (width in listOf(720, 1080, 1440, 2160)) {
            var saved = 259 // An already adjusted phone profile; not the default anchor.
            repeat(10) {
                val windowY = BattleHudOverlayGeometry.windowTopForSavedSeam(saved, width)
                saved = BattleHudOverlayGeometry.savedSeamForWindowTop(windowY, width)
                assertEquals(259, saved)
            }
        }
    }

    @Test fun `reserved box clears both text strips for every list size`() {
        val announcement = com.example.overdex.model.AnchorRegion(0f, .18f, 1f, .10f)
        val grade = com.example.overdex.model.AnchorRegion(.12f, .23f, .76f, .13f)
        val calibration = BattleCalibration(announcementRegion = announcement, qteProgressTextRegion = grade)
        for (height in listOf(2280, 2400)) {
            val box = BattleHudOverlayGeometry.panelBox(1080, height, 645, 350, 415, 432, calibration, 3f)
            org.junit.Assert.assertTrue(box.top > .36f * height)
            assertEquals(432, box.height)
            assertEquals(645, box.left)
        }
        val custom = BattleHudOverlayGeometry.panelBox(1080, 2400, 780, 960, 280, 350, calibration, 3f)
        assertEquals(BattleHudOverlayGeometry.PanelBox(780, 960, 280, 350), custom)
    }

    @Test fun `Moondrop roster and move labels lift inside the badge-safe window`() {
        val width = 410f
        val (top, bottom) = BattleHudBadgeStyle.rosterPadding(width, 3f)
        assertEquals(BattleHudBadgeStyle.cornerPx(width) + BattleHudBadgeStyle.centerDipPx(width), top, .001f)
        assertEquals(3f, bottom, .001f)
    }

    @Test fun `Moondrop saved position cannot cover either native team badge or its counts`() {
        val calibration = BattleCalibration(
            playerTeamInfoRegion = AnchorRegion(.017857144f, .05760215f, .38934523f, .055234425f),
            opponentTeamInfoRegion = AnchorRegion(.59375f, .05760215f, .38339287f, .055234425f),
            opponentShieldsRegion = AnchorRegion(.6979167f, .08573342f, .08928572f, .02f),
            opponentPokeBallsRegion = AnchorRegion(.8125f, .08573342f, .15625f, .021433357f),
            announcementRegion = AnchorRegion(.07440476f, .21841592f, .85119045f, .032508373f),
            qteProgressTextRegion = AnchorRegion(.12f, .28499997f, .76f, .054999962f),
        )
        val requestedTop = BattleHudOverlayGeometry.windowTopForSavedSeam(259, 1080)
        for (x in listOf(643, 20)) {
            val box = BattleHudOverlayGeometry.panelBox(1080, 2400, x, requestedTop, 415, 432, calibration, 3f)
            if (x == 643) {
                assertTrue("dock top ${box.top}", box.top in 236..241)
                assertEquals(641, box.left)
                assertTrue(box.top + BattleHudBadgeStyle.cornerPx(box.width.toFloat()) > 258)
                assertTrue(box.height >= 147)
                assertTrue(box.top + box.height < calibration.announcementRegion.y * 2400)
            } else {
                // This sample also has independently calibrated rows on the player side.
                assertTrue(box.top > (calibration.playerTeamInfoRegion.y + calibration.playerTeamInfoRegion.height) * 2400)
            }
        }
        // Independently adjusted counter rows may extend outside the badge.
        for (moved in listOf(
            calibration.copy(opponentPokeBallsRegion = AnchorRegion(.82f, .40f, .14f, .025f)),
            calibration.copy(opponentShieldsRegion = AnchorRegion(.69f, .40f, .10f, .025f)),
        )) {
            val box = BattleHudOverlayGeometry.panelBox(1080, 2400, 643, 960, 415, 432, moved, 3f)
            assertTrue(box.top >= 1020 || box.top + box.height <= 960)
        }
    }
}
