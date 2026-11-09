package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import org.junit.Assert.*
import org.junit.Test

class TerminalHpWatchPolicyTest {
    private val player = ActivePokemonSide.PLAYER
    private val opponent = ActivePokemonSide.OPPONENT
    private val own = ActiveHpBarMeasurement(40, 600, 232, 617, .18f, .95f)
    private val enemy = own.copy(left = 400, right = 592, top = 400, bottom = 417)
    private val width = 672
    private val height = 1493
    private fun ms(value: Long) = value * 1_000_000L
    private fun sample(bar: ActiveHpBarMeasurement = own, color: HpBarFillColor = HpBarFillColor.RED,
        fraction: Float = if (color == HpBarFillColor.EMPTY) 0f else .12f) =
        TrackedTerminalHpBarSample(bar.copy(filledFraction = fraction), color)
    private fun seed(policy: TerminalHpWatchPolicy, at: Long = 0L, bar: ActiveHpBarMeasurement = own,
        side: ActivePokemonSide = player) = policy.seed(side, bar, width, height, ms(at))
    private fun check(policy: TerminalHpWatchPolicy, at: Long, reading: TrackedTerminalHpBarSample?) =
        policy.checkFrame(width, height, ms(at)) { _, _ -> reading }

    @Test fun `an actual red frame arms and one clear empty frame is accepted once`() {
        val policy = TerminalHpWatchPolicy()
        assertTrue(seed(policy))
        val red = check(policy, 100, sample()).single()
        assertEquals(player, red.side)
        assertEquals(HpBarFillColor.RED, red.sample.fillColor)
        assertEquals(setOf(player), policy.armedSides(ms(100)))
        val zero = check(policy, 130, sample(color = HpBarFillColor.EMPTY)).single()
        assertEquals(0f, zero.sample.measurement.filledFraction, 0f)
        assertTrue(policy.isCurrentGeneration(player, zero.generation))
        assertFalse(policy.isArmed(player, ms(130)))
        assertTrue(check(policy, 160, sample(color = HpBarFillColor.EMPTY)).isEmpty())
        assertFalse(seed(policy, 130))
        assertTrue(seed(policy, 170))
        assertEquals(HpBarFillColor.RED, check(policy, 200, sample()).single().sample.fillColor)
    }

    @Test fun `fresh green ordinary anchor may transition directly to measured red`() {
        val policy = TerminalHpWatchPolicy()
        assertTrue(seed(policy, bar = own.copy(filledFraction = .8f)))
        assertEquals(HpBarFillColor.RED, check(policy, 100, sample()).single().sample.fillColor)
        assertTrue(policy.isArmed(player, ms(100)))
    }

    @Test fun `empty without a preceding actual red never arms or emits zero`() {
        val policy = TerminalHpWatchPolicy()
        assertTrue(seed(policy))
        assertTrue(check(policy, 100, sample(color = HpBarFillColor.EMPTY)).isEmpty())
        assertFalse(policy.isArmed(player, ms(100)))
    }

    @Test fun `low fraction is not red and a red reading above quarter hp is not accepted`() {
        for (color in listOf(HpBarFillColor.GREEN, HpBarFillColor.YELLOW, HpBarFillColor.RED)) {
            val policy = TerminalHpWatchPolicy()
            assertTrue(seed(policy))
            val fraction = if (color == HpBarFillColor.RED) .4f else .10f
            assertTrue(check(policy, 100, sample(color = color, fraction = fraction)).isEmpty())
            assertFalse(policy.isArmed(player, ms(100)))
            assertTrue(check(policy, 130, sample(color = HpBarFillColor.EMPTY)).isEmpty())
        }
    }

    @Test fun `green or yellow after red disarms without invalidating an ordinary locator token`() {
        for (color in listOf(HpBarFillColor.GREEN, HpBarFillColor.YELLOW)) {
            val policy = TerminalHpWatchPolicy()
            assertTrue(seed(policy))
            val prior = check(policy, 100, sample()).single()
            assertTrue(check(policy, 130, sample(color = color, fraction = .4f)).isEmpty())
            assertTrue(policy.isCurrentGeneration(player, prior.generation))
            assertTrue(check(policy, 160, sample(color = HpBarFillColor.EMPTY)).isEmpty())
            assertTrue(seed(policy, 160))
            assertEquals(1, check(policy, 190, sample()).size)
        }
    }

    @Test fun `missing samples retain the arm briefly but never synthesize zero`() {
        val policy = TerminalHpWatchPolicy()
        assertTrue(seed(policy))
        check(policy, 100, sample())
        assertTrue(check(policy, 400, null).isEmpty())
        assertTrue(policy.isArmed(player, ms(400)))
        assertEquals(1, check(policy, 590, sample(color = HpBarFillColor.EMPTY)).size)
    }

