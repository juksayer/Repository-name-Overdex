package com.example.overdex.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.CalibrationManager
import com.example.overdex.battle.observation.TeamSelectCalibration
import com.example.overdex.battle.observation.TeamSelectCalibrationStore
import com.example.overdex.data.BattleCalibration
import com.example.overdex.data.CalibrationScreenshotFilter
import com.example.overdex.data.CalibrationScreenshotLibrary
import com.example.overdex.data.MatchCalibrationProfileStore
import com.example.overdex.data.ScreenshotDirectoryManager
import com.example.overdex.model.AnchorRegion
import com.example.overdex.ui.screens.MatchCalibrationScreen
import com.example.overdex.ui.screens.loadCalibrationSamples
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** The real START modal must load exact boxes and recover an explicitly kept restore point. */
class CalibrationProfilePickerDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun unmatchedDeviceOffersReferencesWithoutSilentlySelectingAnotherProfile() {
        val context = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val store = MatchCalibrationProfileStore(context)
            saveReference(context)
            val foreign = store.saveAs("Other phone", "Other manufacturer and model", 1080, 2280,
                BattleCalibration(), TeamSelectCalibration.measured1080x2400)!!
            var start: () -> Unit = {}
            compose.setContent {
                CompositionLocalProvider(LocalContext provides context,
                    LocalActivityResultRegistryOwner provides compose.activity) {
                    MatchCalibrationScreen(CalibrationManager(context), onStart = { start = it })
                }
            }
            // Decline the initial Save As suggestion without saving or activating anything.
            compose.onNodeWithText("CANCEL").performClick()
            compose.runOnIdle { start() }
            compose.onNodeWithTag("calibration-profile-no-device-match").assertIsDisplayed()
            compose.onNodeWithTag("calibration-profile-show-other-devices").performScrollTo().performClick()
            compose.onNodeWithTag("calibration-profile-picker").performClick()
            compose.onNodeWithTag("calibration-profile-${foreign.id}").assertIsDisplayed().performClick()
            compose.onNodeWithTag("calibration-profile-review-only").assertIsDisplayed()
            compose.onNodeWithText("SAVE CALIBRATION AS").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(foreign.id, store.activeProfile()!!.id)
                assertEquals(1, store.list().size)
                assertEquals(foreign.battleCalibration, store.activeProfile()!!.battleCalibration)
            }
        } finally { context.cleanUp() }
    }

    @Test fun startModalSelectsSavedDeviceAndRestoresBothCalibrationHalves() {
        val context = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val store = MatchCalibrationProfileStore(context)
            saveReference(context)
            val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            val model = listOf(Build.MANUFACTURER, Build.MODEL).map(String::trim)
                .filter(String::isNotBlank).joinToString(" ")
            val first = store.saveAs("This phone", model, bounds.width(), bounds.height(),
                BattleCalibration(), TeamSelectCalibration.measured1080x2400)!!
            val foreignBattle = BattleCalibration(countdownRegion = AnchorRegion(.18f, .32f, .62f, .28f))
            val foreignTeam = TeamSelectCalibration.measured1080x2400.copy(
                leagueText = AnchorRegion(.14f, .35f, .7f, .025f))
            val foreign = store.saveAs("Pixel test reference", "Google Pixel 4", 1080, 2280,
                foreignBattle, foreignTeam)!!
            store.activate(first.id)
            var start: () -> Unit = {}
            var up: () -> Unit = {}
            compose.setContent {
                CompositionLocalProvider(LocalContext provides context,
                    LocalActivityResultRegistryOwner provides compose.activity) {
                    MatchCalibrationScreen(CalibrationManager(context), onStart = { start = it }, onUp = { up = it })
                }
            }
            awaitEditableReference()
            compose.runOnIdle { start() }
            compose.onNodeWithText("DEVICE CALIBRATIONS").assertIsDisplayed()
            compose.onNodeWithTag("calibration-profile-picker").performClick()
            compose.onNodeWithTag("calibration-profile-${foreign.id}").assertDoesNotExist()
            compose.onNodeWithTag("calibration-profile-${first.id}").assertIsDisplayed().performClick()
            compose.onNodeWithTag("calibration-profile-show-other-devices").performScrollTo().performClick()
            compose.onNodeWithTag("calibration-profile-picker").performClick()
            compose.onNodeWithTag("calibration-profile-${foreign.id}").performScrollTo().performClick()
            compose.onNodeWithTag("calibration-profile-review-only").assertIsDisplayed()
            compose.onNodeWithText("SAVE CALIBRATION AS").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals("Reviewing another phone cannot activate its crop settings", first.id, store.activeProfile()!!.id)
                assertEquals(first.battleCalibration, CalibrationManager(context).load())
                assertEquals(first.teamSelectCalibration, TeamSelectCalibrationStore(context).load())
                assertNull(store.activeProfile()!!.workingCheckpoint)
            }
            compose.onNodeWithTag("calibration-profile-keep-working").assertIsNotEnabled()
            compose.onNodeWithTag("calibration-profile-picker").performScrollTo().performClick()
            compose.onNodeWithTag("calibration-profile-${first.id}").performScrollTo().performClick()
            compose.onNodeWithTag("calibration-profile-keep-working").performScrollTo().performClick()
            compose.runOnIdle { assertNotNull(store.activeProfile()!!.workingCheckpoint) }
            compose.onNodeWithText("CLOSE").performClick()
            compose.runOnIdle { up() }
            compose.runOnIdle {
                assertNotEquals(first.battleCalibration, store.activeProfile()!!.battleCalibration)
                assertEquals(first.battleCalibration, store.activeProfile()!!.workingCheckpoint!!.battleCalibration)
                // Exercise both halves through an independent writer before restoring through the UI.
                assertTrue(TeamSelectCalibrationStore(context).save(foreignTeam.copy(
                    leagueText = AnchorRegion(.2f, .3f, .6f, .04f)), bounds.width(), bounds.height()))
                start()
            }
            compose.onNodeWithTag("calibration-profile-restore-working").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(first.battleCalibration, CalibrationManager(context).load())
                assertEquals(first.teamSelectCalibration, TeamSelectCalibrationStore(context).load())
                assertEquals(first.id, store.activeProfile()!!.id)
            }
        } finally { context.cleanUp() }
    }

    @Test fun aForeignScreenshotCanBeReviewedButCannotMoveResizeOrSaveThisPhonesCrops() {
        val context = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            val model = listOf(Build.MANUFACTURER, Build.MODEL).map(String::trim)
                .filter(String::isNotBlank).joinToString(" ")
            val sourceHeight = if (com.example.overdex.data.CalibrationScreenshotLibrary.sameShape(
                    bounds.width(), bounds.height(), 1080, 2400)) 2280 else 2400
            saveReference(context, 1080, sourceHeight)
            val store = MatchCalibrationProfileStore(context)
            val original = store.saveAs("This phone only", model, bounds.width(), bounds.height(),
                BattleCalibration(), TeamSelectCalibration.measured1080x2400)!!
            var library: () -> Unit = {}
            var start: () -> Unit = {}
            var up: () -> Unit = {}
            var select: () -> Unit = {}
            var nextRegion: () -> Unit = {}
            var drag: (Offset) -> Unit = {}
            compose.setContent {
                CompositionLocalProvider(LocalContext provides context,
                    LocalActivityResultRegistryOwner provides compose.activity) {
                    MatchCalibrationScreen(CalibrationManager(context), onALong = { library = it },
                        onStart = { start = it }, onUp = { up = it }, onSelect = { select = it },
                        onA = { nextRegion = it }, onLcdDrag = { drag = it })
                }
            }
            compose.runOnIdle { library() }
            compose.onNodeWithText("All screenshots").performScrollTo().performClick()
            compose.onNodeWithText("CLOSE").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("calibration-reference-read-only").fetchSemanticsNodes().any {
                    it.config[androidx.compose.ui.semantics.SemanticsProperties.Text]
                        .any { text -> text.text.contains("different screen shape") }
                }
            }
            compose.runOnIdle { up() }
            compose.runOnIdle { select() }
            compose.runOnIdle { up(); drag(Offset(30f, 40f)) }
            compose.runOnIdle { nextRegion() }
            compose.runOnIdle {
                up()
                assertEquals(original.battleCalibration, CalibrationManager(context).load())
                assertEquals(original.teamSelectCalibration, TeamSelectCalibrationStore(context).load())
                assertEquals(original.savedAtMillis, store.activeProfile()!!.savedAtMillis)
                start()
            }
            compose.onNodeWithTag("calibration-profile-keep-working").assertIsNotEnabled()
            compose.onNodeWithTag("calibration-profile-save-as").performScrollTo().performClick()
            compose.onNodeWithText("SAVE").performClick()
            compose.runOnIdle {
                assertEquals(1, store.list().size)
                assertEquals(original.id, store.activeProfile()!!.id)
                assertEquals(original.savedAtMillis, store.activeProfile()!!.savedAtMillis)
                assertEquals(original.battleCalibration, CalibrationManager(context).load())
                assertEquals(original.teamSelectCalibration, TeamSelectCalibrationStore(context).load())
            }
            compose.onNodeWithTag("calibration-profile-save-error")
                .assertTextContains("different screen shape", substring = true).assertIsDisplayed()
        } finally { context.cleanUp() }
    }

    @Test fun aForeignUserLibraryCannotHideThisPhonesOwnCapturedReference() {
        val context = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            // Actual PNG headers use the two phones' uniformly downscaled capture geometries.
            saveReference(context, 672, 1419)
            val foreignFile = File(context.filesDir, "moondrop-user-reference.png")
            savePng(foreignFile, 672, 1493)
            val samples = loadCalibrationSamples(context, ScreenshotDirectoryManager(context), userImages = listOf(
                ScreenshotDirectoryManager.Image(Uri.fromFile(foreignFile), "Moondrop reference", "Moondrop"),
            ))
            assertEquals(listOf("DEVICE CAPTURE", "YOUR SCREENSHOT"), samples.take(2).map { it.sourceName })
            assertEquals(listOf(672 to 1419, 672 to 1493), samples.take(2).map { it.reference.width to it.reference.height })
            val compatible = CalibrationScreenshotLibrary.filter(samples.map { it.reference },
                CalibrationScreenshotFilter.ThisScreen, 1080, 2280)
            assertEquals("This screen retains the Pixel capture even with a selected Moondrop folder",
                samples.first().reference, compatible.first())
            assertEquals("The native Pixel pack remains reachable with a custom folder", 33, compatible.size)
            assertTrue(compatible.drop(1).all { it.deviceLabel == "Google Pixel 4" && it.width == 1080 && it.height == 2280 })
            assertTrue("The foreign source remains available for deliberate review", foreignFile.isFile)
            assertEquals(samples.size, CalibrationScreenshotLibrary.filter(samples.map { it.reference },
                CalibrationScreenshotFilter.All, 1080, 2280).size)
        } finally { context.cleanUp() }
    }

    private fun awaitEditableReference() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("calibration-reference-ready").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun saveReference(context: Context, width: Int? = null, height: Int? = null) {
        val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        val file = File(context.filesDir, "calibration_reference/latest_battle.png")
        savePng(file, width ?: bounds.width(), height ?: bounds.height())
    }

    private fun savePng(file: File, width: Int, height: Int) {
        assertTrue(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.BLACK)
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
    }

    private class IsolatedContext(base: Context) : ContextWrapper(base) {
        private val prefix = "calibration-picker-${UUID.randomUUID()}"
        private val directory = File(base.cacheDir, prefix).apply { mkdirs() }
        private val preferenceNames = mutableSetOf<String>()
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = directory
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val isolatedName = "$prefix-$name"
            preferenceNames += isolatedName
            return super.getSharedPreferences(isolatedName, mode)
        }
        fun cleanUp() {
            preferenceNames.forEach { super.getSharedPreferences(it, MODE_PRIVATE).edit().clear().commit() }
            directory.deleteRecursively()
        }
    }
}
