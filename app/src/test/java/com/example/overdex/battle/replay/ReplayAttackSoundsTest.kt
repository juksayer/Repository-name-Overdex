package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.ArchivedActiveHpBarBorderPulseObserved
import com.example.overdex.battle.archive.ArchivedChargedMoveEnergySpent
import com.example.overdex.battle.archive.ArchivedFastMoveEnergyDerived
import com.example.overdex.battle.archive.ArchivedGetReadyWitnessed
import com.example.overdex.battle.archive.ArchivedTestimonyPayload
import com.example.overdex.battle.archive.ArchivedRealityArticle
import com.example.overdex.battle.archive.MatchArchive
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
    private val fast = ReplayAttackSoundEvent(100L, "Spark", false, "PLAYER")
    private val charge = ReplayAttackSoundEvent(200L, "Thunder Punch", true, "PLAYER")

    @Test fun fastMovesFollowTheirCombatantsWithQuarterVolumeInTheOtherChannel() {
        assertEquals(ReplayAttackSoundMix(1f, .25f), replayAttackSoundMix(fast))
        assertEquals(ReplayAttackSoundMix(.25f, 1f), replayAttackSoundMix(fast.copy(side = "OPPONENT")))
    }

    @Test fun chargedMovesUseBothChannelsAtFullVolumeForEitherSide() {
        for (side in listOf("PLAYER", "OPPONENT")) {
            assertEquals(ReplayAttackSoundMix(1f, 1f), replayAttackSoundMix(charge.copy(side = side)))
        }
    }

    @Test fun unknownSideStaysCenteredInsteadOfInventingAttribution() {
        assertEquals(ReplayAttackSoundMix(1f, 1f), replayAttackSoundMix(fast.copy(side = "UNKNOWN")))
    }

    @Test fun reconstructedAttackAudioRetainsTheOwnerRatherThanTheDamageRecipient() {
        val model = MatchReplayModel(MatchArchive(matchId = "stereo", articles = listOf(
            timed("player-hit", ArchivedActiveHpBarBorderPulseObserved("OPPONENT", .4f, 8, .3f, .8f), 1_000_000_000L),
            timed("opponent-hit", ArchivedActiveHpBarBorderPulseObserved("PLAYER", .4f, 8, .3f, .8f), 1_100_000_000L),
            timed("player-move", ArchivedFastMoveEnergyDerived("PLAYER", "Incinerate", 1, 20, 20, "TEST"), 1_000_000_000L),
            timed("opponent-move", ArchivedFastMoveEnergyDerived("OPPONENT", "Ice Shard", 1, 10, 10, "TEST"), 1_100_000_000L),
            timed("charge-ready", ArchivedGetReadyWitnessed, 3_000_000_000L),
            timed("charge-used", ArchivedChargedMoveEnergySpent("PLAYER", "Turtonator", "Dragon Pulse", 60, "ANNOUNCEMENT"), 8_000_000_000L),
        )))

        val fastSounds = model.attackSoundEvents.filterNot { it.charged }.associateBy { it.moveName }
        assertEquals(setOf("Incinerate", "Ice Shard"), fastSounds.keys)
        assertEquals("PLAYER", fastSounds.getValue("Incinerate").side)
        assertEquals("OPPONENT", fastSounds.getValue("Ice Shard").side)
        assertEquals("PLAYER", model.attackSoundEvents.single { it.charged }.side)
    }

    private fun timed(id: String, payload: ArchivedTestimonyPayload, at: Long) = ArchivedRealityArticle(
        articleId = id,
        matchId = "stereo",
        sourceId = "TEST",
        perceivedAt = at / 1_000_000L,
        recordedAt = at / 1_000_000L,
        monotonicTimeNanos = at,
        confidence = 1f,
        payload = payload,
        predecessorIds = emptyList(),
        sequenceNumber = null,
        evidenceReferences = null,
    )

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
