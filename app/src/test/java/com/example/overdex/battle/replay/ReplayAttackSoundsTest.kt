package com.example.overdex.battle.replay

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReplayAttackSoundsTest {
    private val fast = ReplayAttackSoundEvent(100L, "Spark", false)
    private val charge = ReplayAttackSoundEvent(200L, "Thunder Punch", true)

    @Test fun forwardPlaybackDoesNotRepeatBoundarySounds() {
        val events = listOf(fast, charge)
        assertEquals(listOf(fast), replayAttackSoundsBetween(events, 0L, 100L))
        assertEquals(listOf(charge), replayAttackSoundsBetween(events, 100L, 200L))
    }

    @Test fun backwardAndStationaryCursorsDoNotPlay() {
        assertTrue(replayAttackSoundsBetween(listOf(fast), 200L, 0L).isEmpty())
        assertTrue(replayAttackSoundsBetween(listOf(fast), 100L, 100L).isEmpty())
    }

    @Test fun frameSkippingPreservesBothAttackKinds() {
        assertEquals(listOf(fast, charge), replayAttackSoundsBetween(listOf(fast, charge), 0L, 300L))
    }

    @Test fun recognitionExcerptsAreExcludedBeforeSelectingPlayableVariants() {
        val manifest = """{"references":[
            {"moveName":"Bubble","file":"bubble-excerpt.wav","replayEligible":false,"purpose":"recognition_excerpt"},
            {"moveName":"Confusion","file":"confusion-excerpt.wav","replayEligible":false},
            {"moveName":"Spark","file":"legacy-spark.wav"},
            {"moveName":"Bubble","file":"complete-bubble.wav","replayEligible":true},
            {"moveName":"Bubble","file":"another-complete-bubble.wav"},
            {"moveName":"Water Gun","file":"unrequested.wav"}
        ]}"""
        assertEquals(listOf("spark" to "legacy-spark.wav", "bubble" to "complete-bubble.wav",
            "bubble" to "another-complete-bubble.wav"),
            replayAttackSoundFiles(manifest, setOf("Bubble", "CONFUSION", "Spark")))
    }

    @Test fun invalidEligibilityFlagDoesNotSilentlyPermitAnExcerpt() {
        val manifest = """{"references":[
            {"moveName":"Bubble","file":"text-flag.wav","replayEligible":"true"},
            {"moveName":"Confusion","file":"null-flag.wav","replayEligible":null},
            {"moveName":"Rock Smash","file":"number-flag.wav","replayEligible":1}
        ]}"""
        assertTrue(replayAttackSoundFiles(manifest, setOf("Bubble", "Confusion", "Rock Smash")).isEmpty())
    }

    @Test fun bundledCapturedExcerptsStayOutOfReplayWhileLegacySparkRemainsAvailable() {
        val manifest = File("src/main/assets/fast_move_sounds/manifest.json").readText()
        val references = Json.parseToJsonElement(manifest).jsonObject.getValue("references").jsonArray
        val excerpts = references.map { it.jsonObject }
            .filter { it["purpose"] == JsonPrimitive("recognition_excerpt") }
        val excerptNames = excerpts.map { it.getValue("moveName").jsonPrimitive.content }.toSet()
        assertTrue("Expected the three reviewed captured recognition excerpts",
            excerptNames.containsAll(setOf("Bubble", "Confusion", "Rock Smash")))
        assertTrue(excerpts.all { it["replayEligible"] == JsonPrimitive(false) })
        val selected = replayAttackSoundFiles(manifest, excerptNames + "Spark")
        val excerptFiles = excerpts.map { it.getValue("file").jsonPrimitive.content }.toSet()
        assertFalse(selected.any { (_, file) -> file in excerptFiles })
        assertTrue("The original complete Spark effect should still be available", selected.any { (name, _) -> name == "spark" })
    }
}
