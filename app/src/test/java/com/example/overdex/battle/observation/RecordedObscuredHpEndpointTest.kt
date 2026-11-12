package com.example.overdex.battle.observation

import org.junit.Assert.*
import org.junit.Test
import javax.imageio.ImageIO

class RecordedObscuredHpEndpointTest {
    @Test fun `bright effect over remaining fill is unavailable rather than a large HP loss`() {
        // The original reader reported 39%, 52%, then 62% while an effect
        // whitened a roughly 78% bar. These are real video-derived pixels.
        for (name in listOf("64.827211", "64.892000", "65.069867", "65.210756", "65.252378")) {
            assertTrue("Obscured endpoint at $name must not become damage: ${read(name)}", read(name).isEmpty())
        }
    }

    @Test fun `visible orange damage trail still supports the real remaining HP`() {
        for (name in listOf("65.305467", "65.624456")) {
            val bars = read(name)
            assertTrue("The later visible endpoint must remain measurable: $name", bars.isNotEmpty())
            bars.forEach { assertEquals(name, .782f, it.filledFraction, .012f) }
        }
    }

    private fun read(name: String): List<ActiveHpBarMeasurement> {
        val image = ImageIO.read(checkNotNull(javaClass.getResource(
            "/battle/recorded-evidence/2026-11-11-obscured-hp/$name.png")))
        return ActiveHpBarMeasurer.measureAll(image.width, image.height, 193,
            image.getRGB(0, 0, image.width, image.height, null, 0, image.width))
    }
}
