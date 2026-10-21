package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Test

class ReplayMissingMatchStartTest {
    @Test fun `October 14 recording opens when catalogue identities finish loading`() {
        // Actual crash record, with media/unrelated measurements omitted and
        // wall dates removed. Original testimony and relative timing remain.
        val archive = MatchArchiveSerializer.deserialize(checkNotNull(javaClass.getResource(
            "/replay/recorded-no-start-20261014.json")).readText())
        val original = MatchArchiveSerializer.serialize(archive)
        val species = mapOf("Abomasnow" to 460, "Camerupt" to 323, "Sneasel" to 215,
            "Sealeo" to 364, "Vaporeon" to 134, "Gourgeist" to 711)
        // The screen first constructs without reference data, then recomposes
        // with the catalogue. Only the latter previously hit the cycle.
        for (catalogue in listOf(emptyMap(), species)) {
            val model = MatchReplayModel(archive, speciesIdsByName = catalogue)
            assertNull(model.matchStartNanos)
            assertEquals("Camerupt", model.sceneAt(60_000_000_000L).player?.speciesName)
            assertEquals("Vaporeon", model.sceneAt(60_000_000_000L).opponent?.speciesName)
            assertEquals("WIN", model.sceneAt(model.endNanos).outcome)
            assertTrue(model.endNanos > model.startNanos)
        }
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `catalogue recovery can open a completed match without a start article`() {
        val archive = MatchArchive(matchId = "missing-start", articles = listOf(
            event("player", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323), 1),
            event("opponent", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Vaporeon", 134), 1),
            event("get-ready", ArchivedGetReadyWitnessed, 2),
            event("charge", ArchivedChargedMoveEnergySpent("PLAYER", "Camerupt", "Earth Power", 50, "TEST"), 10),
            event("result", ArchivedMatchEnded("WIN"), 10),
        ))
        val original = MatchArchiveSerializer.serialize(archive)
        val model = MatchReplayModel(archive, speciesIdsByName = mapOf("Camerupt" to 323, "Vaporeon" to 134))

        assertNull(model.matchStartNanos)
        assertEquals("Camerupt", model.sceneAt(5_000_000_000L).player?.speciesName)
        assertEquals("Vaporeon", model.sceneAt(5_000_000_000L).opponent?.speciesName)
        assertTrue(model.endNanos >= 10_000_000_000L + ReplayFaintPose.DURATION_NANOS)
        assertEquals("WIN", model.sceneAt(model.endNanos).outcome)
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `empty recording with a catalogue has a valid empty range`() {
        val model = MatchReplayModel(MatchArchive(matchId = "empty", articles = emptyList()),
            speciesIdsByName = mapOf("Camerupt" to 323))
        assertEquals(0L, model.startNanos)
        assertEquals(0L, model.endNanos)
        assertNull(model.sceneAt(0L).player)
    }

    private fun event(id: String, payload: ArchivedTestimonyPayload, seconds: Long) = ArchivedRealityArticle(
        id, "missing-start", 0, 0, "TEST", payload, emptyList(), null, null, emptyList(), seconds * 1_000_000_000L)
}
