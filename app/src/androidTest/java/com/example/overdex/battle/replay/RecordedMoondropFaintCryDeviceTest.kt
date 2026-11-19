package com.example.overdex.battle.replay

import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.archive.*
import com.example.overdex.data.GameMasterLoader
import com.example.overdex.data.LocalSpriteProvider
import com.example.overdex.data.PokemonRepository
import com.example.overdex.data.local.PokedexDatabase
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class RecordedMoondropFaintCryDeviceTest {
    private fun review(argument: String, fileName: String, expectedHash: String,
        controls: ((MatchArchive, ReplayAudioRecovery, (MatchArchive, ReplayAudioRecovery) -> MatchReplayModel) -> Unit)? = null,
        verify: (MatchReplayModel) -> Unit) = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString(argument) == "true")
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(ctx.filesDir, "match_archives/$fileName")
        val hash = ReplayRetainedAudioRecovery.sha(file.readBytes())
        assertEquals(expectedHash, hash)
        val archive = file.inputStream().use(MatchArchivePackageReader::read)
        val catalog = GameMasterLoader(ctx)
        val repo = PokemonRepository(PokedexDatabase.getDatabase(ctx).pokemonDao(), LocalSpriteProvider(ctx.assets), catalog)
        val crops = ArchivedSpeciesCropRecognizer.recognize(archive, file::inputStream, repo.getAllSpeciesNames(),
            resolveSpeciesId = { repo.getPokemonByName(it)?.id })
        val species = (MatchReplayModel.referencedSpeciesNames(archive) + crops.map { it.speciesName })
            .mapNotNull { repo.getPokemonByName(it) }
        val fast = species.associate { it.name to it.fastMoves }
        val charged = species.associate { it.name to it.chargedMoves }
        val asset = { name: String -> ctx.assets.open(name).use { it.readBytes() } }
        val participants = MatchReplayModel.audioParticipantNames(archive, crops)
        val inBattle = species.filter { it.name in participants }
        val audio = ArchivedEndingAudioRecognizer.recognize(archive, inBattle.flatMap { it.fastMoves + it.chargedMoves }.distinct(),
            inBattle.map { it.id }.toSet(), file::inputStream, asset)
        val impact = ReplayImpactFinish.read(archive, File(ctx.filesDir, "artifacts/frames/sha256"),
            species.associate { it.name to (it.fastMoves + it.chargedMoves) }, audio, file::inputStream, asset)
        val last = ReplayLastOpponentFastFinish.read(archive, catalog, file::inputStream, asset)
        fun model(input: MatchArchive, sound: ReplayAudioRecovery) = MatchReplayModel(input, species.associate { it.name to it.id }, crops,
            fastMovesBySpeciesName = fast, chargedMovesBySpeciesName = charged,
            audioRecovery = sound, impactEvidence = impact, lastOpponentFastEvidence = last)
        verify(model(archive, audio))
        controls?.invoke(archive, audio, ::model)
        assertEquals(hash, ReplayRetainedAudioRecovery.sha(file.readBytes()))
    }

    @Test fun nightSlashLandsBeforeCradilysFullCry() = review(
        "recordedMoondropCradily", "2026-11-19_00-13-40_2329d744-dca0-440f-ae4a-8b7e82220806.odxmatch.zip",
        "6b197d6e08401638953d9018d39537152b3aaf79a57c018a3a355b2c0757b5ac",
        controls = { archive, audio, model ->
            fun uncredited(input: MatchArchive, sound: ReplayAudioRecovery = audio) {
                val replay = model(input, sound)
                assertTrue(replay.finishingChargeAssociations.none { it.recipientSpeciesName == "Cradily" })
                assertTrue(replay.eventMarkers.none { it.kind == ReplayEventMarkerKind.FAINT && "Cradily" in it.label })
            }
            assertTrue("Earlier opponent remains a rival", "Cry 254" in audio.referenceCandidateNames)
            assertFalse("Configured but absent Turtonator", "Cry 776" in audio.referenceCandidateNames)
            val at = audio.terminalWholeCries.first { it.speciesId == 346 }.estimatedOnsetNanos
            uncredited(archive, audio.copy(partialCryCandidates = emptyList()))
            uncredited(archive, audio.copy(terminalWholeCries = emptyList()))
            // Without the witnessed pre-QTE reception this use could still be pending.
            uncredited(archive.copy(articles = archive.articles.map { row ->
                if (row.articleId == "d44d126b-59e2-4ee2-b8ae-9a51d3b3f2f2") row.copy(predecessorIds = emptyList()) else row
            }))
            val live = archive.articles.single { it.articleId == "37cddc39-a6d3-49e5-9490-de9314b94ac2" }
            uncredited(archive.copy(articles = archive.articles + live.copy(articleId = "later-live",
                monotonicTimeNanos = at + 250_000_000L)))
            val switch = archive.articles.first { (it.payload as? ArchivedActivePokemonSpeciesWitnessed)?.speciesName == "Sceptile" }
            uncredited(archive.copy(articles = archive.articles + switch.copy(articleId = "living-switch",
                monotonicTimeNanos = at - 100_000_000L)))
        }
    ) { model ->
        val audio = model.audioRecovery
        assertTrue("Full cry, pool errors: ${audio.unavailableReferences}", audio.terminalWholeCries.any { it.speciesId == 346 })
        assertTrue("Second source", audio.partialCryCandidates.any { it.speciesId == 346 })
        val faint = model.eventMarkers.single { it.label == "Faint · Cradily · Night Slash by Greninja" }
        val link = requireNotNull(faint.finishingAttribution)
        assertEquals(1, model.eventMarkers.count { it.kind == ReplayEventMarkerKind.FAINT && "Cradily" in it.label })
        assertTrue(faint.faintTiming is ReplayFaintTiming.Cry)
        assertEquals(106.082, (faint.atNanos - requireNotNull(model.matchStartNanos)) / 1e9, .002)
        assertTrue(link.faintArticleIds.containsAll(listOf("51561b3f-c0a4-4ad7-943a-567bac4b9da7",
            "8f9850e4-39c0-4b6a-8df7-124ca824ef9d")))
        assertNull(link.reception)
        assertEquals(1, model.eventMarkers.count { it.kind == ReplayEventMarkerKind.CHARGED_MOVE && it.finishingAttribution == link })
        val landing = model.sceneAt(link.landingAtNanos)
        val action = landing.chargedMoveActions.single { it.finishingAssociation == link }
        assertEquals("Greninja", landing.player!!.speciesName)
        assertEquals("Cradily", landing.opponent!!.speciesName)
        assertEquals("Night Slash", action.moveName)
        assertEquals(1f, ReplayChargePose.at(action, true).projectileProgress, 0f)
        assertTrue(model.sceneAt(faint.atNanos).opponent!!.isFainted)
        assertEquals(landing, model.sceneAt(link.landingAtNanos))
    }

    @Test fun electrodeAndJolteonReceiveDistinctDragonPulses() = review(
        "recordedMoondropJolteon", "2026-11-18_21-40-23_650b264c-b95e-49ea-80a6-0133d1bca575.odxmatch.zip",
        "e59dbf0c72445ef59ebc9a0ec66aca02d65b3a0216bf14d9f00aeaeee44c3da9",
        controls = { archive, audio, model ->
            fun uncredited(input: MatchArchive, sound: ReplayAudioRecovery = audio) {
                assertTrue(model(input, sound).finishingChargeAssociations.none { it.recipientSpeciesName == "Jolteon" })
            }
            uncredited(archive, audio.copy(terminalWholeCries = emptyList()))
            val cryAt = audio.terminalWholeCries.first { it.speciesId == 135 }.estimatedOnsetNanos
            val added = archive.articles.last { it.payload is ArchivedFastMoveUseObserved }
            fun withEvent(payload: ArchivedTestimonyPayload) = archive.copy(articles = archive.articles +
                added.copy(articleId = "control", payload = payload, monotonicTimeNanos = cryAt - 10_000_000L))
            uncredited(withEvent(added.payload))
            val liveBar = archive.articles.first { row ->
                val bar = row.payload as? ArchivedActiveHpBarMeasured
                bar?.side == "OPPONENT" && bar.fillColor == "RED" && bar.filledFraction > 0f
            }
            uncredited(archive.copy(articles = archive.articles + liveBar.copy(articleId = "surviving-sliver",
                monotonicTimeNanos = cryAt + 250_000_000L)))
            val entry = archive.articles.first { (it.payload as? ArchivedActivePokemonSpeciesWitnessed)?.side == "OPPONENT" }
            uncredited(withEvent((entry.payload as ArchivedActivePokemonSpeciesWitnessed).copy(speciesName = "Electrode", speciesId = 101)))
            uncredited(archive.copy(articles = archive.articles.filterNot { it.payload is ArchivedTerminalParticleObservations }))
        }
    ) { model ->
        val faints = model.eventMarkers.filter { it.kind == ReplayEventMarkerKind.FAINT }
        val links = listOf("Electrode", "Jolteon").map { target ->
            val faint = faints.single { it.label == "Faint · $target · Dragon Pulse by Turtonator" }
            val link = requireNotNull(faint.finishingAttribution)
            val landing = model.sceneAt(link.landingAtNanos)
            val hit = landing.chargedMoveActions.single { it.finishingAssociation == link }
            assertEquals("PLAYER", hit.side)
            assertEquals(target, landing.opponent?.speciesName)
            assertEquals(1f, ReplayChargePose.at(hit, true).projectileProgress, 0f)
            assertTrue(model.sceneAt(faint.atNanos).opponent!!.isFainted)
            assertEquals(landing, model.sceneAt(link.landingAtNanos))
            assertEquals(1, model.eventMarkers.count { it.kind == ReplayEventMarkerKind.CHARGED_MOVE && it.finishingAttribution == link })
            link
        }
        assertTrue(links.first().landingAtNanos < links.last().moveSourceAtNanos)
        val final = links.last()
        assertNull(final.reception)
        assertTrue("Original Jolteon WAV and last-ball badge", final.faintArticleIds.containsAll(listOf(
            "9de59276-ae02-4f0a-ae20-31442a92af26", "9011a321-2b75-4b9d-bc37-d200cd5506d2")))
        assertTrue(faints.last().faintTiming is ReplayFaintTiming.Cry)
    }

    @Test fun finalIncinerateLandsBeforeTheCryMarker() = review(
        "recordedMoondropCry", "2026-11-18_15-17-09_78813508-a0cb-4dd1-9dd9-093c61210e39.odxmatch.zip",
        "0db534ad24460b1f77f9f2c8b5913ed229c922c3a6a903cb64f05fd74eba29d2"
    ) { model ->
        val go = requireNotNull(model.matchStartNanos)
        val faints = model.eventMarkers.filter { it.kind == ReplayEventMarkerKind.FAINT && it.side == "OPPONENT" }
        assertEquals(3, faints.size)
        val marker = faints.single { "Vaporeon" in it.label }
        assertEquals(go + 80_718_263_159L, marker.atNanos)
        assertTrue(marker.faintTiming is ReplayFaintTiming.Cry)
        val credit = requireNotNull(marker.finishingAttribution)
        assertEquals("Camerupt", credit.attackerSpeciesName)
        assertEquals("Incinerate", credit.moveName)
        val landing = model.sceneAt(credit.landingAtNanos)
        val hit = landing.fastMoveActions.single { it.finishingAssociation == credit }
        assertEquals(1f, hit.progress, .001f)
        assertFalse(landing.opponent!!.isFainted)
        assertTrue(model.sceneAt(marker.atNanos).opponent!!.isFainted)
        assertEquals(landing, model.sceneAt(credit.landingAtNanos))
    }

    @Test fun finalEarthPowerNamesBothTheLandingAndVaporeonFaint() = review(
        "recordedMoondropEarthPower", "2026-11-18_16-16-50_5e39cb12-af19-4841-8d2d-3a63539666e8.odxmatch.zip",
        "7477d19a5bf0af4ebeb34e4f0fae85f0e61d7aceaf424fc2c1920dfa395333c0"
    ) { model ->
        val faints = model.eventMarkers.filter { it.kind == ReplayEventMarkerKind.FAINT && it.side == "OPPONENT" }
        assertEquals(listOf("Sneasel", "Sealeo", "Vaporeon"), faints.map { it.label.split(" · ")[1] })
        val faint = faints.last()
        assertEquals("Faint · Vaporeon · Earth Power by Camerupt", faint.label)
        val link = requireNotNull(faint.finishingAttribution)
        val banners = setOf("10f4ac13-1aec-48cb-9465-f9615a611e8b", "14210950-b359-4eb2-a38b-da7218838e8f")
        assertTrue("The finishing move retains both banner reads", link.moveArticleIds.containsAll(banners))
        // Both live and Replay derive this faint from the original collapse frames and one-ball badge.
        val departure = setOf("a23dfea2-2ee5-4968-9a56-f8104194d091", "86ca216a-e961-4943-b85f-d710ae2dea77",
            "c82039a6-81c9-4880-bf7d-ba7f3f12f570")
        assertTrue("The faint retains the original departure and count evidence", link.faintArticleIds.containsAll(departure))
        val move = model.eventMarkers.single { it.kind == ReplayEventMarkerKind.CHARGED_MOVE && it.finishingAttribution == link }
        assertTrue(move.label.contains("Earth Power"))
        val landing = model.sceneAt(link.landingAtNanos)
        val action = landing.chargedMoveActions.single { it.finishingAssociation == link }
        assertEquals("Earth Power", action.moveName)
        assertEquals("Camerupt", landing.player?.speciesName)
        assertEquals("Vaporeon", landing.opponent?.speciesName)
        assertEquals(1f, ReplayChargePose.at(action, true).projectileProgress, 0f)
        assertTrue(model.sceneAt(faint.atNanos).opponent!!.isFainted)
        assertEquals(landing, model.sceneAt(link.landingAtNanos))
    }
}
