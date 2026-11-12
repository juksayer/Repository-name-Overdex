package com.example.overdex.battle.observation

import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Native video pixels diagnose missing coverage without relaxing the terminal verifier. */
class RecordedCharizardTerminalPixelTest {
    @Test fun `ordinary low red is measurable while artwork makes its strict terminal interior unknown`() {
        val frame = frame("red-over-art.png")
        val ordinary = ordinary(frame)
        assertEquals(.0965f, ordinary.filledFraction, .005f)
        assertEquals(416, ordinary.left)
        assertEquals(648, ordinary.right)
        // The translucent unfilled bar crosses orange Charizard artwork. Its
        // tail is not sufficiently dark to certify a terminal measurement.
        assertNull(TrackedTerminalHpBarMeasurer.measure(SOURCE_WIDTH, SOURCE_HEIGHT, ordinary, frame::pixelAt))
    }

    @Test fun `last orange sliver cannot be turned into an empty hp measurement`() {
        val frame = frame("last-orange-sliver.png")
        // Video PTS72.565156: a complete blue outline still contains orange.
        // Its Y differs only a few pixels from the next clear-empty frame.
        assertNull(TrackedTerminalHpBarMeasurer.measure(
            SOURCE_WIDTH, SOURCE_HEIGHT, emptyAnchor, frame::pixelAt))
    }

    @Test fun `the next visible empty outline is verifiable but lies outside the old narrow anchor`() {
        val frame = frame("first-clear-empty.png")
        val empty = TrackedTerminalHpBarMeasurer.measure(
            SOURCE_WIDTH, SOURCE_HEIGHT, emptyAnchor, frame::pixelAt)
        assertNotNull(empty)
        assertEquals(HpBarFillColor.EMPTY, empty!!.fillColor)
        assertEquals(0f, empty.measurement.filledFraction, 0f)
        assertTrue(empty.measurement.confidence > .90f)

        // A native70.708967 red anchor cannot reach this72.598656 outline.
        // This demonstrates tracking coverage, not permission to reuse stale HP.
        val olderRed = ordinary(frame("red-over-art.png"))
        assertNull(TrackedTerminalHpBarMeasurer.measure(
            SOURCE_WIDTH, SOURCE_HEIGHT, olderRed, frame::pixelAt))
    }

    private fun ordinary(frame: Frame): ActiveHpBarMeasurement =
        ActiveHpBarMeasurer.measureAll(frame.image.width, frame.image.height, SOURCE_WIDTH * 2 / 7,
            frame.image::getRGB).maxBy { it.confidence }.let {
                it.copy(left = it.left + CROP_LEFT, right = it.right + CROP_LEFT,
                    top = it.top + CROP_TOP, bottom = it.bottom + CROP_TOP)
            }

    private fun frame(name: String): Frame = Frame(ImageIO.read(checkNotNull(javaClass.getResource(
        "/battle/recorded-evidence/2026-11-11-charizard-terminal/$name"))))

    private class Frame(val image: BufferedImage) {
        fun pixelAt(x: Int, y: Int): Int {
            // Fail if a future search silently reads outside the preserved crop.
            require(x in CROP_LEFT until CROP_LEFT + image.width)
            require(y in CROP_TOP until CROP_TOP + image.height)
            return image.getRGB(x - CROP_LEFT, y - CROP_TOP)
        }
    }

    private val emptyAnchor = ActiveHpBarMeasurement(415, 559, 649, 581, .1f, .99f)

    private companion object {
        const val SOURCE_WIDTH = 816
        const val SOURCE_HEIGHT = 1808
        const val CROP_LEFT = 380
        const val CROP_TOP = 430
    }
}
