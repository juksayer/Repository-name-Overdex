package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Test

class ReplayChargeFocusBoundaryTest {
    @Test fun `preparation starts at either recorded phase cue before the delayed move name`() {
        for ((cue, side) in listOf(ArchivedGetReadyWitnessed to "PLAYER", ArchivedAttackIncoming to "OPPONENT")) {
            val archive = archive(at("cue", 2, cue), at("named", 9, spend(side)))
            val original = MatchArchiveSerializer.serialize(archive)
            val model = MatchReplayModel(archive)
            assertTrue(model.sceneAt(2 * SECOND - 1).chargedMoveActions.isEmpty())
            val start = model.sceneAt(2 * SECOND).chargedMoveActions.single()
            assertEquals(side, start.side)
            assertEquals(ReplayChargePhase.CHARGING, start.phase)
            assertEquals(0L, start.elapsedNanos)
            assertEquals("Example Charge", start.moveName)
            assertEquals(ReplayChargePhase.CHARGING,
                model.sceneAt(8 * SECOND).chargedMoveActions.single().phase)
            assertEquals(ReplayChargePhase.STRIKING,
                model.sceneAt(9 * SECOND).chargedMoveActions.single().phase)
            assertEquals(start, model.sceneAt(2 * SECOND).chargedMoveActions.single())
            assertEquals(original, MatchArchiveSerializer.serialize(archive))
        }
    }

    @Test fun `a preserved shield choice starts opponent preparation without requiring its earlier warning`() {
        for (source in listOf("ANNOUNCEMENT_WITNESS", "MATCH_OUTCOME_TEXT_WITNESS")) {
            for (prompt in listOf("Use a Protect Shield?", "No Protect Shields remaining!")) {
                val cue = at("prompt", 2, ArchivedRawText(prompt)).copy(sourceId = source,
                    recordedAt = 99_000) // publication delay cannot move the observed boundary
                val archive = archive(cue, at("named", 9, spend("OPPONENT")))
                val original = MatchArchiveSerializer.serialize(archive)
                val model = MatchReplayModel(archive)
                assertTrue(model.sceneAt(2 * SECOND - 1).chargedMoveActions.isEmpty())
                val action = model.sceneAt(2 * SECOND).chargedMoveActions.single()
                assertEquals("OPPONENT", action.side)
                assertEquals(ReplayChargePhase.CHARGING, action.phase)
                assertTrue("SHIELD_PROMPT" in action.evidenceKinds)
                assertEquals(9 * SECOND, action.startedAtNanos)
                assertEquals(0L, action.elapsedNanos)
                assertEquals(original, MatchArchiveSerializer.serialize(archive))
            }
        }
    }

