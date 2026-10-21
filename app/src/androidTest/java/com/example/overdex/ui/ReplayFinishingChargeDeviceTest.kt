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
import coil.Coil
import coil.EventListener
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.overdex.battle.archive.*
import com.example.overdex.battle.replay.*
import com.example.overdex.ui.screens.observatory.ReplayBattlefield
import com.example.overdex.model.PokemonType
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Deliberate boundary fixture, not a claim that the missing October 13 attack was observed. */
class ReplayFinishingChargeDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun impactStaggerHoldAndFallRemainVisibleAfterTheResult() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val archive = MatchArchive(matchId = "finishing-charge-test", articles = listOf(
            article("player", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323), 0),
            article("opponent", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Vaporeon", 134), 0),
            article("start", ArchivedMatchStarted, 1_000_000_000L),
            article("ready", ArchivedGetReadyWitnessed, 3_000_000_000L),
            article("charge", ArchivedChargedMoveEnergySpent("PLAYER", "Camerupt", "Earth Power", 55, "TEST"), 10_000_000_000L),
            article("faint", ArchivedActivePokemonFaintedWitnessed("OPPONENT", "Vaporeon", 134, "TEST"), 10_100_000_000L),
            article("result", ArchivedMatchEnded("WIN"), 10_200_000_000L),
        ))
        val model = MatchReplayModel(archive, chargedMoveTypesByName = mapOf("EARTHPOWER" to PokemonType.GROUND))
        val impact = 11_800_000_000L
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
            val hp = compose.onNodeWithContentDescription("Opponent Vaporeon HP", useUnmergedTree = true)
            val projectile = compose.onNodeWithContentDescription("Charged move projectile", useUnmergedTree = true)
            projectile.assertIsDisplayed().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                "PLAYER: Earth Power; GROUND; QTE level 0"))
            hp.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Full HP assumed on first entry"))
            fun sample(at: Long, name: String) {
                compose.runOnIdle { cursor = at }
                compose.waitForIdle()
                File(context.cacheDir, "replay-final-$name.png").outputStream().use {
                    compose.onRoot().captureToImage().asAndroidBitmap()
                        .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            sample(impact, "impact")
            projectile.assertIsDisplayed()
            hp.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "0 percent"))
            sample(impact + 900_000_000L, "hold")
            assertEquals(0f, ReplayFaintPose.at(impact, cursor, false)!!.rotationDegrees, 0f)
            sample(impact + ReplayFaintPose.DURATION_NANOS, "fall")
            assertEquals(90f, ReplayFaintPose.at(impact, cursor, false)!!.rotationDegrees, 0f)
            compose.runOnIdle { cursor = impact - 100_000_000L }
            hp.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Full HP assumed on first entry"))
        } finally {
            Coil.setImageLoader(previousLoader)
            loader.shutdown()
        }
    }

    private fun article(id: String, payload: ArchivedTestimonyPayload, at: Long) = ArchivedRealityArticle(
        id, "finishing-charge-test", 0, 0, "TEST", payload, emptyList(), null, null, emptyList(), at)
}
