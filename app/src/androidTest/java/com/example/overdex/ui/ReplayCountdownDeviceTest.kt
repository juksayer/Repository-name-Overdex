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
import com.example.overdex.battle.archive.MatchArchiveSerializer
import com.example.overdex.battle.archive.ArchivedCountdownGlyphWitnessed
import com.example.overdex.battle.replay.MatchReplayModel
import com.example.overdex.ui.screens.observatory.ReplayBattlefield
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** The real archive is copied to app cache; the original archive is never modified. */
class ReplayCountdownDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun recordedPartialCountdownRendersEveryEvenBeat() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "replay-countdown.json")
        val original = source.readText()
        val archive = MatchArchiveSerializer.deserialize(original)
        val model = MatchReplayModel(archive)
        val countdown = requireNotNull(model.countdown)
        // The October 13 18:21 archive only witnessed 3 and 2.
        assertEquals(setOf("3", "2"), archive.articles.mapNotNull {
            (it.payload as? ArchivedCountdownGlyphWitnessed)?.glyph
        }.toSet())
        var cursor by mutableLongStateOf(countdown.startNanos)
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF163721))) {
                ReplayBattlefield(model.sceneAt(cursor), cursor, context)
            }
        }
        for ((index, glyph) in listOf("3", "2", "1", "GO").withIndex()) {
            for (withinBeat in listOf(0L, 999_999_999L)) {
                compose.runOnIdle { cursor = countdown.startNanos + index * 1_000_000_000L + withinBeat }
                compose.onNodeWithText(glyph, useUnmergedTree = true).assertIsDisplayed()
            }
            File(context.cacheDir, "replay-countdown-$glyph.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap()
                    .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.runOnIdle { cursor = countdown.endNanos }
        compose.onNodeWithText("GO", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(original, source.readText())
        assertEquals(archive.articles.associateBy { it.articleId }, model.timedArticles.associateBy { it.articleId })
    }
}
