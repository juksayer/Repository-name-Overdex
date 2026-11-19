package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.MatchArchiveSerializer
import com.example.overdex.model.GameMaster
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.abs
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Read-only November 13 paired video and November 15 settled-sliver controls. */
class RecordedFastMoveRhythmTest {
    private val paired = "2026-11-13-dual-view-1834/"

    @Test fun pixelViewOfAlternatingAttackers() = pairedView(
        paired + "pixel-2026-11-13_18-34-41_f9fa49c6-033e-4932-ae5e-38167d662159.odxmatch.zip",
        "ca038de94fbfe335b00326c622d4f3a1aa869753679b4b80f3f886f5e49021ab", "PLAYER")

    @Test fun moondropViewOfAlternatingAttackers() = pairedView(
        paired + "moondrop-2026-11-13_18-34-43_47519620-3434-4420-a4b7-51d89932c303.odxmatch.zip",
        "e0e070e265c8f9e568cc961e4fcac3a51ba69b5b7aca8bc7bb11d1cf98b2a91e", "OPPONENT")

    @Test fun settledSliverAcrossShieldAndOrdinaryCombat() {
        val (model, go) = replay("2026-11-15-moondrop-1212-review/match.odxmatch.zip",
            "927c311c86d9ee8d834007db3acb22730b37c62a0040995be40e7a6b4fbc28fe")
        verifyUniqueBeats(model, go)
        val fast = model.eventMarkers.filter { it.kind == ReplayEventMarkerKind.FAST_MOVE && it.side == "PLAYER" }
        // Video shows "Attack incoming!" by +72.25. This archive has neither that
        // announcement nor any opponent bar from +70.47 until after +85. Count the
        // discrepancy separately from the intervals where Replay can establish a pause.
        val videoOnly = fast.filter { it.inferred && (it.atNanos - go) / 1e9 in 71.0..75.0 }
        println("Sealeo video-only pause, inferred landings at ${videoOnly.map { (it.atNanos - go) / 1e9 }}")
        for (seconds in listOf(116.0..121.0, 133.0..135.3)) {
            val projected = fast.filter { it.inferred && (it.atNanos - go) / 1e9 in seconds }
            println("Vaporeon settled sliver $seconds inferred=${projected.size}")
            assertTrue("Added attacks during settled-sliver control $seconds: $projected", projected.isEmpty())
        }
        val allFast = model.eventMarkers.filter { it.kind == ReplayEventMarkerKind.FAST_MOVE }
        val lastSupportedOpponentHit = allFast.filter {
            !it.inferred && it.side == "OPPONENT" && (it.atNanos - go) / 1e9 in 137.581..137.583
        }
        assertEquals("Keep Vaporeon's final supported Water Gun", 1, lastSupportedOpponentHit.size)
        val laterPredictions = allFast.filter { it.inferred }.map { (it.atNanos - go) / 1e9 }
        println("From partial cry (~+138.5) to sustained control loss, inferred=" +
            laterPredictions.count { it in 138.50..141.151 })
        println("From visible departure burst (~+140.0) to sustained control loss, inferred=" +
            laterPredictions.count { it in 140.0..141.151 })
        assertTrue("Prediction after sustained terminal control loss: $laterPredictions",
            laterPredictions.none { it >= 141.152 })
    }

