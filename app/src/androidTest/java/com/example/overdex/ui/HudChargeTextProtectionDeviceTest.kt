package com.example.overdex.ui

import android.graphics.Canvas
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.overdex.battle.observation.DroidballOverlayPresentation
import com.example.overdex.battle.observation.droidballOverlayWindowFlags
import com.example.overdex.model.PokemonType
import com.example.overdex.ui.components.BattleOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HudChargeTextProtectionDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun chargePhaseYieldsMoveLabelsAndNativeWindowHeightWhilePreservingRosterAndSeam() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish") || Build.FINGERPRINT.contains("generic") ||
            Build.MODEL.contains("Emulator")) { "Use a disposable emulator for HUD fixtures" }
        val p = DroidballOverlayPresentation
        lateinit var window: FrameLayout
        lateinit var manager: WindowManager
        compose.runOnUiThread {
            val activity = compose.activity
            activity.setContentView(object : View(activity) {
                override fun onDraw(canvas: Canvas) { canvas.drawColor(android.graphics.Color.rgb(55, 112, 144)) }
            })
            p.reset()
            p.recordOpponentSpecies("Sneasel", 215, listOf("Snarl" to PokemonType.DARK),
                listOf("Thunder Punch" to PokemonType.ELECTRIC, "Ice Punch" to PokemonType.ICE))
            p.recordBattleBadgePresent()
            p.showBattleHud(userRequested = true)
            manager = activity.getSystemService(WindowManager::class.java)
            window = FrameLayout(activity).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                addView(ComposeView(activity).apply { setContent { BattleOverlay(panelWidthPx = 415) } },
                    FrameLayout.LayoutParams(-2, -2))
            }
            manager.addView(window, WindowManager.LayoutParams(-2, -2,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, droidballOverlayWindowFlags(false),
                PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = 620; y = 360 })
        }
        try {
            compose.onNodeWithText("Snarl", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithText("T. Punch", useUnmergedTree = true).assertIsDisplayed()
            val rosterBefore = compose.onNodeWithContentDescription("Sneasel sprite", useUnmergedTree = true)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            var expandedHeight = 0
            val origin = IntArray(2)
            compose.runOnUiThread { expandedHeight = window.height; window.getLocationOnScreen(origin) }
            compose.runOnUiThread { p.setChargeTextProtection(true) }
            compose.onNodeWithText("Snarl", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithText("T. Punch", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithText("Ice Punch", useUnmergedTree = true).assertDoesNotExist()
            val rosterProtected = compose.onNodeWithContentDescription("Sneasel sprite", useUnmergedTree = true)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertEquals(rosterBefore, rosterProtected)
            compose.waitForIdle()
            compose.runOnUiThread {
                val protectedOrigin = IntArray(2).also(window::getLocationOnScreen)
                assertEquals(origin.toList(), protectedOrigin.toList())
                assertTrue("Native capture footprint must shrink with the removed lines", window.height < expandedHeight)
            }
            compose.runOnUiThread { p.setChargeTextProtection(false) }
            compose.onNodeWithText("Snarl", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithText("T. Punch", useUnmergedTree = true).assertIsDisplayed()
            compose.waitForIdle()
            compose.runOnUiThread { assertEquals(expandedHeight, window.height) }
        } finally { compose.runOnUiThread { manager.removeViewImmediate(window); p.reset() } }
    }
}
