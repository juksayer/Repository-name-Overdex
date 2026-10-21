package com.example.overdex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
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
import com.example.overdex.model.PokemonType
import com.example.overdex.ui.screens.observatory.ReplayBattlefield
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Original 17:34 match evidence rendered through the production battlefield. */
class ReplayCombatantReturnDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun recordedNicknameReturnReplacesCameruptAndWinsWithGalvantula() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val archive = MatchArchiveSerializer.deserialize(File(context.cacheDir, "replay-combatant-return.json").readText())
        val model = MatchReplayModel(archive, fastMoveTypesByName = mapOf("VOLTSWITCH" to PokemonType.ELECTRIC))
        val frames = listOf("Camerupt KO" to 123_500_000_000L,
            "Galvantula returned" to 141_100_000_000L, "Result" to 169_000_000_000L)
        val previous = Coil.imageLoader(context)
        val loaded = AtomicInteger()
        val loader = ImageLoader.Builder(context).eventListener(object : EventListener {
            override fun onSuccess(request: ImageRequest, result: SuccessResult) { loaded.incrementAndGet() }
        }).build()
        Coil.setImageLoader(loader)
        try {
            compose.setContent {
                Column(Modifier.fillMaxSize().background(Color(0xFF163721)).padding(12.dp)) {
                    frames.forEach { (title, at) ->
                        Text(title, color = Color.White)
                        Box(Modifier.fillMaxWidth().weight(1f)) { ReplayBattlefield(model.sceneAt(at), at, context) }
                    }
                }
            }
            compose.waitUntil(15_000) { loaded.get() >= 6 }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("Camerupt, fainted").assertExists()
            assertEquals(2, compose.onAllNodesWithContentDescription("Galvantula").fetchSemanticsNodes().size)
            compose.onNodeWithContentDescription("Jolteon, defeated").assertExists()
            val returnScene = model.sceneAt(frames[1].second)
            assertFalse(returnScene.player!!.isFainted)
            assertEquals("Volt Switch", returnScene.fastMoveActions.single { it.side == "PLAYER" }.moveName)
            File(context.cacheDir, "replay-combatant-return.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap()
                    .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            Coil.setImageLoader(previous)
            loader.shutdown()
        }
    }
}