    @Test fun `anchor freshness expires without calling the sampler beyond five hundred milliseconds`() {
        val policy = TerminalHpWatchPolicy()
        assertTrue(seed(policy))
        check(policy, 100, sample())
        val generation = policy.snapshotGeneration(player)
        var calls = 0
        val result = policy.checkFrame(width, height, ms(601)) { _, _ -> calls++; sample(color = HpBarFillColor.EMPTY) }
        assertTrue(result.isEmpty())
        assertEquals(0, calls)
        assertFalse(policy.isArmed(player, ms(601)))
        assertTrue("Sampling expiry does not invalidate an ordinary locator in flight",
            policy.isCurrentGeneration(player, generation))
        assertTrue(seed(policy, 601))
        assertEquals(1, check(policy, 620, sample()).size)
    }

    @Test fun `repeated red cannot extend a burst forever without another ordinary seed`() {
        val policy = TerminalHpWatchPolicy()
        assertTrue(seed(policy))
        for (at in 100L..2900L step 400) assertEquals(1, check(policy, at, sample()).size)
        assertTrue(check(policy, 3100, sample()).isEmpty())
        assertFalse(policy.isArmed(player, ms(3100)))
        assertTrue(check(policy, 3130, sample()).isEmpty())
        assertTrue(seed(policy, 3130))
        assertEquals(1, check(policy, 3160, sample()).size)
    }

    @Test fun `a same geometry ordinary refresh does not restart the hard burst timer`() {
        val policy = TerminalHpWatchPolicy()
        assertTrue(seed(policy))
        for (at in 100L..2900L step 400) assertEquals(1, check(policy, at, sample()).size)
        assertTrue(seed(policy, 3000))
        assertTrue(check(policy, 3100, sample()).isEmpty())
    }

    @Test fun `identity reset invalidates old results and cannot reuse a pre-boundary seed`() {
        val policy = TerminalHpWatchPolicy()
        assertTrue(seed(policy))
        val result = check(policy, 100, sample()).single()
        policy.reset(player, ms(120))
        assertFalse(policy.isCurrentGeneration(player, result.generation))
        assertFalse(seed(policy, 110))
        assertFalse(seed(policy, 120))
        assertTrue(check(policy, 130, sample(color = HpBarFillColor.EMPTY)).isEmpty())
        assertTrue(seed(policy, 130))
        assertEquals(1, check(policy, 160, sample()).size)
    }

    @Test fun `a delayed reset never rewinds its source cutoff`() {
        val policy = TerminalHpWatchPolicy()
        assertTrue(seed(policy))
        check(policy, 100, sample())
        policy.reset(player, ms(50))
        assertFalse(seed(policy, 90))
        assertTrue(seed(policy, 110))
    }

    @Test fun `display size changes clear both arms and invalidate both publication tokens`() {
        val policy = TerminalHpWatchPolicy()
        assertTrue(seed(policy))
        assertTrue(seed(policy, bar = enemy, side = opponent))
        val results = policy.checkFrame(width, height, ms(100)) { _, bar -> sample(bar) }
        assertEquals(2, results.size)
        var calls = 0
        assertTrue(policy.checkFrame(1080, 2280, ms(130)) { _, _ -> calls++; sample() }.isEmpty())
        assertEquals(0, calls)
        results.forEach { assertFalse(policy.isCurrentGeneration(it.side, it.generation)) }
        assertTrue(policy.armedSides(ms(130)).isEmpty())
        // A late old-sized frame must not switch the domain back again.
        val token = policy.snapshotGeneration(player)
        assertTrue(check(policy, 120, sample()).isEmpty())
        assertEquals(token, policy.snapshotGeneration(player))
        assertTrue(policy.seed(player, own, 1080, 2280, ms(130)))
    }

    @Test fun `out of order and duplicate checks do not call the sampler or rewind a newer red lock`() {
        val policy = TerminalHpWatchPolicy()
        assertTrue(seed(policy))
        val result = check(policy, 100, sample()).single()
        var calls = 0
        for (at in listOf(90L, 100L)) {
            assertTrue(policy.checkFrame(width, height, ms(at)) { _, _ -> calls++; sample(color = HpBarFillColor.EMPTY) }.isEmpty())
        }
        assertEquals(0, calls)
        assertFalse(seed(policy, 90))
        assertTrue(policy.isCurrentGeneration(player, result.generation))
        assertEquals(1, check(policy, 130, sample(color = HpBarFillColor.EMPTY)).size)
    }

    @Test fun `a completed null check permits an ordinary seed at that same source frame`() {
        val policy = TerminalHpWatchPolicy()
        assertTrue(seed(policy))
        assertTrue(check(policy, 100, null).isEmpty())
        assertTrue(seed(policy, 100))
        assertFalse(seed(policy, 100))
        assertEquals(1, check(policy, 130, sample()).size)
    }

    @Test fun `advancing empty fast checks do not starve a still fresh delayed ordinary locator`() {
        val policy = TerminalHpWatchPolicy()
        val generation = policy.snapshotGeneration(player)
        for (at in listOf(100L, 200L, 300L)) assertTrue(check(policy, at, null).isEmpty())
        assertTrue(policy.isCurrentGeneration(player, generation))
        assertTrue("An ordinary frame finishing 200 ms later is still a valid seed", seed(policy, 100))
        assertEquals(1, check(policy, 330, sample()).size)
        assertFalse("A delayed ordinary sample cannot rewind accepted red", seed(policy, 200))
    }

