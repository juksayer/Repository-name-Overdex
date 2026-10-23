package com.example.overdex.battle.observation

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class ChargeButtonFillHeightReaderTest {
    @Test fun `height ignores central icon and rejects missing or multiple separators`() {
        val w=240; val h=240; val pixels=IntArray(w*h) { 0xff222244.toInt() }
        fun line(y: Int) { for (x in 50..190) pixels[y*w+x]=-1 }
        // Central icon is deliberately white and tall.
        for(y in 80..160) for(x in 110..130) pixels[y*w+x]=-1
        assertNull(ChargeButtonFillHeightReader.separator(w,h,pixels,120,120,75))
        line(150)
        assertEquals(150f,ChargeButtonFillHeightReader.separator(w,h,pixels,120,120,75)!!,.001f)
        line(100)
        assertNull(ChargeButtonFillHeightReader.separator(w,h,pixels,120,120,75))
        pixels.fill(-1)
        assertNull(ChargeButtonFillHeightReader.separator(w,h,pixels,120,120,75))
    }
    @Test fun `layer ambiguity is retained and two different costs constrain eighty energy`() {
        val left=ChargeButtonFillHeightReader.energyRanges((75-33.5f)/150,35,2f/150)
        val right=ChargeButtonFillHeightReader.energyRanges((75-7f)/150,55,2f/150)
        assertEquals(3,left.size)
        val joint=left.flatMap { a -> right.mapNotNull { b ->
            val low=max(a.start,b.start); val high=min(a.endInclusive,b.endInclusive)
            if(low<=high) low..high else null
        }}
        assertEquals(1,joint.size)
        assertTrue(80f in joint.single())
        assertTrue(ChargeButtonFillHeightReader.energyRanges(null,35,.01f).isEmpty())
    }
    @Test fun `ring search finds a translated circle and blank field stays unknown`() {
        val w=660; val h=250; val pixels=IntArray(w*h) { 0xff222244.toInt() }
        assertNull(ChargeButtonFillHeightReader.measure(w,h,pixels,0))
        for(y in 0 until h) for(x in 0 until w) {
            if(abs(hypot((x-180).toDouble(),(y-140).toDouble())-80)<2) pixels[y*w+x]=-1
        }
        for(y in 154..156) for(x in 120..240) pixels[y*w+x]=-1
        val reading=ChargeButtonFillHeightReader.measure(w,h,pixels,0)!!
        assertEquals(180.0,reading.centerX.toDouble(),2.0)
        assertEquals(140.0,reading.centerY.toDouble(),2.0)
        assertNotNull(reading.fraction)
    }
}
