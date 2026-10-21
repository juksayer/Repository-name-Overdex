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
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil.Coil
import coil.EventListener
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.overdex.battle.replay.ReplayCombatant
import com.example.overdex.battle.replay.ReplayFaintPose
import com.example.overdex.ui.screens.observatory.ReplayCombatantSlot
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** A recorded-time pose sheet rendered with the production sprite slot and local assets. */
class ReplayKnockoutDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rendersBothSidesBeforeDuringAndAfterTheKnockout() {
        val at = 1_000_000_000L
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val previousLoader = Coil.imageLoader(target)
        val loaded = AtomicInteger()
        val loader = ImageLoader.Builder(target).eventListener(object : EventListener {
            override fun onSuccess(request: ImageRequest, result: SuccessResult) { loaded.incrementAndGet() }
        }).build()
        Coil.setImageLoader(loader)
        try {
            compose.setContent {
                val context = LocalContext.current
                Column(Modifier.fillMaxSize().background(Color(0xFF163721)).padding(14.dp)) {
                    listOf(-1L, ReplayFaintPose.FALL_START_NANOS + 200_000_000L,
                        ReplayFaintPose.FALL_END_NANOS, ReplayFaintPose.DURATION_NANOS).forEach { age ->
                        Text(if (age < 0) "Before KO" else "KO ${age / 1_000_000} ms", color = Color.White)
                        Row(Modifier.fillMaxWidth().weight(1f)) {
                            listOf(true, false).forEach { player ->
                                ReplayCombatantSlot(
                                    combatant = ReplayCombatant(
                                        if (player) "Turtonator" else "Sneasel", if (player) 776 else 215, 0,
                                        isFainted = age >= 0, faintedAtNanos = at.takeIf { age >= 0 },
                                    ),
                                    cursorNanos = at + age,
                                    hp = null, context = context, useBackSprite = player,
                                    fastAction = null, chargedAction = null, qteMilestone = null,
                                    spriteSize = 120.dp, modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
            // Advancing Compose's clock does not complete Coil's real I/O.
            compose.waitUntil(10_000) { loaded.get() >= 8 }
            compose.waitForIdle()
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            File(context.cacheDir, "replay-ko-poses.png").outputStream().use {
                image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            Coil.setImageLoader(previousLoader)
            loader.shutdown()
        }
    }
}
