package com.example.overdex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil.Coil
import coil.EventListener
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.overdex.battle.archive.MatchArchiveSerializer
import com.example.overdex.battle.replay.MatchReplayModel
import com.example.overdex.data.GameMasterLoader
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import com.example.overdex.ui.screens.observatory.ReplayBattlefield
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/** Uses the October 16 SFX recording and the installed reference catalogue at CRT dimensions. */
class ReplayChargeLayoutDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun recordedLoadoutsAndEnergyFollowSwitchAndRewind() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val text = File(context.cacheDir, "replay-layout-20261016.json").readText()
        val archive = MatchArchiveSerializer.deserialize(text)
        val before = MatchArchiveSerializer.serialize(archive)
        val catalogue = GameMasterLoader(context)
        val references = MatchReplayModel.referencedSpeciesNames(archive).associateWith { species ->
            catalogue.allPokemon().firstOrNull {
                it.speciesName.equals(species, true) || it.speciesName.substringBefore(" (").equals(species, true)
            }?.chargedMoves.orEmpty().mapNotNull(catalogue::getMove).map {
                Move(it.name, PokemonType.valueOf(it.type.uppercase()), it.power, abs(it.energy), false)
            }
        }
        val model = MatchReplayModel(archive, chargedMovesBySpeciesName = references)
        val origin = model.recordingStartNanos
        var cursor by mutableLongStateOf(origin + 35_000_000_000L)
        val previousLoader = Coil.imageLoader(context)
        val loaded = AtomicInteger()
        val loader = ImageLoader.Builder(context).eventListener(object : EventListener {
            override fun onSuccess(request: ImageRequest, result: SuccessResult) { loaded.incrementAndGet() }
        }).build()
        Coil.setImageLoader(loader)
        try {
            compose.setContent {
                Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(350.dp, 480.dp).testTag("replay-stage")) {
                        ReplayBattlefield(model.sceneAt(cursor), cursor, context)
                    }
                }
            }
            compose.waitUntil(15_000) { loaded.get() >= 2 }
            fun check(seconds: Long, names: List<String>, types: List<PokemonType>, file: String) {
                compose.runOnIdle { cursor = origin + seconds * 1_000_000_000L }
                compose.waitForIdle()
                val scene = model.sceneAt(cursor)
                assertEquals(names, scene.playerChargedMoves.map { it.moveName })
                assertEquals(types, scene.playerChargedMoves.map { it.type })
                names.forEachIndexed { i, name ->
                    compose.onNodeWithContentDescription("Player charged move ${i + 1}: $name", substring = true)
                        .assertIsDisplayed()
                }
                listOf("Player" to scene.playerEnergy, "Opponent" to scene.opponentEnergy).forEach { (side, energy) ->
                    compose.onNodeWithContentDescription("$side energy", useUnmergedTree = true)
                        .assertIsDisplayed().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                            "Approximately ${energy!!.recordedEnergy} of 100, from recorded moves"))
                }
                val stage = compose.onNodeWithTag("replay-stage").fetchSemanticsNode().boundsInRoot
                val buttons = names.mapIndexed { index, name ->
                    compose.onNodeWithContentDescription("Player charged move ${index + 1}: $name", substring = true)
                        .fetchSemanticsNode().boundsInRoot
                }
                assertTrue(buttons.all { it.left >= stage.left && it.right <= stage.right && it.bottom <= stage.bottom })
                assertTrue(buttons[0].right <= buttons[1].left)
                File(context.cacheDir, "replay-charge-$file.png").outputStream().use {
                    compose.onNodeWithTag("replay-stage").captureToImage().asAndroidBitmap()
                        .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            check(35, listOf("Weather Ball (Ice)", "Energy Ball"), listOf(PokemonType.ICE, PokemonType.GRASS), "abomasnow")
            compose.runOnIdle { cursor = origin + 105_000_000_000L }
            compose.waitUntil(15_000) { loaded.get() >= 4 }
            check(105, listOf("Shadow Ball", "Seed Bomb"), listOf(PokemonType.GHOST, PokemonType.GRASS), "gourgeist")
            check(35, listOf("Weather Ball (Ice)", "Energy Ball"), listOf(PokemonType.ICE, PokemonType.GRASS), "rewind")
            assertEquals(before, MatchArchiveSerializer.serialize(archive))
        } finally {
            Coil.setImageLoader(previousLoader)
            loader.shutdown()
        }
    }
}
