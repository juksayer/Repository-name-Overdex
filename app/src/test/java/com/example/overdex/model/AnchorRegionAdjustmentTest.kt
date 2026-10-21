package com.example.overdex.model

import org.junit.Assert.*
import org.junit.Test

class AnchorRegionAdjustmentTest {
    @Test fun `live nudges are physical pixels without adding a notification bar offset`() {
        val original = AnchorRegion(400f / 1080, 900f / 2400, 280f / 1080, 320f / 2400)
        val moved = original.adjustPixels(1f, 84f, 1080, 2400, false)
        assertEquals(401f, moved.x * 1080, 0.001f)
        assertEquals(984f, moved.y * 2400, 0.001f)
        assertEquals(original.width, moved.width)
        val resized = moved.adjustPixels(-80f, -120f, 1080, 2400, true)
        assertEquals(moved.x, resized.x)
        assertEquals(moved.y, resized.y)
        assertEquals(200f, resized.width * 1080, 0.001f)
        assertEquals(200f, resized.height * 2400, 0.001f)
    }

    @Test fun `live box and resize stay within full display at either edge`() {
        val original = AnchorRegion(0.4f, 0.4f, 0.2f, 0.2f)
        val edge = original.adjustPixels(9000f, 9000f, 1080, 2400, false)
        assertEquals(1f, edge.x + edge.width, 0.00001f)
        assertEquals(1f, edge.y + edge.height, 0.00001f)
        val tiny = edge.adjustPixels(-9000f, -9000f, 1080, 2400, true)
        assertEquals(8f, tiny.width * 1080, 0.001f)
        assertEquals(8f, tiny.height * 2400, 0.001f)
        val large = edge.adjustPixels(9000f, 9000f, 1080, 2400, true)
        assertEquals(1f, large.x + large.width, 0.00001f)
        assertEquals(1f, large.y + large.height, 0.00001f)
    }
}
