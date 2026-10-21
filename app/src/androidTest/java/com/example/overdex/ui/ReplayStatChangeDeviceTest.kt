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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
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
import com.example.overdex.ui.screens.observatory.ReplayCombatantSlot
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class ReplayStatChangeDeviceTest {
    @get:Rule val compose = createComposeRule()
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
