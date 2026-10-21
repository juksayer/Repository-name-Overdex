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
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import coil.Coil
import coil.EventListener
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.overdex.battle.archive.*
import com.example.overdex.battle.replay.MatchReplayModel
import com.example.overdex.ui.screens.observatory.ReplayBattlefield
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** UI projection check; synthetic timer testimony does not prove live capture. */
class ReplayDecisionTimerDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun replacementDialAndCompleteKoSurviveScrubbingAndClearAtEntry() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = MatchReplayModel(MatchArchive(matchId = "timer-ui", articles = listOf(
            event("player", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323), 0),
            event("opponent", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Obstagoon", 862), 0),
            event("go", ArchivedMatchStarted, 0),
            event("entry", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Lucario", 448), 10),
            event("late-ko", ArchivedActivePokemonFaintedWitnessed("OPPONENT", "Obstagoon", 862, "BALLS"), 11),
        )))
        var cursor by mutableLongStateOf(9_750_000_000L)
        val previousLoader = Coil.imageLoader(context)
        val loaded = AtomicInteger()
        val loader = ImageLoader.Builder(context).eventListener(object : EventListener {
            override fun onSuccess(request: ImageRequest, result: SuccessResult) { loaded.incrementAndGet() }
        }).build()
        Coil.setImageLoader(loader)
        try {
            compose.setContent { ReplayBattlefield(model.sceneAt(cursor), cursor, context) }
            compose.waitUntil(15_000) { loaded.get() >= 2 }
            compose.onNodeWithContentDescription("Decision timer: waiting for replacement").assertIsDisplayed()
            compose.onNodeWithText("OPPONENT CHOOSING").assertIsDisplayed()
            compose.onNodeWithContentDescription("Obstagoon, fainted").assertIsDisplayed()
            for ((at, name) in listOf(9_750_000_000L to "falling", 9_950_000_000L to "down")) {
                compose.runOnIdle { cursor = at }
                compose.waitForIdle()
                File(context.cacheDir, "replay-replacement-$name.png").outputStream().use {
                    compose.onRoot().captureToImage().asAndroidBitmap()
                        .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            compose.runOnIdle { cursor = 10_000_000_000L }
            compose.waitUntil(15_000) { loaded.get() >= 3 }
            compose.onNodeWithContentDescription("Decision timer: waiting for replacement").assertDoesNotExist()
            compose.onNodeWithContentDescription("Obstagoon, fainted").assertDoesNotExist()
            compose.onNodeWithContentDescription("Lucario").assertIsDisplayed()
            compose.runOnIdle { cursor = 9_950_000_000L }
            compose.onNodeWithContentDescription("Obstagoon, fainted").assertIsDisplayed()
            compose.onNodeWithContentDescription("Decision timer: waiting for replacement").assertIsDisplayed()
        } finally { Coil.setImageLoader(previousLoader); loader.shutdown() }
    }

    @Test fun centralTimerCountsAndClearsWithoutImpossibleAttacks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val archive = MatchArchive(matchId = "timer-ui", articles = listOf(
            event("player", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Turtonator", 776), 0),
            event("opponent", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Sneasel", 215), 0),
            event("go", ArchivedMatchStarted, 0),
            event("incoming", ArchivedAttackIncoming, 10),
            event("four", ArchivedBattleDecisionTimerObserved(4, "4", "TEST"), 11),
            event("noise", ArchivedFastMoveUseObserved("noise", "OPPONENT", "PLAYER", "a", "Sneasel",
                listOf("HP_BORDER_PULSE"), "TEST"), 12),
            event("clear", ArchivedBattleDecisionTimerObserved(null, "", "TEST"), 14),
        ))
        val model = MatchReplayModel(archive)
        var cursor by mutableLongStateOf(11_000_000_000L)
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
            compose.onNodeWithContentDescription("Decision timer: 4").assertIsDisplayed()
            compose.runOnIdle { cursor = 12_000_000_000L }
            compose.onNodeWithContentDescription("Decision timer: 3").assertIsDisplayed()
            compose.onNodeWithText("SHIELD?").assertIsDisplayed()
            assertTrue(model.sceneAt(cursor).fastMoveActions.isEmpty())
            compose.waitForIdle()
            File(context.cacheDir, "replay-decision-timer.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap()
                    .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.runOnIdle { cursor = 14_000_000_000L }
            compose.onNodeWithContentDescription("Decision timer: 3").assertDoesNotExist()
            compose.runOnIdle { cursor = 11_000_000_000L }
            compose.onNodeWithContentDescription("Decision timer: 4").assertIsDisplayed()
        } finally {
            Coil.setImageLoader(previousLoader)
            loader.shutdown()
        }
    }

    private fun event(id: String, payload: ArchivedTestimonyPayload, seconds: Long) = ArchivedRealityArticle(
        id, "timer-ui", 0, 0, "TEST", payload, emptyList(), null, null, emptyList(), seconds * 1_000_000_000L)
}
