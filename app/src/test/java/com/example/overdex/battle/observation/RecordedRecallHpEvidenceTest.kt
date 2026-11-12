package com.example.overdex.battle.observation

import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import org.junit.Assert.*
import org.junit.Test

/** Actual living-recall pixels are a counterexample to shrink/disappearance => zero HP. */
class RecordedRecallHpEvidenceTest {
    @Test fun `shrinking Carbink still has a measurable red health band`() {
        for (name in listOf("shrinking", "last-readable")) {
            val frame = frame(name)
            val ordinary = ordinary(frame)
            assertEquals(.174f, ordinary.filledFraction, .012f)
            val strict = requireNotNull(TrackedTerminalHpBarMeasurer.measure(
                816, 1808, ordinary, frame::pixelAt))
            assertEquals(HpBarFillColor.RED, strict.fillColor)
            assertTrue(strict.measurement.filledFraction in .14f.. .20f)
        }
    }

    @Test fun `living recall and replacement cannot turn the fading bar into empty HP`() {
        var anchor = ordinary(frame("last-readable"))
        for (name in listOf("sprite-gone", "replacement-label")) {
            val sample = TrackedTerminalHpBarMeasurer.measure(816, 1808, anchor, frame(name)::pixelAt)
            // The frame can retain real positive HP or be unreadable. Neither
            // the departed sprite nor the new name permits a numeric zero.
            if (sample != null) {
                assertNotEquals(HpBarFillColor.EMPTY, sample.fillColor)
                assertTrue(sample.measurement.filledFraction > 0f)
                anchor = sample.measurement
            }
        }
    }

    private fun ordinary(frame: Frame): ActiveHpBarMeasurement =
        ActiveHpBarMeasurer.measureAll(frame.image.width, frame.image.height, 816 * 2 / 7,
            frame.image::getRGB).maxBy { it.confidence }.let {
            it.copy(left = it.left + 370, right = it.right + 370,
                top = it.top + 700, bottom = it.bottom + 700)
        }

    private fun frame(name: String) = Frame(ImageIO.read(requireNotNull(javaClass.getResource(
        "/battle/recorded-evidence/2026-11-11-carbink-living-recall/$name.png"))))

    private class Frame(val image: BufferedImage) {
        fun pixelAt(x: Int, y: Int): Int {
            require(x in 370 until 370 + image.width && y in 700 until 700 + image.height)
            return image.getRGB(x - 370, y - 700)
        }
    }
}
