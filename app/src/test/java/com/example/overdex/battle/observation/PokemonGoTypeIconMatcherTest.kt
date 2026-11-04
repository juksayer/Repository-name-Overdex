package com.example.overdex.battle.observation

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.*
import org.junit.Test

/** Exercises the production pixel core without Android bitmap stubs. */
class PokemonGoTypeIconMatcherTest {
    @Test fun `all official shapes survive scale and asymmetric calibration margins`() {
        val references = references()
        val matcher = TypeIconPixelMatcher(references)
        for (size in listOf(24, 30, 42)) for (reference in references) {
            val crop = strip(listOf(reference), size, left = 17, top = 13, right = 24, bottom = 54)
            val result = matcher.matchCrop(crop)
            assertEquals("key=${reference.key}, size=$size, results=$result", listOf(reference.key), result.map { it.key })
        }
    }

    @Test fun `circle position and diameter do not derive from strip height`() {
        val reference = references()[4]
        val small = TypeIconCircleDetector.detect(strip(listOf(reference), 30, 8, 7, 8, 7)).single()
        val roomy = TypeIconCircleDetector.detect(strip(listOf(reference), 30, 8, 7, 47, 83)).single()
        assertEquals(small.centerX, roomy.centerX, 1.0)
        assertEquals(small.centerY, roomy.centerY, 1.0)
        assertEquals(small.radius, roomy.radius, 1.0)
        assertEquals(15.0, roomy.radius, 2.0)
    }

    @Test fun `neutral Dark and bright Electric produce real different color observations`() {
        val references = references()
        val dark = TypeIconPixelMatcher.representativeColor(references[13].pixels, references[13].circle)
        val electric = TypeIconPixelMatcher.representativeColor(references[16].pixels, references[16].circle)
        assertNotNull(dark)
        assertNotNull(electric)
        assertNotEquals(dark, electric)
        assertTrue(requireNotNull(electric).red >= 235)
        assertTrue(requireNotNull(dark).blue > dark.red)
    }

    @Test fun `missing color stays missing instead of becoming black evidence`() {
        val circle = TypeIconCircle(15.5, 15.5, 16.0)
        assertNull(TypeIconPixelMatcher.representativeColor(TypeIconPixels(32, 32, IntArray(1024)), circle))
        assertNull(TypeIconPixelMatcher.representativeColor(TypeIconPixels(32, 32, IntArray(1024) { -1 }), circle))
        val actualBlack = TypeIconPixelMatcher.representativeColor(
            TypeIconPixels(32, 32, IntArray(1024) { 0xff000000.toInt() }), circle)
        assertEquals(TypeIconRgb(0.0, 0.0, 0.0), actualBlack)
    }

    @Test fun `tied references stay unknown despite perfect shape and color`() {
        val first = references()[4]
        val matcher = TypeIconPixelMatcher(listOf(first, first.copy(key = 100)))
        val result = matcher.matchIsolatedBadge(first.pixels)
        assertNull(result.key)
        assertTrue(result.shapeSimilarity > .9f)
        assertEquals(0f, result.runnerUpMargin)
    }

    @Test fun `blank white transparent and isolated white noise cannot identify types`() {
        val matcher = TypeIconPixelMatcher(references())
        for (color in listOf(0, 0xff505050.toInt(), -1)) {
            assertTrue(matcher.matchCrop(TypeIconPixels(100, 70, IntArray(7000) { color })).isEmpty())
        }
        val noise = IntArray(100 * 70) { 0xff505050.toInt() }
        for (x in 6 until 90 step 7) noise[9 * 100 + x] = -1
        assertTrue(matcher.matchCrop(TypeIconPixels(100, 70, noise)).none { it.key != null })
    }

