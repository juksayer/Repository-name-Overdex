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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** Tests rendered local art, including its transparent padding, rather than only layout bounds. */
class ReplayChargeFocusDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun focusKeepsBothVisibleHeadsFixedMovesBothSpritesAndRestoresOnReverse() = withSprites {
        assertTopAnchoredFocus("Raichu", 26, "Combusken", 256, "raichu-combusken")
    }

    @Test fun chargeFocusAnchorsTurtonatorsRearArtAndTallOpponentArt() = withSprites {
        assertTopAnchoredFocus("Turtonator", 776, "Combusken", 256, "turtonator-combusken")
    }

    @Test fun chargeFocusAnchorsCameruptsBroadRearArtAndShortWideOpponentArt() = withSprites {
        assertTopAnchoredFocus("Camerupt", 323, "Stunfisk", 618, "camerupt-stunfisk")
    }

    private fun assertTopAnchoredFocus(playerName: String, playerId: Int,
                                       opponentName: String, opponentId: Int, label: String) {
        val idle = idleScene().copy(
            player = ReplayCombatant(playerName, playerId, 0L),
            opponent = ReplayCombatant(opponentName, opponentId, 0L),
        )
        var scene by mutableStateOf(idle)
        compose.setContent { stage(scene) }
        awaitSprites()
        val resting = frame("$label-rest")
        val restingPlayer = spriteBounds(playerName)
        val restingOpponent = spriteBounds(opponentName)
        val restingPlayerArt = warmArtBounds(resting, player = true, bounds = restingPlayer)
        val restingOpponentArt = warmArtBounds(resting, player = false, bounds = restingOpponent)

        for (side in listOf("PLAYER", "OPPONENT")) {
            compose.runOnIdle {
                scene = idle.copy(chargedMoveActions = listOf(ReplayChargedMoveAction(
                    side = side, moveName = "Aqua Tail", startedAtNanos = 10_000_000_000L,
                    progress = .3f, phase = ReplayChargePhase.CHARGING,
                    elapsedNanos = 2_000_000_000L, type = PokemonType.WATER,
                )))
            }
            compose.waitForIdle()
            val focused = frame("$label-${side.lowercase()}-focus")
            val focusedPlayer = spriteBounds(playerName)
            val focusedOpponent = spriteBounds(opponentName)
            val focusedPlayerArt = warmArtBounds(focused, player = true, bounds = focusedPlayer)
            val focusedOpponentArt = warmArtBounds(focused, player = false, bounds = focusedOpponent)
            assertEquals("The player's focus shift uses physical pixels", 20f,
                focusedPlayer.center.x - restingPlayer.center.x, 1f)
            assertEquals("The opponent's focus shift uses physical pixels", -10f,
                focusedOpponent.center.x - restingOpponent.center.x, 1f)
            assertTrue("The opponent actually shrinks", focusedOpponent.width < restingOpponent.width * .9f)
            assertTrue("The player actually grows", focusedPlayer.width > restingPlayer.width * 1.1f)
            val action = scene.chargedMoveActions.single()
            val playerRise = if (side == "PLAYER") ReplayChargePose.at(action, true).risePixels else 0f
            val opponentRise = if (side == "OPPONENT") ReplayChargePose.at(action, false).risePixels else 0f
            assertEquals("Growing $playerName keeps its visible head anchored beyond the existing attack motion",
                restingPlayerArt.top + playerRise, focusedPlayerArt.top, 2f)
            assertEquals("Shrinking $opponentName keeps its visible head anchored beyond the existing attack motion",
                restingOpponentArt.top + opponentRise, focusedOpponentArt.top, 2f)
            assertTrue("$playerName grows downward from its head", focusedPlayerArt.height > restingPlayerArt.height * 1.1f)
            assertTrue("$opponentName shrinks upward from its feet", focusedOpponentArt.height < restingOpponentArt.height * .9f)
            assertSamePixels("Paused charge framing is stable", focused, frame("$label-${side.lowercase()}-paused"))
        }
        compose.runOnIdle { scene = idle }
        compose.waitForIdle()
        val reversed = frame("$label-reverse")
        assertEquals(restingPlayer, spriteBounds(playerName))
        assertEquals(restingOpponent, spriteBounds(opponentName))
        assertSamePixels("Reverse scrubbing restores the complete resting frame", resting, reversed)
    }

    @Test fun playerArrivalsAndOpponentOriginsPaintInFrontOfTheOpponentBetweenCombatants() = withSprites {
        assertOpponentForegroundEndpoints("Combusken", 256, "tall-combusken")
    }

    @Test fun playerArrivalsAndOpponentOriginsPaintInFrontOfShortWideStunfisk() = withSprites {
        assertOpponentForegroundEndpoints("Stunfisk", 618, "wide-stunfisk")
    }

    private fun assertOpponentForegroundEndpoints(opponentName: String, opponentId: Int, reference: String) {
        val idle = idleScene().copy(opponent = ReplayCombatant(opponentName, opponentId, 0L))
        var scene by mutableStateOf(idle)
        compose.setContent { stage(scene) }
        awaitSprites()
        val resting = frame("$reference-opponent-front-rest")
        val cases = listOf(
            "player-fast-impact" to idle.copy(fastMoveActions = listOf(ReplayFastMoveAction(
                side = "PLAYER", moveName = "Water Gun", type = PokemonType.WATER,
                startedAtNanos = 0L, progress = 1f,
            ))),
            "player-charge-impact" to idle.copy(chargedMoveActions = listOf(ReplayChargedMoveAction(
                side = "PLAYER", moveName = "Aqua Tail", type = PokemonType.WATER,
                startedAtNanos = 0L, progress = 1f, phase = ReplayChargePhase.STRIKING,
            ))),
            "opponent-fast-origin" to idle.copy(fastMoveActions = listOf(ReplayFastMoveAction(
                side = "OPPONENT", moveName = "Water Gun", type = PokemonType.WATER,
                startedAtNanos = 0L, progress = 0f,
            ))),
            "opponent-charge-origin" to idle.copy(chargedMoveActions = listOf(ReplayChargedMoveAction(
                side = "OPPONENT", moveName = "Aqua Tail", type = PokemonType.WATER,
                startedAtNanos = 10_000_000_000L, progress = .3f,
                phase = ReplayChargePhase.CHARGING, elapsedNanos = 2_000_000_000L,
            ))),
        )
        var playerImpactX: Float? = null
        for ((label, nextScene) in cases) {
            compose.runOnIdle { scene = nextScene }
            compose.waitForIdle()
            val rendered = frame("$reference-$label")
            val stageBounds = compose.onNodeWithTag(STAGE).fetchSemanticsNode().boundsInRoot
            val player = spriteBounds("Raichu")
            val opponent = spriteBounds(opponentName)
            val description = when (label) {
                "player-fast-impact" -> "PLAYER Water Gun projectile"
                "opponent-fast-origin" -> "OPPONENT Water Gun projectile"
                "opponent-charge-origin" -> "Charging move"
                else -> "Charged move projectile"
            }
            val projectile = compose.onNodeWithContentDescription(description, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertTrue("$label stays on the opponent's side facing the player: $projectile",
                projectile.center.x > player.center.x && projectile.center.x < opponent.center.x)
            assertTrue("$label reaches the opponent's forward sprite area", projectile.right > opponent.left)
            if (label == "player-fast-impact") playerImpactX = projectile.center.x
            if (label == "player-charge-impact" || label == "opponent-fast-origin") {
                assertEquals("Fast and charged arrivals share the opponent's forward endpoint",
                    requireNotNull(playerImpactX), projectile.center.x, 1f)
            }
            val localOpponent = opponent.translate(-stageBounds.left, -stageBounds.top)
            val localProjectile = projectile.translate(-stageBounds.left, -stageBounds.top)
            val mask = sourceMask(rendered, "sprites/pokemon/$opponentId.png", localOpponent)
            var opaqueOverlap = 0
            var blueOverOpaque = 0
            for (y in floor(localProjectile.top).toInt().coerceAtLeast(2) until
                ceil(localProjectile.bottom).toInt().coerceAtMost(rendered.height - 2)) {
                for (x in floor(localProjectile.left).toInt().coerceAtLeast(2) until
                    ceil(localProjectile.right).toInt().coerceAtMost(rendered.width - 2)) {
                    val opaqueBody = (-2..2).all { dy -> (-2..2).all { dx ->
                        android.graphics.Color.alpha(mask.getPixel(x + dx, y + dy)) == 255
                    } }
                    if (opaqueBody) {
                        opaqueOverlap++
                        val color = rendered.getPixel(x, y)
                        // The selected art is warm. A blue glyph on source-opaque
                        // pixels proves foreground paint, rather than visible empty margins.
                        if (android.graphics.Color.blue(color) > android.graphics.Color.red(color) + 25 &&
                            android.graphics.Color.blue(color) > android.graphics.Color.green(color) + 25) {
                            blueOverOpaque++
                        }
                    }
                }
            }
            mask.recycle()
            assertTrue("$label overlaps actual opaque opponent artwork ($opaqueOverlap pixels)", opaqueOverlap > 15)
            assertTrue("$label visibly paints its blue type glyph over opponent artwork ($blueOverOpaque pixels)", blueOverOpaque > 15)
            assertSamePixels("Paused $label is stable", rendered, frame("$reference-$label-paused"))
        }
        compose.runOnIdle { scene = idle }
        compose.waitForIdle()
        assertSamePixels("Reversing the attacks restores the untouched idle field", resting,
            frame("$reference-opponent-front-reverse"))
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
        // Canvas and the GPU need not rasterize an alpha edge identically. Keep
        // the protected region a full source pixel inside the silhouette, so
        // upscaling a 96px asset cannot turn a texture-filter fringe into a
        // supposedly opaque body pixel. Only 8-bit rounding is allowed inside it.
        val opaqueRadius = ceil(localPlayer.width / source.width).toInt().coerceAtLeast(2)
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
            for (y in floor(localProjectile.top).toInt().coerceAtLeast(opaqueRadius) until
                ceil(localProjectile.bottom).toInt().coerceAtMost(resting.height - opaqueRadius)) {
                for (x in floor(localProjectile.left).toInt().coerceAtLeast(opaqueRadius) until
                    ceil(localProjectile.right).toInt().coerceAtMost(resting.width - opaqueRadius)) {
                    // Build the mask from source alpha, not color differences from the
                    // field: bilinear edge pixels have color but remain translucent.
                    val opaqueBody = (-opaqueRadius..opaqueRadius).all { dy -> (-opaqueRadius..opaqueRadius).all { dx ->
                        android.graphics.Color.alpha(playerMask.getPixel(x + dx, y + dy)) == 255
                    } }
                    if (opaqueBody) {
                        protectedPixels++
                        val before = resting.getPixel(x, y)
                        val after = moving.getPixel(x, y)
                        val difference = maxOf(
                            abs(android.graphics.Color.red(before) - android.graphics.Color.red(after)),
                            abs(android.graphics.Color.green(before) - android.graphics.Color.green(after)),
                            abs(android.graphics.Color.blue(before) - android.graphics.Color.blue(after)),
                        )
                        // Drawing a glyph behind a fully covered texture can change
                        // GPU compositing rounding by one 8-bit step. The saved failing
                        // pair had four such pixels, each in one channel; no glyph
                        // color was visible. Larger changes remain forbidden.
                        if (difference > 1) changedProtectedPixels++
                    } else if (resting.getPixel(x, y) == FIELD && moving.getPixel(x, y) != FIELD) {
                        visibleOutsidePixels++
                    }
                }
            }
            assertTrue("The $label icon intersects actual opaque sprite pixels ($protectedPixels pixels; projectile=$localProjectile, player=$localPlayer)", protectedPixels > 15)
            assertEquals("The $label icon may not alter the player's opaque body beyond 8-bit rounding", 0, changedProtectedPixels)
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

    /** These real sprites' warm/stone palettes exclude blue move glyphs and terminal UI. */
    private fun warmArtBounds(image: Bitmap, player: Boolean, bounds: Rect): Rect {
        var left = image.width
        var top = image.height
        var right = -1
        var bottom = -1
        val stageBounds = compose.onNodeWithTag(STAGE).fetchSemanticsNode().boundsInRoot
        val local = bounds.translate(-stageBounds.left, -stageBounds.top)
        // A wide rear sprite can cross the field's midpoint without overlapping
        // the opponent. Use each actual image node, not half of the whole field.
        val xRange = floor(local.left).toInt().coerceAtLeast(4) until
            ceil(local.right).toInt().coerceAtMost(image.width - 4)
        val yRange = floor(local.top).toInt().coerceAtLeast(image.height / 4) until
            ceil(local.bottom).toInt().coerceAtMost(image.height - 4)
        for (y in yRange) for (x in xRange) {
            val color = image.getPixel(x, y)
            val red = android.graphics.Color.red(color)
            val green = android.graphics.Color.green(color)
            val blue = android.graphics.Color.blue(color)
            val artColor = if (player) {
                // Rear Turtonator and Camerupt include stone-colored head pixels.
                red > 55 && green > 25 && red > green * .9 && blue < green * .9 &&
                    (red > 90 || green > 90)
            } else {
                // Warm feathers/skin include both the head and feet-facing body
                // edge, without isolated gray ankle pixels changing the bounds.
                red > 100 && green > 30 && red > green * 1.05 && blue < green * .85
            }
            if (artColor) {
                left = minOf(left, x); right = maxOf(right, x)
                top = minOf(top, y); bottom = maxOf(bottom, y)
            }
        }
        assertTrue("The rendered ${if (player) "player" else "opponent"} artwork must be present", right > left && bottom > top)
        return Rect(left.toFloat(), top.toFloat(), (right + 1).toFloat(), (bottom + 1).toFloat())
    }

    private fun sourceMask(frame: Bitmap, path: String, area: Rect): Bitmap {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = context.assets.open(path).use(BitmapFactory::decodeStream)
        val mask = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
        Canvas(mask).drawBitmap(source, null, RectF(area.left, area.top, area.right, area.bottom),
            Paint(Paint.FILTER_BITMAP_FLAG))
        source.recycle()
        return mask
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
