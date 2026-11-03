package com.example.overdex.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil.Coil
import coil.EventListener
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.overdex.battle.replay.ReplayChargePose
import com.example.overdex.battle.replay.ReplayChargePhase
import com.example.overdex.battle.replay.ReplayChargedMoveAction
import com.example.overdex.battle.replay.ReplayCombatant
import com.example.overdex.battle.replay.ReplayFastMoveAction
import com.example.overdex.battle.replay.ReplayScene
import com.example.overdex.model.PokemonType
import com.example.overdex.ui.screens.observatory.ReplayBattlefield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.floor

/** Tests rendered local art, including its transparent padding, rather than only layout bounds. */
class ReplayChargeFocusDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun focusKeepsOpponentVisibleHeadFixedMovesBothSpritesAndRestoresOnReverse() = withSprites {
        var scene by mutableStateOf(idleScene())
        compose.setContent { stage(scene) }
        awaitSprites()
        val resting = frame("rest")
        val restingPlayer = spriteBounds("Raichu")
        val restingOpponent = spriteBounds("Combusken")
        val restingHead = warmOpponentBounds(resting)

        for (side in listOf("PLAYER", "OPPONENT")) {
            compose.runOnIdle {
                scene = idleScene().copy(chargedMoveActions = listOf(ReplayChargedMoveAction(
                    side = side, moveName = "Aqua Tail", startedAtNanos = 10_000_000_000L,
                    progress = .3f, phase = ReplayChargePhase.CHARGING,
                    elapsedNanos = 2_000_000_000L, type = PokemonType.WATER,
                )))
            }
            compose.waitForIdle()
            val focused = frame("${side.lowercase()}-focus")
            val focusedPlayer = spriteBounds("Raichu")
            val focusedOpponent = spriteBounds("Combusken")
            val focusedHead = warmOpponentBounds(focused)
            assertEquals("The player's focus shift uses physical pixels", 20f,
                focusedPlayer.center.x - restingPlayer.center.x, 1f)
            assertEquals("The opponent's focus shift uses physical pixels", -10f,
                focusedOpponent.center.x - restingOpponent.center.x, 1f)
            assertTrue("The opponent actually shrinks", focusedOpponent.width < restingOpponent.width * .9f)
            assertTrue("The player actually grows", focusedPlayer.width > restingPlayer.width * 1.1f)
            val existingAttackRise = if (side == "OPPONENT") ReplayChargePose.at(scene.chargedMoveActions.single(), false).risePixels else 0f
            assertEquals("Shrinking adds no visible head drift beyond the existing attack motion",
                restingHead.top + existingAttackRise, focusedHead.top, 2f)
        }
        compose.runOnIdle { scene = idleScene() }
        compose.waitForIdle()
        val reversed = frame("reverse")
        assertEquals(restingPlayer, spriteBounds("Raichu"))
        assertEquals(restingOpponent, spriteBounds("Combusken"))
        assertSamePixels("Reverse scrubbing restores the complete resting frame", resting, reversed)
    }

    @Test fun incomingFastAndChargedTypeIconsPassBehindThePlayersOpaqueBody() = withSprites {
        var scene by mutableStateOf(idleScene())
        compose.setContent { stage(scene) }
        awaitSprites()
        val resting = frame("occlusion-rest")
        val player = spriteBounds("Raichu")
        val stageBounds = compose.onNodeWithTag(STAGE).fetchSemanticsNode().boundsInRoot
        val localPlayer = player.translate(-stageBounds.left, -stageBounds.top)
        val playerMask = Bitmap.createBitmap(resting.width, resting.height, Bitmap.Config.ARGB_8888)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = context.assets.open("sprites/pokemon/back/26.png").use(BitmapFactory::decodeStream)
        Canvas(playerMask).drawBitmap(source, null, RectF(localPlayer.left, localPlayer.top,
            localPlayer.right, localPlayer.bottom), Paint(Paint.FILTER_BITMAP_FLAG))
        source.recycle()

        val incoming = listOf(
            "fast" to idleScene().copy(fastMoveActions = listOf(ReplayFastMoveAction(
                side = "OPPONENT", moveName = "Water Gun", type = PokemonType.WATER,
                startedAtNanos = 0L, progress = .90f,
            ))),
            "charged" to idleScene().copy(chargedMoveActions = listOf(ReplayChargedMoveAction(
                side = "OPPONENT", moveName = "Aqua Tail", type = PokemonType.WATER,
                startedAtNanos = 0L, progress = .93f, phase = ReplayChargePhase.STRIKING,
            ))),
        )
        for ((label, nextScene) in incoming) {
            compose.runOnIdle { scene = nextScene }
            compose.waitForIdle()
            val moving = frame("$label-behind-player")
            val description = if (label == "fast") "OPPONENT Water Gun projectile" else "Charged move projectile"
            val projectile = compose.onNodeWithContentDescription(description, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val localProjectile = projectile.translate(-stageBounds.left, -stageBounds.top)
            val overlap = localProjectile.intersect(localPlayer)
            assertTrue("The $label icon must reach the player's sprite area", overlap.width > 0 && overlap.height > 0)
            var protectedPixels = 0
            var changedProtectedPixels = 0
            var visibleOutsidePixels = 0
            for (y in floor(localProjectile.top).toInt().coerceAtLeast(2) until
                ceil(localProjectile.bottom).toInt().coerceAtMost(resting.height - 2)) {
                for (x in floor(localProjectile.left).toInt().coerceAtLeast(2) until
                    ceil(localProjectile.right).toInt().coerceAtMost(resting.width - 2)) {
                    // Build the mask from source alpha, not color differences from the
                    // field: bilinear edge pixels have color but remain translucent.
                    val opaqueBody = (-2..2).all { dy -> (-2..2).all { dx ->
                        android.graphics.Color.alpha(playerMask.getPixel(x + dx, y + dy)) == 255
                    } }
                    if (opaqueBody) {
                        protectedPixels++
                        if (resting.getPixel(x, y) != moving.getPixel(x, y)) changedProtectedPixels++
                    } else if (resting.getPixel(x, y) == FIELD && moving.getPixel(x, y) != FIELD) {
                        visibleOutsidePixels++
                    }
                }
            }
            assertTrue("The $label icon intersects actual opaque sprite pixels ($protectedPixels pixels; projectile=$localProjectile, player=$localPlayer)", protectedPixels > 15)
            assertEquals("The $label icon may not paint over the player's opaque body", 0, changedProtectedPixels)
            assertTrue("The $label icon still appears outside the player's silhouette", visibleOutsidePixels > 15)
        }
    }

    private lateinit var loaded: AtomicInteger

    private fun withSprites(block: () -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previous = Coil.imageLoader(context)
        loaded = AtomicInteger()
        val loader = ImageLoader.Builder(context).eventListener(object : EventListener {
            override fun onSuccess(request: ImageRequest, result: SuccessResult) { loaded.incrementAndGet() }
        }).build()
        Coil.setImageLoader(loader)
        try { block() } finally { Coil.setImageLoader(previous); loader.shutdown() }
    }

    @androidx.compose.runtime.Composable
    private fun stage(scene: ReplayScene) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.TopStart) {
            Box(Modifier.size(350.dp, 300.dp).testTag(STAGE)) {
                ReplayBattlefield(scene, 2_000_000_000L, context)
            }
        }
    }

    private fun awaitSprites() {
        compose.waitUntil(15_000) { loaded.get() >= 2 }
        compose.waitForIdle()
    }

    private fun spriteBounds(name: String) = compose.onNodeWithContentDescription(name)
        .fetchSemanticsNode().boundsInRoot

    private fun frame(name: String): Bitmap = compose.onNodeWithTag(STAGE).captureToImage().asAndroidBitmap().also { image ->
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, "replay-charge-focus-$name.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    /** Combusken's warm feather palette excludes the blue charge glyph and all terminal UI. */
    private fun warmOpponentBounds(image: Bitmap): Rect {
        var left = image.width
        var top = image.height
        var right = -1
        var bottom = -1
        for (y in image.height / 4 until image.height * 4 / 5) for (x in image.width / 2 until image.width - 4) {
            val color = image.getPixel(x, y)
            val red = android.graphics.Color.red(color)
            val green = android.graphics.Color.green(color)
            val blue = android.graphics.Color.blue(color)
            if (red > 100 && green > 30 && red > green * 1.1 && blue < green * .85) {
                left = minOf(left, x); right = maxOf(right, x)
                top = minOf(top, y); bottom = maxOf(bottom, y)
            }
        }
        assertTrue("The rendered opponent's feather palette must be present", right > left && bottom > top)
        return Rect(left.toFloat(), top.toFloat(), (right + 1).toFloat(), (bottom + 1).toFloat())
    }

    private fun assertSamePixels(message: String, expected: Bitmap, actual: Bitmap) {
        assertEquals(expected.width, actual.width)
        assertEquals(expected.height, actual.height)
        val before = IntArray(expected.width * expected.height)
        val after = IntArray(actual.width * actual.height)
        expected.getPixels(before, 0, expected.width, 0, 0, expected.width, expected.height)
        actual.getPixels(after, 0, actual.width, 0, 0, actual.width, actual.height)
        assertTrue(message, before.contentEquals(after))
    }

    private fun idleScene() = ReplayScene(
        player = ReplayCombatant("Raichu", 26, 0L),
        opponent = ReplayCombatant("Combusken", 256, 0L),
        observedSpeciesNames = listOf("Raichu", "Combusken"), candidateSpeciesId = null,
        candidateSimilarity = null, outcome = null, playerGeneratedEnergy = 0, opponentGeneratedEnergy = 0,
        playerSpentEnergy = 0, opponentSpentEnergy = 0, fastMoveActions = emptyList(), chargedMoveActions = emptyList(),
        countdownGlyph = null, qteMilestone = null, qteMilestoneSide = null, latestEvidenceLabel = "Visual test fixture",
    )

    private companion object {
        const val STAGE = "charge-focus-stage"
        val FIELD = android.graphics.Color.rgb(0x16, 0x37, 0x21)
    }
}