    @Test fun `visible unreadable disk remains an unknown result`() {
        val image = BufferedImage(70, 64, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.color = java.awt.Color(45, 50, 55); graphics.fillRect(0, 0, 70, 64)
        graphics.color = java.awt.Color(90, 100, 160); graphics.fillOval(17, 12, 30, 30)
        graphics.dispose()
        val result = TypeIconPixelMatcher(references()).matchCrop(pixels(image))
        assertEquals(result.toString(), 1, result.size)
        assertNull(result.single().key)
    }

    @Test fun `three visible badges are not silently truncated to two`() {
        val references = references()
        val result = TypeIconPixelMatcher(references).matchCrop(strip(
            listOf(references[4], references[14], references[12]), 30, 8, 7, 8, 7))
        assertEquals(result.toString(), listOf(4, 14, 12), result.map { it.key })
    }

    @Test fun `clipped first badge cannot disappear into an accepted monotype`() {
        val references = references()
        val source = strip(listOf(references[17], references[7]), 30, 8, 7, 8, 7)
        val width = source.width - 17
        val clipped = TypeIconPixels(width, source.height, IntArray(width * source.height) { index ->
            source.pixel(index % width + 17, index / width)
        })
        val result = TypeIconPixelMatcher(references).matchCrop(clipped)
        assertEquals(result.toString(), 2, result.size)
        assertNull(result.first().key)
        assertEquals(7, result.last().key)
    }

    @Test fun `mostly clipped badge with its center outside crop still prevents a false monotype`() {
        val references = references()
        val source = strip(listOf(references[17], references[7]), 30, 8, 7, 8, 7)
        for (left in listOf(24, 28, 32)) {
            val width = source.width - left
            val clipped = TypeIconPixels(width, source.height, IntArray(width * source.height) { index ->
                source.pixel(index % width + left, index / width)
            })
            val result = TypeIconPixelMatcher(references).matchCrop(clipped)
            assertTrue("left=$left result=$result", result.any { it.key == null })
            assertEquals("left=$left result=$result", 7, result.last().key)
        }
    }

    @Test fun `outside arcs in another row do not become clipped sibling badges`() {
        val references = references()
        val source = strip(listOf(references[17], references[7]), 30, 8, 39, 8, 8)
        val image = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, source.width, source.height, source.argb, 0, source.width)
        val graphics = image.createGraphics()
        graphics.color = java.awt.Color(110, 80, 145)
        graphics.fillOval(-21, -8, 30, 30)
        graphics.fillOval(source.width - 10, -7, 30, 30)
        graphics.dispose()
        val result = TypeIconPixelMatcher(references).matchCrop(pixels(image))
        assertEquals(result.toString(), listOf(17, 7), result.map { it.key })
    }

    private fun references(): List<TypeIconReference> {
        val source = listOf(File("src/main/res/drawable-nodpi/pokemon_go_type_icon_reference.png"),
            File("app/src/main/res/drawable-nodpi/pokemon_go_type_icon_reference.png")).first { it.isFile }
        val sheet = requireNotNull(ImageIO.read(source))
        return (0 until 18).map { index ->
            val x = 150 + index % 6 * 180 - 65
            val y = 150 + index / 6 * 180 - 65
            TypeIconReference(index, pixels(sheet.getSubimage(x, y, 130, 130)), TypeIconCircle(64.5, 64.5, 65.0))
        }
    }

    private fun strip(references: List<TypeIconReference>, size: Int, left: Int, top: Int, right: Int, bottom: Int): TypeIconPixels {
        val gap = 9
        val image = BufferedImage(left + right + references.size * size + (references.size - 1) * gap,
            top + bottom + size, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.color = java.awt.Color(52, 57, 62); graphics.fillRect(0, 0, image.width, image.height)
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        references.forEachIndexed { index, reference ->
            val tile = BufferedImage(130, 130, BufferedImage.TYPE_INT_ARGB)
            tile.setRGB(0, 0, 130, 130, reference.pixels.argb, 0, 130)
            graphics.drawImage(tile, left + index * (size + gap), top, size, size, null)
        }
        graphics.dispose()
        return pixels(image)
    }

    private fun pixels(image: BufferedImage): TypeIconPixels = TypeIconPixels(image.width, image.height,
        image.getRGB(0, 0, image.width, image.height, null, 0, image.width))
}
