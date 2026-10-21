package com.example.overdex.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.observation.*
import com.example.overdex.data.observation.SpeciesNameRecognizer
import com.example.overdex.data.observation.SharedLatinTextRecognizer
import com.example.overdex.model.PokemonType
import com.example.overdex.ui.components.BattleOverlay
import com.example.overdex.ui.components.BattleHudBadgeStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Actual retained text and a real overlay window, captured by Android on the Moondrop. */
class BattleHudReadThroughDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    /** Word bounds, measured before OCR enlargement, can distinguish some text sizes. */
    @Test fun compareGoAndHudLetterHeightsInCapturedPixels() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun asset(path: String) = instrumentation.context.assets.open(path).use(BitmapFactory::decodeStream)!!
        val announcement = asset("battle/hud_oct13/go-plusle.png")
        val species = asset("battle/oct13/species.png")
        val window = asset("battle/hud_oct13/violet-window.png")
        val panel = Bitmap.createBitmap(window, 400, 280, 415, 470)
        // The retained GO source is 672 px wide; our native Android overlay was
        // captured at 1080 px. Compare in the same captured-screen coordinates.
        val captureScale = 672f / 1080f
        val hud = Bitmap.createScaledBitmap(panel,
            (panel.width * captureScale).toInt(), (panel.height * captureScale).toInt(), true)
        val probes = JSONArray()
        try {
            for ((name, source) in listOf("GO_ANNOUNCEMENT" to announcement, "GO_SPECIES" to species, "HUD" to hud)) {
                val result = SharedLatinTextRecognizer.read(source)
                val words = JSONArray()
                result.textBlocks.flatMap { it.lines }.flatMap { it.elements }.forEach { word ->
                    val rect = word.boundingBox ?: return@forEach
                    words.put(JSONObject().put("text", word.text).put("x", rect.left).put("y", rect.top)
                        .put("width", rect.width()).put("height", rect.height()))
                }
                assertTrue("Expected measurable text in $name", words.length() > 0)
                probes.put(JSONObject().put("source", name).put("width", source.width)
                    .put("height", source.height).put("words", words))
            }
            File(instrumentation.targetContext.filesDir, "hud-text-geometry.json").writeText(probes.toString(2))
            instrumentation.sendStatus(0, Bundle().apply { putString("hud_text_geometry", probes.toString()) })
        } finally {
            announcement.recycle(); species.recycle(); hud.recycle(); panel.recycle(); window.recycle()
        }
    }

    @Test fun recordedTextThroughBackgroundAndRealWindow() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun asset(path: String) = instrumentation.context.assets.open(path).use(BitmapFactory::decodeStream)!!
        val original = asset("battle/hud_oct13/go-plusle.png")
        val species = asset("battle/oct13/species.png")
        val readings = mutableListOf<String>()
        for ((label, source) in listOf("announcement" to original, "species" to species)) {
            for (opacity in listOf(0f, BattleHudBadgeStyle.background.alpha, .90f)) {
                val sample = source.copy(Bitmap.Config.ARGB_8888, true)
                Canvas(sample).drawColor(android.graphics.Color.argb((opacity * 255).toInt(), 255, 255, 255))
                val text = if (label == "species") SpeciesNameRecognizer.recognizeCandidates(sample).joinToString(" | ") { it.text }
                    else AnnouncementRecognizer.recognizeCandidates(sample).joinToString(" | ")
                readings += "$label opacity=$opacity: $text"
                if (opacity == 0f) assertTrue(text, text.contains(if (label == "species") "Sneasel" else "Plusle", true))
                sample.recycle()
            }
        }

        var overlay: ComposeView? = null
        lateinit var manager: WindowManager
        val width = 415
        // GO strip at screen Y=720. A real HUD occupies its right third.
        var stripY = 570
        lateinit var backdrop: View
        compose.runOnUiThread {
            val activity = compose.activity
            activity.window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            backdrop = object : View(activity) {
                override fun onDraw(canvas: Canvas) {
                    canvas.drawColor(android.graphics.Color.rgb(55, 112, 144))
                    val loc = IntArray(2); getLocationOnScreen(loc)
                    canvas.drawBitmap(original, null, android.graphics.Rect(80, stripY - loc[1], 1000,
                        stripY - loc[1] + 90), Paint(Paint.FILTER_BITMAP_FLAG))
                }
            }
            activity.setContentView(backdrop)
            DroidballOverlayPresentation.reset()
            DroidballOverlayPresentation.showBattleHud()
            DroidballOverlayPresentation.recordOpponentSpecies("Sneasel", 215,
                listOf("Feint Attack" to PokemonType.DARK, "Ice Shard" to PokemonType.ICE),
                listOf("Foul Play" to PokemonType.DARK, "Ice Punch" to PokemonType.ICE, "Avalanche" to PokemonType.ICE))
            manager = activity.getSystemService(WindowManager::class.java)
            overlay = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(activity); setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setContent { BattleOverlay(panelWidthPx = width) }
            }
            val flags = droidballOverlayWindowFlags(false)
            assertEquals(0, flags and WindowManager.LayoutParams.FLAG_SECURE)
            manager.addView(overlay, WindowManager.LayoutParams(width, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT).apply {
                    gravity = Gravity.TOP or Gravity.START; x = 400; y = 280
                })
        }
        try {
            compose.waitForIdle()
            instrumentation.waitForIdleSync()
            // Wait for the composited window and its arrival motion to settle.
            kotlinx.coroutines.delay(1_500)
            for (at in listOf(570, 630, 720)) {
                compose.runOnUiThread { stripY = at; backdrop.invalidate() }
                instrumentation.waitForIdleSync()
                kotlinx.coroutines.delay(150)
                val captured = instrumentation.uiAutomation.takeScreenshot()!!
                File(instrumentation.targetContext.filesDir, "hud-readthrough-$at.png").outputStream().use {
                    captured.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                // The background must remain visible instead of a black secure-window rectangle.
                assertTrue(android.graphics.Color.red(captured.getPixel(805, 450)) > 80)
                val strip = Bitmap.createBitmap(captured, 80, stripY, 920, 90)
                readings += "real_window y=$at: " + AnnouncementRecognizer.recognizeCandidates(strip).joinToString(" | ")
                // Probe only: erase our violet ink from a derived OCR copy. This
                // cannot reconstruct game strokes covered by opaque lettering.
                val isolated = strip.copy(Bitmap.Config.ARGB_8888, true)
                for (y in 0 until isolated.height) for (x in 320 until 735) {
                    val pixel = isolated.getPixel(x, y)
                    val r = android.graphics.Color.red(pixel)
                    val g = android.graphics.Color.green(pixel)
                    val b = android.graphics.Color.blue(pixel)
                    if (b - r >= 12 && r - g >= 8 && b - g >= 25) {
                        isolated.setPixel(x, y, android.graphics.Color.WHITE)
                    }
                }
                readings += "violet_removed y=$at: " + AnnouncementRecognizer.recognizeCandidates(isolated).joinToString(" | ")
                File(instrumentation.targetContext.filesDir, "hud-violet-removed-$at.png").outputStream().use {
                    isolated.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                isolated.recycle()
                strip.recycle(); captured.recycle()
            }
            val own = instrumentation.uiAutomation.takeScreenshot()!!
            val ownCrop = Bitmap.createBitmap(own, 400, 280, width, 400)
            val ownText = AnnouncementRecognizer.recognizeCandidates(ownCrop).joinToString(" ")
            assertNull(AnnouncementSpeciesTextResolver.resolveUsedMovePerformer(ownText, setOf("Sneasel", "Tauros", "Plusle")))
            ownCrop.recycle(); own.recycle()
            instrumentation.sendStatus(0, Bundle().apply { putString("hud_ocr_probe", readings.joinToString("\n")) })
        } finally {
            compose.runOnUiThread { overlay?.let(manager::removeViewImmediate); DroidballOverlayPresentation.reset() }
            original.recycle(); species.recycle()
        }
    }
}
