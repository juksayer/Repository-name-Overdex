package com.example.overdex.data

import org.junit.Assert.*
import org.junit.Test

class CalibrationScreenshotLibraryTest {
    private val moondrop = reference("Moondrop", 1080, 2400, "Moondrop")
    private val pixel = reference("Pixel", 1080, 2280, "Pixel 4")
    private val tablet = reference("Tablet", 800, 1280, "onn 10.1")
    private val library = listOf(moondrop, pixel, tablet)

    @Test
    fun `this screen chooses physical capture shape rather than app content height`() {
        assertEquals(listOf(moondrop), select(CalibrationScreenshotFilter.ThisScreen, 1080, 2400))
        assertEquals(listOf(pixel), select(CalibrationScreenshotFilter.ThisScreen, 1080, 2280))
        assertEquals(listOf(tablet), select(CalibrationScreenshotFilter.ThisScreen, 800, 1280))
        assertTrue(select(CalibrationScreenshotFilter.ThisScreen, 1080, 2244).isEmpty())
    }

    @Test
    fun `uniform capture scaling and width alignment preserve matching shape`() {
        assertTrue(CalibrationScreenshotLibrary.sameShape(216, 480, 1080, 2400))
        assertTrue(CalibrationScreenshotLibrary.sameShape(1084, 2400, 1080, 2400))
        assertFalse(CalibrationScreenshotLibrary.sameShape(1080, 2280, 1080, 2400))
        assertFalse(CalibrationScreenshotLibrary.sameShape(2400, 1080, 1080, 2400))
    }

    @Test
    fun `unknown dimensions remain available without disguising a known mismatched image`() {
        val unknown = reference("Unreadable header", 0, 0)
        val actual = CalibrationScreenshotLibrary.filter(library + unknown, CalibrationScreenshotFilter.ThisScreen, 1080, 2400)
        assertEquals(listOf(moondrop, unknown), actual)
        assertEquals(listOf(unknown), CalibrationScreenshotLibrary.filter(listOf(pixel, unknown), CalibrationScreenshotFilter.ThisScreen, 1080, 2400))
        assertTrue(CalibrationScreenshotLibrary.filter(listOf(pixel), CalibrationScreenshotFilter.ThisScreen, 1080, 2400).isEmpty())
    }

    @Test
    fun `all device and shape selectors remain independent and leave references intact`() {
        assertEquals(library, select(CalibrationScreenshotFilter.All))
        assertEquals(listOf(pixel), select(CalibrationScreenshotFilter.Device("Pixel 4")))
        assertEquals(listOf(tablet), select(CalibrationScreenshotFilter.Shape(1600, 2560)))
        assertEquals(listOf(moondrop, pixel, tablet), library)
    }

    @Test
    fun `shape choices collapse scaled copies and aligned captures but retain landscape`() {
        val shapes = CalibrationScreenshotLibrary.shapeChoices(library + listOf(
            reference("Scaled", 216, 480), reference("Aligned", 1084, 2400),
            reference("Landscape", 1280, 800), reference("Unknown", 0, 0)
        ))
        assertEquals(4, shapes.size)
        assertEquals(listOf("9:20", "9:19", "5:8", "8:5"), shapes.map { CalibrationScreenshotLibrary.shapeLabel(it.width, it.height) })
    }

    @Test
    fun `invalid dimensions never become a known shape`() {
        assertFalse(CalibrationScreenshotLibrary.sameShape(0, 1280, 800, 1280))
        assertFalse(CalibrationScreenshotLibrary.sameShape(800, -1, 800, 1280))
        assertEquals("Unknown shape", CalibrationScreenshotLibrary.shapeLabel(0, 1280))
    }

    @Test
    fun `portrait reference fits square preview with horizontal margins`() {
        val viewport = CalibrationImageViewport.fit(400f, 400f, 1080, 2400)
        assertEquals(110f, viewport.left, 0.0001f)
        assertEquals(0f, viewport.top, 0.0001f)
        assertEquals(180f, viewport.width, 0.0001f)
        assertEquals(400f, viewport.height, 0.0001f)
        // A source point stays at the same normalized location inside the fitted image.
        val sourceX = 0.25f * 1080
        val previewX = viewport.left + 0.25f * viewport.width
        assertEquals(sourceX, (previewX - viewport.left) / viewport.width * 1080, 0.0001f)
        assertEquals(0.1f, 18f / viewport.width, 0.0001f)
    }

    @Test
    fun `landscape reference letterboxes vertically without distorting crop geometry`() {
        val viewport = CalibrationImageViewport.fit(400f, 400f, 1280, 800)
        assertEquals(0f, viewport.left, 0.0001f)
        assertEquals(75f, viewport.top, 0.0001f)
        assertEquals(400f, viewport.width, 0.0001f)
        assertEquals(250f, viewport.height, 0.0001f)
        assertEquals(0.1f, 25f / viewport.height, 0.0001f)
    }

    @Test
    fun `missing source or preview dimensions cannot create a misleading crop outline`() {
        assertEquals(CalibrationImageViewport(0f, 0f, 0f, 0f), CalibrationImageViewport.fit(400f, 400f, 0, 0))
        assertEquals(CalibrationImageViewport(0f, 0f, 0f, 0f), CalibrationImageViewport.fit(0f, 400f, 1080, 2400))
    }

    private fun reference(id: String, width: Int, height: Int, device: String? = null) =
        CalibrationScreenshotReference(id, "$id.png", width, height, device)

    private fun select(filter: CalibrationScreenshotFilter, width: Int = 1080, height: Int = 2400) =
        CalibrationScreenshotLibrary.filter(library, filter, width, height)
}
