package com.example.overdex.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil.Coil
import coil.EventListener
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.overdex.battle.archive.MatchArchiveSerializer
import com.example.overdex.battle.replay.MatchReplayModel
import com.example.overdex.battle.replay.ReplayChargePose
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import com.example.overdex.ui.screens.observatory.ReplayBattlefield
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Render the actual final Dragon Pulse; a projectile alone is insufficient proof of actor motion. */
class RecordedFinalDragonPulseDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun recordedIncinerateSpriteBobsAfterThePriorChargeFinishes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val archive = instrumentation.context.assets.open("replay/final-dragon-pulse-20261111.json")
            .bufferedReader().use { MatchArchiveSerializer.deserialize(it.readText()) }
        val original = MatchArchiveSerializer.serialize(archive)
        val impactAt = archive.articles.single { it.articleId == HANDOFF_FAST_ID }.monotonicTimeNanos!!
        val fastStart = impactAt - 2_500_000_000L
        val releaseAt = archive.articles.single { it.articleId == HANDOFF_CHARGE_ID }.monotonicTimeNanos!!
        val flightAt = releaseAt + ((fastStart - releaseAt) * .9).toLong()
        val model = MatchReplayModel(archive, fastMovesBySpeciesName = mapOf("Turtonator" to
            listOf(Move("Incinerate", PokemonType.FIRE, 20, 20, true, 5))))
        var cursor by mutableLongStateOf(flightAt)
        val previousLoader = Coil.imageLoader(context)
        val loaded = AtomicInteger()
        val loader = ImageLoader.Builder(context).eventListener(object : EventListener {
            override fun onSuccess(request: ImageRequest, result: SuccessResult) { loaded.incrementAndGet() }
        }).build()
        Coil.setImageLoader(loader)
        try {
            compose.setContent {
                Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.TopStart) {
                    Box(Modifier.size(350.dp, 300.dp).testTag(STAGE)) {
                        ReplayBattlefield(model.sceneAt(cursor), cursor, context)
                    }
                }
            }
            compose.waitUntil(15_000) { loaded.get() >= 2 }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("Charged move projectile", useUnmergedTree = true).assertIsDisplayed()
            frame("handoff-prior-charge-flight")

            compose.runOnIdle { cursor = fastStart }
            compose.waitForIdle()
            assertTrue(model.sceneAt(cursor).chargedMoveActions.none { it.side == "PLAYER" })
            val resting = playerBounds()
            frame("handoff-fast-start")
            compose.runOnIdle { cursor = fastStart + 150_000_000L }
            compose.waitForIdle()
            val fast = model.sceneAt(cursor).fastMoveActions.single { HANDOFF_FAST_ID in it.sourceArticleIds }
            assertEquals(impactAt, fast.impactAtNanos)
            assertEquals(fastStart, fast.startedAtNanos)
            assertTrue("The recorded Incinerate must move its sprite after the old charge lands",
                playerBounds().top < resting.top - 10f)
            val raised = frame("handoff-fast-bob")
            assertTrue("Pause preserves the newly visible bob", raised.sameAs(frame("handoff-fast-paused")))

            compose.runOnIdle { cursor = fastStart + 300_000_000L }
            compose.waitForIdle()
            assertEquals("The same supported attack completes its short sprite bob",
                resting.top, playerBounds().top, 1.5f)
            frame("handoff-fast-return")
            compose.runOnIdle { cursor = impactAt + 20_000_000L }
            compose.waitForIdle()
            assertEquals(.14814815f, model.sceneAt(cursor).opponentHp!!.filledFraction!!, .0001f)
            frame("handoff-fast-impact")
            assertEquals(original, MatchArchiveSerializer.serialize(archive))
        } finally {
            Coil.setImageLoader(previousLoader)
            loader.shutdown()
        }
    }

    @Test fun recordedFinalDragonPulseRaisesAndSlamsItsActualPlayerSprite() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val archive = instrumentation.context.assets.open("replay/final-dragon-pulse-20261111.json")
            .bufferedReader().use { MatchArchiveSerializer.deserialize(it.readText()) }
        val original = MatchArchiveSerializer.serialize(archive)
        val chargeAt = archive.articles.single { it.articleId == FINAL_CHARGE_ID }.monotonicTimeNanos!!
        val model = MatchReplayModel(archive,
            chargedMoveTypesByName = mapOf("DRAGONPULSE" to PokemonType.DRAGON))
        val idleAt = chargeAt - 3_000_000_000L
        var cursor by mutableLongStateOf(idleAt)
        val previousLoader = Coil.imageLoader(context)
        val loaded = AtomicInteger()
        val loader = ImageLoader.Builder(context).eventListener(object : EventListener {
            override fun onSuccess(request: ImageRequest, result: SuccessResult) { loaded.incrementAndGet() }
        }).build()
        Coil.setImageLoader(loader)
        try {
            compose.setContent {
                Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.TopStart) {
                    Box(Modifier.size(350.dp, 300.dp).testTag(STAGE)) {
                        ReplayBattlefield(model.sceneAt(cursor), cursor, context)
                    }
                }
            }
            compose.waitUntil(15_000) { loaded.get() >= 2 }
            compose.waitForIdle()
            val restingBounds = playerBounds()
            val resting = frame("idle")

            val apexAt = chargeAt + 1_200_000_000L
            compose.runOnIdle { cursor = apexAt }
            compose.waitForIdle()
            val apex = model.sceneAt(apexAt).chargedMoveActions.single { it.side == "PLAYER" }
            val raisedBounds = playerBounds()
            assertTrue("Turtonator must actually rise, not only emit a projectile",
                raisedBounds.top < restingBounds.top - 40f)
            assertEquals("Sprite translation uses the same source-time charge pose",
                ReplayChargePose.at(apex, player = true).risePixels,
                raisedBounds.top - restingBounds.top, 1.5f)
            assertEquals("A strike does not retain the QTE scale", restingBounds.width, raisedBounds.width, 1f)
            val raised = frame("apex")
            assertTrue("The rendered frame changes while the actor rises", !resting.sameAs(raised))
            compose.onNodeWithContentDescription("Charging move", useUnmergedTree = true).assertIsDisplayed()
            assertTrue("Pausing preserves the drawn pose", raised.sameAs(frame("apex-paused")))

            compose.runOnIdle { cursor = chargeAt + 1_650_000_000L }
            compose.waitForIdle()
            assertTrue("The actual sprite slams down while the projectile travels",
                playerBounds().top > raisedBounds.top + 35f)
            compose.onNodeWithContentDescription("Charged move projectile", useUnmergedTree = true).assertIsDisplayed()
            frame("slam")

            compose.runOnIdle { cursor = chargeAt + 1_800_000_000L }
            compose.waitForIdle()
            assertEquals("Turtonator returns to its resting height on impact", restingBounds.top, playerBounds().top, 1.5f)
            compose.onNodeWithContentDescription("Charged move projectile", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithContentDescription("Opponent Leavanny HP", useUnmergedTree = true).assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "25 percent"))
            frame("impact")

            compose.runOnIdle { cursor = idleAt }
            compose.waitForIdle()
            assertEquals(restingBounds, playerBounds())
            assertTrue("Reverse seeking restores the resting frame", resting.sameAs(frame("reverse-idle")))
            assertEquals(original, MatchArchiveSerializer.serialize(archive))
        } finally {
            Coil.setImageLoader(previousLoader)
            loader.shutdown()
        }
    }

    private fun playerBounds(): Rect = compose.onNodeWithContentDescription("Turtonator", useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot

    private fun frame(name: String): Bitmap = compose.onNodeWithTag(STAGE).captureToImage().asAndroidBitmap().also { bitmap ->
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, "recorded-final-dragon-pulse-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    companion object {
        private const val STAGE = "recorded-final-charge-stage"
        private const val FINAL_CHARGE_ID = "3429122f-72e6-416f-9a48-74400467ad1f"
        private const val HANDOFF_FAST_ID = "c8e5b483-f241-475f-be19-ce7819378edc"
        private const val HANDOFF_CHARGE_ID = "9156bb17-514a-4213-b8bb-1792dcef2d1c"
    }
}
