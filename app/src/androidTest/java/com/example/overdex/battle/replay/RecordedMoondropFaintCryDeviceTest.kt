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
    @Test fun finalIncinerateLandsBeforeTheCryMarker() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("recordedMoondropCry") == "true")
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(ctx.filesDir, "match_archives/2026-11-18_15-17-09_78813508-a0cb-4dd1-9dd9-093c61210e39.odxmatch.zip")
        val hash = ReplayRetainedAudioRecovery.sha(file.readBytes())
        assertEquals("0db534ad24460b1f77f9f2c8b5913ed229c922c3a6a903cb64f05fd74eba29d2", hash)
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
        assertEquals(hash, ReplayRetainedAudioRecovery.sha(file.readBytes()))
    }
}
