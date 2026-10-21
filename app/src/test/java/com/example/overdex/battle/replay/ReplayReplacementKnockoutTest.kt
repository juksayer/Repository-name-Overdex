package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Test

class ReplayReplacementKnockoutTest {
    @Test fun `October 18 paired Obstagoon replacements finish both KO poses`() {
        // Exact identity/lifecycle/configuration articles from the two original
        // archives. Media and unrelated measurements are omitted, times/IDs untouched.
        for ((device, side) in listOf("pixel" to "OPPONENT", "moondrop" to "PLAYER")) {
            val archive = MatchArchiveSerializer.deserialize(checkNotNull(
                javaClass.getResource("/replay/oct18-$device-lifetimes.json")).readText())
            val original = MatchArchiveSerializer.serialize(archive)
            val model = MatchReplayModel(archive)
            val faintAt = archive.articles.single {
                (it.payload as? ArchivedActivePokemonFaintedWitnessed)?.let { p -> p.side == side && p.speciesName == "Obstagoon" } == true
            }.monotonicTimeNanos!!
            val entry = archive.articles.filter {
                (it.payload as? ArchivedActivePokemonSpeciesWitnessed)?.let { p -> p.side == side && p.speciesName == "Lucario" } == true
                    && it.monotonicTimeNanos!! >= faintAt - 2_000_000_000L
            }.minOf { it.monotonicTimeNanos!! }
            val beforeAt = entry - 50_000_000L
            val before = model.sceneAt(beforeAt).let { (if (side == "PLAYER") it.player else it.opponent)!! }
            assertEquals(device, "Obstagoon", before.speciesName)
            assertTrue(device, before.isFainted)
            val pose = ReplayFaintPose.at(before.knockoutPresentationAtNanos, beforeAt,
                side == "PLAYER", before.knockoutFinishByNanos)!!
            assertEquals(device, 90f, kotlin.math.abs(pose.rotationDegrees), 0f)
            assertEquals(device, 0f, pose.saturation, 0f)
            assertNotNull(device, model.sceneAt(beforeAt).decisionTimer)
            val next = model.sceneAt(entry).let { (if (side == "PLAYER") it.player else it.opponent)!! }
            assertEquals(device, "Lucario", next.speciesName)
            assertFalse(device, next.isFainted)
            assertEquals(device, original, MatchArchiveSerializer.serialize(archive))
        }
    }

    @Test fun `late faint testimony still animates the outgoing actor before the replacement`() {
        for (side in listOf("PLAYER", "OPPONENT")) {
            val archive = archive(side, faintMs = 11_000, replacementMs = 10_000)
            val original = MatchArchiveSerializer.serialize(archive)
            val model = MatchReplayModel(archive)
            assertFalse(combatant(model, side, 9_000).isFainted)
            val falling = combatant(model, side, 9_750)
            assertEquals("Obstagoon", falling.speciesName)
            assertTrue(falling.isFainted)
            assertEquals(11_000 * MS, falling.faintedAtNanos)
            assertEquals("FAINT_FIT_BEFORE_REPLACEMENT", falling.knockoutBasis)
            assertEquals(setOf("ko", "replacement"), falling.knockoutSourceArticleIds.toSet())
            assertTrue(kotlin.math.abs(pose(falling, 9_750, side).rotationDegrees) in 1f..89f)
            val lying = combatant(model, side, 9_950)
            assertEquals(90f, kotlin.math.abs(pose(lying, 9_950, side).rotationDegrees), 0f)
            assertEquals(0f, pose(lying, 9_950, side).saturation, 0f)
            val entry = combatant(model, side, 10_000)
            assertEquals("Lucario", entry.speciesName)
            assertFalse(entry.isFainted)
            assertEquals(falling, combatant(model, side, 9_750))
            assertEquals(original, MatchArchiveSerializer.serialize(archive))
        }
    }

