package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Test

class ReplayChargedHpCameraTest {
    @Test fun `late charged camera damage does not replay as a fast move or generate energy`() {
        val archive = battle(listOf(
            hp("baseline-a", 8.0, top = 450, fraction = .9f),
            hp("baseline-b", 8.5, top = 453, fraction = .9f),
            hp("baseline-c", 9.0, top = 448, fraction = .9f),
            event("hydro", 10.0, ArchivedChargeMoveUsedAnnounced),
            event("spend", 10.0, ArchivedChargedMoveEnergySpent("OPPONENT", "Greninja", "Hydro Cannon", 40, "ANNOUNCEMENT")),
            hp("camera", 11.2, top = 660, fraction = .9f),
            event("same-species-again", 11.3, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Golbat", 42)),
            hp("impact-hp", 11.5, top = 660, fraction = .3f),
            use("old-misclassified-hit", 11.5),
            event("old-energy", 11.5, ArchivedFastMoveEnergyDerived("OPPONENT", "Water Shuriken", 1, 14, 14, "OBSERVED_USE"),
                predecessors = listOf("old-misclassified-hit")),
            hp("camera-return", 12.2, top = 450, fraction = .3f),
            use("resumed-fast", 12.8),
        ))
        val original = MatchArchiveSerializer.serialize(archive)
        val model = MatchReplayModel(archive)

        // This is 1.5 seconds after the named charge: outside the legacy
        // 750 ms tail, but still in the independently measured charge camera.
        val impact = model.sceneAt(ns(11.6))
        assertTrue(impact.fastMoveActions.isEmpty())
        assertEquals(.3f, impact.playerHp!!.filledFraction!!, .0001f)
        assertNotNull(impact.opponentEnergy)
        assertEquals(0, impact.opponentEnergy!!.generated)
        assertEquals(40, impact.opponentEnergy.spent)
        assertTrue(model.sceneAt(ns(12.9)).fastMoveActions.any { "resumed-fast" in it.sourceArticleIds })
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `faint or genuine replacement ends the old camera before the new pokemon attacks`() {
        for (includeFaint in listOf(false, true)) {
            val archive = battle(listOf(
                hp("baseline-a", 8.0, top = 450),
                hp("baseline-b", 8.5, top = 453),
                hp("baseline-c", 9.0, top = 448),
                event("hydro", 10.0, ArchivedChargeMoveUsedAnnounced),
                hp("charged-camera", 11.2, top = 660, fraction = .3f),
            ) + (if (includeFaint) listOf(
                hp("charged-ko", 11.5, top = 660, fraction = 0f),
                use("final-charged-impact", 11.5),
                event("faint", 11.5, ArchivedActivePokemonFaintedWitnessed("PLAYER", "Golbat", 42, "POKE_BALLS")),
            ) else emptyList()) + listOf(
                event("replacement", 12.0, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Victreebel", 71)),
                hp("replacement-bar", 12.1, top = 780, fraction = 1f),
                hp("replacement-bar-again", 12.3, top = 782, fraction = 1f),
                use("real-fast-after-entry", 12.6),
            ))
            val model = MatchReplayModel(archive)
            if (includeFaint) assertTrue(model.sceneAt(ns(11.6)).fastMoveActions
                .none { "final-charged-impact" in it.sourceArticleIds })
            val scene = model.sceneAt(ns(12.7))
            assertEquals("Victreebel", scene.player?.speciesName)
            assertTrue("New combatant's Fast Move was suppressed (faint=$includeFaint)",
                scene.fastMoveActions.any { "real-fast-after-entry" in it.sourceArticleIds })
        }
    }

    @Test fun `ordinary HP bobbing does not suppress a large legitimate fast move`() {
        val archive = battle(listOf(
            hp("baseline-a", 8.0, top = 450, fraction = .9f),
            hp("baseline-b", 8.5, top = 453, fraction = .9f),
            hp("baseline-c", 9.0, top = 448, fraction = .9f),
            event("hydro", 10.0, ArchivedChargeMoveUsedAnnounced),
            hp("ordinary-motion", 11.2, top = 485, fraction = .9f),
            hp("large-loss", 11.5, top = 480, fraction = .3f),
            use("large-fast", 11.5),
        ))
        assertTrue(MatchReplayModel(archive).sceneAt(ns(11.6)).fastMoveActions
            .any { "large-fast" in it.sourceArticleIds })
    }

    @Test fun `geometry without a named charge cannot suppress preserved fast move testimony`() {
        val archive = battle(listOf(
            hp("baseline-a", 8.0, top = 450),
            hp("baseline-b", 8.5, top = 453),
            hp("baseline-c", 9.0, top = 448),
            hp("relocated", 11.2, top = 660),
            use("unrelated-hit", 11.5),
        ))
        assertTrue(MatchReplayModel(archive).sceneAt(ns(11.6)).fastMoveActions
            .any { "unrelated-hit" in it.sourceArticleIds })
    }

    @Test fun `legacy bars without source dimensions keep the existing short charge fallback`() {
        val archive = battle(listOf(
            hp("baseline-a", 8.0, top = 450, sourceDimensions = false),
            hp("baseline-b", 8.5, top = 453, sourceDimensions = false),
            hp("baseline-c", 9.0, top = 448, sourceDimensions = false),
            event("hydro", 10.0, ArchivedChargeMoveUsedAnnounced),
            use("fallback-hit", 10.5),
            hp("crop-relative", 11.2, top = 660, sourceDimensions = false),
            use("later-hit", 11.5),
        ))
        val model = MatchReplayModel(archive)
        assertTrue(model.sceneAt(ns(10.6)).fastMoveActions.isEmpty())
        assertTrue(model.sceneAt(ns(11.6)).fastMoveActions.any { "later-hit" in it.sourceArticleIds })
    }

    private fun battle(records: List<ArchivedRealityArticle>) = MatchArchive(matchId = "charge-camera", articles = listOf(
        event("player", 0.0, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Golbat", 42)),
        event("opponent", 0.0, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Greninja", 658)),
        event("start", 1.0, ArchivedMatchStarted),
    ) + records)

    private fun hp(id: String, seconds: Double, top: Int, fraction: Float = .9f, sourceDimensions: Boolean = true) =
        event(id, seconds, ArchivedActiveHpBarMeasured("PLAYER", 20, top, 220, top + 12, fraction,
            if (sourceDimensions) 672 else null, if (sourceDimensions) 1493 else null))

    private fun use(id: String, seconds: Double) = event(id, seconds,
        ArchivedFastMoveUseObserved(id, "OPPONENT", "PLAYER", "greninja", "Greninja",
            listOf("HP_BORDER_PULSE"), "PRESERVED_CANONICAL_USE"))

    private fun event(id: String, seconds: Double, payload: ArchivedTestimonyPayload, predecessors: List<String> = emptyList()) =
        ArchivedRealityArticle(id, "charge-camera", (seconds * 1000).toLong(), (seconds * 1000).toLong(),
            "TEST", payload, predecessors, 1f, null, emptyList(), ns(seconds))

    private fun ns(seconds: Double) = (seconds * 1e9).toLong()
}
