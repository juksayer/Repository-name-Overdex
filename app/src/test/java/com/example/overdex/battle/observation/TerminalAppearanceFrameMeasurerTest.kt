package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.inference.*
import org.junit.Assert.*
import org.junit.Test

class TerminalAppearanceFrameMeasurerTest {
    private val regions = listOf(BattleCropBounds(0,64,40,120),
        BattleCropBounds(44,64,64,120), BattleCropBounds(0,20,40,50))
    private fun pixels(bodyHeight: Int, bright: Boolean = false, backgroundOffset: Int = 0): IntArray =
        IntArray(64*128) { i ->
            val x=i%64; val y=i/64
            when {
                x in 5..24 && y in 70 until 70+bodyHeight -> 0xffa03020.toInt()
                bright && x in 5..24 && y in 70..89 -> 0xffeeeeee.toInt()
                else -> (if ((x+y)%2==0) 40 else 100).plus(backgroundOffset).let {
                    0xff000000.toInt() or (it shl 16) or (it shl 8) or it
                }
            }
        }
    private fun frame(a: TerminalAppearanceMeasurement, at: Long) = TerminalEffectFrame("source-$at",
        TerminalParticleSample(at,0,null,"UNREADABLE",64,128,null,1,at,0,emptyList(),appearance=a))
    private fun sequence(bright: Boolean=false, changeBackground: Boolean=false): List<TerminalEffectFrame> {
        val reader=TerminalAppearanceFrameMeasurer()
        return listOf(0 to 20,100 to 20,300 to 10,400 to 0).map { (ms,height) ->
            val at=ms*1_000_000L
            frame(reader.measure(64,128,regions,at,pixels(height,bright && ms>=300,
                if(changeBackground && ms>=300) 30 else 0)),at)
        }
    }

    @Test fun `measured color contraction is independent of an unavailable particle crop`() {
        val frames=sequence()
        assertEquals("BASELINE",frames.first().sample.appearance!!.status)
        val seq=TerminalAppearanceSequences.find(frames).single()
        assertEquals(100_000_000L,seq.startedAt)
        assertEquals(300_000_000L,seq.contractedAt)
        assertEquals(400_000_000L,seq.clearedAt)
        val source=frames[1].sample.appearance!!.supports.single()
        assertEquals(400,source.baselineArea)
        assertEquals(400,source.baselineLargestComponent)
        assertEquals(200,frames[2].sample.appearance!!.supports.first().area)
        assertEquals(0,frames[3].sample.appearance!!.supports.first().area)
        assertEquals(seq,TerminalAppearanceSequences.find(frames.reversed()+frames).single())
        for(status in listOf("BASELINE","SOURCE_GAP","GEOMETRY_CHANGED","INVALID","CLUTTER")) {
            assertEquals("An independent particle interruption must not erase valid appearance: $status",seq,
                TerminalAppearanceSequences.find(frames.map { it.copy(sample=it.sample.copy(status=status)) }).single())
        }
    }

    @Test fun `bright occlusion and background motion do not become disappearance`() {
        assertTrue(TerminalAppearanceSequences.find(sequence(bright=true)).isEmpty())
        assertTrue(TerminalAppearanceSequences.find(sequence(changeBackground=true)).isEmpty())
        val f=sequence()
        assertTrue("An abrupt absent mask lacks contraction",TerminalAppearanceSequences.find(f.filterIndexed { i,_ -> i!=2 }).isEmpty())
    }

    @Test fun `missing source frames metadata conflicts and skipped baselines cannot be bridged`() {
        val frames=sequence()
        val clear=frames.last()
        val a=clear.sample.appearance!!
        val invalid=listOf(
            clear.sample.copy(status="OBSCURED"),
            clear.sample.copy(publicationStream=2),
            clear.sample.copy(publicationSequence=1),
            clear.sample.copy(sourceWidth=65),
            clear.sample.copy(appearance=a.copy(status="SOURCE_GAP")),
            clear.sample.copy(appearance=a.copy(previousSourceAtNanos=1)),
            clear.sample.copy(appearance=a.copy(backgroundChange=Double.NaN)),
            clear.sample.copy(appearance=a.copy(regions=regions.reversed().map { listOf(it.left,it.top,it.right,it.bottom) })),
            clear.sample.copy(appearance=a.copy(supports=emptyList())),
        )
        invalid.forEach { s -> assertTrue(s.toString(),TerminalAppearanceSequences.find(frames.dropLast(1)+clear.copy(sample=s)).isEmpty()) }
        assertTrue(TerminalAppearanceSequences.find(frames+clear.copy(sample=clear.sample.copy(status="OBSCURED"))).isEmpty())
        assertTrue(TerminalAppearanceSequences.find(frames.filterIndexed { i,_ -> i!=1 }).isEmpty())
        val throttled=clear.copy(articleId="throttled",sample=clear.sample.copy(sourceAtNanos=350_000_000,
            appearance=a.copy(status="THROTTLED")))
        assertEquals(TerminalAppearanceSequences.find(frames),TerminalAppearanceSequences.find(frames+throttled))
    }

    @Test fun `reader resets retained colors after source gaps geometry changes and unavailable pixels`() {
        val r=TerminalAppearanceFrameMeasurer()
        r.measure(64,128,regions,0,pixels(20))
        assertTrue(r.measure(64,128,regions,100_000_000,pixels(20)).supports.isNotEmpty())
        assertEquals("OLD_FRAME",r.measure(64,128,regions,99_000_000,pixels(0)).status)
        assertEquals("SOURCE_GAP",r.measure(64,128,regions,400_000_000,pixels(0)).status)
        assertTrue(r.measure(64,128,regions,500_000_000,pixels(0)).supports.isEmpty())
        val moved=regions.toMutableList().also { it[0]=BattleCropBounds(1,64,40,120) }
        assertEquals("GEOMETRY_CHANGED",r.measure(64,128,moved,600_000_000,pixels(20)).status)
        assertEquals("UNREADABLE",r.measure(64,128,moved,700_000_000,IntArray(64*128)).status)
        assertEquals("BASELINE",r.measure(64,128,moved,800_000_000,pixels(20)).status)
        assertEquals("INVALID",r.measure(64,128,emptyList(),900_000_000,pixels(20)).status)
    }
}
