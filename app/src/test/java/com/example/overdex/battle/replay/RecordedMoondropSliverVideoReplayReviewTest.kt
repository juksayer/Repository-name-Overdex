package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.MatchArchiveSerializer
import com.example.overdex.model.GameMaster
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/** Read-only archived Replay review for the paired 12:12 recording. */
class RecordedMoondropSliverVideoReplayReviewTest {
    @Test fun `review source-time actions and faints against recorded archive`() = runBlocking {
        val path = System.getenv("OVERDEX_SLIVER_VIDEO_ARCHIVE")
        assumeTrue(!path.isNullOrBlank())
        val file = File(requireNotNull(path))
        val hash = ReplayRetainedAudioRecovery.sha(file.readBytes())
        assertEquals("927c311c86d9ee8d834007db3acb22730b37c62a0040995be40e7a6b4fbc28fe", hash)
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
        for (second in listOf(84.812, 135.896)) {
            assertTrue("Retained overlapping audio should preserve the Incinerate candidate at $second",
                audio.mixedCandidates.any { it.moveName == "Incinerate" &&
                    kotlin.math.abs(it.estimatedOnsetNanos - start - (second * 1e9).toLong()) < 100_000_000L })
        }
        assertTrue("Recovered sound alone cannot create the missing hit or final faint",
            model.finishingFastMoveAssociations.none { it.moveName == "Incinerate" &&
                (it.recipientSpeciesName == "Sealeo" || it.recipientSpeciesName == "Vaporeon") })
        for (second in listOf(83.856, 85.402, 137.376)) {
            val hp = requireNotNull(model.sceneAt(start + (second * 1e9).toLong()).opponentHp)
            assertFalse("Historical HP must remain unconfirmed", hp.currentHpConfirmed)
            assertNotNull("A validated source observation can still be shown as last seen", hp.lastSeenFraction)
        }
        println("audioRecovery=$audio")
        println("replayMarkers=" + model.eventMarkers.filter { (it.atNanos - start) / 1e9 in 60.0..150.0 })
        for (second in listOf(70.472, 83.856, 85.402, 86.369, 86.510, 101.741, 137.124, 137.376, 138.165, 149.869)) {
            val scene = model.sceneAt(start + (second * 1e9).toLong())
            println("t=$second player=${scene.player} opponent=${scene.opponent} opponentHP=${scene.opponentHp} fast=${scene.fastMoveActions}")
        }
        for (second in 0..150) {
            val scene = model.sceneAt(start + second * 1_000_000_000L)
            println("HP_REVIEW,$second,${scene.player?.speciesName},${scene.player?.isFainted},${scene.playerHp?.filledFraction},${scene.playerHp?.currentHpConfirmed}," +
                "${scene.opponent?.speciesName},${scene.opponent?.isFainted},${scene.opponentHp?.filledFraction},${scene.opponentHp?.currentHpConfirmed}")
        }
        assertEquals(before, MatchArchiveSerializer.serialize(archive))
        assertEquals(hash, ReplayRetainedAudioRecovery.sha(file.readBytes()))
    }
}
