package com.example.overdex.battle.observation

import java.util.Properties
import javax.imageio.ImageIO
import org.junit.Assert.*
import org.junit.Test

class RecordedCompleteHpBarTest {
    private fun read(name: String, collection: String = "2026-11-13-complete-bar",
                     overridePixel: (Int, Int, Int) -> Int = { _, _, color -> color }): CompleteHpBarSample? {
        val base="/battle/hp/$collection/$name"
        val info=Properties().apply { load(this@RecordedCompleteHpBarTest.javaClass.getResourceAsStream("$base.properties")) }
        fun n(key:String)=info.getProperty(key).toInt()
        val image=ImageIO.read(javaClass.getResource("$base.png"))
        return TrackedTerminalHpBarMeasurer.measureComplete(n("sourceWidth"),n("sourceHeight"),
            ActiveHpBarMeasurement(n("left"),n("top"),n("right"),n("bottom"),.1f,.9f)) { x,y ->
            assertTrue(x in n("cropLeft") until n("cropRight") && y in n("cropTop") until n("cropBottom"))
            overridePixel(x, y, image.getRGB(x-n("cropLeft"),y-n("cropTop")))
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

    @Test fun `Swampert later hit changes red to orange without a measured empty bar`() {
        val live = requireNotNull(read("live", "2026-11-14-swampert-fade"))
        assertTrue(live.liveColumns > 0)
        assertFalse(live.orangeOnly)
        for (name in listOf("orange", "orange-later")) {
            val sample = requireNotNull(read(name, "2026-11-14-swampert-fade"))
            assertTrue("Dim possible live colors remain unresolved", sample.unmaskedUncertainColumns > 0)
            assertTrue(sample.damageColumns > 0)
            assertFalse("Ambiguous pixels cannot establish an orange-only bar", sample.orangeOnly)
        }
        for (name in listOf("orange", "orange-later", "fading")) {
            val sample = read(name, "2026-11-14-swampert-fade")
            println("Swampert $name: $sample")
            assertNotEquals("Fading is not measured depletion", HpBarFillColor.EMPTY, sample?.hpSample?.fillColor)
            assertNull("Orange/fading pixels must not establish surviving HP", sample?.hpSample)
        }
    }
    @Test fun `injected tiny surviving HP stays protected on real Swampert orange frames`() {
        for ((name, y) in listOf("orange" to 922, "orange-later" to 935)) {
            for (color in listOf(0xffe84d40.toInt(), 0xffe8db40.toInt(), 0xff40e8ab.toInt(),
                                 0xff733529.toInt(), 0xff71632a.toInt(), 0xff286d42.toInt())) {
                val sample = requireNotNull(read(name, "2026-11-14-swampert-fade") { x, py, original ->
                    if (x == 620 && py == y) color else original
                })
                assertFalse("One possible live pixel must block orange-only: $name / $color", sample.orangeOnly)
                assertTrue("The injected pixel must be accounted for", sample.liveColumns > 0 || sample.unmaskedUncertainColumns > 0)
                assertNotEquals(HpBarFillColor.EMPTY, sample.hpSample?.fillColor)
            }
        }
    }
    @Test fun `a single injected live pixel cancels a recorded orange-only Cramorant bar`() {
        val name = "cramorant_retained_ko-178.553633"
        assertTrue(requireNotNull(read(name)).orangeOnly)
        for (color in listOf(0xffe84d40.toInt(), 0xffe8db40.toInt(), 0xff40e8ab.toInt(),
                             0xff733529.toInt(), 0xff71632a.toInt(), 0xff286d42.toInt())) {
            val sample = requireNotNull(read(name) { x, y, original ->
                if (x == 400 && y == 943) color else original
            })
            assertFalse("Single bright or dim survivor cannot be masked", sample.orangeOnly)
            assertTrue(sample.liveColumns > 0 || sample.unmaskedUncertainColumns > 0)
            assertNotEquals(HpBarFillColor.EMPTY, sample.hpSample?.fillColor)
        }
    }
}
