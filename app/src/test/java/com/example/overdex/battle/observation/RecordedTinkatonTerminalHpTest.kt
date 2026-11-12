package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import org.junit.Assert.*
import org.junit.Test
import javax.imageio.ImageIO

/** Actual Moondrop video pixels; source timestamps and resize are documented beside the crops. */
class RecordedTinkatonTerminalHpTest {
    private val side = ActivePokemonSide.OPPONENT
    private val anchor = ActiveHpBarMeasurement(394, 677, 587, 694, .15343915f, .95f)
    private val width = 672
    private val height = 1493
    private fun at(seconds: Double) = (seconds * 1_000_000_000).toLong()

    private fun crop(name: String) = ImageIO.read(requireNotNull(javaClass.getResource(
            "/battle/hp/2026-11-11-tinkaton-terminal/$name.png")))

    private fun ordinarySeed(): ActiveHpBarMeasurement {
        val crop = crop("seed")
        val candidates = ActiveHpBarMeasurer.measureAll(crop.width, crop.height, anchor.width,
            crop.getRGB(0, 0, crop.width, crop.height, null, 0, crop.width))
        val local = candidates.first { it.filledFraction in .13f.. .17f }
        return local.copy(left = local.left + 380, right = local.right + 380,
            top = local.top + 650, bottom = local.bottom + 650)
    }

    private fun read(name: String, bar: ActiveHpBarMeasurement): TrackedTerminalHpBarSample? {
        val crop = crop(name)
        return TrackedTerminalHpBarMeasurer.measure(width, height, bar) { x, y ->
            assertTrue("The narrow search must remain inside the preserved actual crop", x in 380 until 610 && y in 650 until 730)
            crop.getRGB(x - 380, y - 650)
        }
    }

    @Test fun `recorded red to orange trail to displaced empty outline preserves one actual zero`() {
        val policy = TerminalHpWatchPolicy()
        // The ordinary locator supplies the low-HP anchor; an independent red
        // interior observation on the next frame arms the terminal burst.
        assertTrue(policy.seed(side, ordinarySeed(), width, height, at(58.773722222)))
        val red = policy.checkFrame(width, height, at(59.005411111)) { _, bar -> read("red", bar) }.single()
        assertEquals(HpBarFillColor.RED, red.sample.fillColor)
        assertTrue(red.sample.measurement.filledFraction in .13f.. .17f)

        assertTrue(policy.checkFrame(width, height, at(59.518433333)) { _, bar ->
            read("orange-tail", bar)
        }.isEmpty())
        assertTrue(policy.isArmed(side, at(60.504888889)))
        val zero = policy.checkFrame(width, height, at(60.504888889)) { _, bar -> read("empty", bar) }.single()
        assertEquals(HpBarFillColor.EMPTY, zero.sample.fillColor)
        assertEquals(0f, zero.sample.measurement.filledFraction, 0f)
        assertTrue("The empty bar moves down as Tinkaton shrinks", zero.sample.measurement.top - red.sample.measurement.top in 12..16)
        assertTrue(policy.checkFrame(width, height, at(60.6)) { _, bar -> read("empty", bar) }.isEmpty())
    }

    @Test fun `recorded empty outline cannot cross an appearance or whole watch reset`() {
        for (resetAll in listOf(false, true)) {
            val policy = TerminalHpWatchPolicy()
            policy.seed(side, ordinarySeed(), width, height, at(58.773722222))
            assertEquals(1, policy.checkFrame(width, height, at(59.005411111)) { _, bar -> read("red", bar) }.size)
            if (resetAll) policy.resetAll(at(59.6)) else policy.reset(side, at(59.6))
            assertTrue(policy.checkFrame(width, height, at(60.504888889)) { _, bar -> read("empty", bar) }.isEmpty())
        }
    }

    @Test fun `recorded disappeared outline and orange only trail remain unavailable`() {
        assertNull(read("orange-tail", anchor))
        assertNull(read("vanished", anchor))
        // The same real empty pixels are insufficient without a prior measured red frame.
        val policy = TerminalHpWatchPolicy()
        val moved = anchor.copy(top = 692, bottom = 709)
        policy.seed(side, moved, width, height, at(60.4))
        assertTrue(policy.checkFrame(width, height, at(60.504888889)) { _, bar -> read("empty", bar) }.isEmpty())
    }
}