    @Test fun `advancing nonred checks preserve generation and permit delayed ordinary refresh`() {
        for (color in listOf(HpBarFillColor.GREEN, HpBarFillColor.YELLOW)) {
            val policy = TerminalHpWatchPolicy()
            assertTrue(seed(policy, bar = own.copy(filledFraction = .8f)))
            val generation = policy.snapshotGeneration(player)
            for (at in listOf(100L, 200L, 300L)) {
                assertTrue(check(policy, at, sample(color = color, fraction = .8f)).isEmpty())
                assertTrue(policy.isCurrentGeneration(player, generation))
            }
            assertTrue(seed(policy, 200, own.copy(filledFraction = .6f)))
            assertTrue(policy.isCurrentGeneration(player, generation))
            assertEquals(1, check(policy, 330, sample()).size)
        }
    }

    @Test fun `a delayed ordinary reading beyond freshness is rejected without changing generation`() {
        val policy = TerminalHpWatchPolicy()
        val generation = policy.snapshotGeneration(player)
        assertTrue(check(policy, 600, null).isEmpty())
        assertFalse(seed(policy, 0))
        assertTrue(policy.isCurrentGeneration(player, generation))
        assertTrue(seed(policy, 101))
    }

    @Test fun `ordinary evidence older than sampling freshness may still be published`() {
        val policy = TerminalHpWatchPolicy()
        assertTrue(check(policy, 800, null).isEmpty())
        assertFalse("A 700 ms old locator result cannot seed future narrow sampling", seed(policy, 100))
        assertTrue("Its captured geometry remains ordinary evidence", policy.canPublishOrdinary(player, ms(100)))
    }

    @Test fun `ordinary publication cannot cross an identity reset boundary`() {
        val policy = TerminalHpWatchPolicy()
        assertTrue(seed(policy))
        policy.reset(player, ms(200))
        assertFalse(policy.canPublishOrdinary(player, ms(100)))
        assertFalse(policy.canPublishOrdinary(player, ms(200)))
        assertTrue(policy.canPublishOrdinary(player, ms(201)))
        assertTrue("The other side has an independent cutoff", policy.canPublishOrdinary(opponent, ms(100)))
    }

    @Test fun `ordinary publication cannot replace a consumed zero with an older positive bar`() {
        val policy = TerminalHpWatchPolicy()
        assertTrue(seed(policy))
        check(policy, 100, sample())
        assertEquals(1, check(policy, 130, sample(color = HpBarFillColor.EMPTY)).size)
        assertFalse(policy.canPublishOrdinary(player, ms(100)))
        assertFalse(policy.canPublishOrdinary(player, ms(130)))
        assertTrue(policy.canPublishOrdinary(player, ms(131)))
    }

    @Test fun `a distant accepted ordinary bar breaks the old arm and invalidates its result`() {
        val policy = TerminalHpWatchPolicy()
        assertTrue(seed(policy))
        val red = check(policy, 100, sample()).single()
        val moved = own.copy(top = 700, bottom = 717)
        assertTrue(seed(policy, 130, moved))
        assertFalse(policy.isCurrentGeneration(player, red.generation))
        assertTrue(check(policy, 160, sample(moved, HpBarFillColor.EMPTY)).isEmpty())
    }

    @Test fun `sampler output cannot jump to a different bar or invent invalid geometry`() {
        for (bad in listOf(enemy, own.copy(filledFraction = Float.NaN), own.copy(right = 900))) {
            val policy = TerminalHpWatchPolicy()
            assertTrue(seed(policy))
            check(policy, 100, sample())
            assertTrue(check(policy, 130, TrackedTerminalHpBarSample(bad, HpBarFillColor.RED)).isEmpty())
            assertFalse(policy.isArmed(player, ms(130)))
        }
    }

    @Test fun `two sides have independent arms and a reset on one preserves the other`() {
        val policy = TerminalHpWatchPolicy()
        assertTrue(seed(policy))
        assertTrue(seed(policy, bar = enemy, side = opponent))
        val results = policy.checkFrame(width, height, ms(100)) { _, bar -> sample(bar) }
        policy.reset(player, ms(120))
        assertFalse(policy.isCurrentGeneration(player, results.single { it.side == player }.generation))
        assertTrue(policy.isCurrentGeneration(opponent, results.single { it.side == opponent }.generation))
        val zero = policy.checkFrame(width, height, ms(130)) { _, bar -> sample(bar, HpBarFillColor.EMPTY) }
        assertEquals(listOf(opponent), zero.map { it.side })
    }

    @Test fun `reset all and invalid seeds cannot leave a watch armed`() {
        val policy = TerminalHpWatchPolicy()
        assertFalse(seed(policy, bar = own.copy(filledFraction = 0f)))
        assertFalse(seed(policy, bar = own.copy(confidence = Float.NaN)))
        assertTrue(seed(policy))
        check(policy, 100, sample())
        policy.resetAll(ms(120))
        assertTrue(check(policy, 130, sample(color = HpBarFillColor.EMPTY)).isEmpty())
        assertTrue(policy.armedSides(ms(130)).isEmpty())
    }
}
