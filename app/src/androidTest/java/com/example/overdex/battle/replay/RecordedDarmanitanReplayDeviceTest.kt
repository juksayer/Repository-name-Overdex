package com.example.overdex.battle.replay

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.archive.MatchArchivePackageReader
import com.example.overdex.data.GameMasterLoader
import com.example.overdex.data.LocalSpriteProvider
import com.example.overdex.data.PokemonRepository
import com.example.overdex.data.local.PokedexDatabase
import com.example.overdex.ui.screens.observatory.ReplayBattlefield
import com.example.overdex.ui.screens.observatory.ReplayEventMarkerControls
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class RecordedDarmanitanReplayDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun counterLandsOnDarmanitanAndSurvivesSeeking() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("recordedDarmanitan") == "true")
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(ctx.filesDir, "match_archives").listFiles().orEmpty()
            .single { it.name.endsWith("182052fd-1e9c-481c-b23b-f82ba88e59d7.odxmatch.zip") }
        val hash = ReplayRetainedAudioRecovery.sha(file.readBytes())
        assertEquals("4738ef05155d47ccb259b2d33236792aab709f36638afeb34867c6e759c2af96", hash)
        val archive = file.inputStream().use(MatchArchivePackageReader::read)
        val catalog = GameMasterLoader(ctx)
        val repo = PokemonRepository(PokedexDatabase.getDatabase(ctx).pokemonDao(), LocalSpriteProvider(ctx.assets), catalog)
        val ids = MatchReplayModel.referencedSpeciesNames(archive).mapNotNull { repo.getPokemonByName(it) }
        val asset = { path: String -> ctx.assets.open(path).use { it.readBytes() } }
        val crops = ArchivedSpeciesCropRecognizer.recognize(archive, file::inputStream, repo.getAllSpeciesNames(),
            { repo.getPokemonByName(it)?.id })
        val moves = ids.flatMap { it.fastMoves + it.chargedMoves }
        val audio = ArchivedEndingAudioRecognizer.recognize(archive, moves, ids.map { it.id }.toSet(), file::inputStream, asset)
        val finish = requireNotNull(ReplayLastOpponentFastFinish.read(archive, catalog, file::inputStream, asset))
        val model = MatchReplayModel(archive, ids.associate { it.name to it.id }, crops,
            fastMovesBySpeciesName = ids.associate { it.name to it.fastMoves },
            chargedMovesBySpeciesName = ids.associate { it.name to it.chargedMoves },
            audioRecovery = audio, lastOpponentFastEvidence = finish)
        val link = model.finishingFastMoveAssociations.single { it.recipientSpeciesName == "Darmanitan" }
        val faint = model.eventMarkers.single { it.kind == ReplayEventMarkerKind.FAINT && it.finishingAttribution == link }
        assertEquals("Faint · Darmanitan · Counter by Zapdos · inferred", faint.label)
        var cursor by mutableLongStateOf(link.landingAtNanos)
        compose.setContent {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f)) { ReplayBattlefield(model.sceneAt(cursor), cursor, ctx) }
                ReplayEventMarkerControls(model.eventMarkers, cursor, faint.id, model.startNanos,
                    onSeek = { cursor = it.atNanos }, matchStartNanos = model.matchStartNanos)
            }
        }
        compose.onNodeWithText("KO window 1:33–1:34 from GO").assertIsDisplayed()
        compose.onNodeWithContentDescription("PLAYER Counter projectile", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Darmanitan, fainted", useUnmergedTree = true).assertExists()
        compose.runOnIdle { cursor = link.moveSourceAtNanos }
        compose.onNodeWithText("KO window 1:33–1:34 from GO").assertDoesNotExist()
        compose.onNodeWithContentDescription("Darmanitan, fainted", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { cursor = link.landingAtNanos }
        compose.onNodeWithText("KO window 1:33–1:34 from GO").assertIsDisplayed()
        compose.onNodeWithContentDescription("PLAYER Counter projectile", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(hash, ReplayRetainedAudioRecovery.sha(file.readBytes()))
    }
}
