package com.example.overdex.model

import org.junit.Assert.*
import org.junit.Test

class RegistrationBoxGeometryTest {
    private val box = CaptureRegion("SpeciesName", .1f, .2f, .4f, .05f, ObservationType.OCR_TEXT)

    @Test fun `one pixel nudge uses source resolution rather than CRT dimensions`() {
        val adjusted = box.adjustPixels(1f, 1f, 1080, 2400, false)
        assertEquals(1f, (adjusted.x - box.x) * 1080, .001f)
        assertEquals(1f, (adjusted.y - box.y) * 2400, .001f)
        assertEquals(box.width, adjusted.width, 0f)
        assertEquals(box.height, adjusted.height, 0f)
    }

    @Test fun `movement and resizing stay inside the screenshot`() {
        val moved = box.adjustPixels(99999f, -99999f, 1080, 2400, false)
        assertEquals(1f - box.width, moved.x, .0001f)
        assertEquals(0f, moved.y, 0f)
        val large = box.adjustPixels(99999f, 99999f, 1080, 2400, true)
        assertEquals(1f, large.x + large.width, .0001f)
        assertEquals(1f, large.y + large.height, .0001f)
        val tiny = box.adjustPixels(-99999f, -99999f, 1080, 2400, true)
        assertEquals(1f, tiny.width * 1080, .0001f)
        assertEquals(1f, tiny.height * 2400, .0001f)
    }
}
