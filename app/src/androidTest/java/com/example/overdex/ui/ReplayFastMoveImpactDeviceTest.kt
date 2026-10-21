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
import com.example.overdex.battle.replay.MatchReplayModel
import com.example.overdex.model.PokemonType
import com.example.overdex.ui.screens.observatory.ReplayBattlefield
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Cache input is the unchanged October 13 17:34 archive Timeline, not synthetic HP. */
class ReplayFastMoveImpactDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun recordedVoltSwitchAndOpponentHpShareOneImpact() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val input = File(context.cacheDir, "replay-fast-impact.json").readText()
        val archive = MatchArchiveSerializer.deserialize(input)
        val original = MatchArchiveSerializer.serialize(archive)
        val model = MatchReplayModel(archive, fastMoveTypesByName = mapOf("VOLTSWITCH" to PokemonType.ELECTRIC))
        val go = requireNotNull(model.matchStartNanos)
        val impact = go + 2_000_000_000L
        val before = model.sceneAt(impact - 1)
        val landed = model.sceneAt(impact)
        assertEquals("Galvantula", landed.player!!.speciesName)
        assertTrue(landed.opponentHp!!.filledFraction!! < before.opponentHp!!.filledFraction!!)
        assertEquals(before.opponentHp.filledFraction, landed.opponentHp.damageTrailFraction)
        assertTrue(landed.opponentHp.damageTimingReconstructed)
        val hit = landed.fastMoveActions.single { it.side == "PLAYER" }
        assertEquals("Volt Switch", hit.moveName)
        assertEquals(2_000_000_000L, hit.durationNanos)
        assertEquals(impact, hit.impactAtNanos)
        assertEquals(1f, hit.progress, 0f)
        var cursor by mutableLongStateOf(impact - 200_000_000L)
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF163721))) {
                ReplayBattlefield(model.sceneAt(cursor), cursor, context)
            }
        }
        val projectile = compose.onNodeWithContentDescription("PLAYER Volt Switch projectile", useUnmergedTree = true)
        projectile.assertIsDisplayed().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "In flight"))
        compose.runOnIdle { cursor = impact }
        projectile.assertIsDisplayed().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Impact"))
        val percent = "${(landed.opponentHp.filledFraction!! * 100).toInt()} percent"
        compose.onNodeWithContentDescription("Opponent ${landed.opponent!!.speciesName} HP", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, percent))
        File(context.cacheDir, "replay-fast-impact.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.runOnIdle { cursor = impact - 1 }
        assertEquals(before, model.sceneAt(cursor))
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }
}
