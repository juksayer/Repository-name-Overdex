package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.ArchivedActivePokemonSpeciesWitnessed
import com.example.overdex.battle.archive.MatchArchiveSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Recorded on Moondrop with the player deliberately idle. This fixture keeps
 * the original measurements and timing, including bad measurements. Only ids,
 * wall-clock dates, media and unrelated articles were removed; time starts at 0.
 */
class RecordedIdleReplayTest {
    private val archive = MatchArchiveSerializer.deserialize(
        checkNotNull(javaClass.getResource("/replay/idle-and-gourgeist.json")).readText()
    )
    private val model = MatchReplayModel(
        archive,
        speciesIdsByName = mapOf("Turtonator" to 776, "Gourgeist" to 711, "Camerupt" to 323,
            "Sneasel" to 215, "Sealeo" to 364, "Vaporeon" to 134)
    )

    @Test fun `idle player does not attack while opponent continues hitting`() {
        var opponentAttacks = 0
        for (millis in 39_500L..61_000L step 50) {
            val actions = model.sceneAt(millis * 1_000_000).fastMoveActions
            assertTrue("False player attack at $millis ms: $actions", actions.none { it.side == "PLAYER" })
            opponentAttacks += actions.count { it.side == "OPPONENT" }
        }
        assertTrue("Opponent's witnessed hits must remain visible", opponentAttacks > 0)
    }

    @Test fun `orange pulse readings never regenerate the active players hp`() {
        var previous = 1f
        for (millis in 39_500L..61_000L step 50) {
            val hp = model.sceneAt(millis * 1_000_000).playerHp?.filledFraction ?: continue
            assertTrue("HP grew at $millis ms: $previous -> $hp", hp <= previous)
            previous = hp
        }
        assertTrue(previous < 0.20f)
        val sample = model.sceneAt(56_950_000_000L)
        assertTrue("HP and actions at 56.95 s: ${sample.playerHp}; ${sample.fastMoveActions}",
            checkNotNull(sample.playerHp?.filledFraction) < 0.25f)
    }

    @Test fun `pulse on a smaller rectangle is not an active pokemon attack`() {
        // At 67.116 seconds the supposed 193x17 bar became 144x8 in the
        // very same frame that reported an orange pulse. Keep it as raw data.
        assertTrue(model.sceneAt(67_200_000_000L).fastMoveActions.none { it.side == "PLAYER" })
        assertEquals(0.9894f, checkNotNull(model.sceneAt(67_200_000_000L).opponentHp?.filledFraction), 0.001f)
    }

    @Test fun `seed bomb announcement keeps Gourgeist left and Sealeo right`() {
        val originalMisattribution = archive.articles.single {
            it.sourceId == "ANNOUNCEMENT_SPECIES_ROSTER_ATTRIBUTION_WITNESS" &&
                (it.payload as? ArchivedActivePokemonSpeciesWitnessed)?.speciesName == "Gourgeist"
        }
        assertEquals("OPPONENT", (originalMisattribution.payload as ArchivedActivePokemonSpeciesWitnessed).side)

        for (second in 98L..129L) {
            val scene = model.sceneAt(second * 1_000_000_000 + 200_000_000)
            assertEquals("At $second s", "Gourgeist", scene.player?.speciesName?.substringBefore(" ("))
            assertEquals("At $second s", "Sealeo", scene.opponent?.speciesName)
        }
        assertEquals("Vaporeon", model.sceneAt(133_000_000_000L).opponent?.speciesName)
        // Replay reconstruction never edits the contradictory source article.
        assertEquals("OPPONENT", (originalMisattribution.payload as ArchivedActivePokemonSpeciesWitnessed).side)
    }
}