    private fun pairedView(path: String, sha: String, first: String) {
        val (model, go) = replay(path, sha)
        verifyUniqueBeats(model, go)
        val second = if (first == "PLAYER") "OPPONENT" else "PLAYER"
        // Both recordings show the attacking alternation; alignment to GO is approximate.
        for ((seconds, attacker) in listOf((1.0..8.25) to first, (43.0..49.5) to second, (66.0..73.0) to first)) {
            val fast = model.eventMarkers.filter { it.kind == ReplayEventMarkerKind.FAST_MOVE &&
                (it.atNanos - go) / 1e9 in seconds }
            println("$path $seconds active=$attacker observed=${fast.count { !it.inferred && it.side == attacker }} inferred=${fast.count { it.inferred && it.side == attacker }} idleInferred=${fast.count { it.inferred && it.side != attacker }}")
            assertTrue("Lost all supported attacks $attacker at $seconds", fast.any { !it.inferred && it.side == attacker })
            assertFalse("Predicted an attack on the idle side at $seconds", fast.any { it.inferred && it.side != attacker })
        }
        // The Pixel player's Camerupt previously dealt a measurable Incinerate to
        // Turtonator. The red remainder holds steady on both phones during ordinary combat.
        val extraLandings = model.eventMarkers.filter {
            it.kind == ReplayEventMarkerKind.FAST_MOVE && it.side == first && it.inferred &&
                (it.atNanos - go) / 1e9 in 33.0..35.1
        }
        println("$path Turtonator settled sliver 33.0..35.1 inferred=${extraLandings.size}")
        assertTrue("Predicted damage during Turtonator’s settled sliver: $extraLandings", extraLandings.isEmpty())
        if (first == "PLAYER") {
            // The two Pixel QTEs start before their first archived grade. Both clips
            // show lost charge controls. The second clip also shows the preceding fire hit.
            val projected = model.eventMarkers.filter {
                it.kind == ReplayEventMarkerKind.FAST_MOVE && it.side == first && it.inferred
            }.map { (it.atNanos - go) / 1e9 }
            for (seconds in listOf(10.0..12.75, 101.0..104.0)) {
                assertFalse("Projected Pixel Fast Move in its QTE at $seconds: $projected",
                    projected.any { it in seconds })
            }
            // 18 live columns at +27.287 become 8 live plus 10 orange, then
            // settle to 8. This accounts for one Incinerate across the capture gap.
            val inGap = projected.filter { it in 28.5..32.0 }
            assertEquals("Exactly one landing should account for the ten-column loss: $inGap",
                1, inGap.size)
            assertEquals("Use the first capture of the new edge and trail", 30.774, inGap.single(), .001)
            assertEquals("Keep the fire hit visibly landing on Gourgeist ahead of the second QTE",
                1, projected.count { it in 98.65..99.1 })
        }
    }

    private val ReplayEventMarker.inferred get() = label.startsWith("Inferred · ")

    private fun verifyUniqueBeats(model: MatchReplayModel, go: Long) {
        val fast = model.eventMarkers.filter { it.kind == ReplayEventMarkerKind.FAST_MOVE }
        val predicted = fast.filter { it.inferred }
        println("rhythm " + fast.joinToString { "${it.side}:${(it.atNanos - go) / 1e9}:${it.label}" })
        for (marker in predicted) {
            assertNull(marker.finishingAttribution)
            assertTrue(marker.sourceArticleIds.isEmpty())
            val action = model.sceneAt(marker.atNanos).fastMoveActions.single {
                it.side == marker.side && it.impactAtNanos == marker.atNanos
            }
            assertTrue(ReplayFastMoveRhythm.INFERRED in action.evidenceKinds)
            assertEquals(1f, action.progress)
            assertNull(action.finishingAssociation)
            val tolerance = minOf(400_000_000L, requireNotNull(action.durationNanos) / 4)
            assertFalse("Duplicate animation for ${marker.label}", fast.any {
                marker !== it && it.side == marker.side && abs(it.atNanos - marker.atNanos) <= tolerance
            })
        }
    }

    private fun replay(path: String, expectedSha: String): Pair<MatchReplayModel, Long> {
        val root = System.getenv("OVERDEX_RHYTHM_RECORDINGS")
        assumeTrue("Set OVERDEX_RHYTHM_RECORDINGS to the original backup directory", !root.isNullOrBlank())
        val file = File(root, path)
        assertEquals(expectedSha, ReplayRetainedAudioRecovery.sha(file.readBytes()))
        val archive = ZipFile(file).use { zip ->
            zip.getInputStream(zip.getEntry("timeline.json")).bufferedReader().use {
                MatchArchiveSerializer.deserialize(it.readText())
            }
        }
        val moves = gameMaster.moves.associateBy { it.moveId }
        fun resolve(id: String, fast: Boolean) = moves[id]?.let { reference ->
            Move(reference.name, PokemonType.valueOf(reference.type.uppercase()), reference.power,
                if (fast) reference.energyGain else abs(reference.energy), fast, reference.turns.takeIf { fast })
        }
        val names = MatchReplayModel.referencedSpeciesNames(archive)
        val species = gameMaster.pokemon.filter { it.speciesName in names }
        val model = MatchReplayModel(archive, species.associate { it.speciesName to it.dex },
            fastMovesBySpeciesName = species.associate { it.speciesName to it.fastMoves.mapNotNull { id -> resolve(id, true) } },
            chargedMovesBySpeciesName = species.associate { it.speciesName to it.chargedMoves.mapNotNull { id -> resolve(id, false) } })
        return model to requireNotNull(model.matchStartNanos)
    }

    private companion object {
        val gameMaster: GameMaster by lazy {
            val json = Json { ignoreUnknownKeys = true }
            json.decodeFromString<GameMaster>(File("src/main/assets/gamemaster.json").readText())
        }
    }
}
