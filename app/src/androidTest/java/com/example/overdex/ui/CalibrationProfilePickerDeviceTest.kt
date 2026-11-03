package com.example.overdex.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.CalibrationManager
import com.example.overdex.battle.observation.TeamSelectCalibration
import com.example.overdex.battle.observation.TeamSelectCalibrationStore
import com.example.overdex.data.BattleCalibration
import com.example.overdex.data.MatchCalibrationProfileStore
import com.example.overdex.model.AnchorRegion
import com.example.overdex.ui.screens.MatchCalibrationScreen
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** The real START modal must load exact boxes and recover an explicitly kept restore point. */
class CalibrationProfilePickerDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun startModalSelectsSavedDeviceAndRestoresBothCalibrationHalves() {
        val context = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val store = MatchCalibrationProfileStore(context)
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
            compose.runOnIdle { start() }
            compose.onNodeWithText("DEVICE CALIBRATIONS").assertIsDisplayed()
            compose.onNodeWithTag("calibration-profile-picker").performClick()
            compose.onNodeWithTag("calibration-profile-${foreign.id}").performScrollTo().performClick()
            compose.onNodeWithText("SAVE CALIBRATION AS").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(foreign.id, store.activeProfile()!!.id)
                assertEquals(foreignBattle, CalibrationManager(context).load())
                assertEquals(foreignTeam, TeamSelectCalibrationStore(context).load())
                assertNull(store.activeProfile()!!.workingCheckpoint)
            }
            compose.onNodeWithTag("calibration-profile-keep-working").performScrollTo().performClick()
            compose.runOnIdle { assertNotNull(store.activeProfile()!!.workingCheckpoint) }
            compose.onNodeWithText("CLOSE").performClick()
            compose.runOnIdle { up() }
            compose.runOnIdle {
                assertNotEquals(foreignBattle, store.activeProfile()!!.battleCalibration)
                assertEquals(foreignBattle, store.activeProfile()!!.workingCheckpoint!!.battleCalibration)
                // Exercise both halves through an independent writer before restoring through the UI.
                assertTrue(TeamSelectCalibrationStore(context).save(foreignTeam.copy(
                    leagueText = AnchorRegion(.2f, .3f, .6f, .04f)), 1080, 2280))
                start()
            }
            compose.onNodeWithTag("calibration-profile-restore-working").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(foreignBattle, CalibrationManager(context).load())
                assertEquals(foreignTeam, TeamSelectCalibrationStore(context).load())
                assertEquals(first.battleCalibration, store.activate(first.id)!!.battleCalibration)
            }
        } finally { context.cleanUp() }
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
