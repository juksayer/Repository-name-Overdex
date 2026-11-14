package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

internal fun pairedMirrorArchive(device: String): MatchArchive = MatchArchiveSerializer.deserialize(
    GZIPInputStream(requireNotNull(RecordedMirrorReplayTest::class.java.getResourceAsStream(
        "/battle/2026-11-13-paired-mirror/$device-timeline.json.gz"))).bufferedReader().use { it.readText() })

internal fun pairedMirrorModel(archive: MatchArchive): MatchReplayModel {
    val reference = Json { ignoreUnknownKeys = true }.decodeFromString<GameMaster>(
        File("src/main/assets/gamemaster.json").readText())
    val moves = reference.moves.associateBy { it.moveId }
    fun move(id: String, fast: Boolean) = moves[id]?.let {
        Move(it.name,PokemonType.valueOf(it.type.uppercase()),it.power,
            if (fast) it.energyGain else kotlin.math.abs(it.energy),fast,if (fast) it.turns else null)
    }
    return MatchReplayModel(archive,
        speciesIdsByName=reference.pokemon.associate { it.speciesName to it.dex },
        fastMovesBySpeciesName=reference.pokemon.associate { p -> p.speciesName to p.fastMoves.mapNotNull { move(it,true) } },
        chargedMovesBySpeciesName=reference.pokemon.associate { p -> p.speciesName to p.chargedMoves.mapNotNull { move(it,false) } })
}

class RecordedMirrorReplayTest {
    @Test fun `paired Rock Tomb is named only on the phone with independent preparation evidence`() {
        for (device in listOf("pixel", "moondrop")) {
            val archive = pairedMirrorArchive(device)
            val before = MatchArchiveSerializer.serialize(archive)
            val model = pairedMirrorModel(archive)
            val scene = model.sceneAt(requireNotNull(model.matchStartNanos) + 58_000_000_000L)
            if (device == "moondrop") {
                val charge = scene.chargedMoveActions.single { it.moveName == "Rock Tomb" }
                assertEquals("PLAYER", charge.side)
                assertTrue("SIDE_FROM_INDEPENDENT_CHARGE_PREPARATION" in charge.evidenceKinds)
            } else {
                assertTrue(scene.chargedMoveActions.none { it.moveName == "Rock Tomb" })
            }
            assertEquals(before, MatchArchiveSerializer.serialize(archive))
        }
    }

    @Test fun `Pixel opponent entry replaces Turtonator before Camerupt attacks without changing archive`() {
        val archive=pairedMirrorArchive("pixel")
        val original=MatchArchiveSerializer.serialize(archive)
        for (input in listOf(archive,archive.copy(articles=archive.articles.reversed()))) {
            val model=pairedMirrorModel(input)
            val at=requireNotNull(model.matchStartNanos)+43_000_000_000L
            assertEquals("Camerupt",model.sceneAt(at).opponent?.speciesName)
            assertEquals("Camerupt",model.sceneAt(at).player?.speciesName)
            assertEquals(false, model.sceneAt(at).opponent?.isFainted)
            assertTrue(model.sceneAt(requireNotNull(model.matchStartNanos)+44_200_000_000L).fastMoveActions.any { it.side=="OPPONENT" })
        }
        assertEquals(original,MatchArchiveSerializer.serialize(archive))
    }
}
