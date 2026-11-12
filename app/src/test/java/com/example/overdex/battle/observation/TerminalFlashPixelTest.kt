package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.TerminalFlashFeature
import com.example.overdex.battle.custody.TerminalParticleFeature
import com.example.overdex.battle.custody.TerminalParticleSample
import com.example.overdex.battle.inference.TerminalCollapseSequences
import com.example.overdex.battle.inference.TerminalEffectFrame
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TerminalFlashPixelTest {
    @Test fun `factory receiving crop and twenty-Hz sampling retain both recorded finish sequences`() {
        val root="/battle/recorded-evidence/2026-11-12-terminal-sequences/"
        val json=Json { ignoreUnknownKeys=true }
        val entries=javaClass.getResourceAsStream(root+"sequence-pixels.json")!!.bufferedReader().use {
            json.parseToJsonElement(it.readText()).jsonArray.map { it.jsonObject } }
        for ((stamp,all) in entries.groupBy { it.getValue("stamp").jsonPrimitive.content }) for (decimate in listOf(false,true)) {
            val reader=TerminalParticleFrameMeasurer(); var previousAt=Long.MIN_VALUE
            val frames=mutableListOf<TerminalEffectFrame>()
            for (c in all) {
                val at=c.getValue("sourceAtNanos").jsonPrimitive.long
                if (decimate && previousAt != Long.MIN_VALUE && at-previousAt<50_000_000L) continue
                previousAt=at
                val name=c.getValue("file").jsonPrimitive.content
                val bytes=javaClass.getResourceAsStream(root+name)!!.readBytes()
                assertEquals(c.getValue("sha256").jsonPrimitive.content,
                    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
                val image=ImageIO.read(bytes.inputStream())
                val b=c.getValue("bounds").jsonArray.map { it.jsonPrimitive.int }
                val bounds=BattleCropBounds(b[0],b[1],b[2],b[3])
                val r=reader.measure(672,1493,bounds,at) { x,y -> image.getRGB(x-b[0],y-b[1]) }
                frames+=TerminalEffectFrame(name,TerminalParticleSample(at,at/1_000_000,r.previousSourceAtNanos,
                    r.status.name,672,1493,b,1,frames.size.toLong(),0,r.cluster.map { TerminalParticleFeature(
                        it.x,it.y,it.area,it.width,it.height,it.freshFraction,it.haloCoverage,it.haloWeakestQuadrant,it.brightnessFalloff) },r.flashes))
            }
            assertEquals("$stamp decimated=$decimate",1,TerminalCollapseSequences.find(frames).size)
        }
    }

    @Test fun `native and capture-sized bright components agree with independent video reference`() {
        val root = "/battle/recorded-evidence/2026-11-12-terminal-sequences/"
        val json = Json { ignoreUnknownKeys = true }
        val cases = javaClass.getResourceAsStream(root + "pixels.json")!!.bufferedReader().use {
            json.parseToJsonElement(it.readText()).jsonArray }
        for (entry in cases) {
            val c = entry.jsonObject; fun number(k: String) = c.getValue(k).jsonPrimitive.int
            val bytes = javaClass.getResourceAsStream(root+c.getValue("file").jsonPrimitive.content)!!.readBytes()
            assertEquals(c.getValue("sha256").jsonPrimitive.content,
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
            val image = ImageIO.read(bytes.inputStream())
            val b = c.getValue("bounds").jsonArray.map { it.jsonPrimitive.int }
            val bounds = BattleCropBounds(b[0],b[1],b[2],b[3])
            val reader = TerminalParticleFrameMeasurer()
            val read: (Int,Int)->Int = { x,y -> image.getRGB(x-b[0],y-b[1]) }
            reader.measure(number("width"),number("height"),bounds,1,read)
            val result = reader.measure(number("width"),number("height"),bounds,50_000_001,read)
            val expected = c.getValue("flashes").jsonArray.map { json.decodeFromJsonElement<TerminalFlashFeature>(it) }
            assertEquals(c.getValue("file").toString(), expected, result.flashes)
        }
    }
}
