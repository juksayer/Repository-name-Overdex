package com.example.overdex.model.navigation

import org.junit.Assert.*
import org.junit.Test

class DesktopPointerControllerTest {
    private fun pointer() = DesktopPointerController().apply {
        beginSurface("desktop")
        setViewport(1_000f, 800f)
        setEnabled(true)
    }

    @Test fun `pointer input is inactive until explicitly enabled`() {
        val pointer = DesktopPointerController()
        pointer.setViewport(1_000f, 800f)
        pointer.setTarget("center", 400f, 300f, 600f, 500f)
        assertFalse(pointer.moveBy(50f, 50f, 100f, 100f))
        assertNull(pointer.tap())
        assertEquals(0.5f, pointer.x, 0f)
        assertTrue(pointer.toggle())
        assertEquals("center", pointer.tap())
        assertFalse(pointer.toggle())
        assertNull(pointer.tap())
        assertEquals(0L, pointer.motionRevision)
    }

    @Test fun `relative motion maps LCD fractions independently on both axes`() {
        val pointer = pointer()
        assertTrue(pointer.moveBy(50f, -25f, 200f, 100f))
        assertEquals(0.75f, pointer.x, 0f)
        assertEquals(0.25f, pointer.y, 0f)
        assertEquals(750f, pointer.pixelX, 0f)
        assertEquals(200f, pointer.pixelY, 0f)
        assertEquals(1L, pointer.motionRevision)
        // A second gesture starts from the existing pointer, not a finger's down position.
        assertTrue(pointer.moveBy(-25f, 10f, 100f, 100f))
        assertEquals(0.5f, pointer.x, 0.0001f)
        assertEquals(0.35f, pointer.y, 0.0001f)
    }

    @Test fun `the pointer clamps at each edge and keeps outer-edge targets clickable`() {
        val pointer = pointer()
        pointer.setTarget("corner", 900f, 700f, 1_000f, 800f)
        assertTrue(pointer.moveBy(1_000f, 1_000f, 100f, 100f))
        assertEquals(1f, pointer.x, 0f)
        assertEquals(1f, pointer.y, 0f)
        assertEquals("corner", pointer.tap())
        assertFalse(pointer.moveBy(10f, 10f, 100f, 100f))
        assertEquals(1L, pointer.motionRevision)
        assertTrue(pointer.moveBy(-1_000f, -1_000f, 100f, 100f))
        assertEquals(0f, pointer.x, 0f)
        assertEquals(0f, pointer.y, 0f)
    }

