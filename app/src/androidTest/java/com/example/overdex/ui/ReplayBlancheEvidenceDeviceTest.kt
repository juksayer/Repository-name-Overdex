package com.example.overdex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil.Coil
import coil.EventListener
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.overdex.battle.archive.*
import com.example.overdex.battle.replay.MatchReplayModel
import com.example.overdex.ui.screens.observatory.ReplayBattlefield
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Host supplies the reduced, original-clock fixture on a disposable emulator only. */
class ReplayBlancheEvidenceDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rendersBothDelayedFaintsAndTheRecordedFinalGrade() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val archive = MatchArchiveSerializer.deserialize(File(context.cacheDir, "replay-blanche-review.json").readText())
        val original = MatchArchiveSerializer.serialize(archive)
        val model = MatchReplayModel(archive)
        fun speciesAt(name: String) = archive.articles.single {
            (it.payload as? ArchivedActivePokemonSpeciesWitnessed)?.speciesName == name
        }.monotonicTimeNanos!!
        val niceAt = archive.articles.single {
            it.sourceId == "QTE_PROGRESS_TEXT_WITNESS" && (it.payload as? ArchivedRawText)?.value == "NICE!"
        }.monotonicTimeNanos!!
        val resultAt = archive.articles.single { it.payload is ArchivedMatchEnded }.monotonicTimeNanos!!
        val frames = listOf("Sneasel KO" to speciesAt("Sealeo") - 50_000_000L,
            "Sealeo KO" to speciesAt("Vaporeon") - 50_000_000L,
            "Recorded NICE; finishing move unknown" to niceAt + 100_000_000L,
            "Result; no recorded final faint" to resultAt + 1_000_000_000L)
        val previous = Coil.imageLoader(context)
        val loaded = AtomicInteger()
        val loader = ImageLoader.Builder(context).eventListener(object : EventListener {
            override fun onSuccess(request: ImageRequest, result: SuccessResult) { loaded.incrementAndGet() }
        }).build()
        Coil.setImageLoader(loader)
        try {
            compose.setContent {
                Column(Modifier.fillMaxSize().background(Color(0xFF163721)).padding(10.dp)) {
                    frames.forEach { (title, at) ->
                        Text(title, color = Color.White)
                        Box(Modifier.fillMaxWidth().weight(1f)) { ReplayBattlefield(model.sceneAt(at), at, context) }
                    }
                }
            }
            compose.waitUntil(15_000) { loaded.get() >= 6 }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("Sneasel, fainted").assertExists()
            compose.onNodeWithContentDescription("Sealeo, fainted").assertExists()
            compose.onNodeWithContentDescription("Vaporeon, defeated").assertExists()
            assertTrue(compose.onAllNodesWithContentDescription("Charging move").fetchSemanticsNodes().isNotEmpty())
            assertTrue(compose.onAllNodesWithContentDescription("Charged move projectile").fetchSemanticsNodes().isEmpty())
            assertEquals("NICE", model.sceneAt(niceAt).qteMilestone)
            assertFalse(model.sceneAt(resultAt).opponent!!.isFainted)
            assertNull(model.sceneAt(resultAt).opponentHp)
            assertEquals(original, MatchArchiveSerializer.serialize(archive))
            File(context.cacheDir, "replay-blanche-evidence.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            Coil.setImageLoader(previous)
            loader.shutdown()
        }
    }
}
