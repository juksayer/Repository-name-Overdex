package com.example.overdex.battle.observation

import java.util.Properties
import javax.imageio.ImageIO
import org.junit.Assert.*
import org.junit.Test

class RecordedCompleteHpBarTest {
    private fun read(name: String, collection: String = "2026-11-13-complete-bar"): CompleteHpBarSample? {
        val base="/battle/hp/$collection/$name"
        val info=Properties().apply { load(this@RecordedCompleteHpBarTest.javaClass.getResourceAsStream("$base.properties")) }
        fun n(key:String)=info.getProperty(key).toInt()
        val image=ImageIO.read(javaClass.getResource("$base.png"))
        return TrackedTerminalHpBarMeasurer.measureComplete(n("sourceWidth"),n("sourceHeight"),
            ActiveHpBarMeasurement(n("left"),n("top"),n("right"),n("bottom"),.1f,.9f)) { x,y ->
            assertTrue(x in n("cropLeft") until n("cropRight") && y in n("cropTop") until n("cropBottom"))
            image.getRGB(x-n("cropLeft"),y-n("cropTop"))
        }
    }
    @Test fun `recorded Sealeo survivor is never orange only or zero`() {
        for (t in listOf("41.299011","42.905467")) {
            val sample=requireNotNull(read("p_sealeo_nonlethal_charge-$t"))
            assertTrue(sample.liveColumns>0)
            assertFalse(sample.orangeOnly)
            assertNotEquals(HpBarFillColor.EMPTY,sample.hpSample?.fillColor)
        }
    }
    @Test fun `recorded Cramorant orange remainder keeps complete geometry without inventing zero`() {
        val sample=requireNotNull(read("cramorant_retained_ko-178.553633"))
        assertTrue(sample.damageColumns>0)
        assertTrue(sample.orangeOnly)
        assertNull(sample.hpSample)
    }

    @Test fun `recorded Turtonator blue tail is masked but ambiguous edge still vetoes terminal color`() {
        val sample=requireNotNull(read("p_turtonator_fast_ko-78.402311", "2026-11-14-background-mask"))
        assertEquals(143,sample.backgroundMaskedColumns)
        assertEquals(2,sample.unmaskedUncertainColumns)
        assertEquals(8,sample.damageColumns)
        assertEquals(0,sample.liveColumns)
        assertFalse(sample.orangeOnly)
        assertNull(sample.hpSample)
    }
}
