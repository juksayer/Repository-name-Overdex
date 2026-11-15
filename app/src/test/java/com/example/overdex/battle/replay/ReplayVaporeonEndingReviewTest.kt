package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
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

/** Optional read-only review of the trainer's November 15 shielded final exchange. */
class ReplayVaporeonEndingReviewTest {
    @Test fun `review preserved trial with packaged audio and replay`() = runBlocking {
        val folder = System.getenv("OVERDEX_VAPOREON_ENDING_REVIEW")
        assumeTrue(!folder.isNullOrBlank())
        val root = File(requireNotNull(folder))
        val input = File(root, "2026-11-15_09-32-11_b16a482c-3b02-4e52-b0db-7c8f602a54cd.odxmatch.zip")
        val hash = ReplayRetainedAudioRecovery.sha(input.readBytes())
        assertEquals("005e78ef51a4f272a89b7a555cb80241703b57770a21b9f3966f34efb797253f", hash)
        val archive = ZipFile(input).use { zip -> MatchArchiveSerializer.deserialize(zip.getInputStream(zip.getEntry("timeline.json")).bufferedReader().readText()) }
        val before = MatchArchiveSerializer.serialize(archive)
        val gm = Json { ignoreUnknownKeys = true }.decodeFromString<GameMaster>(File("src/main/assets/gamemaster.json").readText())
        val moves = gm.moves.associateBy { it.moveId }
        fun move(id: String, fast: Boolean) = moves[id]?.let { Move(it.name, PokemonType.valueOf(it.type.uppercase()), it.power,
            if (fast) it.energyGain else kotlin.math.abs(it.energy), fast, if (fast) it.turns else null) }
        val names = MatchReplayModel.referencedSpeciesNames(archive)
        val pokemon = gm.pokemon.filter { it.speciesName in names }
        val fast = pokemon.associate { it.speciesName to it.fastMoves.mapNotNull { id -> move(id, true) } }
        val charged = pokemon.associate { it.speciesName to it.chargedMoves.mapNotNull { id -> move(id, false) } }
        val audio = ArchivedEndingAudioRecognizer.recognize(archive, fast.values.flatten() + charged.values.flatten(), pokemon.map { it.dex }.toSet(),
            { input.inputStream() }, { File("src/main/assets", it).readBytes() })
        val replay = MatchReplayModel(archive, pokemon.associate { it.speciesName to it.dex },
            fastMovesBySpeciesName = fast, chargedMovesBySpeciesName = charged, audioRecovery = audio)
        val start = requireNotNull(replay.matchStartNanos)
        File(root, "full-audio-replay-review.txt").printWriter().use { out ->
            out.println("audio=$audio")
            out.println("faint markers=${replay.eventMarkers.filter { it.kind == ReplayEventMarkerKind.FAINT }}")
            out.println("fast markers=${replay.eventMarkers.filter { it.kind == ReplayEventMarkerKind.FAST_MOVE && it.atNanos >= start + 80_000_000_000L }}")
            out.println("links=${replay.finishingChargeAssociations}")
            for (second in listOf(82, 86, 87, 94, 96, 100, 108)) {
                val scene = replay.sceneAt(start + second * 1_000_000_000L)
                out.println("$second opponent=${scene.opponent} opponentHp=${scene.opponentHp} fast=${scene.fastMoveActions} charged=${scene.chargedMoveActions}")
            }
        }
        assertEquals(before, MatchArchiveSerializer.serialize(archive))
        assertEquals(hash, ReplayRetainedAudioRecovery.sha(input.readBytes()))
    }
}
