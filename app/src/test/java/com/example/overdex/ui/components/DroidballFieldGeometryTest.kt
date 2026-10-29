package com.example.overdex.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DroidballFieldGeometryTest {
    @Test fun `field rest attaches compact window to right edge`() {
        assertEquals(936, DroidballFieldGeometry.windowLeftPx(1080, 144, 0f))
        assertEquals(624, DroidballFieldGeometry.windowLeftPx(720, 96, 0f))
    }

    @Test fun `arrival starts off screen and skids two body diameters inward`() {
        val edge = DroidballFieldGeometry.windowLeftPx(1080, 144, 0f)
        assertTrue(DroidballFieldGeometry.windowLeftPx(1080, 144, 2.2f) > 1080)
        assertEquals(207, edge - DroidballFieldGeometry.windowLeftPx(1080, 144, -2f))
    }

    @Test fun `travel scales with the body rather than device resolution`() {
        val first = DroidballFieldGeometry.windowLeftPx(1080, 144, -2f)
        val doubled = DroidballFieldGeometry.windowLeftPx(2160, 288, -2f)
        assertTrue(kotlin.math.abs(doubled - first * 2) <= 1)
    }

    @Test fun `travel padding preserves the canvas anchor and does not change roll distance`() {
        val plainEdge = DroidballFieldGeometry.windowLeftPx(1080, 144, 0f)
        val paddedEdge = DroidballFieldGeometry.windowLeftPx(1080, 144, 0f, 48)
        val paddedSkid = DroidballFieldGeometry.windowLeftPx(1080, 144, -2f, 48)
        assertEquals(plainEdge, paddedEdge + 48)
        assertEquals(207, paddedEdge - paddedSkid)
    }
}
