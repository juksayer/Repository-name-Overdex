package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in recording experiment. Writes measurements, never changes recognition thresholds. */
class RecordedDualViewHpReaderStudyTest {
    @Test fun `compare original and expanded territories using production whole bar readers`() {
        val directory = System.getenv("OVERDEX_DUAL_HP_STUDY")
        assumeTrue("Set OVERDEX_DUAL_HP_STUDY to the external recording fixture directory", directory != null)
        val root = File(requireNotNull(directory))
        assertTrue("Extraction must finish before replay begins", File(root,"cases.json").isFile)
        val rows = File(root, "frames.tsv").readLines().drop(1).map { it.split('\t') }
        assertTrue(rows.isNotEmpty())
        File(root, "reader-results.tsv").printWriter().use { out ->
            out.println("case\tdevice\tlabel\tbox\tpathMode\ttimeNs\tgeometry\tstatus\tliveColumns\tdamageColumns\torangeOnly\thpColor\thpFraction\tleft\ttop\tright\tbottom\tbackgroundMaskedColumns\tunmaskedUncertainColumns")
            for ((case, frames) in rows.groupBy { it[0] }) {
                val device = frames.first()[1]
                val label = frames.first()[2]
                val charge = frames.first()[3] == "charge"
                val side = ActivePokemonSide.valueOf(frames.first()[4])
                val height = if (device == "pixel") 1419 else 1493
                val regions = if (device == "pixel") mapOf(
                    ActivePokemonSide.PLAYER to BattleCropBounds(19,319,332,990),
                    ActivePokemonSide.OPPONENT to BattleCropBounds(325,341,653,792),
                ) else mapOf(
                    ActivePokemonSide.PLAYER to BattleCropBounds(35,367,241,1020),
                    ActivePokemonSide.OPPONENT to BattleCropBounds(312,329,576,834),
                )
                for (box in listOf("original", "expanded")) {
                    val impact = if (device == "pixel") BattleCropBounds(228,439,447,if (box == "expanded") 951 else 887)
                        else BattleCropBounds(228,634,440,1015)
                    val territory = if (charge) requireNotNull(ChargedImpactHpBarMeasurer.sharedTerritory(672,height,impact,regions.getValue(side)))
                        else regions.getValue(side)
                    for (pathMode in if (charge) listOf("acquire_each_frame", "acquire_then_track") else listOf("normal_then_track")) {
                        val normal = BattlefieldHpTracker()
                        val track = CompleteHpBarTrack()
                        track.frameSize(672,height)
                        var anchor: ActiveHpBarMeasurement? = null
                        var lastAt: Long? = null
                        var lastStatus = "UNAVAILABLE"
                        val reference = ActiveHpBarMeasurement(370,551,562,569,.7f,.95f)
                        var previousTime = Long.MIN_VALUE
                        for (row in frames) {
                            val at = row[5].toLong()
                            assertTrue("Recording timestamps must increase", at > previousTime)
                            previousTime = at
                            val image = ImageIO.read(File(root,row[6]))
                            assertEquals(672,image.width); assertEquals(height,image.height)
                            val pixels = image.getRGB(0,0,672,height,null,0,672)
                            val pixel: (Int,Int)->Int = { x,y -> pixels[y*672+x] }
                            val sample = if (charge) {
                                if (anchor == null || pathMode == "acquire_each_frame")
                                    ChargedImpactHpBarMeasurer.acquireComplete(672,height,reference,territory,pixel)
                                else TrackedTerminalHpBarMeasurer.measureComplete(672,height,requireNotNull(anchor),territory,pixel)
                            } else {
                                val located = normal.measure(672,height,at,regions,pixel)[side]
                                val prior = anchor?.takeIf { lastAt != null && at-requireNotNull(lastAt) <= CompleteHpBarTrack.MAX_GAP }
                                (located ?: prior)?.let { TrackedTerminalHpBarMeasurer.measureComplete(672,height,it,territory,pixel) }
                            }
                            if (sample != null) {
                                assertTrue(sample.bounds.left >= territory.left && sample.bounds.right <= territory.right)
                                assertTrue(sample.bounds.top >= territory.top && sample.bounds.bottom < territory.bottom)
                                assertTrue(sample.interiorColumns >= 32)
                                if (sample.orangeOnly) {
                                    assertEquals(0,sample.liveColumns)
                                    assertNotEquals(HpBarFillColor.EMPTY,sample.hpSample?.fillColor)
                                }
                                anchor = sample.anchor(); lastAt = at
                                track.accept(side,sample,at,track.generation(side))?.let { lastStatus=it.status }
                            }
                            out.println(listOf(case,device,label,box,pathMode,at,sample!=null,
                                if (sample==null) "UNAVAILABLE" else lastStatus,
                                sample?.liveColumns,sample?.damageColumns,sample?.orangeOnly,
                                sample?.hpSample?.fillColor,sample?.hpSample?.measurement?.filledFraction,
                                sample?.bounds?.left,sample?.bounds?.top,sample?.bounds?.right,sample?.bounds?.bottom,
                                sample?.backgroundMaskedColumns,sample?.unmaskedUncertainColumns).joinToString("\t") { it?.toString() ?: "" })
                        }
                    }
                }
            }
        }
    }
}