    @Test fun `invalid gesture dimensions and nonfinite deltas cannot corrupt coordinates`() {
        val pointer = pointer()
        val invalid = listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)
        for (value in invalid) {
            assertFalse(pointer.moveBy(1f, 1f, value, 100f))
            assertFalse(pointer.moveBy(1f, 1f, 100f, value))
        }
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertFalse(pointer.moveBy(value, 1f, 100f, 100f))
            assertFalse(pointer.moveBy(1f, value, 100f, 100f))
        }
        assertEquals(0.5f, pointer.x, 0f)
        assertEquals(0.5f, pointer.y, 0f)
        assertEquals(0L, pointer.motionRevision)
    }

    @Test fun `actual measured bounds determine hover while gaps remain inactive`() {
        val pointer = pointer()
        pointer.setTarget("left", 0f, 0f, 200f, 800f)
        pointer.setTarget("right", 700f, 0f, 1_000f, 800f)
        assertNull(pointer.tap())
        pointer.moveBy(30f, 0f, 100f, 100f)
        assertEquals("right", pointer.hoveredTargetId)
        pointer.removeTarget("right")
        assertNull(pointer.tap())
        // Scrolled-out areas cannot be activated outside the viewport.
        pointer.setTarget("above", 700f, -100f, 1_000f, -10f)
        assertNull(pointer.tap())
    }

    @Test fun `overlapping targets resolve to the latest measured target`() {
        val pointer = pointer()
        pointer.setTarget("behind", 0f, 0f, 1_000f, 800f)
        pointer.setTarget("front", 400f, 300f, 600f, 500f)
        assertEquals("front", pointer.tap())
        pointer.removeTarget("front")
        assertEquals("behind", pointer.tap())
    }

    @Test fun `resize preserves normalized position and rejects stale targets until remeasured`() {
        val pointer = pointer()
        pointer.moveBy(25f, -25f, 100f, 100f)
        pointer.setTarget("old", 0f, 0f, 1_000f, 800f)
        assertEquals("old", pointer.tap())
        pointer.setViewport(500f, 400f)
        assertEquals(0.75f, pointer.x, 0f)
        assertEquals(0.25f, pointer.y, 0f)
        assertEquals(375f, pointer.pixelX, 0f)
        assertEquals(100f, pointer.pixelY, 0f)
        assertNull(pointer.tap())
        pointer.setTarget("new", 350f, 50f, 450f, 150f)
        assertEquals("new", pointer.tap())
        pointer.setViewport(500f, 400f)
        assertEquals("Repeated unchanged layout must retain current bounds", "new", pointer.tap())
        assertEquals(1L, pointer.motionRevision)
    }

    @Test fun `surface changes and disposal clear targets without moving or disabling the pointer`() {
        val pointer = pointer()
        pointer.moveBy(-25f, 25f, 100f, 100f)
        pointer.setTarget("desktop", 0f, 0f, 1_000f, 800f)
        pointer.beginSurface("desktop")
        assertEquals("desktop", pointer.tap())
        pointer.beginSurface("programs")
        assertNull(pointer.tap())
        assertTrue(pointer.enabled)
        assertEquals(0.25f, pointer.x, 0f)
        assertEquals(0.75f, pointer.y, 0f)
        pointer.setTarget("program", 0f, 0f, 1_000f, 800f)
        pointer.clearTargets()
        assertNull(pointer.tap())
        assertTrue(pointer.enabled)
        assertEquals(1L, pointer.motionRevision)
    }

    @Test fun `invalid viewport or target measurements never leave clickable stale geometry`() {
        val pointer = pointer()
        pointer.setTarget("target", 0f, 0f, 1_000f, 800f)
        assertFalse(pointer.setTarget("target", Float.NaN, 0f, 1_000f, 800f))
        assertNull(pointer.tap())
        assertFalse(pointer.setTarget("target", 50f, 0f, 50f, 800f))
        pointer.setTarget("target", 0f, 0f, 1_000f, 800f)
        pointer.setViewport(Float.POSITIVE_INFINITY, 800f)
        assertNull(pointer.tap())
        assertFalse(pointer.moveBy(1f, 1f, 100f, 100f))
        assertEquals(0f, pointer.viewportWidth, 0f)
        assertEquals(0f, pointer.viewportHeight, 0f)
    }

    @Test fun `physical selection centers on visible clipped bounds without creating motion`() {
        val pointer = pointer()
        pointer.setTarget("row", 100f, -100f, 300f, 100f)
        assertTrue(pointer.centerOnTarget("row"))
        assertEquals(0.2f, pointer.x, 0f)
        assertEquals(0.0625f, pointer.y, 0f)
        assertEquals("row", pointer.tap())
        assertEquals(0L, pointer.motionRevision)
    }

    @Test fun `centering waits for missing or offscreen rows to receive visible layout`() {
        val pointer = pointer()
        assertFalse(pointer.centerOnTarget("later"))
        pointer.setTarget("other", 0f, 0f, 100f, 100f)
        assertEquals(0.5f, pointer.x, 0f)
        pointer.setTarget("later", 100f, 900f, 300f, 1_000f)
        assertEquals(0.5f, pointer.y, 0f)
        pointer.setTarget("later", 100f, 600f, 300f, 700f)
        assertEquals(0.2f, pointer.x, 0f)
        assertEquals(0.8125f, pointer.y, 0f)
        assertEquals(0L, pointer.motionRevision)
        // Keep aligned while the scroll animation finishes, without selecting by hover.
        pointer.setTarget("later", 300f, 400f, 500f, 500f)
        assertEquals(0.4f, pointer.x, 0f)
        assertEquals(0.5625f, pointer.y, 0f)
        assertEquals(0L, pointer.motionRevision)
    }

    @Test fun `real pointer movement cancels delayed centering`() {
        val pointer = pointer()
        assertFalse(pointer.centerOnTarget("later"))
        pointer.moveBy(10f, 0f, 100f, 100f)
        pointer.setTarget("later", 0f, 0f, 100f, 100f)
        assertEquals(0.6f, pointer.x, 0.0001f)
        assertEquals(0.5f, pointer.y, 0f)
        assertEquals(1L, pointer.motionRevision)
    }

    @Test fun `surface changes cancel delayed centering from an earlier menu`() {
        val pointer = pointer()
        assertFalse(pointer.centerOnTarget("later"))
        pointer.beginSurface("programs")
        pointer.setTarget("later", 0f, 0f, 100f, 100f)
        assertEquals(0.5f, pointer.x, 0f)
        assertEquals(0.5f, pointer.y, 0f)
        assertFalse(pointer.centerOnTarget("missing"))
        pointer.clearTargets()
        pointer.setTarget("missing", 0f, 0f, 100f, 100f)
        assertEquals(0.5f, pointer.x, 0f)
    }

    @Test fun `an explicit physical selection follows fresh bounds after resize without pointer motion`() {
        val pointer = pointer()
        pointer.setTarget("selected", 100f, 100f, 300f, 200f)
        assertTrue(pointer.centerOnTarget("selected"))
        pointer.setViewport(500f, 400f)
        assertEquals("Resize itself preserves normalized X", 0.2f, pointer.x, 0f)
        assertEquals("Resize itself preserves normalized Y", 0.1875f, pointer.y, 0f)
        assertNull(pointer.tap())
        pointer.setTarget("selected", 300f, 200f, 400f, 300f)
        assertEquals(0.7f, pointer.x, 0f)
        assertEquals(0.625f, pointer.y, 0f)
        assertEquals("selected", pointer.tap())
        assertEquals(0L, pointer.motionRevision)
    }
}
