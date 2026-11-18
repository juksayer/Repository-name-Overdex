package com.example.overdex.battle.replay

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.archive.MatchArchivePackageReader
import com.example.overdex.data.GameMasterLoader
import com.example.overdex.data.LocalSpriteProvider
import com.example.overdex.data.PokemonRepository
import com.example.overdex.data.local.PokedexDatabase
import com.example.overdex.ui.screens.observatory.ReplayBattlefield
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class RecordedPixelIncinerateDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bothProjectilesLandBeforeTheirRecipientsFaint() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("recordedIncinerate") == "true")
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        // A test copy can be used on the other phone without adding a match to its history.
        val testCopy = File(ctx.cacheDir, "field-replay-cbe00c2c.odxmatch.zip")
        val file = if (testCopy.isFile) testCopy else File(ctx.filesDir, "match_archives").listFiles().orEmpty()
            .single { it.name.endsWith("cbe00c2c-4a5b-4aa9-a284-f9fb62c3d41e.odxmatch.zip") }
        val digest = "7750b6a15daf43fd377e1dd1e4ec854a55a537d01703ca2757f91997ef25b10a"
        assertEquals(digest, ReplayRetainedAudioRecovery.sha(file.readBytes()))
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
        val links = model.finishingFastMoveAssociations.filter { it.basis == ReplaySliverExchange.BASIS }
        assertEquals(listOf("Sealeo", "Vaporeon"), links.map { it.recipientSpeciesName })
        var cursor by mutableLongStateOf(links.first().landingAtNanos)
        compose.setContent { ReplayBattlefield(model.sceneAt(cursor), cursor, ctx) }
        for (link in links) {
            val faint = model.eventMarkers.single { it.kind == ReplayEventMarkerKind.FAINT && it.finishingAttribution == link }
            assertTrue(faint.label.contains("Incinerate by Camerupt"))
            val projectile = compose.onNodeWithContentDescription("PLAYER Incinerate projectile", useUnmergedTree = true)
            val fallen = compose.onNodeWithContentDescription("${link.recipientSpeciesName}, fainted", useUnmergedTree = true)
            compose.runOnIdle { cursor = link.landingAtNanos }
            projectile.assertIsDisplayed()
            fallen.assertDoesNotExist()
            compose.runOnIdle { cursor = faint.atNanos }
            assertEquals((faint.faintTiming as ReplayFaintTiming.Window).by.atNanos, faint.atNanos)
            fallen.assertExists()
            compose.runOnIdle { cursor = link.landingAtNanos }
            projectile.assertIsDisplayed()
            fallen.assertDoesNotExist()
        }
        assertEquals(digest, ReplayRetainedAudioRecovery.sha(file.readBytes()))
    }
}
