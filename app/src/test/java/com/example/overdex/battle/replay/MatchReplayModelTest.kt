package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.ArchivedActivePokemonSpeciesWitnessed
import com.example.overdex.battle.archive.ArchivedRealityArticle
import com.example.overdex.battle.archive.MatchArchive
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchReplayModelTest {
    @Test fun `replay changes the right combatant at its witnessed switch time`() {
        val model = MatchReplayModel(MatchArchive(matchId = "match", articles = listOf(
            article("p", "PLAYER", "GOURGEIST", 711, 10),
            article("o1", "OPPONENT", "VAPOREON", 134, 10),
            article("o2", "OPPONENT", "SNEASEL", 215, 20)
        )))

        assertEquals("VAPOREON", model.sceneAt(15).opponent?.speciesName)
        assertEquals("SNEASEL", model.sceneAt(20).opponent?.speciesName)
        assertEquals("GOURGEIST", model.sceneAt(15).player?.speciesName)
    }

    private fun article(id: String, side: String, name: String, speciesId: Int, nanos: Long) =
        ArchivedRealityArticle(
            articleId = id, matchId = "match", perceivedAt = 0, recordedAt = 0,
            sourceId = "test", payload = ArchivedActivePokemonSpeciesWitnessed(side, name, speciesId),
            predecessorIds = emptyList(), confidence = null, sequenceNumber = null,
            evidenceReferences = emptyList(), monotonicTimeNanos = nanos
        )
}
