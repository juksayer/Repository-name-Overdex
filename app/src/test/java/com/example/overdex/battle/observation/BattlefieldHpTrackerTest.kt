package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

class BattlefieldHpTrackerTest {
    private val player = ActivePokemonSide.PLAYER
    private val opponent = ActivePokemonSide.OPPONENT
    private val regions = mapOf(
        player to BattleCropBounds(35, 367, 241, 1080),
        opponent to BattleCropBounds(312, 381, 576, 863),
    )

    @Test fun `recorded Sealeo bar beyond saved crop end is measured completely`() {
        val source = ImageIO.read(File("src/test/resources/battle/recorded-evidence/2026-10-12-1546/frame-055.png"))
        val before = regions.toMap()
        val result = BattlefieldHpTracker().measure(source.width, source.height, 55_000_000_000L, regions, source::getRGB)
        val bar = requireNotNull(result[opponent]) { "Missing Sealeo: $result" }
        assertTrue("End cap should extend past saved X=576: $bar", bar.right > 576)
        assertEquals(192f, bar.width.toFloat(), 5f)
        assertTrue("Sealeo was full in this source: $bar", bar.filledFraction > .96f)
        assertNotNull(result[player])
        assertEquals("Recovery must not edit saved boxes", before, regions)
    }

    @Test fun `Gourgeist and Sealeo source retains both sides after camera layout shift`() {
        val source = ImageIO.read(File("src/test/resources/battle/recorded-evidence/2026-10-12-1546/frame-089.png"))
        val result = BattlefieldHpTracker().measure(source.width, source.height, 89_000_000_000L, regions, source::getRGB)
        assertEquals(setOf(player, opponent), result.keys)
        assertTrue(requireNotNull(result[player]).centerX < requireNotNull(result[opponent]).centerX)
    }

    @Test fun `a lone central bar cannot be assigned from a charge announcement assumption`() {
        val source = ImageIO.read(File("src/test/resources/battle/recorded-evidence/2026-10-12-1546/frame-156.png"))
        val bars = ActiveHpBarMeasurer.measureAll(source.width, 1080 - 367, source.width * 2 / 7) { x, y -> source.getRGB(x, y + 367) }
        assertTrue("Central recipient bar geometry must be found", bars.any { it.left in 235..250 && it.right in 425..440 })
        val result = BattlefieldHpTracker().measure(source.width, source.height, 156_000_000_000L, regions, source::getRGB)
        assertTrue("Missing ownership continuity is unavailable, not an invented side: $result", result.isEmpty())
    }

    @Test fun `continuity follows temporary horizontal motion and recovers at home after loss`() {
        val tracker = BattlefieldHpTracker()
        val home = bar(380)
        assertEquals(home, tracker.assign(672, 0, regions, listOf(home))[opponent])
        for (i in 1..3) {
            val moving = bar(380 - i * 40)
            assertEquals(moving, tracker.assign(672, i * 100_000_000L, regions, listOf(moving))[opponent])
        }
        assertTrue(tracker.assign(672, 400_000_000L, regions, emptyList()).isEmpty())
        assertTrue("Old continuity cannot assign a new centered bar", tracker.assign(672, 2_000_000_000L, regions, listOf(bar(240))).isEmpty())
        assertEquals(home, tracker.assign(672, 3_000_000_000L, regions, listOf(home))[opponent])
    }

    @Test fun `two moved bars are assigned once each by relative position`() {
        val result = BattlefieldHpTracker().assign(672, 0, regions, listOf(bar(175), bar(310)))
        assertEquals(175, result[player]?.left)
        assertEquals(310, result[opponent]?.left)
    }

    @Test fun `clipped white outline cannot use an orange damage chunk as its end cap`() {
        val bars = ActiveHpBarMeasurer.measureAll(240, 120) { x, y ->
            when {
                (y == 40 || y == 60) && x >= 20 -> 0xffeeeeee.toInt()
                x == 20 && y in 40..60 -> 0xffeeeeee.toInt()
                x in 23..135 && y in 42..58 -> 0xff1effbc.toInt()
                x in 136..239 && y in 41..59 -> 0xffff8a20.toInt()
                else -> 0xff182825.toInt()
            }
        }
        assertTrue("Missing right cap cannot produce a shortened bar: $bars", bars.isEmpty())
    }

    @Test fun `white Abomasnow behind its outline does not hide either active HP bar`() {
        for ((name, expectedHp) in listOf("89" to .886f, "77" to .773f, "66" to .665f)) {
            val source = ImageIO.read(File("src/test/resources/battle/recorded-evidence/2026-10-16-hp-outline/abomasnow-$name.png"))
            val result = BattlefieldHpTracker().measure(source.width, source.height, 0, regions, source::getRGB)
            assertEquals("Both real bars must survive overlapping white outlines in $name", setOf(player, opponent), result.keys)
            assertEquals(expectedHp, requireNotNull(result[player]).filledFraction, .02f)
            assertTrue(requireNotNull(result[opponent]).filledFraction > .97f)
        }
    }

    @Test fun `nested outline candidates sharing the bottom edge keep the strongest bar`() {
        val actual = bar(40)
        val whiteBody = actual.copy(top = actual.top - 10, confidence = .8f)
        val enemy = bar(400)
        val result = BattlefieldHpTracker().assign(672, 0, regions, listOf(whiteBody, enemy, actual))
        assertEquals(mapOf(player to actual, opponent to enemy), result)
    }

    @Test fun `recorded orange Camerupt art beside the white bar does not suppress either combatant`() {
        val source = ImageIO.read(File("src/test/resources/battle/recorded-evidence/2026-10-16-camerupt-hp/camerupt-vaporeon.png"))
        val result = BattlefieldHpTracker().measure(source.width, source.height, 0, regions, source::getRGB)
        assertEquals(setOf(player, opponent), result.keys)
        val own = result.getValue(player)
        assertEquals(38, own.left)
        assertEquals(231, own.right)
        assertEquals(.725f, own.filledFraction, .02f)
        assertEquals(.878f, result.getValue(opponent).filledFraction, .02f)
    }

    @Test fun `weaker overlapping artwork yields to a clear bar without changing its fill`() {
        val actual = bar(40)
        val artwork = actual.copy(left = 8, right = 200, top = 590, bottom = 622,
            filledFraction = .99f, confidence = .70f)
        val result = BattlefieldHpTracker().assign(672, 0, regions, listOf(artwork, actual, bar(400)))
        assertEquals(actual, result[player])
        assertEquals(bar(400), result[opponent])
    }

    @Test fun `equally plausible offset rectangles remain ambiguous`() {
        val actual = bar(40)
        val competing = actual.copy(left = 8, right = 200, top = 590, bottom = 622)
        assertTrue(BattlefieldHpTracker().assign(672, 0, regions, listOf(actual, competing, bar(400))).isEmpty())
    }

    @Test fun `weak independent outline is not erased to force two combatants`() {
        val unrelated = bar(220).copy(top = 500, bottom = 517, confidence = .65f)
        assertTrue(BattlefieldHpTracker().assign(672, 0, regions, listOf(bar(40), unrelated, bar(400))).isEmpty())
    }

    @Test fun `separate stacked outlines remain ambiguous instead of becoming one bar`() {
        val actual = bar(40)
        val separate = actual.copy(top = actual.top - 8, bottom = actual.bottom - 8)
        assertTrue(BattlefieldHpTracker().assign(672, 0, regions, listOf(actual, separate, bar(400))).isEmpty())
    }

    private fun bar(left: Int) = ActiveHpBarMeasurement(left, 600, left + 192, 617, .8f, .95f)
}
