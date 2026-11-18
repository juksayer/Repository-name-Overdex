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
    private fun review(argument: String, fileName: String, expectedHash: String, verify: (MatchReplayModel) -> Unit) = runBlocking<Unit> {
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
        val audio = ArchivedEndingAudioRecognizer.recognize(archive, (fast.values.flatten() + charged.values.flatten()).distinct(),
            species.map { it.id }.toSet(), file::inputStream, asset)
        val impact = ReplayImpactFinish.read(archive, File(ctx.filesDir, "artifacts/frames/sha256"),
            species.associate { it.name to (it.fastMoves + it.chargedMoves) }, audio, file::inputStream, asset)
        val last = ReplayLastOpponentFastFinish.read(archive, catalog, file::inputStream, asset)
        val model = MatchReplayModel(archive, species.associate { it.name to it.id }, crops,
            fastMovesBySpeciesName = fast, chargedMovesBySpeciesName = charged,
            audioRecovery = audio, impactEvidence = impact, lastOpponentFastEvidence = last)
        verify(model)
        assertEquals(hash, ReplayRetainedAudioRecovery.sha(file.readBytes()))
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
