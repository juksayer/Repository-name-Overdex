package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.model.GameMaster
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import java.io.File

/** Read-only diagnostic over immutable external archives; no injected testimony. */
class RecordedMirrorReplayReviewTest {
    @Test fun `export paired archive actor and action reconstruction`() {
        val directory = System.getenv("OVERDEX_MIRROR_REVIEW")
        assumeTrue(directory != null)
        val root = File(requireNotNull(directory))
        val reference = Json { ignoreUnknownKeys = true }.decodeFromString<GameMaster>(
            File("src/main/assets/gamemaster.json").readText())
        val moves = reference.moves.associateBy { it.moveId }
        fun move(id: String, fast: Boolean) = moves[id]?.let {
            Move(it.name,PokemonType.valueOf(it.type.uppercase()),it.power,
                if (fast) it.energyGain else kotlin.math.abs(it.energy),fast,if (fast) it.turns else null)
        }
        val fast = reference.pokemon.associate { p -> p.speciesName to p.fastMoves.mapNotNull { move(it,true) } }
        val charged = reference.pokemon.associate { p -> p.speciesName to p.chargedMoves.mapNotNull { move(it,false) } }
        for (device in listOf("pixel", "moondrop")) {
            val archive = MatchArchiveSerializer.deserialize(File(root,"$device-paired-timeline.json").readText())
            val before = MatchArchiveSerializer.serialize(archive)
            val model = MatchReplayModel(archive, speciesIdsByName=reference.pokemon.associate { it.speciesName to it.dex }, fastMovesBySpeciesName=fast, chargedMovesBySpeciesName=charged)
            val start = requireNotNull(model.matchStartNanos)
            File(root,"$device-replay-review.txt").printWriter().use { out ->
                for (field in listOf("recordedFastMoveUses", "fastMoveCycles", "sourceChargedMoveUses", "recordedChargedMoveUses")) {
                    val reflected = model.javaClass.declaredFields.singleOrNull { it.name == field } ?: continue
                    reflected.isAccessible = true
                    out.println("$field=${reflected.get(model)}")
                }
                out.println("MARKERS ${model.eventMarkers}")
                var previous = ""
                for (step in 0..1300) {
                    val at = start + step*100_000_000L
                    val scene = model.sceneAt(at)
                    val state = "player=${scene.player}; opponent=${scene.opponent}; fast=${scene.fastMoveActions.map { it.copy(progress=0f) }}; charge=${scene.chargedMoveActions.map { it.copy(progress=0f,elapsedNanos=0) }}"
                    if (state != previous) { out.println("${step/10.0}\t$state"); previous=state }
                }
            }
            assertEquals(before,MatchArchiveSerializer.serialize(archive))
        }
    }
}
