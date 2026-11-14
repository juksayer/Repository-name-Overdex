package com.example.overdex.battle.observation

import org.junit.Assert.*
import org.junit.Test

class CompleteHpBarBackgroundMaskTest {
    private val anchor = ActiveHpBarMeasurement(40,80,240,96,.05f,.95f)
    private val blueBackground = 0xff1c2f51.toInt()
    private val damageColor = 0xffffa124.toInt()

    private fun read(overridePixel: (Int,Int,Int)->Int = { _,_,c -> c }): CompleteHpBarSample? =
        TrackedTerminalHpBarMeasurer.measureComplete(400,300,anchor) { x,y ->
            val color = when {
                (y==80 || y==96) && x in 40..239 -> -1
                (x==40 || x==239) && y in 80..96 -> -1
                x in 42..49 && y in 82..94 -> damageColor
                else -> blueBackground
            }
            overridePixel(x,y,color)
        }

    @Test fun `dim non HP background after remaining damage fill is excluded only from color test`() {
        val sample = requireNotNull(read())
        assertTrue(sample.orangeOnly)
        assertTrue(sample.backgroundMaskedColumns>100)
        assertEquals(0,sample.unmaskedUncertainColumns)
        assertEquals(8,sample.damageColumns)
        assertEquals(0,sample.liveColumns)
        assertNull(sample.hpSample)
    }

    @Test fun `one surviving live pixel anywhere in whole interior prevents masking and terminal candidate`() {
        for (color in listOf(0xffe84d40.toInt(),0xffe8db40.toInt(),0xff40e8ab.toInt())) {
            for (x in listOf(42,49,50,170,237)) {
                val sample = requireNotNull(read { px,y,c -> if (px==x && y==87) color else c })
                assertTrue(sample.liveColumns>0)
                assertEquals(0,sample.backgroundMaskedColumns)
                assertFalse(sample.orangeOnly)
                assertNotEquals(HpBarFillColor.EMPTY,sample.hpSample?.fillColor)
            }
        }
    }

    @Test fun `dim possible HP is unresolved even after a bright damage prefix`() {
        for (color in listOf(0xff733529.toInt(),0xff71632a.toInt(),0xff286d42.toInt())) {
            val sample = requireNotNull(read { x,y,c -> if (x==170 && y==87) color else c })
            assertFalse(sample.orangeOnly)
            assertTrue(sample.unmaskedUncertainColumns>0)
        }
    }

    @Test fun `uncertainty inside remaining fill and bright effects in tail are not masked`() {
        for ((x,color) in listOf(45 to blueBackground,170 to 0xffab40df.toInt())) {
            val sample = requireNotNull(read { px,y,c -> if (px==x && y==87) color else c })
            assertFalse(sample.orangeOnly)
            assertTrue(sample.unmaskedUncertainColumns>0)
        }
    }

    @Test fun `missing end caps cannot be repaired by background mask`() {
        assertNull(read { x,_,c -> if (x==239) blueBackground else c })
    }

    @Test fun `background alone and scattered orange cannot create a terminal remainder`() {
        val noFill = requireNotNull(read { _,_,c -> if (c==damageColor) blueBackground else c })
        assertFalse(noFill.orangeOnly)
        assertEquals(0,noFill.backgroundMaskedColumns)
        val scattered = requireNotNull(read { x,_,c -> if (c==damageColor && x%2==0) blueBackground else c })
        assertFalse(scattered.orangeOnly)
        assertEquals(0,scattered.backgroundMaskedColumns)
    }

    @Test fun `tiny survivor cancels sustained masked color candidate without publishing zero`() {
        val side = com.example.overdex.battle.custody.ActivePokemonSide.OPPONENT
        val track = CompleteHpBarTrack()
        val masked = requireNotNull(read())
        track.accept(side,masked,0L,track.generation(side))
        track.accept(side,masked,50_000_000L,track.generation(side))
        assertEquals("SUSTAINED_DAMAGE_COLOR",
            track.accept(side,masked,100_000_000L,track.generation(side))?.status)
        val survivor = requireNotNull(read { x,y,c -> if (x==170 && y==87) 0xffe84d40.toInt() else c })
        val change = requireNotNull(track.accept(side,survivor,125_000_000L,track.generation(side)))
        assertEquals("LIVE_COLOR",change.status)
        assertNull(change.candidateSince)
        assertFalse(change.sample.orangeOnly)
        assertNotEquals(HpBarFillColor.EMPTY,change.sample.hpSample?.fillColor)
    }
}
