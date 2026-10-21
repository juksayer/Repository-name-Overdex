package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import org.junit.Assert.*
import org.junit.Test

/** October 14 Moondrop records. The user confirms no shields or charges in the later match.
 * Fixtures omit media/unrelated witnesses and shift the monotonic origin; testimony is unchanged. */
class RecordedNoPlayerChargesTest {
    private fun archive(name: String) = MatchArchiveSerializer.deserialize(
        checkNotNull(javaClass.getResource("/replay/$name-20261014.json")).readText())

    private fun model(archive: MatchArchive) = MatchReplayModel(archive, chargedMovesBySpeciesName = mapOf(
        "Abomasnow" to listOf(Move("Weather Ball (Ice)", PokemonType.ICE, 60, 35, false),
            Move("Energy Ball", PokemonType.GRASS, 90, 45, false)),
        "Sneasel" to listOf(Move("Foul Play", PokemonType.DARK, 60, 40, false)),
        "Sealeo" to listOf(Move("Water Pulse", PokemonType.WATER, 80, 50, false)),
        "Vaporeon" to listOf(Move("Aqua Tail", PokemonType.WATER, 50, 35, false))))

    @Test fun `no player charges means no player QTE or shield consumption`() {
        val archive = archive("no-player-charges")
        val original = MatchArchiveSerializer.serialize(archive)
        val model = model(archive)
        val start = archive.articles.first { it.payload is ArchivedMatchStarted }.monotonicTimeNanos!!
        val scenes = (0L..1230L).map { model.sceneAt(start + it * 100_000_000L) }
        val charges = scenes.flatMap { it.chargedMoveActions }.filter { it.phase == ReplayChargePhase.STRIKING }
            .distinctBy { it.side to it.startedAtNanos }
        assertTrue(charges.all { it.side == "OPPONENT" })
        assertEquals(listOf("Foul Play", "Water Pulse", "Water Pulse", "Aqua Tail"), charges.map { it.moveName })
        assertTrue("Unexpected player QTE: " + scenes.filter { it.qteMilestoneSide == "PLAYER" }.map { it.qteMilestone },
            scenes.none { it.qteMilestoneSide == "PLAYER" })
        assertTrue(scenes.all { it.playerResources.shields == 2 })
        assertEquals("LOSS", scenes.last().outcome)
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `later incoming announcement pauses fast moves even in an older archive`() {
        val archive = archive("no-player-charges")
        val model = model(archive)
        val start = archive.articles.first { it.payload is ArchivedMatchStarted }.monotonicTimeNanos!!
        val scene = model.sceneAt(start + 109_000_000_000L)
        assertTrue(scene.fastMoveActions.isEmpty())
        assertTrue(scene.chargedMoveActions.any { it.side == "OPPONENT" && it.phase == ReplayChargePhase.CHARGING })
    }

    @Test fun `earlier recording retains the players three real charges and QTE`() {
        val archive = archive("abomasnow-three-charges")
        val model = model(archive)
        val start = archive.articles.first { it.payload is ArchivedMatchStarted }.monotonicTimeNanos!!
        val scenes = (0L..670L).map { model.sceneAt(start + it * 100_000_000L) }
        val charges = scenes.flatMap { it.chargedMoveActions }.filter { it.phase == ReplayChargePhase.STRIKING }
            .distinctBy { it.side to it.startedAtNanos }
        assertEquals(listOf("Weather Ball (Ice)", "Energy Ball", "Energy Ball"), charges.map { it.moveName })
        assertTrue(charges.all { it.side == "PLAYER" })
        assertTrue(scenes.any { it.qteMilestoneSide == "PLAYER" })
    }
}
