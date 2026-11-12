package com.example.overdex.battle.observation

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.custody.TerminalParticleSample
import com.example.overdex.battle.inference.TerminalAppearanceSequences
import com.example.overdex.battle.inference.TerminalEffectFrame
import com.example.overdex.data.BattleCalibration
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.File

/** Original decoded video pixels through Android scaling, not a live-match acceptance test. */
class TerminalAppearanceReaderDeviceTest {
    @Test fun chargedEndsLivingRecallAndBrightOcclusionRemainDistinctWithAndroidResampling() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val fixtureDirectory=InstrumentationRegistry.getArguments().getString("appearanceFixtureDirectory")
        assumeTrue("Provide the original decoded video fixture directory for this recorded-image check",fixtureDirectory!=null)
        val directory=File(requireNotNull(fixtureDirectory))
        val cases=File(directory,"manifest.json").bufferedReader().use {
            Json.parseToJsonElement(it.readText()).jsonArray
        }
        val calibration=BattleCalibration()
        val regions=listOf(calibration.playerAppearanceRegion,calibration.appearanceColorReferenceRegion,
            calibration.appearanceBackgroundRegion)
        cases.forEach { item ->
            val data=item.jsonObject; val name=data.getValue("name").jsonPrimitive.content
            val reader=TerminalAppearanceFrameMeasurer()
            val times=mutableListOf<Long>()
            try {
                val frames=data.getValue("frames").jsonArray.mapIndexed { i,entry ->
                    val row=entry.jsonObject
                    val at=row.getValue("at").jsonPrimitive.long
                    val bitmap=File(directory,row.getValue("file").jsonPrimitive.content).inputStream().use {
                        requireNotNull(BitmapFactory.decodeStream(it))
                    }
                    try {
                        val began=System.nanoTime()
                        val a=reader.measure(bitmap,regions,at)
                        val elapsed=System.nanoTime()-began
                        times+=elapsed
                        // Studio videos are 816x1808; source geometry determines a 336x744 raster.
                        assertEquals(336,a.width)
                        assertEquals(744,a.height)
                        assertEquals(regions.map { r -> BattleCropResolver.resolveRect(r,a.width,a.height)!!.let {
                            listOf(it.left,it.top,it.right,it.bottom) } },a.regions)
                        TerminalEffectFrame("$name-$i",TerminalParticleSample(at,0,null,"UNREADABLE",bitmap.width,
                            bitmap.height,null,1,i.toLong(),elapsed,emptyList(),appearance=a))
                    } finally { bitmap.recycle() }
                }
                val sequences=TerminalAppearanceSequences.find(frames)
                val expected=data["expectedClearNanos"]?.jsonPrimitive?.longOrNull
                if(expected==null) assertTrue(name,sequences.isEmpty()) else {
                    assertEquals(name,1,sequences.size)
                    assertEquals(name,expected.toDouble(),sequences.single().clearedAt.toDouble(),150_000_000.0)
                }
                val sorted=times.sorted()
                val report="$name: frames=${frames.size}, clear=${sequences.map { it.clearedAt }}, " +
                    "medianMs=${sorted[sorted.size/2]/1e6}, p95Ms=${sorted[(sorted.lastIndex*.95).toInt()]/1e6}"
                instrumentation.sendStatus(0,Bundle().apply { putString("appearance_reader",report) })
            } finally { reader.close() }
        }
    }
}
