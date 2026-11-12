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

class MoondropCalibrationSamplesTest {
    private val assets = File("src/main/assets")
    private val catalog = File(assets, BundledCalibrationSampleCatalog.ASSET_PATH).readText()
    private val device = "MOONDROP MD-PH-001"

    @Test fun `reviewed Moondrop core preserves native geometry hashes and honest scene coverage`() {
        val entries = Json.parseToJsonElement(catalog).jsonObject.getValue("entries").jsonArray
            .map { it.jsonObject }.filter { it["collectionId"]?.jsonPrimitive?.content == "moondrop-2026-11-10" }
        val labels = BundledCalibrationSampleCatalog.labels(catalog).filterValues { it.deviceLabel == device }
        assertEquals(21, entries.size)
        assertEquals(21, labels.size)
        assertEquals(21, entries.map { it.getValue("sourceSha256").jsonPrimitive.content }.toSet().size)
        assertFalse(entries.any { it.getValue("sourceFile").jsonPrimitive.content == "YouWinAndCheckmark.png" })
        for (entry in entries) {
            val name = entry.getValue("assetFile").jsonPrimitive.content
            val file = File(assets, "battle_samples/$name")
            assertTrue(name, file.isFile)
            val hash = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            assertEquals(name, entry.getValue("assetSha256").jsonPrimitive.content, hash)
            val image = requireNotNull(ImageIO.read(file)) { name }
            assertEquals(name, 1080, image.width)
            assertEquals(name, 2400, image.height)
            assertEquals(device, labels.getValue(name).deviceLabel)
            assertTrue(name, entry.getValue("sourceSha256").jsonPrimitive.content.matches(Regex("[0-9a-f]{64}")))
        }
        val tags = entries.flatMap { it.getValue("tags").jsonArray.map { tag -> tag.jsonPrimitive.content } }.toSet()
        assertTrue(tags.containsAll(setOf("team_select", "versus", "countdown", "go", "ordinary_hp",
            "charged_impact_hp", "empty_hp", "shield_choice", "forced_replacement", "win", "effort",
            "battle_party_tabs", "qte_type_icons")))
        val hpText = entries.single { it.getValue("sourceFile").jsonPrimitive.content == "supereffectivetext.png" }
        val hpTextTags = hpText.getValue("tags").jsonArray.map { it.jsonPrimitive.content }.toSet()
        assertTrue("SUPER EFFECTIVE above ordinary HP is a separate placement example from charged-button text",
            hpTextTags.containsAll(setOf("ordinary_hp", "hp_effectiveness_text", "opponent_hp_effectiveness")))
        val qte = entries.filter { "qte_type_icons" in it.getValue("tags").jsonArray.map { tag -> tag.jsonPrimitive.content } }
        assertEquals(3, qte.size)
        assertTrue(qte.all { entry -> "grade_text_absent" in entry.getValue("tags").jsonArray.map { it.jsonPrimitive.content } })
        assertFalse("Bubble images must not be labeled as missing grade reveals", tags.any { it in setOf("nice", "great", "excellent") })
    }

    @Test fun `reviewed Moondrop and Pixel references stay in their own screen shapes`() {
        val references = BundledCalibrationSampleCatalog.labels(catalog).map { (name, label) ->
            val image = requireNotNull(ImageIO.read(File(assets, "battle_samples/$name")))
            CalibrationScreenshotReference("asset:$name", label.name, image.width, image.height, label.deviceLabel)
        }
        val moondrop = CalibrationScreenshotLibrary.filter(references, CalibrationScreenshotFilter.ThisScreen, 1080, 2400)
        assertEquals(21, moondrop.size)
        assertTrue(moondrop.all { it.deviceLabel == device })
        assertEquals(moondrop, CalibrationScreenshotLibrary.filter(references, CalibrationScreenshotFilter.Device(device), 1080, 2280))
        val pixel = CalibrationScreenshotLibrary.filter(references, CalibrationScreenshotFilter.ThisScreen, 1080, 2280)
        assertEquals(32, pixel.size)
        assertTrue(pixel.all { it.deviceLabel == "Google Pixel 4" })
        assertEquals(53, CalibrationScreenshotLibrary.filter(references, CalibrationScreenshotFilter.All, 1080, 2400).size)
    }
}
