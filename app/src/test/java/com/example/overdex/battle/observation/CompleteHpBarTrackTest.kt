package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import org.junit.Assert.*
import org.junit.Test

class CompleteHpBarTrackTest {
    private val side = ActivePokemonSide.OPPONENT
    private val anchor = ActiveHpBarMeasurement(40, 80, 240, 96, .05f, .95f)
    private fun pixels(live: Int = 0, damage: Int = 100, missingCap: Boolean = false): (Int, Int) -> Int = { x, y ->
        when {
            (y == 80 || y == 96) && x in 40..239 -> -1
            (x == 40 || x == 239 && !missingCap) && y in 80..96 -> -1
            x in 42 until 42 + live && y in 82..94 -> 0xffe84d40.toInt()
            x in 42 + live until 42 + live + damage && y in 82..94 -> 0xffffa124.toInt()
            else -> 0xff202020.toInt()
        }
    }
    private fun sample(live: Int = 0, damage: Int = 100) = requireNotNull(
        TrackedTerminalHpBarMeasurer.measureComplete(400, 300, anchor, pixelAt = pixels(live, damage)))

    @Test fun `live damage trail changes are published without a faint or empty hp`() {
        val track = CompleteHpBarTrack()
        assertEquals("LIVE_COLOR", track.accept(side, sample(live = 1, damage = 0), 1, 0)?.status)
        val damage = requireNotNull(track.accept(side, sample(live = 1), 50_000_001, 0))
        assertEquals("LIVE_COLOR", damage.status)
        assertEquals(1, damage.sample.liveColumns)
        assertTrue(damage.sample.damageColumns > 0)
        assertNull(damage.candidateSince)
        assertNotEquals(HpBarFillColor.EMPTY, damage.sample.hpSample?.fillColor)
        assertNull(track.accept(side, sample(live = 1), 60_000_001, 0))
        assertEquals("LIVE_COLOR", track.accept(side, sample(live = 1, damage = 0), 70_000_001, 0)?.status)
    }

    @Test fun `unavailable frame breaks a color run without inventing a measurement`() {
        val track = CompleteHpBarTrack()
        track.accept(side, sample(), 1, 0)
        track.accept(side, sample(), 50_000_001, 0)
        track.unavailable(side, 75_000_001, 0)
        val restarted = requireNotNull(track.accept(side, sample(), 100_000_001, 0))
        assertEquals("DAMAGE_COLOR", restarted.status)
        assertEquals(100_000_001L, restarted.candidateSince)
        assertNull(track.accept(side, sample(), 150_000_001, 0))
        assertEquals("SUSTAINED_DAMAGE_COLOR", track.accept(side, sample(), 200_000_001, 0)?.status)
    }

    @Test fun `delayed sample cannot bridge a later unavailable frame`() {
        val track = CompleteHpBarTrack()
        track.accept(side, sample(), 1, 0)
        track.unavailable(side, 80_000_001, 0)
        track.accept(side, sample(), 50_000_001, 0)
        val restarted = requireNotNull(track.accept(side, sample(), 100_000_001, 0))
        assertEquals("DAMAGE_COLOR", restarted.status)
        assertEquals(100_000_001L, restarted.candidateSince)
        track.unavailable(side, 110_000_001, -1)
        track.accept(side, sample(), 150_000_001, 0)
        assertEquals("SUSTAINED_DAMAGE_COLOR", track.accept(side, sample(), 200_000_001, 0)?.status)
    }

    @Test fun `whole orange remainder retains geometry but never reports zero hp`() {
        val sample = sample()
        assertTrue(sample.orangeOnly)
        assertNull(sample.hpSample)
        assertEquals(100, sample.damageColumns)
        assertEquals(0, sample.liveColumns)
        assertEquals(BattleCropBounds(40,80,240,96), sample.bounds)
    }
    @Test fun `one live source column blocks terminal color even with a large damage trail`() {
        val sample = sample(1)
        assertEquals(1, sample.liveColumns)
        assertFalse(sample.orangeOnly)
        // Unknown fraction is not an empty bar.
        assertNotEquals(HpBarFillColor.EMPTY, sample.hpSample?.fillColor)
    }
    @Test fun `fragment and disappeared outline stay unknown`() {
        assertNull(TrackedTerminalHpBarMeasurer.measureComplete(400,300,anchor,pixelAt=pixels(missingCap=true)))
        assertNull(TrackedTerminalHpBarMeasurer.measureComplete(400,300,anchor) { _, _ -> 0xff202020.toInt() })
    }
    @Test fun `sustained color needs source time samples but never requires fading`() {
        val track = CompleteHpBarTrack()
        val generation = track.generation(side)
        assertEquals("DAMAGE_COLOR",track.accept(side,sample(),1,generation)?.status)
        assertNull(track.accept(side,sample(),50_000_001,generation))
        val change = requireNotNull(track.accept(side,sample(),100_000_001,generation))
        assertEquals("SUSTAINED_DAMAGE_COLOR",change.status)
        assertEquals(1L,change.candidateSince)
        assertEquals(255f,change.sample.railBrightness,0f)
        assertNull(track.accept(side,sample(),150_000_001,generation))
        assertEquals("LIVE_COLOR",track.accept(side,sample(1),200_000_001,generation)?.status)
    }
    @Test fun `appearance reset rejects in flight and older pixels even for same species geometry`() {
        val track = CompleteHpBarTrack()
        val old = track.generation(side)
        track.accept(side,sample(),100,old)
        track.reset(side,200)
        assertTrue(track.requests(201).isEmpty())
        assertNull(track.accept(side,sample(),300,old))
        assertNull(track.accept(side,sample(),199,track.generation(side)))
        assertEquals("DAMAGE_COLOR",track.accept(side,sample(),300,track.generation(side))?.status)
    }
    @Test fun `reordered or widely spaced frames cannot sustain an orange sequence`() {
        val track = CompleteHpBarTrack()
        track.accept(side,sample(),200_000_000,0)
        assertNull(track.accept(side,sample(),100_000_000,0))
        track.accept(side,sample(),400_000_000,0)
        assertNull(track.accept(side,sample(),450_000_000,0))
        assertEquals("SUSTAINED_DAMAGE_COLOR",track.accept(side,sample(),500_000_000,0)?.status)
        assertTrue(track.requests(1_100_000_000).isEmpty())
    }
    @Test fun `resize and disable invalidate requests without producing empty hp`() {
        val track=CompleteHpBarTrack()
        track.frameSize(400,300)
        track.accept(side,sample(),1,0)
        val request=track.requests(2).single()
        track.frameSize(500,300)
        assertTrue(track.requests(3).isEmpty())
        assertNull(track.accept(side,sample(),4,request.generation))
        track.resetAll(10)
        assertNull(track.accept(side,sample(),9,track.generation(side)))
    }
    @Test fun `charged acquisition accepts orange geometry independently of hp amount`() {
        val shifted: (Int,Int)->Int = {x,y -> pixels()(x-60,y-50)}
        val complete=requireNotNull(ChargedImpactHpBarMeasurer.acquireComplete(400,300,anchor,pixelAt=shifted))
        assertTrue(complete.orangeOnly)
        assertNull(complete.hpSample)
        assertNull(ChargedImpactHpBarMeasurer.acquire(400,300,anchor,pixelAt=shifted))
    }
}
