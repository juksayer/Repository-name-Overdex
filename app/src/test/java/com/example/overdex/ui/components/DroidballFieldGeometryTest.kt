package com.example.overdex.ui.components

import com.example.overdex.battle.observation.droidballFreeWindowLeft
import com.example.overdex.presentation.DroidballChoreography
import com.example.overdex.presentation.DroidballJourneyPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DroidballFieldGeometryTest {
    @Test fun `visible body matches the measured GO circle and edge inset across display widths`() {
        for ((screenWidth, canvasWidth) in listOf(720 to 94, 1080 to 141, 2160 to 282)) {
            assertEquals(canvasWidth, DroidballFieldGeometry.bodyCanvasWidthPx(screenWidth))
            val left = DroidballFieldGeometry.windowLeftPx(screenWidth, canvasWidth, 0f)
            val scale = screenWidth / 1080f
            // The renderer's outline spans 0.127..0.873 of its canvas; the
            // supplied screenshot places the reference circle at X=935..1040.
            assertEquals(935f * scale, left + canvasWidth * .127f, .6f)
            assertEquals(1040f * scale, left + canvasWidth * .873f, .6f)
        }
    }

    @Test fun `arrival starts off screen and skids two body diameters inward`() {
        val edge = DroidballFieldGeometry.windowLeftPx(1080, 141, 0f)
        val start = DroidballChoreography.pose(DroidballJourneyPhase.ARRIVING, 0).xBodies
        val startWindow = DroidballFieldGeometry.windowLeftPx(1080, 141, start)
        assertTrue(startWindow + 141 * .127f > 1080)
        assertEquals(203, edge - DroidballFieldGeometry.windowLeftPx(1080, 141, -2f))
    }

    @Test fun `travel scales with the body rather than device resolution`() {
        val first = DroidballFieldGeometry.windowLeftPx(1080, 141, -2f)
        val doubled = DroidballFieldGeometry.windowLeftPx(2160, 282, -2f)
        assertTrue(kotlin.math.abs(doubled - first * 2) <= 1)
    }

    @Test fun `travel padding preserves the canvas anchor and does not change roll distance`() {
        val plainEdge = DroidballFieldGeometry.windowLeftPx(1080, 141, 0f)
        val padding = DroidballFieldGeometry.travelPaddingPx(141)
        val paddedEdge = DroidballFieldGeometry.windowLeftPx(1080, 141, 0f, padding)
        val paddedSkid = DroidballFieldGeometry.windowLeftPx(1080, 141, -2f, padding)
        assertEquals(plainEdge, paddedEdge + padding)
        assertEquals(203, paddedEdge - paddedSkid)
    }

    @Test fun `reopening and collapsing a right dock never replaces it with panel left`() {
        val dock = DroidballFieldGeometry.dockedWindowLeftPx(1080, 141)
        repeat(10) {
            assertEquals(665, droidballFreeWindowLeft(1080, 415, 141, dock, true))
            // Android may still report the previous panel width during collapse.
            assertEquals(dock, droidballFreeWindowLeft(1080, 415, 141, dock, false))
            assertEquals(dock, droidballFreeWindowLeft(1080, 141, 141, dock, false))
        }
    }

    @Test fun `manually selecting left or right docks survives later expansion`() {
        for (right in listOf(false, true)) {
            val dock = DroidballFieldGeometry.dockedWindowLeftPx(1080, 141, right)
            val visibleEdge = if (right) dock + 141f * .873f else dock + 141f * .127f
            assertEquals(if (right) 1040f else 40f, visibleEdge, .6f)
            repeat(10) {
                assertEquals(if (right) 665 else 0,
                    droidballFreeWindowLeft(1080, 415, 141, dock, true))
                assertEquals(dock, droidballFreeWindowLeft(1080, 141, 141, dock, false))
            }
        }
    }

    @Test fun `collapse restores a chosen body position instead of resnapping it`() {
        assertEquals(120, droidballFreeWindowLeft(1080, 144, 144, 120, false))
        assertEquals(0, droidballFreeWindowLeft(1080, 415, 144, 120, true))
        assertEquals(120, droidballFreeWindowLeft(1080, 144, 144, 120, false))
    }
}
