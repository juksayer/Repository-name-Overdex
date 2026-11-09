package com.example.overdex.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.overdex.data.ODXFiAppearance
import com.example.overdex.data.ODXFiAppearancePreferences
import com.example.overdex.data.odxFiColorHex
import com.example.overdex.data.parseODXFiHexColor
import com.example.overdex.diagnostics.DiagnosticLogger
import com.example.overdex.ui.screens.ODXFiAppearanceScreen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Exercises the real handheld: LCD controls and hardware remain outside the protected CRT. */
class ODXFiAppearanceDeviceTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferenceFiles = mutableListOf<String>()

    @After fun removeOnlyTestPreferences() {
        preferenceFiles.forEach(context::deleteSharedPreferences)
    }

    @Test fun lcdCanChooseThreeIndependentColorsAndRgbValuesThatSurviveReopening() {
        val fixture = showEditor()
        val originalBounds = hardwareBounds()
        java.io.File(context.cacheDir, "odxfi-colors-default.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }

        choosePreset("case", "red")
        // Real drag input proves the picker supports colors beyond its preset palette.
        compose.onNodeWithTag("odx-fi-colors-slider-red").performScrollTo()
            .performTouchInput {
                swipe(center, Offset(width.toFloat() - 1f, center.y), durationMillis = 250)
            }
        compose.waitForIdle()
        val customCase = requireNotNull(parseODXFiHexColor(requireNotNull(
            compose.onNodeWithTag("odx-fi-colors-preview-case").fetchSemanticsNode()
                .config.getOrNull(SemanticsProperties.StateDescription))))
        assertTrue("The physical slider drag must increase the red channel", (customCase ushr 16 and 0xFF) > 0xB6)
        assertEquals("Dragging red must preserve green and blue", 0x3B42, customCase and 0x00FFFF)
        assertTrue("The slider must produce a color outside the preset palette", customCase !in setOf(
            ODXFiAppearance.DEFAULT_CASE_COLOR_ARGB, 0xFF292C33.toInt(), 0xFFB7BDC7.toInt(),
            0xFFB63B42.toInt(), 0xFF3466A8.toInt(), 0xFF734A91.toInt(), 0xFF287D80.toInt()))
        choosePreset("buttons", "blue")
        choosePreset("bezels", "silver")

        val selected = ODXFiAppearance(
            caseColorArgb = customCase,
            buttonColorArgb = 0xFF3466A8.toInt(),
            bezelColorArgb = 0xFFB7BDC7.toInt(),
        )
        assertAllDraftColors(selected)
        assertEquals("Cosmetic editing must preserve the existing hardware geometry", originalBounds, hardwareBounds())
        assertHardwareColors(selected)
        java.io.File(context.cacheDir, "odxfi-colors-custom.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        assertEquals("Previewing must not write durable preferences", ODXFiAppearance(), fixture.read())

        touch("odx-fi-colors-save")
        compose.waitUntil(5_000) { fixture.backCount.get() == 1 }
        compose.onNodeWithText("Colors closed").assertIsDisplayed()
        assertEquals(1, fixture.saveCount.get())
        assertEquals(selected, fixture.read())

        compose.runOnIdle { fixture.reopen() }
        assertAllDraftColors(selected)
        assertHardwareColors(selected)
        assertEquals(originalBounds, hardwareBounds())
    }

    @Test fun hardwareBCancelsAVisibleDraftWithoutSavingIt() {
        val original = ODXFiAppearance(0xFF365171.toInt(), 0xFFE2D5BA.toInt(), 0xFF5D397C.toInt())
        val fixture = showEditor(original)
        choosePreset("buttons", "teal")
        assertDraft("buttons", 0xFF287D80.toInt())
        assertColorVisible("odxfi-button-A", 0xFF287D80.toInt())

        press("B")
        compose.onNodeWithText("Colors closed").assertIsDisplayed()
        assertEquals(1, fixture.backCount.get())
        assertEquals("Cancel must never invoke the persistence callback", 0, fixture.saveCount.get())
        assertEquals(original, fixture.read())

        compose.runOnIdle { fixture.reopen() }
        assertAllDraftColors(original)
        assertHardwareColors(original)
    }

    @Test fun failedSaveKeepsTheDraftAndAllowsARealHardwareSaveRetry() {
        val fixture = showEditor()
        fixture.allowSave.set(false)
        choosePreset("bezels", "purple")
        touch("odx-fi-colors-save")

        compose.onNodeWithTag("odx-fi-colors-save-error").assertExists()
        compose.onNodeWithTag("odx-fi-colors-save").assertIsEnabled().assertIsDisplayed()
        assertDraft("bezels", 0xFF734A91.toInt())
        assertEquals("A rejected save must leave the editor open", 0, fixture.backCount.get())
        assertEquals(1, fixture.saveCount.get())
        assertEquals(ODXFiAppearance(), fixture.read())

        fixture.allowSave.set(true)
        // SAVE is the actual START-position hardware button, independent of the LCD action.
        press("SAVE")
        compose.waitUntil(5_000) { fixture.backCount.get() == 1 }
        compose.onNodeWithText("Colors closed").assertIsDisplayed()
        assertEquals(2, fixture.saveCount.get())
        assertEquals(ODXFiAppearance(bezelColorArgb = 0xFF734A91.toInt()), fixture.read())
    }

    @Test fun originalColorsResetAllThreeDraftsAndBecomeDurableOnlyAfterSave() {
        val custom = ODXFiAppearance(0xFF7A3159.toInt(), 0xFFEED4A4.toInt(), 0xFF1E5970.toInt())
        val fixture = showEditor(custom)
        touch("odx-fi-colors-reset")

        assertAllDraftColors(ODXFiAppearance())
        assertHardwareColors(ODXFiAppearance())
        assertEquals("Restoring the preview must not silently save it", custom, fixture.read())
        assertEquals(0, fixture.saveCount.get())

        touch("odx-fi-colors-save")
        compose.waitUntil(5_000) { fixture.backCount.get() == 1 }
        assertEquals(ODXFiAppearance(), fixture.read())
        compose.runOnIdle { fixture.reopen() }
        assertAllDraftColors(ODXFiAppearance())
    }

    @Test fun crtTouchesCannotChoosePartsButThePhysicalControlsStillEditTheSelectedPart() {
        val fixture = showEditor()
        compose.onNodeWithTag("odx-fi-colors-part-case").assertIsSelected()
        compose.onNodeWithTag("odx-fi-colors-preview-buttons").performTouchInput { click() }
        compose.onNodeWithTag("odx-fi-colors-part-case").assertIsSelected()
        assertAllDraftColors(ODXFiAppearance())

        press("Down")
        compose.onNodeWithTag("odx-fi-colors-part-buttons").assertIsSelected()
        press("A")
        assertDraft("buttons", 0xFF292C33.toInt())
        assertColorVisible("odxfi-button-A", 0xFF292C33.toInt())
        press("Right")
        assertDraft("buttons", 0xFF2E2C33.toInt())
        press("SELECT")
        press("Right")
        assertDraft("buttons", 0xFF2E3133.toInt())
        assertDraft("case", ODXFiAppearance.DEFAULT_CASE_COLOR_ARGB)
        assertDraft("bezels", ODXFiAppearance.DEFAULT_BEZEL_COLOR_ARGB)
        assertEquals("Hardware edits remain drafts until Save", ODXFiAppearance(), fixture.read())
    }

    private fun showEditor(initial: ODXFiAppearance = ODXFiAppearance()): EditorFixture {
        val name = "odx_fi_colors_device_test_${UUID.randomUUID()}"
        preferenceFiles += name
        val fixture = EditorFixture(name)
        assertTrue(fixture.preferences().write(initial))
        compose.setContent {
            CompositionLocalProvider(DiagnosticLogger.LocalCurrentRoute provides "odx-fi-colors-test") {
                Box(Modifier.size(360.dp, 700.dp)) {
                    if (fixture.showing.value) key(fixture.instance.intValue) {
                        ODXFiAppearanceScreen(
                            initialAppearance = fixture.read(),
                            onSave = { appearance ->
                                fixture.saveCount.incrementAndGet()
                                fixture.allowSave.get() && fixture.preferences().write(appearance)
                            },
                            onBack = {
                                fixture.backCount.incrementAndGet()
                                fixture.showing.value = false
                            },
                        )
                    } else Text("Colors closed")
                }
            }
        }
        return fixture
    }

    private inner class EditorFixture(private val preferencesName: String) {
        val showing = mutableStateOf(true)
        val instance = mutableIntStateOf(0)
        val allowSave = AtomicBoolean(true)
        val saveCount = AtomicInteger()
        val backCount = AtomicInteger()

        // Reconstruct the preferences reader on each access, rather than reusing screen state.
        fun preferences() = ODXFiAppearancePreferences(context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE))
        fun read(): ODXFiAppearance = preferences().read()
        fun reopen() {
            instance.intValue++
            showing.value = true
        }
    }

    private fun choosePreset(part: String, preset: String) {
        touch("odx-fi-colors-part-$part")
        compose.onNodeWithTag("odx-fi-colors-preset-$preset").performScrollTo()
            .performTouchInput { click() }
        compose.waitForIdle()
    }

    private fun assertAllDraftColors(appearance: ODXFiAppearance) {
        assertDraft("case", appearance.caseColorArgb)
        assertDraft("buttons", appearance.buttonColorArgb)
        assertDraft("bezels", appearance.bezelColorArgb)
    }

    private fun assertDraft(part: String, argb: Int) {
        compose.onNodeWithTag("odx-fi-colors-preview-$part").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, odxFiColorHex(argb)))
    }

    private fun assertHardwareColors(appearance: ODXFiAppearance) {
        assertColorVisible("odxfi-case", appearance.caseColorArgb)
        assertColorVisible("odxfi-crt-bezel", appearance.bezelColorArgb)
        assertColorVisible("odxfi-control-bezel", appearance.bezelColorArgb)
        assertColorVisible("odxfi-button-A", appearance.buttonColorArgb)
        assertColorVisible("odxfi-button-B", appearance.buttonColorArgb)
    }

    private fun assertColorVisible(tag: String, argb: Int) {
        val bitmap = compose.onNodeWithTag(tag).assertIsDisplayed().captureToImage().asAndroidBitmap()
        var matches = 0
        var samples = 0
        for (y in 0 until bitmap.height step 2) for (x in 0 until bitmap.width step 2) {
            samples++
            if (bitmap.getPixel(x, y) == argb) matches++
        }
        assertTrue("$tag must actually render ${odxFiColorHex(argb)}, not only report it in preview text ($matches/$samples pixels)",
            matches > samples / 100)
    }

    private fun hardwareBounds() = listOf(
        "odxfi-case", "odxfi-crt-bezel", "odxfi-control-bezel", "odxfi-button-A", "odxfi-button-B",
    ).associateWith { compose.onNodeWithTag(it).getUnclippedBoundsInRoot() }

    private fun touch(tag: String) {
        compose.onNodeWithTag(tag).assertIsDisplayed().performTouchInput { click() }
        compose.waitForIdle()
    }

    private fun press(label: String) {
        val node = if (label in listOf("Up", "Down", "Left", "Right")) compose.onNodeWithContentDescription(label)
            else compose.onNodeWithTag("odxfi-button-${if (label == "SELECT") "Select" else label}")
        node.assertIsDisplayed().performTouchInput { click() }
        compose.waitForIdle()
    }
}
