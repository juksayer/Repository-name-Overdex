package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.model.PokemonType
import org.junit.Assert.*
import org.junit.Test

/** Real Oct 14 entry/badge/move articles, retaining the original bad conclusions. */
class RecordedTeamAttributionTest {
    private fun archive(name: String) = MatchArchiveSerializer.deserialize(
        checkNotNull(javaClass.getResource("/replay/$name.json")).readText()
    )
    private val ids = mapOf("Abomasnow" to 460, "Camerupt" to 323, "Gourgeist" to 711,
        "Sneasel" to 215, "Sealeo" to 364, "Vaporeon" to 134, "Samurott" to 503)
    private fun model(archive: MatchArchive) = MatchReplayModel(archive, speciesIdsByName = ids,
        fastMoveTypesByName = mapOf("INCINERATE" to PokemonType.FIRE, "RAZORLEAF" to PokemonType.GRASS))

    @Test fun `Camerupt entry stays on the player team and opponents keep their actual sequence`() {
        val archive = archive("stale-team-camerupt")
        val replay = model(archive)
        fun check(second: Long, player: String, opponent: String) {
            val scene = replay.sceneAt(second * 1_000_000_000L)
            assertEquals("Player at $second", player, scene.player?.speciesName)
            assertEquals("Opponent at $second", opponent, scene.opponent?.speciesName)
        }
        check(25, "Abomasnow", "Sneasel")
        check(41, "Camerupt", "Sneasel")
        check(50, "Camerupt", "Sealeo")
        check(70, "Camerupt", "Vaporeon")
        assertTrue(archive.articles.any { it.sourceId == "ENTRY_ANNOUNCEMENT_SPECIES_WITNESS" &&
            (it.payload as? ArchivedActivePokemonSpeciesWitnessed)?.let { p -> p.speciesName == "Camerupt" && p.side == "OPPONENT" } == true })
    }

    @Test fun `pre-GO Abomasnow entry beats stale Samurott lead and all later player entries stay left`() {
        val replay = model(archive("stale-team-gourgeist"))
        listOf(21L to "Abomasnow", 25L to "Abomasnow", 45L to "Gourgeist", 79L to "Camerupt", 91L to "Abomasnow").forEach { (second, name) ->
            val scene = replay.sceneAt(second * 1_000_000_000L)
            assertEquals("Player at $second", name, scene.player?.speciesName)
            assertFalse("Player on opponent side at $second", scene.opponent?.speciesName in setOf("Abomasnow", "Gourgeist", "Camerupt", "Samurott"))
        }
    }

    @Test fun `Gourgeist fast move stays fire despite the archived Seed Bomb aftermath error`() {
        val archive = archive("stale-team-gourgeist")
        val replay = model(archive)
        var attacks = 0
        for (millis in 47_500L..55_000L step 50) {
            replay.sceneAt(millis * 1_000_000).fastMoveActions.filter { it.side == "PLAYER" }.forEach {
                attacks++
                assertEquals("Incinerate", it.moveName)
                assertEquals(PokemonType.FIRE, it.type)
            }
        }
        assertTrue("Recorded player attacks must remain visible", attacks > 0)
        assertTrue(archive.articles.any { (it.payload as? ArchivedFastMoveIdentified)?.moveName == "Razor Leaf" })
    }
}
