package com.example.overdex.battle.replay

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import com.example.overdex.ui.screens.observatory.ReplayBattlefield
import org.junit.Rule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.archive.*
import com.example.overdex.model.Move
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class RecordedImpactFrameReplayDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rockTombLandsFromTheOriginalPixelEvidence() = runBlocking<Unit> {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(InstrumentationRegistry.getArguments().getString("recordedDragonite") == "true")
        val archiveFile = File(ctx.filesDir, "match_archives").listFiles()!!.single { it.name.endsWith("809f10ac-c26c-4566-8e66-3e674d3fc537.odxmatch.zip") }
        val originalHash = "1a896bc59d99d136c569f2127b568e22a6a7efb8f27e3aae238d19aa9d5761a3"
        assertEquals(originalHash, ReplayRetainedAudioRecovery.sha(archiveFile.readBytes()))
        val archive = ZipFile(archiveFile).use { zip -> MatchArchiveSerializer.deserialize(zip.getInputStream(zip.getEntry("timeline.json")).bufferedReader().readText()) }
        val repository = com.example.overdex.data.PokemonRepository(
            com.example.overdex.data.local.PokedexDatabase.getDatabase(ctx).pokemonDao(),
            com.example.overdex.data.LocalSpriteProvider(ctx.assets), com.example.overdex.data.GameMasterLoader(ctx))
        val pokemon = MatchReplayModel.referencedSpeciesNames(archive).mapNotNull { repository.getPokemonByName(it) }
        val moves = pokemon.associate { it.name to it.fastMoves + it.chargedMoves }
        val ids = pokemon.associate { it.name to it.id }
        val asset = { path: String -> ctx.assets.open(path).use { it.readBytes() } }
        val audio = ArchivedEndingAudioRecognizer.recognize(archive, moves.values.flatten(), ids.values.toSet(), archiveFile::inputStream, asset)
        val frameDir = File(ctx.filesDir,"artifacts/frames/sha256")
        val evidence = requireNotNull(ReplayImpactFinish.read(archive,frameDir,moves,audio,archiveFile::inputStream,asset)) { "No exact frame and named move" }
        val f = evidence.frame
        assertEquals("1859e8f3b5120d2f15669b5776d3572c1c846658cdc6441c6fc1446bc1720a58", f.frameSha256)
        assertEquals("Rock Tomb", f.move)
        assertNotNull("No departure burst", f.clearedAt)
        assertTrue("Ending sounds: ${evidence.sounds}", evidence.sounds.cries.any { it.speciesId == 149 })
        val identities = ArchivedSpeciesCropRecognizer.recognize(archive,archiveFile::inputStream,repository.getAllSpeciesNames(), { repository.getPokemonByName(it)?.id })
        fun replay(rows: List<ArchivedRealityArticle>, support: ReplayImpactEvidence = evidence) = MatchReplayModel(archive.copy(articles = rows),ids,identities,
            fastMovesBySpeciesName = moves.mapValues { it.value.filter(Move::isFast) },
            chargedMovesBySpeciesName = moves.mapValues { it.value.filterNot(Move::isFast) },
            audioRecovery = audio, impactEvidence = support)
        val model = replay(archive.articles)
        val action = model.sceneAt(f.landing).chargedMoveActions.single { it.moveName == f.move }
        val link = requireNotNull(action.finishingAssociation) { "Uncredited attack. Actors=${model.sceneAt(f.at).player?.speciesName}/${model.sceneAt(f.at).opponent?.speciesName}, sounds=${evidence.sounds}" }
        assertEquals("Camerupt",link.attackerSpeciesName)
        assertEquals("Dragonite",link.recipientSpeciesName)
        assertNull(link.reception)
        assertEquals(1f, ReplayChargePose.at(action,true).projectileProgress,0f)
        assertEquals("Faint · Dragonite · Rock Tomb by Camerupt", model.eventMarkers.single {
            it.kind == ReplayEventMarkerKind.FAINT && it.finishingAttribution == link }.label)
        assertTrue(model.sceneAt(f.clearedAt!!).opponent!!.isFainted)
        model.sceneAt(model.endNanos)
        assertEquals(action,model.sceneAt(f.landing).chargedMoveActions.single { it.moveName == f.move })
        var cursor by mutableLongStateOf(f.landing)
        compose.setContent { ReplayBattlefield(model.sceneAt(cursor), cursor, ctx) }
        compose.onNodeWithContentDescription("Charged move projectile", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Dragonite, fainted", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { cursor = f.clearedAt!! }
        compose.onNodeWithContentDescription("Dragonite, fainted", useUnmergedTree = true).assertExists()
        compose.runOnIdle { cursor = f.landing }
        compose.onNodeWithContentDescription("Charged move projectile", useUnmergedTree = true).assertIsDisplayed()
        File(ctx.cacheDir, "recorded-dragonite-impact.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
        val base = archive.articles.single { it.articleId == f.profileId }
        val oneClip = evidence.copy(sounds = evidence.sounds.copy(partialCryCandidates = emptyList()))
        assertEquals(f.move, replay(archive.articles, oneClip).finishingChargeAssociations.single { it.recipientSpeciesName == "Dragonite" }.moveName)
        val badge = archive.articles.last { (it.payload as? ArchivedOpponentBattleResourceCountMeasured)?.resource == "POKE_BALLS" }
        val empty = badge.copy(articleId = "terminal-count-control", monotonicTimeNanos = f.clearedAt, payload =
            ArchivedOpponentBattleResourceCountMeasured("POKE_BALLS", 0, 3))
        val noAudio = evidence.copy(sounds = ReplayAudioRecovery(unavailableArtifacts = listOf("missing")))
        val noBurst = evidence.copy(frame = f.copy(departureIds = emptyList(), clearedAt = null))
        for (support in listOf(noAudio, noBurst)) {
            assertEquals(f.move, replay(archive.articles + empty, support).finishingChargeAssociations.single { it.recipientSpeciesName == "Dragonite" }.moveName)
            assertTrue(replay(archive.articles, support).finishingAttributions.none { it.moveName == f.move })
        }
        fun veto(p: ArchivedTestimonyPayload) {
            val extra = base.copy(articleId = "control", monotonicTimeNanos = f.landing+1, payload = p)
            assertTrue("Unexpected credit for $p", replay(archive.articles+extra).finishingAttributions.none { it.moveName == f.move })
        }
        veto(ArchivedActivePokemonSpeciesWitnessed("OPPONENT","Swablu",333))
        veto(ArchivedFastMoveUseObserved("other-hit","PLAYER","OPPONENT",attackerSpeciesName="Camerupt",
            evidenceKinds=listOf("HP_DAMAGE_TICK"),basis="test"))
        veto((base.payload as ArchivedCompleteHpBarObserved).copy(status="LIVE_COLOR",liveColumns=1))
        assertEquals(originalHash, ReplayRetainedAudioRecovery.sha(archiveFile.readBytes()))
        assertEquals(f.frameSha256,ReplayRetainedAudioRecovery.sha(File(frameDir,"${f.frameSha256}.png").readBytes()))
    }
}
