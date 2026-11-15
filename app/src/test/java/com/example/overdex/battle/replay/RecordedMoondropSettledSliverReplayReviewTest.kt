package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.MatchArchiveSerializer
import com.example.overdex.model.GameMaster
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/** Optional read-only review of the actual archived Replay and retained audio. */
class RecordedMoondropSettledSliverReplayReviewTest {
    @Test fun `review Sealeo and Vaporeon without altering the archive`() = runBlocking {
        val path = System.getenv("OVERDEX_SETTLED_SLIVER_ARCHIVE")
        assumeTrue(!path.isNullOrBlank())
        val file = File(requireNotNull(path))
        val hash = ReplayRetainedAudioRecovery.sha(file.readBytes())
        assertEquals("2349f44eb481bb4b521253a4e9572647a003f5116f473a0177e38f91c90f8653", hash)
        val archive = ZipFile(file).use { zip -> MatchArchiveSerializer.deserialize(
            zip.getInputStream(zip.getEntry("timeline.json")).bufferedReader().readText()) }
        val before = MatchArchiveSerializer.serialize(archive)
        val gm = Json { ignoreUnknownKeys = true }.decodeFromString<GameMaster>(File("src/main/assets/gamemaster.json").readText())
        val moves = gm.moves.associateBy { it.moveId }
        fun move(id: String, fast: Boolean) = moves[id]?.let { Move(it.name, PokemonType.valueOf(it.type.uppercase()), it.power,
            if (fast) it.energyGain else kotlin.math.abs(it.energy), fast, if (fast) it.turns else null) }
        val names = MatchReplayModel.referencedSpeciesNames(archive)
        val pokemon = gm.pokemon.filter { it.speciesName in names }
        val fast = pokemon.associate { it.speciesName to it.fastMoves.mapNotNull { id -> move(id, true) } }
        val charged = pokemon.associate { it.speciesName to it.chargedMoves.mapNotNull { id -> move(id, false) } }
        val audio = ArchivedEndingAudioRecognizer.recognize(archive, fast.values.flatten() + charged.values.flatten(),
            pokemon.map { it.dex }.toSet(), { file.inputStream() }, { File("src/main/assets", it).readBytes() })
        val model = MatchReplayModel(archive, pokemon.associate { it.speciesName to it.dex },
            fastMovesBySpeciesName = fast, chargedMovesBySpeciesName = charged, audioRecovery = audio)
        val start = requireNotNull(model.matchStartNanos)
        println("audioRecovery=$audio")
        println("replayMarkers=" + model.eventMarkers.filter { (it.atNanos - start) / 1e9 in 42.0..130.0 })
        for (second in listOf(44.894, 56.26, 59.071, 60.861, 109.537, 112.468, 119.799, 123.492, 124.611, 129.746)) {
            val scene = model.sceneAt(start + (second * 1e9).toLong())
            println("t=$second player=${scene.player} opponent=${scene.opponent} opponentHP=${scene.opponentHp} fast=${scene.fastMoveActions}")
        }
        assertEquals(before, MatchArchiveSerializer.serialize(archive))
        assertEquals(hash, ReplayRetainedAudioRecovery.sha(file.readBytes()))
    }
}
