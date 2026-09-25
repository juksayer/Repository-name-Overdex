package com.example.overdex.battle.observation

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CountdownGoGeometryTest {
    @Test
    fun `scores the paired GO forms preserved in a real countdown crop`() {
        val score = CountdownGoGeometry.score(
            first = CountdownComponentGeometry(20, 168, 354, 563, 81_014),
            second = CountdownComponentGeometry(315, 168, 670, 563, 67_095),
            sourceWidth = 691,
            sourceHeight = 720
        )

        assertTrue(score != null && score >= 0.70f)
    }

    @Test
    fun `rejects a single glyph with unrelated small bright noise`() {
        val score = CountdownGoGeometry.score(
            first = CountdownComponentGeometry(154, 138, 670, 619, 80_116),
            second = CountdownComponentGeometry(81, 313, 290, 329, 834),
            sourceWidth = 691,
            sourceHeight = 720
        )

        assertNull(score)
    }
}
