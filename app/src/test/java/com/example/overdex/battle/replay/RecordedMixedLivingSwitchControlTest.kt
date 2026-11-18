package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.MatchArchiveSerializer
import com.example.overdex.model.GameMaster
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/** The 16:05 recording shows Swampert switching out and later returning alive. */
class RecordedMixedLivingSwitchControlTest {
    @Test fun `recovered audio cannot convert a living switch into a KO`() = runBlocking {
        val path = System.getenv("OVERDEX_LIVING_SWITCH_ARCHIVE")
        assumeTrue(!path.isNullOrBlank())
        val file = File(requireNotNull(path)); val hash = ReplayRetainedAudioRecovery.sha(file.readBytes())
        assertEquals("e3620d1e8c954537806e4ac1760f4bfc313c4fe4a70bbc0f46b1483baa555aaf", hash)
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
        val returned = model.sceneAt(start + 85_000_000_000L)
        assertEquals("Swampert", returned.opponent?.displayName)
        assertFalse("The returning Swampert survived its earlier switch", returned.opponent!!.isFainted)
        assertTrue("The earlier living switch cannot receive finishing credit", model.finishingAttributions.none {
            it.recipientSpeciesName == "Swampert" && it.landingAtNanos < start + 84_092_000_000L
        })
        assertEquals(before, MatchArchiveSerializer.serialize(archive))
        assertEquals(hash, ReplayRetainedAudioRecovery.sha(file.readBytes()))
    }
}
