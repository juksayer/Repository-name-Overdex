package com.example.overdex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil.Coil
import coil.EventListener
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.overdex.battle.replay.ReplayCombatant
import com.example.overdex.battle.replay.ReplayStatChangeAction
import com.example.overdex.battle.replay.ReplayHpState
import com.example.overdex.battle.replay.MatchReplayModel
import com.example.overdex.battle.archive.ArchivedActivePokemonSpeciesWitnessed
import com.example.overdex.battle.archive.ArchivedChargedMoveEnergySpent
import com.example.overdex.battle.archive.ArchivedRealityArticle
import com.example.overdex.battle.archive.ArchivedStatChangeObserved
import com.example.overdex.battle.archive.ArchivedStatChangeSoundMeasured
import com.example.overdex.battle.archive.ArchivedTestimonyPayload
import com.example.overdex.battle.archive.MatchArchive
import com.example.overdex.ui.screens.observatory.ReplayCombatantSlot
import com.example.overdex.ui.screens.observatory.ReplayBattlefield
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class ReplayStatChangeDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun knownCausesRenderOnTheirRecipientsAndUnassignedCuesStayOffBothSprites() {
        fun article(id: String, payload: ArchivedTestimonyPayload, at: Long) = ArchivedRealityArticle(
            id, "stat-cause-render", at / 1_000_000L, at / 1_000_000L, "TEST", payload,
            emptyList(), null, null, emptyList(), at)
        fun sound(id: String, at: Long) = article(id,
            ArchivedStatChangeSoundMeasured("DEFENSE_DOWN", "sha", .99f, .8f, 917_125_000L), at)
        val model = MatchReplayModel(MatchArchive(matchId = "stat-cause-render", articles = listOf(
            article("p", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Golbat", 42), 0),
            article("o", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Pancham", 674), 0),
            sound("unassigned", 2_000_000_000L),
            article("crunch", ArchivedChargedMoveEnergySpent("OPPONENT", "Pancham", "Crunch", 45,
                "ANNOUNCEMENT"), 5_800_000_000L),
            sound("crunch-sound", 6_000_000_000L),
            article("crunch-recipient", ArchivedStatChangeObserved("PLAYER", "DEFENSE", -1, 1,
                "Crunch", "crunch-sound", "AUDIO_CUE_AND_NAMED_CHARGED_MOVE"), 6_000_000_000L),
            article("fang", ArchivedChargedMoveEnergySpent("PLAYER", "Golbat", "Poison Fang", 40,
                "ANNOUNCEMENT"), 9_800_000_000L),
            sound("fang-sound", 10_000_000_000L),
            article("fang-recipient", ArchivedStatChangeObserved("OPPONENT", "DEFENSE", -1, 1,
                "Poison Fang", "fang-sound", "AUDIO_CUE_AND_NAMED_CHARGED_MOVE"), 10_000_000_000L),
        )))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var cursor by mutableLongStateOf(6_200_000_000L)
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF163721))) {
                ReplayBattlefield(model.sceneAt(cursor), cursor, context)
            }
        }

        compose.onAllNodesWithText("DEF ↓").assertCountEquals(1)
        val crunch = compose.onNodeWithText("Crunch").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val field = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("Crunch must identify the player recipient", crunch.center.x < field.center.x)
        compose.onAllNodesWithText("Poison Fang").assertCountEquals(0)

        compose.runOnIdle { cursor = 10_200_000_000L }
        compose.onAllNodesWithText("DEF ↓").assertCountEquals(1)
        val fang = compose.onNodeWithText("Poison Fang").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Poison Fang must identify the opponent recipient", fang.center.x > field.center.x)
        compose.onAllNodesWithText("Crunch").assertCountEquals(0)

        // No announcement/attribution exists for this cue. The model preserves
        // it, but the actual Battlefield must attach no badge to either sprite.
        compose.runOnIdle { cursor = 2_200_000_000L }
        assertTrue(model.sceneAt(cursor).statChanges.single().side == null)
        assertTrue(model.sceneAt(cursor).statChanges.single().moveName == null)
        compose.onAllNodesWithText("DEF ↓").assertCountEquals(0)
        compose.onAllNodesWithText("Crunch").assertCountEquals(0)
        compose.onAllNodesWithText("Poison Fang").assertCountEquals(0)
    }

    @Test fun labelsRenderOnBothSidesWithLocalSprites() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val previous = Coil.imageLoader(target)
        val loaded = AtomicInteger()
        val loader = ImageLoader.Builder(target).eventListener(object : EventListener {
            override fun onSuccess(request: ImageRequest, result: SuccessResult) { loaded.incrementAndGet() }
        }).build()
        Coil.setImageLoader(loader)
        try {
            compose.setContent {
                val context = LocalContext.current
                Column(Modifier.fillMaxSize().background(Color(0xFF163721)).padding(14.dp)) {
                    listOf("ATTACK" to 1, "DEFENSE" to 1, "ATTACK" to -1, "DEFENSE" to -1).forEach { (stat, direction) ->
                        Text("${if (direction > 0) "Buff" else "Debuff"}: $stat", color = Color.White)
                        Row(Modifier.fillMaxWidth().weight(1f)) {
                            listOf(true, false).forEach { player ->
                                ReplayCombatantSlot(ReplayCombatant(if (player) "Turtonator" else "Sneasel", if (player) 776 else 215, 0),
                                    1_000_000_000, ReplayHpState(1f), context, player, null, null, null, 120.dp,
                                    Modifier.weight(1f), listOf(ReplayStatChangeAction(if (player) "PLAYER" else "OPPONENT", stat, direction, .25f)))
                            }
                        }
                    }
                }
            }
            compose.waitUntil(10_000) { loaded.get() >= 8 }
            compose.onAllNodesWithText("ATK ↑").assertCountEquals(2)
            compose.onAllNodesWithText("DEF ↓").assertCountEquals(2)
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            File(target.cacheDir, "replay-stat-cues.png").outputStream().use {
                image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { Coil.setImageLoader(previous); loader.shutdown() }
    }
}
