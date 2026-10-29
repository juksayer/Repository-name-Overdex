package com.example.overdex.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.forecast.OpponentEnergyEstimate
import com.example.overdex.battle.observation.*
import com.example.overdex.model.PokemonType
import com.example.overdex.ui.components.BattleOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** The actual overlay root: deliberate long-press movement, no reserved gap or capture flashes. */
class HudAnnouncementReadDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun compactFixedHudWaitsForBadgeWithoutDuplicatingResources(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var overlay: FrameLayout
        lateinit var manager: WindowManager
        lateinit var params: WindowManager.LayoutParams
        var completedDrags = 0
        compose.runOnUiThread {
            val activity = compose.activity
            activity.setContentView(object : View(activity) {
                override fun onDraw(canvas: Canvas) { canvas.drawColor(android.graphics.Color.rgb(55, 112, 144)) }
            })
            DroidballOverlayPresentation.reset()
            DroidballOverlayPresentation.showBattleHud(userRequested = true)
            DroidballOverlayPresentation.recordOpponentSpecies("Sneasel", 215,
                listOf("Feint Attack" to PokemonType.DARK, "Ice Shard" to PokemonType.ICE),
                listOf("Foul Play" to PokemonType.DARK, "Ice Punch" to PokemonType.ICE,
                    "Avalanche" to PokemonType.ICE, "Triple Axel" to PokemonType.ICE))
            manager = activity.getSystemService(WindowManager::class.java)
            params = WindowManager.LayoutParams(-2, -2,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, droidballOverlayWindowFlags(false), PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.START; x = 620; y = 360
            }
            overlay = FrameLayout(activity).apply {
                setViewTreeLifecycleOwner(activity); setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                addView(ComposeView(activity).apply { setContent { BattleOverlay(panelWidthPx = 415,
                    onDrag = { x, y ->
                        params.x += x.toInt(); params.y += y.toInt()
                        manager.updateViewLayout(overlay, params)
                    },
                    onDragFinished = { completedDrags++ },
                ) } },
                    FrameLayout.LayoutParams(-2, -2))
            }
            manager.addView(overlay, params)
        }
        suspend fun shot(name: String): Bitmap {
            compose.waitForIdle(); delay(150)
            return instrumentation.uiAutomation.takeScreenshot()!!.also { bitmap ->
                File(instrumentation.targetContext.cacheDir, "hud-compact-$name.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
        try {
            val location = IntArray(2)
            val before = shot("waiting")
            compose.runOnUiThread { overlay.getLocationOnScreen(location) }
            val background = before.getPixel(location[0] + 35, location[1] + 45)
            compose.runOnUiThread {
                DroidballOverlayPresentation.recordBattleBadgePresent()
                DroidballOverlayPresentation.recordOpponentResources(
                    OpponentBattleResourceCountMeasured(OpponentBattleResource.POKE_BALLS, 2, 3), 100)
                DroidballOverlayPresentation.recordOpponentResources(
                    OpponentBattleResourceCountMeasured(OpponentBattleResource.SHIELDS, 1, 2), 100)
                DroidballOverlayPresentation.beginEnergyForecast("hud-test")
                DroidballOverlayPresentation.setOpponentEnergyEstimate("hud-test", OpponentEnergyEstimate(
                    "Sneasel", "Feint Attack", 40, 5, 3, 10, false, false, 0, false))
            }
            val visible = shot("visible")
            assertNotEquals("Wait for badge before drawing", background,
                visible.getPixel(location[0] + 35, location[1] + 45))
            for (label in listOf("Sneasel", "POSSIBLE FAST", "POSSIBLE CHARGED", "POSITION", "QTE BOX", "DRAG HUD / SHELLS")) {
                compose.onNodeWithText(label).assertDoesNotExist()
            }
            compose.onNodeWithContentDescription("Move HUD").assertDoesNotExist()
            compose.onNodeWithText("≈40 E", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithContentDescription("Opponent: 2 of 3 Pokémon remaining, 1 of 2 shields remaining",
                useUnmergedTree = true).assertDoesNotExist()
            val labels = listOf("Feint Attack", "Ice Shard", "Foul Play", "Ice Punch", "Avalanche", "Triple Axel")
            val bounds = labels.map { compose.onNodeWithText(it, substring = true, useUnmergedTree = true)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
            bounds.zipWithNext().forEach { (a, b) ->
                assertTrue("No reserved announcement gap", b.top - a.bottom < 3f)
                assertEquals("Move labels share right edge", a.right, b.right, 1f)
            }
            assertEquals("Empty space beside the moves must reveal the game without a panel tint",
                background, visible.getPixel(location[0] + 4, location[1] + bounds.first().center.y.toInt()))
            val sprite = compose.onNodeWithContentDescription("Sneasel sprite", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertEquals(36f * compose.activity.resources.displayMetrics.density, sprite.height, 1f)
            val originalPosition = location.copyOf()
            val initialHeight = overlay.height
            compose.onNodeWithText("Triple Axel", useUnmergedTree = true).performTouchInput {
                swipe(center, center.copy(x = center.x - 40, y = center.y + 50))
            }
            delay(1_500)
            val stable = shot("stable")
            compose.runOnUiThread { overlay.getLocationOnScreen(location) }
            assertArrayEquals("Battle HUD cannot be dragged accidentally", originalPosition, location)
            assertEquals(initialHeight, overlay.height)
            assertEquals("No timed hide/reveal", visible.getPixel(location[0] + 35, location[1] + 45),
                stable.getPixel(location[0] + 35, location[1] + 45))

            // Inject real screen coordinates while WindowManager moves the
            // window: a static Compose-only drag can hide coordinate feedback.
            fun longDrag(localX: Float, localY: Float) {
                compose.runOnUiThread { overlay.getLocationOnScreen(location) }
                val start = location.copyOf()
                val downAt = SystemClock.uptimeMillis()
                fun send(action: Int, dx: Float = 0f, dy: Float = 0f) {
                    val event = MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), action,
                        start[0] + localX + dx, start[1] + localY + dy, 0)
                    try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
                }
                send(MotionEvent.ACTION_DOWN)
                // Compose tests own the coroutine frame clock; real event time
                // alone does not advance the long-press timeout in this rule.
                compose.mainClock.advanceTimeBy(ViewConfiguration.getLongPressTimeout() + 100L)
                SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 100L)
                repeat(6) { step ->
                    send(MotionEvent.ACTION_MOVE, -10f * (step + 1), 12f * (step + 1))
                    SystemClock.sleep(35)
                }
                send(MotionEvent.ACTION_UP, -60f, 72f)
                compose.waitForIdle()
                compose.runOnUiThread { overlay.getLocationOnScreen(location) }
                assertEquals("Whole HUD follows the finger horizontally", start[0] - 60f, location[0].toFloat(), 3f)
                assertEquals("Whole HUD follows the finger vertically", start[1] + 72f, location[1].toFloat(), 3f)
            }
            assertEquals("Quick swipe does not complete a drag", 0, completedDrags)
            longDrag(bounds.last().center.x, bounds.last().center.y)
            longDrag(sprite.center.x, sprite.center.y)
            assertEquals("Releasing either a label or sprite saves once", 2, completedDrags)
            before.recycle(); visible.recycle(); stable.recycle()
        } finally {
            compose.runOnUiThread { manager.removeViewImmediate(overlay); DroidballOverlayPresentation.reset() }
        }
    }
}
