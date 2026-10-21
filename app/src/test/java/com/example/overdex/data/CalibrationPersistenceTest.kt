package com.example.overdex.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.example.overdex.CalibrationManager
import com.example.overdex.battle.observation.TeamSelectCalibration
import com.example.overdex.battle.observation.TeamSelectCalibrationStore
import com.example.overdex.model.AnchorRegion
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Uses real profile files and independent store instances, as on an app restart. */
class CalibrationPersistenceTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `live QTE adjustment survives restart and changes only its own box in active profile`() {
        val context = TestContext(temporary.newFolder())
        val before = CalibrationManager(context).load().copy(
            playerSpeciesNameRegion = strip(31f, 237f, 272f, 48f),
            opponentSpeciesNameRegion = strip(780f, 243f, 269f, 53f),
        )
        val box = strip(410f, 1000f, 256f, 270f)
        // No named profile yet: legacy preferences must also preserve the new field.
        assertTrue(CalibrationManager(context).save(before.copy(qteTypeIconsRegion = box)))
        assertEquals(box, CalibrationManager(context).load().qteTypeIconsRegion)
        val profile = saveProfile(context, before)
        val changed = CalibrationManager(context).load().copy(qteTypeIconsRegion = box)
        assertTrue(CalibrationManager(context).save(changed))
        val reloaded = CalibrationManager(context).load()
        assertEquals(box, reloaded.qteTypeIconsRegion)
        assertEquals(before, reloaded.copy(qteTypeIconsRegion = before.qteTypeIconsRegion))
        val named = MatchCalibrationProfileStore(context).activeProfile()!!
        assertEquals(changed, named.battleCalibration)
        assertEquals(profile.id, named.id)
        assertEquals(profile.teamSelectCalibration, named.teamSelectCalibration)
    }

    @Test fun `older profiles get only a default QTE box without rewriting saved coordinates`() {
        val context = TestContext(temporary.newFolder())
        val positioned = BattleCalibration(opponentSpeciesNameRegion = strip(784f, 241f, 269f, 58f))
        val saved = saveProfile(context, positioned)
        val file = File(context.filesDir, "calibration_profiles/${saved.id}.json")
        val json = kotlinx.serialization.json.Json.parseToJsonElement(file.readText()) as kotlinx.serialization.json.JsonObject
        val regions = json["battleRegions"] as kotlinx.serialization.json.JsonObject
        file.writeText(kotlinx.serialization.json.JsonObject(json + ("battleRegions" to
            kotlinx.serialization.json.JsonObject(regions - "qte_type_icons"))).toString())
        val before = file.readText()
        assertEquals(positioned, CalibrationManager(context).load())
        assertEquals(before, file.readText())
    }

    @Test fun `pointer move label and decision timer boxes survive restart and named profile reload`() {
        val context = TestContext(temporary.newFolder())
        val positioned = CalibrationManager(context).load().copy(
            pointerCoordinatesRegion = strip(0f, 90f, 465f, 40f),
            decisionTimerRegion = strip(430f, 180f, 210f, 230f),
            playerChargeMoveNameLeftRegion = strip(90f, 2200f, 430f, 70f),
            playerChargeMoveNameRightRegion = strip(540f, 2200f, 430f, 70f),
            playerChargeMoveEffectivenessLeftRegion = strip(90f, 2300f, 430f, 55f),
            playerChargeMoveEffectivenessRightRegion = strip(540f, 2320f, 430f, 55f),
        )
        assertTrue(CalibrationManager(context).save(positioned))
        assertEquals(positioned, CalibrationManager(context).load())
        val profile = saveProfile(context, positioned)
        assertEquals(positioned, MatchCalibrationProfileStore(context).activeProfile()!!.battleCalibration)
        assertTrue(File(context.filesDir, "calibration_profiles/${profile.id}.json").readText().contains("pointer_coordinates"))
    }

    @Test fun `old profiles add effectiveness defaults without moving saved boxes or writing the profile`() {
        val context = TestContext(temporary.newFolder())
        val positioned = BattleCalibration(opponentSpeciesNameRegion = strip(785f, 237f, 250f, 50f))
        val saved = saveProfile(context, positioned)
        val file = File(context.filesDir, "calibration_profiles/${saved.id}.json")
        val json = kotlinx.serialization.json.Json.parseToJsonElement(file.readText()) as kotlinx.serialization.json.JsonObject
        val regions = json["battleRegions"] as kotlinx.serialization.json.JsonObject
        file.writeText(kotlinx.serialization.json.JsonObject(json + ("battleRegions" to
            kotlinx.serialization.json.JsonObject(regions - "player_charge_move_effectiveness_left" - "player_charge_move_effectiveness_right"))).toString())
        val before = file.readText()
        assertEquals(positioned, CalibrationManager(context).load())
        assertEquals(before, file.readText())
    }

    @Test
    fun `saved species boxes are not replaced when they match former defaults`() {
        val context = TestContext(temporary.newFolder())
        val manager = CalibrationManager(context)
        val positioned = manager.load().copy(
            playerSpeciesNameRegion = strip(20f, 237f, 272f, 48f),
            opponentSpeciesNameRegion = strip(788f, 237f, 272f, 48f),
            countdownRegion = BattleCalibration.PREVIOUS_NARROW_COUNTDOWN_REGION,
            vsScreenRegion = BattleCalibration.PREVIOUS_DEFAULT_VS_SCREEN_REGION
        )

        assertTrue(manager.save(positioned))
        repeat(3) {
            val reloaded = CalibrationManager(context).load()
            assertEquals(positioned.playerSpeciesNameRegion, reloaded.playerSpeciesNameRegion)
            assertEquals(positioned.opponentSpeciesNameRegion, reloaded.opponentSpeciesNameRegion)
            assertEquals(positioned.countdownRegion, reloaded.countdownRegion)
            assertEquals(positioned.vsScreenRegion, reloaded.vsScreenRegion)
        }
    }

    @Test
    fun `loading an older named profile preserves its exact coordinates and file`() {
        val context = TestContext(temporary.newFolder())
        val positioned = BattleCalibration(
            playerSpeciesNameRegion = strip(20f, 229f, 272f, 60f),
            opponentSpeciesNameRegion = strip(788f, 241f, 272f, 60f)
        )
        val saved = saveProfile(context, positioned)
        val file = File(context.filesDir, "calibration_profiles/${saved.id}.json")
        file.writeText(file.readText().replace("\"schemaVersion\": 2", "\"schemaVersion\": 1"))
        val before = file.readText()

        repeat(3) {
            assertEquals(positioned, MatchCalibrationProfileStore(context).activeProfile()!!.battleCalibration)
            assertEquals(positioned, CalibrationManager(context).load())
            assertEquals(before, file.readText())
        }
    }

    @Test
    fun `saving through the calibration manager updates the named profile used by load`() {
        val context = TestContext(temporary.newFolder())
        val saved = saveProfile(context, BattleCalibration())
        val positioned = saved.battleCalibration.copy(
            playerSpeciesNameRegion = strip(36.2f, 241f, 250.4f, 48f),
            opponentSpeciesNameRegion = strip(788f, 241f, 250.4f, 48f)
        )

        assertTrue(CalibrationManager(context).save(positioned))
        assertEquals(positioned, CalibrationManager(context).load())
        val reloaded = MatchCalibrationProfileStore(context).activeProfile()!!
        assertEquals(saved.id, reloaded.id)
        assertEquals(positioned, reloaded.battleCalibration)
        assertEquals(saved.teamSelectCalibration, reloaded.teamSelectCalibration)
    }

    @Test
    fun `saving team select updates only that half of the active profile`() {
        val context = TestContext(temporary.newFolder())
        val saved = saveProfile(context, BattleCalibration(playerSpeciesNameRegion = strip(36f, 241f, 250f, 48f)))
        val team = saved.teamSelectCalibration.copy(rosterName1 = strip(104f, 1850f, 290f, 90f))

        assertTrue(TeamSelectCalibrationStore(context).save(team))
        assertEquals(team, TeamSelectCalibrationStore(context).load())
        assertEquals(saved.battleCalibration, CalibrationManager(context).load())
    }

    @Test
    fun `an unreadable active profile cannot silently report a successful save`() {
        val context = TestContext(temporary.newFolder())
        val saved = saveProfile(context, BattleCalibration())
        val file = File(context.filesDir, "calibration_profiles/${saved.id}.json")
        file.writeText("interrupted or invalid profile")

        assertFalse(CalibrationManager(context).save(saved.battleCalibration))
        assertEquals("interrupted or invalid profile", file.readText())
    }

    @Test
    fun `switching named profiles never imports the other profiles species boxes`() {
        val context = TestContext(temporary.newFolder())
        val first = saveProfile(context, BattleCalibration(), "First")
        val second = saveProfile(context, BattleCalibration(playerSpeciesNameRegion = strip(35f, 242f, 249f, 49f)), "Second")
        val changed = second.battleCalibration.copy(opponentSpeciesNameRegion = strip(789f, 242f, 249f, 49f))

        assertTrue(CalibrationManager(context).save(changed))
        assertNotNull(MatchCalibrationProfileStore(context).activate(first.id))
        assertEquals(first.battleCalibration, CalibrationManager(context).load())
        assertNotNull(MatchCalibrationProfileStore(context).activate(second.id))
        assertEquals(changed, CalibrationManager(context).load())
    }

    private fun saveProfile(context: Context, battle: BattleCalibration, name: String = "Moondrop") =
        MatchCalibrationProfileStore(context).saveAs(
            name, "MOONDROP MD-PH-001", 1080, 2400, battle, TeamSelectCalibration.measured1080x2400
        )!!

    private fun strip(x: Float, y: Float, width: Float, height: Float) =
        AnchorRegion(x / 1080f, y / 2400f, width / 1080f, height / 2400f)

    private class TestContext(private val directory: File) : ContextWrapper(null) {
        private val stores = mutableMapOf<String, SharedPreferences>()
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = directory
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            stores.getOrPut(name) { TestPreferences() }
    }

    private class TestPreferences : SharedPreferences {
        private val values = mutableMapOf<String?, Any?>()
        override fun getAll(): Map<String?, *> = values.toMap()
        override fun contains(key: String?) = values.containsKey(key)
        override fun getString(key: String?, defValue: String?) = values[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?) =
            (values[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int) = values[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long) = values[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float) = values[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean) = values[key] as? Boolean ?: defValue
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String?, Any?>()
            private var clear = false
            override fun putString(key: String?, value: String?) = apply { pending[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { pending[key] = values?.toSet() }
            override fun putInt(key: String?, value: Int) = apply { pending[key] = value }
            override fun putLong(key: String?, value: Long) = apply { pending[key] = value }
            override fun putFloat(key: String?, value: Float) = apply { pending[key] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { pending[key] = value }
            override fun remove(key: String?) = apply { pending[key] = null }
            override fun clear() = apply { clear = true }
            override fun commit(): Boolean {
                if (clear) values.clear()
                pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                return true
            }
            override fun apply() { commit() }
        }
    }
}
