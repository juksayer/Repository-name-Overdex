package com.example.overdex.data

import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class Pixel4CalibrationSamplesTest {
    private val assets = File("src/main/assets")
    private val catalog = File(assets, BundledCalibrationSampleCatalog.ASSET_PATH).readText()

    @Test fun `supplied native Pixel pack retains geometry hashes and coverage without gallery controls`() {
        val entries = Json.parseToJsonElement(catalog).jsonObject.getValue("entries").jsonArray
            .map { it.jsonObject }.filter { it["deviceLabel"]?.jsonPrimitive?.content == "Google Pixel 4" }
        val labels = BundledCalibrationSampleCatalog.labels(catalog).filterValues { it.deviceLabel == "Google Pixel 4" }
        assertEquals(34, entries.size) // 33 supplied, plus the extra gallery screenshot discovered in the folder.
        assertEquals(32, labels.size)
        assertFalse(labels.keys.any { "152222" in it || "153001" in it })
        for (entry in entries.filter { it["calibrationEligible"]?.jsonPrimitive?.content == "true" }) {
            val name = entry.getValue("assetFile").jsonPrimitive.content
            val file = File(assets, "battle_samples/$name")
            val expectedHash = entry.getValue("assetSha256").jsonPrimitive.content
            val hash = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            assertEquals(name, expectedHash, hash)
            val image = ImageIO.read(file)
            assertNotNull(name, image)
            assertEquals(name, 1080, image.width)
            assertEquals(name, 2280, image.height)
            assertEquals("Google Pixel 4", labels.getValue(name).deviceLabel)
        }
        val tags = entries.flatMap { it.getValue("tags").jsonArray.map { tag -> tag.jsonPrimitive.content } }.toSet()
        assertTrue(tags.containsAll(setOf("team_select", "versus", "go", "qte_progress", "shield_choice",
            "forced_replacement", "faint_animation", "empty_landscape", "badge", "win")))
    }

    @Test fun `Pixel pack is offered on Pixel geometry and excluded on Moondrop geometry`() {
        val references = BundledCalibrationSampleCatalog.labels(catalog)
            .filterValues { it.deviceLabel == "Google Pixel 4" }
            .map { (file, label) ->
                val image = ImageIO.read(File(assets, "battle_samples/$file"))
                CalibrationScreenshotReference("asset:$file", label.name, image.width, image.height, label.deviceLabel)
            }
        assertEquals(32, CalibrationScreenshotLibrary.filter(references, CalibrationScreenshotFilter.ThisScreen, 1080, 2280).size)
        assertTrue(CalibrationScreenshotLibrary.filter(references, CalibrationScreenshotFilter.ThisScreen, 1080, 2400).isEmpty())
        assertEquals(references, CalibrationScreenshotLibrary.filter(references, CalibrationScreenshotFilter.Device("Google Pixel 4"), 1080, 2400))
    }

    @Test fun `invalid catalog metadata never invents a path or device label`() {
        assertTrue(BundledCalibrationSampleCatalog.labels("broken json").isEmpty())
        assertTrue(BundledCalibrationSampleCatalog.labels("""{"schemaVersion":2,"entries":[]}""").isEmpty())
        val invalid = """{"schemaVersion":1,"entries":[
          {"calibrationEligible":true,"assetFile":"../outside.jpg","name":"Outside","deviceLabel":"Pixel"},
          {"calibrationEligible":false,"assetFile":"gallery.jpg","name":"Gallery","deviceLabel":"Pixel"},
          {"calibrationEligible":true,"assetFile":"unknown.jpg","name":"No label"}
        ]}"""
        assertTrue(BundledCalibrationSampleCatalog.labels(invalid).isEmpty())
    }
}
