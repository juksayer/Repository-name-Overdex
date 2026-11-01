package com.example.overdex.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.CalibrationManager
import com.example.overdex.battle.observation.TeamSelectCalibration
import com.example.overdex.model.AnchorRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/** Actual preference/profile persistence, isolated from the phone's user calibration. */
class QteProgressCalibrationPersistenceDeviceTest {
    @Test fun progressBoxRoundTripsThroughLegacyPreferencesAndNamedProfile() = isolated { context ->
        val custom = BattleCalibration(
            announcementRegion = AnchorRegion(.08f, .22f, .84f, .06f),
            chargeMoveExecutionRegion = AnchorRegion(.04f, .24f, .92f, .58f),
            qteProgressTextRegion = AnchorRegion(.17f, .27f, .66f, .065f),
        )
        assertTrue(CalibrationManager(context).save(custom))
        val legacyReloaded = CalibrationManager(context).load()
        assertEquals(custom.qteProgressTextRegion, legacyReloaded.qteProgressTextRegion)
        assertEquals(custom.announcementRegion, legacyReloaded.announcementRegion)
        assertEquals(custom.chargeMoveExecutionRegion, legacyReloaded.chargeMoveExecutionRegion)

        val store = MatchCalibrationProfileStore(context)
        assertNotNull(store.saveAs("QTE test", "Isolated test", 672, 1493,
            custom, TeamSelectCalibration.measured1080x2400))
        assertEquals(custom, MatchCalibrationProfileStore(context).activeProfile()?.battleCalibration)

        val edited = custom.copy(qteProgressTextRegion = AnchorRegion(.14f, .25f, .72f, .08f))
        assertTrue(CalibrationManager(context).save(edited))
        assertEquals(edited, CalibrationManager(context).load())
        assertEquals(edited, MatchCalibrationProfileStore(context).activeProfile()?.battleCalibration)
    }

    @Test fun oldProfileKeepsExistingBoxesAndDefaultsOnlyTheNewProgressBox() = isolated { context ->
        val directory = File(context.filesDir, "calibration_profiles").apply { mkdirs() }
        File(directory, "old-profile.json").writeText("""
            {
              "schemaVersion": 2, "id": "old-profile", "name": "Existing calibration",
              "deviceModel": "Isolated test", "screenWidth": 672, "screenHeight": 1493,
              "savedAtMillis": 1,
              "battleRegions": {
                "announcement": {"x":0.08,"y":0.22,"width":0.84,"height":0.06},
                "charge_move_execution": {"x":0.04,"y":0.24,"width":0.92,"height":0.58}
              },
              "teamSelectRegions": {}
            }
        """.trimIndent())

        val old = MatchCalibrationProfileStore(context).activate("old-profile")!!
        assertEquals(AnchorRegion(.08f, .22f, .84f, .06f), old.battleCalibration.announcementRegion)
        assertEquals(AnchorRegion(.04f, .24f, .92f, .58f), old.battleCalibration.chargeMoveExecutionRegion)
        assertEquals(BattleCalibration().qteProgressTextRegion, old.battleCalibration.qteProgressTextRegion)
        assertEquals(old.battleCalibration, CalibrationManager(context).load())
    }

    @Test fun oldLegacyPreferencesUseTheNewDefaultWithoutMovingExecution() = isolated { context ->
        assertTrue(context.getSharedPreferences("overmon_calibration", Context.MODE_PRIVATE).edit()
            .putFloat("charge_move_execution_x", .04f)
            .putFloat("charge_move_execution_y", .24f)
            .putFloat("charge_move_execution_w", .92f)
            .putFloat("charge_move_execution_h", .58f)
            .commit())

        val loaded = CalibrationManager(context).load()
        assertEquals(AnchorRegion(.04f, .24f, .92f, .58f), loaded.chargeMoveExecutionRegion)
        assertEquals(BattleCalibration().qteProgressTextRegion, loaded.qteProgressTextRegion)
    }

    private fun isolated(test: (Context) -> Unit) {
        val context = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext)
        try { test(context) } finally { context.cleanUp() }
    }

    private class IsolatedContext(base: Context) : ContextWrapper(base) {
        private val prefix = "qte-progress-calibration-test-${UUID.randomUUID()}"
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
            preferenceNames.forEach { baseContext.deleteSharedPreferences(it) }
            directory.deleteRecursively()
        }
    }
}
