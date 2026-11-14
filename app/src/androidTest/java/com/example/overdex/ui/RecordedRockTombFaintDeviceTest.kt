package com.example.overdex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.archive.MatchArchiveSerializer
import com.example.overdex.battle.replay.*
import com.example.overdex.model.*
import com.example.overdex.ui.screens.observatory.ReplayBattlefield
import kotlinx.serialization.json.Json
import coil.Coil
import coil.EventListener
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** The preserved Moondrop archive, copied into cache explicitly by the review harness. */
class RecordedRockTombFaintDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun recordedRockTombReachesCameruptBeforeGourgeistReplacesIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val archive = MatchArchiveSerializer.deserialize(File(context.cacheDir, "rock-tomb-review.json").readText())
        val original = MatchArchiveSerializer.serialize(archive)
        val reference = Json { ignoreUnknownKeys = true }.decodeFromString<GameMaster>(
            context.assets.open("gamemaster.json").bufferedReader().use { it.readText() })
        val moves = reference.moves.associateBy { it.moveId }
        fun move(id: String, fast: Boolean) = moves[id]?.let {
            Move(it.name, PokemonType.valueOf(it.type.uppercase()), it.power,
                if (fast) it.energyGain else kotlin.math.abs(it.energy), fast, if (fast) it.turns else null)
        }
        val model = MatchReplayModel(archive,
            speciesIdsByName = reference.pokemon.associate { it.speciesName to it.dex },
            fastMovesBySpeciesName = reference.pokemon.associate { p -> p.speciesName to p.fastMoves.mapNotNull { move(it, true) } },
            chargedMovesBySpeciesName = reference.pokemon.associate { p -> p.speciesName to p.chargedMoves.mapNotNull { move(it, false) } })
        val link = model.finishingChargeAssociations.single { it.moveName == "Rock Tomb" }
        val impact = link.presentationImpactAtNanos
        var cursor by mutableLongStateOf(impact - 100_000_000L)
        val previousLoader = Coil.imageLoader(context)
        val loaded = AtomicInteger()
        val loader = ImageLoader.Builder(context).eventListener(object : EventListener {
            override fun onSuccess(request: ImageRequest, result: SuccessResult) { loaded.incrementAndGet() }
        }).build()
        Coil.setImageLoader(loader)
        try {
            compose.setContent {
                Box(Modifier.fillMaxSize().background(Color(0xFF163721))) {
                    ReplayBattlefield(model.sceneAt(cursor), cursor, context)
                }
            }
            compose.waitUntil(15_000) { loaded.get() >= 2 }
            val projectile = compose.onNodeWithContentDescription("Charged move projectile", useUnmergedTree = true)
            projectile.assertIsDisplayed()
            compose.onNodeWithContentDescription("Camerupt, fainted", useUnmergedTree = true).assertDoesNotExist()
            fun sample(at: Long, name: String) {
                compose.runOnIdle { cursor = at }
                // Flush the new cursor's draw before capturing; a semantics update
                // alone can precede the renderer by one frame on a physical phone.
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                File(context.cacheDir, "rock-tomb-$name.png").outputStream().use {
                    compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            val strikeStart = model.sceneAt(impact).chargedMoveActions.single().startedAtNanos
            val strikeDuration = impact - strikeStart
            sample(strikeStart + (strikeDuration * .65).toLong(), "apex")
            compose.onNodeWithContentDescription("Charging move", useUnmergedTree = true).assertIsDisplayed()
            val apex = ReplayChargePose.at(model.sceneAt(cursor).chargedMoveActions.single(), true)
            assertEquals(-48f, apex.risePixels, .001f)
            sample(strikeStart + (strikeDuration * .80).toLong(), "descent")
            projectile.assertIsDisplayed()
            val descent = ReplayChargePose.at(model.sceneAt(cursor).chargedMoveActions.single(), true)
            assertTrue(descent.risePixels > apex.risePixels)
            assertEquals(apex.risePixels, descent.projectileRisePixels, .001f)
            sample(impact, "impact")
            projectile.assertIsDisplayed()
            compose.onNodeWithContentDescription("Camerupt, fainted", useUnmergedTree = true).assertExists()
            compose.onNodeWithContentDescription("Opponent Camerupt HP", useUnmergedTree = true).assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "0 percent"))
            assertEquals(1f, model.sceneAt(cursor).chargedMoveActions.single().progress, 0f)
            sample(impact + ReplayFaintPose.DURATION_NANOS, "fall")
            assertEquals(90f, ReplayFaintPose.at(impact, cursor, false)!!.rotationDegrees, 0f)
            sample(link.replacementAtNanos, "replacement")
            compose.onNodeWithContentDescription("Gourgeist", useUnmergedTree = true).assertExists()
            compose.onNodeWithContentDescription("Camerupt, fainted", useUnmergedTree = true).assertDoesNotExist()
            sample(impact - 100_000_000L, "reverse")
            projectile.assertIsDisplayed()
            assertFalse(model.sceneAt(cursor).opponent!!.isFainted)
            assertEquals(original, MatchArchiveSerializer.serialize(archive))
        } finally {
            Coil.setImageLoader(previousLoader)
            loader.shutdown()
        }
    }
}
