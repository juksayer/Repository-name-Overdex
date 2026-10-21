package com.example.overdex.battle.observation

import javax.imageio.ImageIO
import org.junit.Assert.*
import org.junit.Test

class DecisionTimerReadingTest {
    @Test fun `last second numeral is isolated without the white timer arc`() {
        val image = ImageIO.read(checkNotNull(javaClass.getResource("/battle/decision-timer/moondrop-1.png")))
        assertEquals(BattleCropBounds(51, 52, 75, 91), DecisionTimerNumerals.bounds(image.width, image.height, image::getRGB))
    }

    @Test fun `recorded decision dial is distinct from the empty sky`() {
        for (name in listOf("4", "3", "absent", "moondrop-3", "moondrop-1", "moondrop-absent",
            "moondrop-night-9", "pixel-night-11", "moondrop-night-absent", "pixel-night-absent")) {
            val image = ImageIO.read(checkNotNull(javaClass.getResource("/battle/decision-timer/$name.png")))
            assertEquals(name, !name.endsWith("absent"), DecisionTimerAppearance.hasDial(image.width, image.height, image::getRGB))
        }
    }

    @Test fun `requires repeated dial numerals and disappearance without treating unreadable text as clear`() {
        val filter = DecisionTimerReadingFilter()
        assertNull(filter.accept(true, "4", 0))
        assertEquals(4, filter.accept(true, "4", 250_000_000L)!!.seconds)
        assertNull(filter.accept(true, "", 500_000_000L))
        assertEquals(3, filter.accept(true, "3", 1_000_000_000L)!!.seconds)
        assertNull(filter.accept(false, "", 1_250_000_000L))
        assertEquals(DecisionTimerChange(null), filter.accept(false, "", 1_500_000_000L))
        assertNull(filter.accept(false, "", 2_000_000_000L))
    }

    @Test fun `CP labels long battle clocks and nonnumerical OCR cannot open a decision timer`() {
        for (text in listOf("CP 4", "59", "60", "240", "GO", "4\n3")) assertNull(DecisionTimerReadingFilter.parse(text))
        assertEquals(10, DecisionTimerReadingFilter.parse("1O"))
    }
}
