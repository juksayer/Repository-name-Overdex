package com.example.overdex.ui.components

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
}
