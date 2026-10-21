package com.example.overdex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil.Coil
import coil.EventListener
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.overdex.battle.archive.MatchArchiveSerializer
import com.example.overdex.battle.replay.MatchReplayModel
import com.example.overdex.ui.screens.observatory.ReplayBattlefield
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Feed the preserved Oct 13 fixture into the production replay scene and sprite renderer. */
class ReplayChargeEndingDeviceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun rendersRecordedChargeAndDefeatedCombatant() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val archive = MatchArchiveSerializer.deserialize(File(context.cacheDir, "replay-review.json").readText())
        val model = MatchReplayModel(archive)
        val frames = listOf("Lunge charging" to 76_000_000_000L,
            "Lunge impact" to 80_650_000_000L, "Result" to 134_000_000_000L)
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
                        val scene = model.sceneAt(at)
                        Text(title, color = Color.White)
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            ReplayBattlefield(scene, at, context)
                        }
                    }
                }
            }
            compose.waitUntil(15_000) { loaded.get() >= 6 }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("Flareon, defeated").assertExists()
            assertTrue(compose.onAllNodesWithContentDescription("Charging move").fetchSemanticsNodes().isNotEmpty())
            assertNull(model.sceneAt(134_000_000_000L).opponentHp)
            assertNotNull(model.sceneAt(134_000_000_000L).playerHp)
            File(context.cacheDir, "replay-charge-ending.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            Coil.setImageLoader(previous)
            loader.shutdown()
        }
    }
}
