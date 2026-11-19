package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Test

class ReplayRhythmPresentationTest {
    private val turn = 500_000_000L

    @Test fun markerProjectileAndSoundUseOneScheduleWithoutCreatingTestimonyOrCombatState() {
        val archive = battle()
        val original = MatchArchiveSerializer.serialize(archive)
        val model = MatchReplayModel(archive)
        val markers = model.eventMarkers.filter { it.kind == ReplayEventMarkerKind.FAST_MOVE }
        val predicted = markers.filter { it.label.startsWith("Inferred") }
        assertEquals(listOf(10 * turn, 15 * turn), predicted.map { it.atNanos })
        assertEquals(markers.size, markers.distinctBy { it.id }.size)
        assertEquals(listOf(5 * turn), (markers - predicted.toSet()).map { it.atNanos })
        val sounds = model.attackSoundEvents.filter { !it.charged }
        assertEquals(markers.map { it.atNanos }, sounds.map { it.impactAtNanos })
        assertEquals(listOf(5 * turn, 10 * turn), sounds.filter { ReplayFastMoveRhythm.INFERRED in it.evidenceKinds }.map { it.atNanos })

        val first = model.sceneAt(5 * turn)
        assertEquals(20, first.playerEnergy!!.recordedEnergy)
        for (marker in predicted) {
            assertEquals("Inferred · Incinerate", marker.label)
            assertEquals("Expected from Camerupt’s last confirmed Incinerate, every 5 turns.", marker.inferenceExplanation)
            assertTrue(marker.sourceArticleIds.isEmpty())
            assertNull(marker.finishingAttribution)
            val scene = model.sceneAt(marker.atNanos)
            val action = scene.fastMoveActions.single { it.side == "PLAYER" }
            assertEquals("Incinerate", action.moveName)
            assertEquals(1f, action.progress, 0f)
            assertTrue(ReplayFastMoveRhythm.INFERRED in action.evidenceKinds)
            assertTrue(action.observedAtNanos.isEmpty())
            assertNull(action.finishingAssociation)
            assertEquals(first.opponentHp!!.filledFraction, scene.opponentHp!!.filledFraction)
            assertEquals(first.playerEnergy, scene.playerEnergy)
            assertFalse(scene.opponent!!.isFainted)
        }
        val atFinish = model.sceneAt(15 * turn)
        assertEquals(first, model.sceneAt(5 * turn))
        assertEquals(atFinish, model.sceneAt(15 * turn))
        assertTrue(model.finishingAttributions.isEmpty())
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun recordedConnectionLossStopsForecastingUntilAnotherIndependentUse() {
        val lost = event("lost", ArchivedRawText("Connection lost!"), 11).copy(sourceId = "ANNOUNCEMENT_WITNESS")
        val returned = hit("after-connection", 19)
        fun inferredAt(archive: MatchArchive) = MatchReplayModel(archive).eventMarkers.filter {
            it.kind == ReplayEventMarkerKind.FAST_MOVE && it.label.startsWith("Inferred")
        }.map { it.atNanos / turn }
        assertEquals(listOf(10L, 24L), inferredAt(battle(listOf(lost, returned), end = 29)))
        val warning = lost.copy(payload = ArchivedRawText("Weak connection"))
        assertEquals(listOf(10L, 15L, 20L, 25L), inferredAt(battle(listOf(warning), end = 29)))
    }

    private fun battle(extra: List<ArchivedRealityArticle> = emptyList(), end: Int = 20) =
        MatchArchive(matchId = "rhythm", articles = listOf(
            event("go", ArchivedMatchStarted, 0),
            event("player", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323), 0),
            event("opponent", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Sealeo", 364), 0),
            event("move", ArchivedFastMoveIdentified("PLAYER", "Camerupt", "Incinerate", 5 * turn,
                cadenceSampleCount = 0, basis = "CONFIGURED_TEAM"), 0),
            hit("witnessed", 5),
            event("energy", ArchivedFastMoveEnergyDerived("PLAYER", "Incinerate", 1, 20, 20, "IDENTIFIED"), 5)
                .copy(predecessorIds = listOf("witnessed")),
            event("bar", ArchivedActiveHpBarMeasured("OPPONENT", 10, 20, 210, 35, .75f), 5),
        ) + extra + event("end", ArchivedMatchEnded("WIN"), end))

    private fun hit(id: String, t: Int) = event(id, ArchivedFastMoveUseObserved(id,
        "PLAYER", "OPPONENT", "appearance", "Camerupt", listOf("HP_BORDER_PULSE"), "FUSED"), t)

    private fun event(id: String, payload: ArchivedTestimonyPayload, t: Int) =
        ArchivedRealityArticle(id, "rhythm", 0, 0, "TEST", payload, emptyList(), null, null, emptyList(), t * turn)
}
