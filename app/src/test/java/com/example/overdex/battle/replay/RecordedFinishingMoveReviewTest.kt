package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.MatchArchiveSerializer
import com.example.overdex.model.GameMaster
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Opt-in, read-only review of complete external archives through the actual Replay model. */
class RecordedFinishingMoveReviewTest {
    @Test fun exportFinishingMovesAndFaintBoundaries() {
        val directory = System.getenv("OVERDEX_FINISHING_REVIEW")
        assumeTrue(directory != null)
        val root = File(requireNotNull(directory))
        val inputs = File(root, "inputs").listFiles { file -> file.name.endsWith(".zip") }!!.sortedBy { it.name }
        check(inputs.isNotEmpty())
        val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
        val reference = json.decodeFromString<GameMaster>(File("src/main/assets/gamemaster.json").readText())
        val moves = reference.moves.associateBy { it.moveId }
        fun move(id: String, fast: Boolean) = moves[id]?.let {
            Move(it.name, PokemonType.valueOf(it.type.uppercase()), it.power,
                if (fast) it.energyGain else kotlin.math.abs(it.energy), fast, if (fast) it.turns else null)
        }
        for (input in inputs) {
            val bytes = input.readBytes()
            val archive = ZipFile(input).use { zip ->
                MatchArchiveSerializer.deserialize(zip.getInputStream(zip.getEntry("timeline.json")).bufferedReader().readText())
            }
            val before = MatchArchiveSerializer.serialize(archive)
            fun model() = MatchReplayModel(archive,
                speciesIdsByName = reference.pokemon.associate { it.speciesName to it.dex },
                fastMovesBySpeciesName = reference.pokemon.associate { p -> p.speciesName to p.fastMoves.mapNotNull { move(it, true) } },
                chargedMovesBySpeciesName = reference.pokemon.associate { p -> p.speciesName to p.chargedMoves.mapNotNull { move(it, false) } })
            val replay = model()
            val charges = field(replay, "recordedChargedMoveUses") as List<*>
            val cycles = field(replay, "fastMoveCycles") as List<*>
            val ko = field(replay, "knockoutTrack") as ReplayKnockoutTrack
            val times = sortedSetOf(replay.matchStartNanos ?: replay.startNanos, replay.endNanos)
            replay.eventMarkers.forEach { marker ->
                if (marker.kind != ReplayEventMarkerKind.FAST_MOVE) {
                    times.addAll(listOf(marker.atNanos - 1, marker.atNanos, marker.atNanos + 100_000_000L))
                }
            }
            charges.filterNotNull().forEach { use ->
                val start = field(use, "animationAtNanos") as Long
                val duration = field(use, "visualDurationNanos") as Long
                times.addAll(listOf(start, start + (duration * .65).toLong(), start + (duration * .8).toLong(),
                    start + duration, start + duration + 100_000_000L))
            }
            cycles.filterIsInstance<ReplayFastMoveCycle>().forEach { cycle ->
                times.addAll(listOf(cycle.startedAtNanos, cycle.impactAtNanos - 1, cycle.impactAtNanos))
            }
            val scenes = times.associateWith(replay::sceneAt)
            times.reversed().forEach { at -> assertEquals("Reverse seeking at $at", scenes[at], replay.sceneAt(at)) }
            val report = buildJsonObject {
                put("input", input.name)
                put("sha256", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
                put("matchId", archive.matchId)
                put("articleCount", archive.articles.size)
                put("matchStartNanos", value(replay.matchStartNanos))
                put("recordingStartNanos", replay.recordingStartNanos)
                put("endNanos", replay.endNanos)
                put("sourceCharges", value(field(replay, "sourceChargedMoveUses")))
                put("charges", value(charges))
                put("fastCycles", value(cycles))
                put("knockouts", value(ko.knockouts))
                put("associations", value(replay.finishingChargeAssociations))
                put("markers", value(replay.eventMarkers))
                put("scenes", value(scenes))
            }
            File(root, "${archive.matchId}-review.json").writeText(json.encodeToString(JsonObject.serializer(), report))
            assertEquals(before, MatchArchiveSerializer.serialize(archive))
            check(bytes.contentEquals(input.readBytes())) { "Input archive changed" }
        }
    }

    private fun field(target: Any, name: String): Any? {
        val direct = target.javaClass.declaredFields.firstOrNull { it.name == name }
        if (direct != null) return direct.apply { isAccessible = true }.get(target)
        return (target.javaClass.getDeclaredField(name + "\$delegate").apply { isAccessible = true }.get(target) as Lazy<*>).value
    }

    private fun value(item: Any?): JsonElement = when (item) {
        null -> JsonNull
        is String -> JsonPrimitive(item)
        is Number -> JsonPrimitive(item)
        is Boolean -> JsonPrimitive(item)
        is Enum<*> -> JsonPrimitive(item.name)
        is Iterable<*> -> JsonArray(item.map(::value))
        is Map<*, *> -> JsonObject(item.entries.associate { it.key.toString() to value(it.value) })
        else -> JsonObject(item.javaClass.declaredFields.filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
            .associate { it.name to value(it.apply { isAccessible = true }.get(item)) })
    }
}
