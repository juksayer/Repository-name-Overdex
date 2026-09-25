package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerInactiveSpriteFingerprinterTest {
    @Test fun `fingerprint is reproducible and records sampled bounds`() {
        val pixelAt = { x: Int, y: Int -> if ((x + y) % 2 == 0) 0xffeeeeee.toInt() else 0xff111111.toInt() }
        val first = PlayerInactiveSpriteFingerprinter.measure(160, 190, pixelAt)!!
        val second = PlayerInactiveSpriteFingerprinter.measure(160, 190, pixelAt)!!
        assertEquals(first, second)
        assertEquals(18, first.left)
        assertEquals(28, first.top)
    }

    @Test fun `separately captured cards produce independent fingerprints`() {
        val upperPixels = { _: Int, y: Int ->
            val bright = y < 80
            if (bright) 0xffeeeeee.toInt() else 0xff111111.toInt()
        }
        val lowerPixels = { _: Int, y: Int ->
            val bright = y >= 80
            if (bright) 0xffeeeeee.toInt() else 0xff111111.toInt()
        }
        val upper = PlayerInactiveSpriteFingerprinter.measure(160, 190, upperPixels)!!
        val lower = PlayerInactiveSpriteFingerprinter.measure(160, 190, lowerPixels)!!
        assertEquals(true, upper.fingerprint != lower.fingerprint)
    }
}