    @Test fun `short observed gap completes KO without delaying the recorded arrival`() {
        val model = MatchReplayModel(archive("PLAYER", faintMs = 10_000, replacementMs = 10_840))
        val outgoing = combatant(model, "PLAYER", 10_800)
        assertEquals(10_000 * MS, outgoing.knockoutPresentationAtNanos)
        assertEquals(-90f, pose(outgoing, 10_800, "PLAYER").rotationDegrees, 0f)
        assertEquals(0f, pose(outgoing, 10_800, "PLAYER").saturation, 0f)
        assertEquals("Lucario", combatant(model, "PLAYER", 10_840).speciesName)
    }

    @Test fun `finishing charged projectile lands before the compressed KO and replacement`() {
        val original = archive("OPPONENT", faintMs = 10_000, replacementMs = 10_840)
        val model = MatchReplayModel(original.copy(articles = original.articles + listOf(
            event("ready", ArchivedGetReadyWitnessed, 8_000),
            event("charge", ArchivedChargedMoveEnergySpent("PLAYER", "Camerupt", "Earth Power", 55, "ANNOUNCEMENT"), 9_800),
        )))
        assertEquals("Earth Power", model.sceneAt(9_900 * MS).chargedMoveActions.single().moveName)
        assertTrue(model.sceneAt(10_000 * MS).chargedMoveActions.isEmpty())
        val victim = combatant(model, "OPPONENT", 10_800)
        assertEquals(10_000 * MS, victim.knockoutPresentationAtNanos)
        assertEquals(90f, pose(victim, 10_800, "OPPONENT").rotationDegrees, 0f)
        assertEquals(0f, pose(victim, 10_800, "OPPONENT").saturation, 0f)
        assertEquals("Lucario", combatant(model, "OPPONENT", 10_840).speciesName)
    }

    @Test fun `a much later faint cannot be attached to an earlier voluntary switch`() {
        val model = MatchReplayModel(archive("PLAYER", faintMs = 20_000, replacementMs = 10_000))
        assertFalse(combatant(model, "PLAYER", 9_950).isFainted)
        assertNull(model.sceneAt(9_950 * MS).decisionTimer)
        assertFalse(combatant(model, "PLAYER", 20_000).isFainted)
    }

    @Test fun `faint belongs to the latest appearance when a species switches away and returns`() {
        val original = archive("PLAYER", faintMs = 21_000, replacementMs = 10_000)
        val model = MatchReplayModel(original.copy(articles = original.articles +
            event("return", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Obstagoon", 862), 20_000)))
        assertFalse(combatant(model, "PLAYER", 9_950).isFainted)
        assertFalse(combatant(model, "PLAYER", 20_500).isFainted)
        assertTrue(combatant(model, "PLAYER", 21_500).isFainted)
    }

    private fun archive(side: String, faintMs: Long, replacementMs: Long) = MatchArchive(matchId = "replacement", articles = listOf(
        event("first", ArchivedActivePokemonSpeciesWitnessed(side, "Obstagoon", 862), 0),
        event("foe", ArchivedActivePokemonSpeciesWitnessed(if (side == "PLAYER") "OPPONENT" else "PLAYER", "Camerupt", 323), 0),
        event("go", ArchivedMatchStarted, 0),
        event("replacement", ArchivedActivePokemonSpeciesWitnessed(side, "Lucario", 448), replacementMs),
        event("ko", ArchivedActivePokemonFaintedWitnessed(side, "Obstagoon", 862, "BALL_COUNT"), faintMs),
    ))

    private fun combatant(model: MatchReplayModel, side: String, ms: Long) = model.sceneAt(ms * MS).let {
        (if (side == "PLAYER") it.player else it.opponent)!!
    }
    private fun pose(pokemon: ReplayCombatant, ms: Long, side: String) = ReplayFaintPose.at(
        pokemon.knockoutPresentationAtNanos, ms * MS, side == "PLAYER", pokemon.knockoutFinishByNanos)!!
    private fun event(id: String, payload: ArchivedTestimonyPayload, ms: Long) = ArchivedRealityArticle(
        id, "replacement", 0, 0, "TEST", payload, emptyList(), null, null, emptyList(), ms * MS)
    private companion object { const val MS = 1_000_000L }
}
