package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.BattleCryCandidateMeasurement
import com.example.overdex.battle.custody.BattleCryCandidatesMeasured
import com.example.overdex.battle.custody.OpponentBattleResource
import com.example.overdex.battle.custody.OpponentBattleResourceCountMeasured
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpponentFaintTrackerTest {
    @Test fun `resource increase noise cannot create an extra faint on its return`() {
        val tracker = TeamFaintTracker()
        tracker.accept(article("sneasel", 1, ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sneasel", 215)))
        tracker.accept(article("balls-3", 2, balls(3)))
        tracker.accept(article("balls-2", 3, balls(2)))
        tracker.accept(article("sealeo", 4, ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364)))
        assertNull(tracker.accept(article("noise-3", 5, balls(3))))
        assertNull(tracker.accept(article("back-2", 6, balls(2))))
    }

    @Test fun `player ball decrease faints player without consuming opponent resources`() {
        val tracker = TeamFaintTracker(ActivePokemonSide.PLAYER)
        tracker.accept(article("camerupt", 1, ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Camerupt", 323)))
        tracker.accept(article("player-3", 2, com.example.overdex.battle.custody.PlayerPokeBallCountMeasured(3)))
        assertNull(tracker.accept(article("opponent-1", 3, balls(1))))
        val faint = tracker.accept(article("player-2", 4, com.example.overdex.battle.custody.PlayerPokeBallCountMeasured(2)))
        assertEquals("Camerupt", faint?.speciesName)
        assertEquals("PLAYER_POKE_BALL_COUNT_DECREASE", faint?.basis)
    }

    @Test fun `delayed pre-entry count does not clear the departing faint candidate`() {
        val tracker = TeamFaintTracker()
        tracker.accept(article("sneasel", 1, ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sneasel", 215)))
        tracker.accept(article("balls-3", 2, balls(3)))
        tracker.accept(article("sealeo", 5, ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364)))
        tracker.accept(article("delayed-still-3", 4, balls(3)))
        assertEquals("Sneasel", tracker.accept(article("balls-2", 6, balls(2)))?.speciesName)
        assertNull(tracker.accept(article("even-older", 3, balls(3))))
        assertNull(tracker.accept(article("still-2", 7, balls(2))))
    }

    @Test fun `poke ball decrease faints opponent active before the decrease`() {
        val tracker = TeamFaintTracker()
        tracker.accept(article("sneasel", 1, ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sneasel", 215)))
        assertNull(tracker.accept(article("balls-3", 2, balls(3))))
        tracker.accept(article(
            "cry",
            3,
            BattleCryCandidatesMeasured("SPECIES_ENTRY", listOf(BattleCryCandidateMeasurement(215, "sha", 0.8f)))
        ))

        val faint = tracker.accept(article("balls-2", 4, balls(2)))

        assertEquals("Sneasel", faint?.speciesName)
        assertEquals(215, faint?.speciesId)
        assertEquals(true, faint?.cryCorroborated)
        assertTrue(faint?.evidenceReferences.orEmpty().containsAll(listOf("sneasel", "balls-3", "cry", "balls-2")))
    }

    @Test fun `same count and increases do not manufacture a faint`() {
        val tracker = TeamFaintTracker()
        tracker.accept(article("sneasel", 1, ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sneasel", 215)))
        assertNull(tracker.accept(article("balls-2a", 2, balls(2))))
        assertNull(tracker.accept(article("balls-2b", 3, balls(2))))
        assertNull(tracker.accept(article("balls-3", 4, balls(3))))
    }

    @Test fun `replacement species becomes the active pokemon for the next decrease`() {
        val tracker = TeamFaintTracker()
        tracker.accept(article("sneasel", 1, ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sneasel", 215)))
        tracker.accept(article("balls-3", 2, balls(3)))
        assertEquals("Sneasel", tracker.accept(article("balls-2", 3, balls(2)))?.speciesName)
        tracker.accept(article("sealeo", 4, ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364)))

        assertEquals("Sealeo", tracker.accept(article("balls-1", 5, balls(1)))?.speciesName)
    }

    @Test fun `late poke ball result still faints species active before replacement OCR`() {
        val tracker = TeamFaintTracker()
        tracker.accept(article("sneasel", 1, ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sneasel", 215)))
        tracker.accept(article("balls-3", 2, balls(3)))
        tracker.accept(article("sealeo", 3, ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364)))

        val faint = tracker.accept(article("balls-2", 4, balls(2)))

        assertEquals("Sneasel", faint?.speciesName)
    }

    @Test fun `repeated count after a switch prevents fainting the previous occupant`() {
        val tracker = TeamFaintTracker()
        tracker.accept(article("sneasel", 1, ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sneasel", 215)))
        tracker.accept(article("balls-3", 2, balls(3)))
        tracker.accept(article("sealeo", 3, ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Sealeo", 364)))
        assertNull(tracker.accept(article("still-3", 4, balls(3))))
        assertEquals("Sealeo", tracker.accept(article("balls-2", 5, balls(2)))?.speciesName)
    }

    private fun balls(count: Int) = OpponentBattleResourceCountMeasured(
        OpponentBattleResource.POKE_BALLS,
        count,
        3
    )

    private fun article(id: String, seconds: Long, payload: com.example.overdex.battle.custody.TestimonyPayload) =
        RealityArticle(
            id = ArticleId(id),
            perceivedAt = seconds * 1_000,
            recordedAt = seconds * 1_000,
            sourceId = SourceId("test"),
            payload = payload,
            monotonicTimeNanos = seconds * 1_000_000_000L
        )
}
