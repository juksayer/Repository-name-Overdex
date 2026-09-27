package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.ArchivedActivePokemonSpeciesWitnessed
import com.example.overdex.battle.archive.ArchivedRealityArticle
import com.example.overdex.battle.archive.ArchivedRawText
import com.example.overdex.battle.archive.ArchivedFastMoveEnergyDerived
import com.example.overdex.battle.archive.ArchivedFastMoveIdentified
import com.example.overdex.battle.archive.ArchivedChargedMoveEnergySpent
import com.example.overdex.battle.archive.MatchArchive
import com.example.overdex.model.PokemonType
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

    @Test fun `completed replay reconstructs missing combatants from preserved announcements`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            raw("sneasel", "Go, Sneasel!", 10),
            raw("raichu", "Raichu used Trailblaze!", 20),
            raw("vaporeon", "Go, Vaporeon!", 30)
        ))
        val model = MatchReplayModel(archive, mapOf("Sneasel" to 215, "Raichu" to 26, "Vaporeon" to 134))

        // The completed archive is buffered: later identity evidence can fill
        // the opening interval while retaining its reconstruction basis.
        assertEquals("Raichu", model.sceneAt(10).player?.speciesName)
        assertEquals("Sneasel", model.sceneAt(10).opponent?.speciesName)
        assertEquals("Vaporeon", model.sceneAt(30).opponent?.speciesName)
        assertEquals("RECONSTRUCTED FROM ARCHIVED ANNOUNCEMENT", model.sceneAt(10).player?.identityBasis)
    }

    @Test fun `replay exposes archived announcement species for reference lookup`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            raw("entry", "Go, Sneasel!", 10),
            raw("move", "Raichu used Trailblaze!", 20)
        ))

        assertEquals(setOf("Sneasel", "Raichu"), MatchReplayModel.referencedSpeciesNames(archive))
    }

    @Test fun `replay reconstructs both sides and switches from archived species crops`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            ArchivedRealityArticle(
                articleId = "start", matchId = "match", perceivedAt = 0, recordedAt = 0,
                sourceId = "DROIDBALL_SESSION", payload = com.example.overdex.battle.archive.ArchivedMatchRecordStarted,
                predecessorIds = emptyList(), confidence = null, sequenceNumber = 0,
                evidenceReferences = emptyList(), monotonicTimeNanos = 1
            )
        ))
        val identities = listOf(
            ReplayIdentityObservation("PLAYER", "Turtonator", 776, 10, "ARCHIVED CROP"),
            ReplayIdentityObservation("PLAYER", "Camerupt", 323, 30, "ARCHIVED CROP"),
            ReplayIdentityObservation("OPPONENT", "Sneasel", 215, 10, "ARCHIVED CROP"),
            ReplayIdentityObservation("OPPONENT", "Sealeo", 364, 20, "ARCHIVED CROP"),
            ReplayIdentityObservation("OPPONENT", "Vaporeon", 134, 30, "ARCHIVED CROP")
        )
        val model = MatchReplayModel(archive, archivedCropIdentities = identities)

        assertEquals("Turtonator", model.sceneAt(10).player?.speciesName)
        assertEquals("Sneasel", model.sceneAt(10).opponent?.speciesName)
        assertEquals("Sealeo", model.sceneAt(20).opponent?.speciesName)
        assertEquals("Camerupt", model.sceneAt(30).player?.speciesName)
        assertEquals("Vaporeon", model.sceneAt(30).opponent?.speciesName)
    }

    @Test fun `replay projects confirmed fast move type and derived generation`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            timed("identified", ArchivedFastMoveIdentified("OPPONENT", "Sneasel", "Ice Shard", 1_000_000_000L, 1_000_000_000L, 3, "TEST"), 100),
            timed("energy", ArchivedFastMoveEnergyDerived("OPPONENT", "Ice Shard", 1, 10, 10, "TEST"), 200)
        ))
        val model = MatchReplayModel(archive, fastMoveTypesByName = mapOf("ICESHARD" to PokemonType.ICE))

        val scene = model.sceneAt(200)
        assertEquals(10, scene.opponentGeneratedEnergy)
        assertEquals(PokemonType.ICE, scene.fastMoveAction?.type)
        assertEquals("OPPONENT", scene.fastMoveAction?.side)
    }

    @Test fun `replay subtracts only confirmed named charged move costs`() {
        val archive = MatchArchive(matchId = "match", articles = listOf(
            timed("fast", ArchivedFastMoveEnergyDerived("PLAYER", "Incinerate", 1, 20, 20, "TEST"), 100),
            timed("charged", ArchivedChargedMoveEnergySpent("PLAYER", "Turtonator", "Dragon Pulse", 60, "TEST"), 200)
        ))

        val scene = MatchReplayModel(archive).sceneAt(200)

        assertEquals(20, scene.playerGeneratedEnergy)
        assertEquals(60, scene.playerSpentEnergy)
    }

    private fun article(id: String, side: String, name: String, speciesId: Int, nanos: Long) =
        ArchivedRealityArticle(
            articleId = id, matchId = "match", perceivedAt = 0, recordedAt = 0,
            sourceId = "test", payload = ArchivedActivePokemonSpeciesWitnessed(side, name, speciesId),
            predecessorIds = emptyList(), confidence = null, sequenceNumber = null,
            evidenceReferences = emptyList(), monotonicTimeNanos = nanos
        )

    private fun raw(id: String, text: String, nanos: Long) = ArchivedRealityArticle(
        articleId = id, matchId = "match", perceivedAt = 0, recordedAt = 0,
        sourceId = "ANNOUNCEMENT_WITNESS", payload = ArchivedRawText(text),
        predecessorIds = emptyList(), confidence = null, sequenceNumber = null,
        evidenceReferences = emptyList(), monotonicTimeNanos = nanos
    )

    private fun timed(id: String, payload: com.example.overdex.battle.archive.ArchivedTestimonyPayload, nanos: Long) =
        ArchivedRealityArticle(
            articleId = id, matchId = "match", perceivedAt = 0, recordedAt = 0,
            sourceId = "test", payload = payload,
            predecessorIds = emptyList(), confidence = null, sequenceNumber = null,
            evidenceReferences = emptyList(), monotonicTimeNanos = nanos
        )
}
