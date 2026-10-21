package com.example.overdex.data

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.archive.*
import com.example.overdex.battle.history.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BattleRecordsDeviceTest {
    @Test fun liveAlignmentSharesScreenshotTemplateAndKeepsSourceCoordinates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-live-boxes-${UUID.randomUUID()}"
        val selection = "$name-selection"
        try {
            val manager = com.example.overdex.CaptureTemplateManager(context, name)
            val before = manager.getPokemonDetailTemplate()
            val original = before.regions.first { it.id == "SpeciesName" }
            val alignment = com.example.overdex.data.registration.RegistrationLiveAlignment(context, name)
            alignment.begin(1080, 2400)
            alignment.edit()
            alignment.drag("SpeciesName", 108f, 240f)
            alignment.save()
            assertFalse(alignment.state.value.editing)
            assertNotNull(alignment.state.value.template)
            val saved = manager.getPokemonDetailTemplate()
            val changed = saved.regions.first { it.id == "SpeciesName" }
            assertEquals(original.x + .1f, changed.x, .0001f)
            assertEquals(original.y + .1f, changed.y, .0001f)
            assertEquals(before.regions.filterNot { it.id == "SpeciesName" }, saved.regions.filterNot { it.id == "SpeciesName" })
            val reloaded = com.example.overdex.data.registration.RegistrationLiveAlignment(context, name)
            reloaded.begin(1080, 2400)
            assertEquals(saved, reloaded.state.value.template)
            reloaded.edit()
            reloaded.drag("FastMoveRow", -40f, 50f)
            reloaded.close() // cancelling never persists a draft
            assertEquals(saved, manager.getPokemonDetailTemplate())
            assertNull(reloaded.state.value.template)
        } finally { context.deleteSharedPreferences(name); context.deleteSharedPreferences(selection) }
    }

    @Test fun fullAndCompactCopiesReplaceOneIndexAndDoNotDoubleMetrics() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.filesDir, "test-records-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) { override fun getFilesDir() = root }
        fun article(id: String, time: Long, payload: ArchivedTestimonyPayload) = ArchivedRealityArticle(id, "same-match",
            time, time, "TEST", payload, emptyList(), null, null, emptyList(), time * 1_000_000L)
        val archive = MatchArchive(matchId = "same-match", articles = listOf(
            article("identity", 900, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Charizard", 6)),
            article("start", 1000, ArchivedMatchStarted),
            article("hit", 1500, ArchivedFastMoveUseObserved("use", "PLAYER", "OPPONENT", attackerSpeciesName = "Charizard", evidenceKinds = listOf("HP_DAMAGE_TICK"), basis = "TEST"))))
        try {
            val dir = File(root, "match_archives").apply { mkdirs() }
            listOf("compact", "full").forEach { label -> File(dir, "$label.odxmatch.zip").outputStream().use { MatchArchivePackageWriter.write(archive, it) } }
            val first = BattleRecordStore(context).refresh()
            assertTrue(first.failures.toString(), first.failures.isEmpty())
            assertEquals(1, first.records.size)
            assertEquals(1, first.records.single().record.events.count { it.kind == BattleMetricKind.FAST_MOVE })
            val reloaded = BattleRecordStore(context).refresh()
            assertEquals(first.records.single().record, reloaded.records.single().record)
            assertEquals(1, File(root, "battle_records").listFiles()!!.count { it.extension == "json" })
        } finally { root.deleteRecursively() }
    }
}