    @Test fun `the shield prompt anchors the opponent rise until the recorded announcement`() {
        val prompt = at("prompt", 3, ArchivedRawText("Use a Protect Shield?"))
            .copy(sourceId = "ANNOUNCEMENT_WITNESS", recordedAt = 20_000)
        val namedAt = 12 * SECOND + SECOND / 2
        val done = at("named", 12, spend("OPPONENT")).copy(monotonicTimeNanos = namedAt)
        val archive = archive(done, prompt.copy(articleId = "repeat", monotonicTimeNanos = 4 * SECOND),
            at("early-warning", 1, ArchivedAttackIncoming), prompt)
        val original = MatchArchiveSerializer.serialize(archive)
        val model = MatchReplayModel(archive)
        val start = 3 * SECOND
        assertTrue(model.sceneAt(start - 1).chargedMoveActions.isEmpty())
        assertEquals("Opponent charging…", model.sceneAt(start).announcement)
        assertEquals(start, model.sceneAt(start).chargeFocus?.startedAtNanos)
        val midpoint = model.sceneAt(start + 4_750_000_000L)
        val action = midpoint.chargedMoveActions.single()
        assertEquals(.5f, action.progress, .0001f)
        assertEquals(-12f, ReplayChargePose.at(action, player = false).risePixels, .0001f)
        assertEquals("Opponent charging…", model.sceneAt(namedAt - 1).announcement)
        val announced = model.sceneAt(namedAt)
        assertEquals("Sneasel used Example Charge!", announced.announcement)
        assertEquals(ReplayChargePhase.STRIKING, announced.chargedMoveActions.single().phase)
        assertEquals(namedAt, announced.chargedMoveActions.single().startedAtNanos)
        assertEquals(-24f, ReplayChargePose.at(announced.chargedMoveActions.single(), player = false).risePixels, 0f)
        val flight = model.sceneAt(namedAt + 900_000_000L).chargedMoveActions.single()
        assertEquals(.5f, ReplayChargePose.at(flight, player = false).projectileProgress, .0001f)
        assertEquals(action, model.sceneAt(start + 4_750_000_000L).chargedMoveActions.single())
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `unclosed shield prompt has bounded preparation and cannot invent a strike or move`() {
        val model = MatchReplayModel(archive(at("prompt", 2, ArchivedRawText("Use a Protect Shield?"))
            .copy(sourceId = "ANNOUNCEMENT_WITNESS")))
        val action = model.sceneAt(3 * SECOND).chargedMoveActions.single()
        assertEquals("OPPONENT", action.side)
        assertEquals(ReplayChargePhase.CHARGING, action.phase)
        assertNull(action.moveName)
        assertNull(action.type)
        assertNull(model.sceneAt(3 * SECOND).qteMilestone)
        val expiry = 11 * SECOND + SECOND / 2
        assertEquals("Opponent charging…", model.sceneAt(expiry - 1).announcement)
        assertTrue(model.sceneAt(expiry).chargedMoveActions.isEmpty())
        assertNull(model.sceneAt(expiry).announcement)
    }

    @Test fun `generic shield text unrelated crops and capture silence cannot manufacture preparation`() {
        val examples = listOf(
            "Protect Shield" to "ANNOUNCEMENT_WITNESS",
            "Shield used" to "ANNOUNCEMENT_WITNESS",
            "" to "ANNOUNCEMENT_WITNESS",
            "Use a Protect Shield?" to "PLAYER_SPECIES_OVERLAY_PIPELINE_OCR_RAW",
            "No Protect Shields remaining!" to "UNRELATED_LOG",
        )
        examples.forEach { (text, source) ->
            val model = MatchReplayModel(archive(at("text", 2, ArchivedRawText(text)).copy(sourceId = source)))
            assertTrue("$source: $text", model.sceneAt(3 * SECOND).chargedMoveActions.isEmpty())
        }
    }

    @Test fun `late vibrations from a completed charge cannot reopen it or absorb the next charge`() {
        val oldPattern = at("pattern", 11, ArchivedChargeMoveQteVibrationPatternInferred(
            "PLAYER", 3, SECOND, "GET_READY_LINKED_SHORT_DEVICE_MOTION_PULSES"
        )).copy(predecessorIds = listOf("first-ready"))
        val first = at("first-ready", 2, ArchivedGetReadyWitnessed)
        val completion = at("named", 9, spend("PLAYER"))
        val between = MatchReplayModel(archive(oldPattern, completion, first))
        assertTrue(between.sceneAt(13 * SECOND).chargedMoveActions.isEmpty())
        val marker = between.eventMarkers.single { it.kind == ReplayEventMarkerKind.CHARGED_MOVE }
        assertTrue(marker.sourceArticleIds.containsAll(listOf("named", "pattern", "first-ready")))
        val next = at("second-ready", 10, ArchivedGetReadyWitnessed)
        val model = MatchReplayModel(archive(oldPattern, next, completion, first))
        val later = model.sceneAt(13 * SECOND).chargedMoveActions.single()
        assertEquals(ReplayChargePhase.CHARGING, later.phase)
        assertFalse("QTE_VIBRATION_PATTERN" in later.evidenceKinds)
    }

    private fun spend(side: String) = ArchivedChargedMoveEnergySpent(side,
        if (side == "PLAYER") "Camerupt" else "Sneasel", "Example Charge", 45, "TEST")

    private fun archive(vararg evidence: ArchivedRealityArticle) = MatchArchive(matchId = "focus", articles = listOf(
        at("player", 0, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323)),
        at("opponent", 0, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Sneasel", 215)),
    ) + evidence)

    private fun at(id: String, second: Long, payload: ArchivedTestimonyPayload) = ArchivedRealityArticle(
        id, "focus", 0, 0, "TEST", payload, emptyList(), null, null, emptyList(), second * SECOND)

    private companion object { const val SECOND = 1_000_000_000L }
}
