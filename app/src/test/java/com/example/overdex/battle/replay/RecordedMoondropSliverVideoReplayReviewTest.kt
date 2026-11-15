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
        assertTrue("The preserved terminal clip should retain the partial Vaporeon cry as a candidate",
            audio.partialCryCandidates.any { it.speciesId == 134 &&
                kotlin.math.abs(it.estimatedOnsetNanos - start - (138.497 * 1e9).toLong()) < 100_000_000L &&
                it.fragments.size >= 2 && it.fragments.all { fragment ->
                    fragment.articleId == "68fc1d27-7778-4865-a0dd-bca408e84a73"
                } })
        assertTrue(model.eventMarkers.any { it.kind == ReplayEventMarkerKind.POSSIBLE_CRY &&
            it.label.contains("Vaporeon") && it.side == null &&
            "68fc1d27-7778-4865-a0dd-bca408e84a73" in it.sourceArticleIds })
        val recoveredHit = model.sceneAt(start + (137.376 * 1e9).toLong()).fastMoveActions
            .single { it.side == "PLAYER" && it.moveName == "Incinerate" }
        assertEquals(1f, recoveredHit.progress)
        assertTrue("The recovered hit must cite the source color transition",
            "d5390588-c98b-45d6-8714-acc9ab0611af" in recoveredHit.sourceArticleIds)
        assertTrue("The final faint is still not established by a cry or result alone",
            model.finishingFastMoveAssociations.none { it.recipientSpeciesName == "Vaporeon" })
        val sealeoKo = model.finishingFastMoveAssociations.singleOrNull {
            it.recipientSpeciesName == "Sealeo" && it.moveName == "Incinerate"
        }
        assertNotNull("The recovered move and independently resolved Sealeo loss should be linked", sealeoKo)
        val sealeoImpact = requireNotNull(sealeoKo).impactAtNanos
        val automaticSealeo = model.sceneAt(sealeoImpact)
        assertFalse("Reverse seeking must restore the living Sealeo before the inferred impact",
            model.sceneAt(sealeoImpact - 10_000_000L).opponent!!.isFainted)
        assertTrue(automaticSealeo.opponent!!.isFainted)
        assertEquals("Sealeo", automaticSealeo.opponent!!.displayName)
        assertTrue(automaticSealeo.fastMoveActions.any { it.side == "PLAYER" && it.moveName == "Incinerate" &&
            it.progress == 1f && it.finishingAssociation?.recipientSpeciesName == "Sealeo" })
        assertEquals(sealeoKo.faintSourceAtNanos, automaticSealeo.opponent!!.faintedAtNanos)
        val afterSealeo = model.sceneAt(sealeoImpact + 500_000_000L)
        assertTrue("A confirmed faint must not start another attack", afterSealeo.fastMoveActions.none {
            it.side == "OPPONENT" && it.startedAtNanos >= sealeoImpact
        })
        println("AUTO_FAST_KOS=" + model.finishingFastMoveAssociations)
        System.getenv("OVERDEX_REVIEWED_CORRECTIONS")?.let { path ->
            val correctionFile = ReplayCorrectionTransfer.decode(File(path).readBytes())
            assertEquals(archive.matchId, correctionFile.sourceMatchId)
            val projection = ReplayTrainerProjection(archive.matchId, start, correctionFile.entries, fast)
            for ((recipient, hitAt, faintAt) in listOf(Triple("Sealeo", 85.63, 86.42),
                Triple("Vaporeon", 137.30, 139.40))) {
                val hit = start + (hitAt * 1e9).toLong()
                val landing = projection.apply(model.sceneAt(hit), hit)
                assertEquals("Gourgeist", landing.player?.displayName)
                assertEquals(recipient, landing.opponent?.displayName)
                assertTrue("Reviewed Incinerate must land", landing.fastMoveActions.any {
                    it.side == "PLAYER" && it.moveName == "Incinerate" && it.type == PokemonType.FIRE &&
                        it.progress == 1f && "TRAINER_ENTRY" in it.evidenceKinds
                })
                assertEquals("An automatic recovery and its reviewed correction are one attack", 1,
                    landing.fastMoveActions.count { it.side == "PLAYER" && it.moveName == "Incinerate" })
                val faint = start + (faintAt * 1e9).toLong()
                val down = projection.apply(model.sceneAt(faint), faint)
                assertTrue("Reviewed recipient must faint", down.opponent!!.isFainted)
                assertEquals("Incinerate", down.opponent!!.trainerFinishingMove)
                val earlier = start + ((hitAt - 1.0) * 1e9).toLong()
                val reversed = projection.apply(model.sceneAt(earlier), earlier)
                assertFalse("Reverse seek must not retain the later faint", reversed.opponent!!.isFainted)
            }
            val otherMatch = ReplayTrainerProjection("different-match", start, correctionFile.entries, fast)
            val hit = start + (85.63 * 1e9).toLong()
            assertFalse("Corrections cannot affect another match", otherMatch.apply(model.sceneAt(hit), hit)
                .fastMoveActions.any { "TRAINER_ENTRY" in it.evidenceKinds })
        }
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
