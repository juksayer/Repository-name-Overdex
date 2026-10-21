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
import com.example.overdex.battle.archive.MatchArchiveSerializer
import com.example.overdex.battle.replay.MatchReplayModel
import com.example.overdex.model.PokemonType
import com.example.overdex.ui.screens.observatory.ReplayBattlefield
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Render the recorded October 13 opening; no synthetic attack or HP values. */
class ReplayOpeningHpDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun separateIncineratesKeepTheirOwnDamageChunks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val archive = MatchArchiveSerializer.deserialize(File(context.cacheDir,
            "incinerate-opening-hp-20261013.json").readText())
        val model = MatchReplayModel(archive, fastMoveTypesByName = mapOf("INCINERATE" to PokemonType.FIRE))
        val first = 3_092_031_462L
        var cursor by mutableLongStateOf(first)
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
            fun sample(at: Long, hpPercent: Int, name: String) {
                compose.runOnIdle { cursor = at }
                compose.waitForIdle()
                compose.onNodeWithContentDescription("Opponent Sneasel HP", useUnmergedTree = true)
                    .assertIsDisplayed().assert(SemanticsMatcher.expectValue(
                        SemanticsProperties.StateDescription, "$hpPercent percent"))
                compose.onNodeWithContentDescription("PLAYER Incinerate projectile", useUnmergedTree = true)
                    .assertIsDisplayed().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Impact"))
                assertEquals(PokemonType.FIRE, model.sceneAt(at).fastMoveActions.single { it.side == "PLAYER" }.type)
                File(context.cacheDir, "replay-incinerate-$name.png").outputStream().use {
                    compose.onRoot().captureToImage().asAndroidBitmap()
                        .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            sample(first, 73, "first")
            sample(first + 2_500_000_000L, 47, "second")
            sample(first + 5_000_000_000L, 21, "third")
            sample(first, 73, "rewind")
        } finally {
            Coil.setImageLoader(previousLoader)
            loader.shutdown()
        }
    }
}
