package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.model.GameMaster
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class ReplayEndingAudioRecoveryTest {
    @Test fun `original ending recovers a single Seed Bomb and partial Dragonair cry through production loader`() = runBlocking {
        val path = System.getenv("OVERDEX_ENDING_AUDIO_REVIEW")
        assumeTrue(!path.isNullOrBlank())
        val root = File(requireNotNull(path))
        val fixture = Json.parseToJsonElement(File(root, "fixture.json").readText()).jsonObject
        val input = File(fixture.getValue("archive").jsonPrimitive.content)
        val hash = ReplayRetainedAudioRecovery.sha(input.readBytes())
        assertEquals(fixture.getValue("archiveSha256").jsonPrimitive.content, hash)
        val archive = ZipFile(input).use { zip -> MatchArchiveSerializer.deserialize(zip.getInputStream(zip.getEntry("timeline.json")).bufferedReader().readText()) }
        val original = MatchArchiveSerializer.serialize(archive)
        val gm = Json { ignoreUnknownKeys = true }.decodeFromString<GameMaster>(File("src/main/assets/gamemaster.json").readText())
        val moveMap = gm.moves.associateBy { it.moveId }
        fun move(id: String, fast: Boolean) = moveMap[id]?.let { Move(it.name, PokemonType.valueOf(it.type.uppercase()), it.power,
            if (fast) it.energyGain else kotlin.math.abs(it.energy), fast, if (fast) it.turns else null) }
        val names = MatchReplayModel.referencedSpeciesNames(archive)
        val species = gm.pokemon.filter { it.speciesName in names }
        val fast = species.associate { it.speciesName to it.fastMoves.mapNotNull { id -> move(id, true) } }
        val charged = species.associate { it.speciesName to it.chargedMoves.mapNotNull { id -> move(id, false) } }
        val recovery = ArchivedEndingAudioRecognizer.recognize(archive, fast.values.flatten() + charged.values.flatten(), species.map { it.dex }.toSet(),
            { input.inputStream() }, { File("src/main/assets", it).readBytes() })
        File(root, "recovery-result.txt").writeText("Species: $names\n$recovery\n")
        val seed = recovery.charges.single { it.moveName == "Seed Bomb" }
        assertEquals(2, seed.fragments.size)
        val cry = recovery.cries.single { it.speciesId == 148 }
        assertEquals(listOf("b0ff96b6-8521-4db9-8377-56f3c19ec70f"), cry.fragments.map { it.articleId })
        assertEquals(setOf("SCORCHINGSANDS", "BRUTALSWING"), recovery.unavailableReferences.toSet())
        assertTrue(recovery.unavailableArtifacts.toString(), recovery.unavailableArtifacts.isEmpty())
        val ids = gm.pokemon.associate { it.speciesName to it.dex }
        fun model(a: MatchArchive, audio: ReplayAudioRecovery = recovery) = MatchReplayModel(a, ids,
            fastMovesBySpeciesName = fast, chargedMovesBySpeciesName = charged, audioRecovery = audio)
        val replay = model(archive)
        File(root, "recovery-result.txt").appendText("\nAssociations: ${replay.finishingChargeAssociations}\nClaims: ${replay.possibleDamageClaims.filter { it.atNanos >= seed.estimatedOnsetNanos - 5_000_000_000L }}\n")
        val link = replay.finishingChargeAssociations.single { it.basis == ReplayTerminalAudioAssociation.BASIS }
        assertEquals("Gourgeist", link.attackerSpeciesName.substringBefore(" ("))
        assertEquals("Dragonair", link.recipientSpeciesName)
        assertNull(link.replacementAtNanos)
        assertTrue(replay.sceneAt(link.presentationImpactAtNanos).opponent!!.isFainted)
        assertEquals(1f, replay.sceneAt(link.presentationImpactAtNanos).chargedMoveActions.single { it.moveName == "Seed Bomb" }.progress, 0f)
        assertFalse(replay.sceneAt(link.presentationImpactAtNanos - 1).opponent!!.isFainted)
        assertEquals(1, replay.eventMarkers.count { it.kind == ReplayEventMarkerKind.CHARGED_MOVE && it.sourceAtNanos == seed.estimatedOnsetNanos })
        val reverse = model(archive.copy(articles = archive.articles.reversed()))
        assertEquals(replay.finishingChargeAssociations, reverse.finishingChargeAssociations)
        assertEquals(replay.sceneAt(link.presentationImpactAtNanos), reverse.sceneAt(link.presentationImpactAtNanos))
        val baseline = model(archive, ReplayAudioRecovery())
        assertEquals(baseline.eventMarkers.filter { it.kind == ReplayEventMarkerKind.FAST_MOVE }, replay.eventMarkers.filter { it.kind == ReplayEventMarkerKind.FAST_MOVE })
        // Tiny surviving HP, competing uses, incomplete reference/media coverage and absent cry block attribution.
        val template = archive.articles.first()
        fun blocked(p: ArchivedTestimonyPayload, at: Long = seed.estimatedOnsetNanos + 100_000_000L) {
            val base = if (p is ArchivedActiveHpBarMeasured) archive.articles.last { it.payload is ArchivedActiveHpBarMeasured } else template
            val payload = if (p is ArchivedActiveHpBarMeasured) (base.payload as ArchivedActiveHpBarMeasured).copy(side = p.side, filledFraction = p.filledFraction) else p
            val altered = archive.copy(articles = archive.articles + base.copy(articleId = "conflict", payload = payload, monotonicTimeNanos = at))
            assertTrue(p.toString(), model(altered).finishingChargeAssociations.none { it.basis == ReplayTerminalAudioAssociation.BASIS })
        }
        blocked(ArchivedFastMoveUseObserved("pending", "PLAYER", "OPPONENT", evidenceKinds = emptyList(), basis = "test"))
        blocked(ArchivedFastMoveUseObserved("unknown", "UNKNOWN", "UNKNOWN", evidenceKinds = emptyList(), basis = "test"), seed.estimatedOnsetNanos - 4_000_000_000L)
        blocked(ArchivedChargedMoveEnergySpent("PLAYER", "Gourgeist", "Shadow Ball", 55, "test"))
        blocked(ArchivedActiveHpBarMeasured("OPPONENT", 0, 0, 100, 10, .00001f, 1080, 2400), cry.estimatedOnsetNanos + 1)
        blocked(ArchivedOpponentBattleResourceCountMeasured("SHIELDS", 1, 2))
        for (bad in listOf(recovery.copy(cries = emptyList()), recovery.copy(unavailableReferences = listOf("unknown move")),
            recovery.copy(unavailableArtifacts = listOf("missing")))) assertTrue(model(archive, bad).finishingChargeAssociations.none { it.basis == ReplayTerminalAudioAssociation.BASIS })
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
        assertEquals(hash, ReplayRetainedAudioRecovery.sha(input.readBytes()))
    }
}
