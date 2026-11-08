package com.example.overdex.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.model.FieldNoteDraft
import com.example.overdex.model.SyllableCorrection
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Real route-scoped ViewModels and durable journal storage; never run on a trainer's phone. */
class FieldNotesViewModelDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as Application
    private val models = mutableListOf<ModelHandle>()
    private val preferenceBackups = mutableListOf<Pair<SharedPreferences, Map<String, Any?>>>()
    private lateinit var fixtureDirectory: File
    private lateinit var journalDirectory: File
    private var originalJournalMoved = false
    private var isolated = false

    @Before fun preserveTheEmulatorsJournal() {
        assertTrue("Disposable emulator only; this test must never modify a trainer's phone",
            Build.HARDWARE in setOf("ranchu", "goldfish") || Build.FINGERPRINT.contains("generic"))
        fixtureDirectory = File(app.cacheDir, "field_notes_vm_test_${UUID.randomUUID()}")
        assertTrue(fixtureDirectory.mkdirs())
        journalDirectory = File(app.noBackupFilesDir, "field_notes")
        if (journalDirectory.exists()) {
            assertTrue("Preserve the complete original journal before opening any test ViewModel",
                journalDirectory.renameTo(File(fixtureDirectory, "original-field-notes")))
            originalJournalMoved = true
        }
        isolated = true
        listOf("field_note_favorites", "overdex_trainer_prefs").forEach { name ->
            val preferences = app.getSharedPreferences(name, Context.MODE_PRIVATE)
            val snapshot = preferences.all.mapValues { (_, value) ->
                if (value is Set<*>) value.toSet() else value
            }
            preferenceBackups += preferences to snapshot
        }
        assertTrue(app.getSharedPreferences("field_note_favorites", Context.MODE_PRIVATE)
            .edit().clear().commit())
    }

    @After fun restoreTheEmulatorsJournal() {
        if (!isolated) return
        // Join cancellation, including any non-cancellable AtomicFile commit, before restoring files.
        models.toList().forEach(::close)
        assertTrue("Remove only this test's journal", !journalDirectory.exists() || journalDirectory.deleteRecursively())
        if (originalJournalMoved) {
            assertTrue("Restore the original no-backup journal unchanged",
                File(fixtureDirectory, "original-field-notes").renameTo(journalDirectory))
        }
        preferenceBackups.forEach { (preferences, snapshot) -> restorePreferences(preferences, snapshot) }
        assertTrue(fixtureDirectory.deleteRecursively())
    }

    @Test fun flushedDraftReopensAndRevisionKeepsItsIdentityFavoriteAndEarlierCorrection() {
        val first = openModel()
        val draft = startDraft(first.model, "A durable field note")
        val leftEditor = AtomicBoolean()
        main { first.model.keepDraft { leftEditor.set(true) } }
        await { leftEditor.get() && !first.model.busy.value }
        assertEquals(draft, first.model.localState.value.draft)
        close(first)

        val reopened = openModel()
        assertEquals("A fresh ViewModel restores the last keystroke", draft, reopened.model.draft.value)
        val noteId = saveNote(reopened.model)
        assertEquals("personal-${draft.id}", noteId)
        val initial = reopened.model.localState.value.notes.single()
        assertEquals(1, initial.revision)
        assertNull(reopened.model.draft.value)
        assertNull(reopened.model.localState.value.draft)
        assertFalse(reopened.model.entries.value.single { it.note.id == noteId }.note.verified)

        main { reopened.model.toggleFavorite(noteId) }
        await { noteId in reopened.model.favorites.value }
        val correctionSaved = AtomicBoolean()
        val correctionId = "test-correction-${UUID.randomUUID()}"
        main {
            reopened.model.saveCorrection(SyllableCorrection(
                id = correctionId, entryId = noteId, entryRevision = 1, lineIndex = 0,
                proposedCount = 5, pronunciation = "A local reading", replacementLine = "", createdAt = 1,
                originalLine = "This caller-supplied text must not replace the source line",
            )) { correctionSaved.set(true) }
        }
        await { correctionSaved.get() && !reopened.model.busy.value }
        val correction = reopened.model.localState.value.corrections.single()
        assertEquals(initial.lines.first(), correction.originalLine)
        assertEquals(initial.originTrainerId, correction.originTrainerId)

        main { assertTrue(reopened.model.editNote(noteId)) }
        val revisedDraft = reopened.model.draft.value!!.copy(
            title = "An edited field note", lines = listOf("A different opening", "The other line remains"),
        )
        main {
            reopened.model.updateDraft(revisedDraft)
            assertTrue(reopened.model.editNote(noteId))
        }
        assertEquals("Opening edit again must resume the in-progress text", revisedDraft, reopened.model.draft.value)
        assertEquals(noteId, saveNote(reopened.model))
        close(reopened)

        val final = openModel().model
        val saved = final.localState.value.notes.single()
        assertEquals(noteId, saved.id)
        assertEquals(2, saved.revision)
        assertEquals(initial.createdAt, saved.createdAt)
        assertEquals(initial.originTrainerId, saved.originTrainerId)
        assertEquals(revisedDraft.lines, saved.lines)
        assertTrue(noteId in final.favorites.value)
        assertNull(final.draft.value)
        assertEquals("A correction remains testimony about the original revision", correction,
            final.localState.value.corrections.single())
        assertEquals(1, correction.entryRevision)
    }

    @Test fun deletingTheEditedNoteCancelsItsPendingDraftAndCannotResurrectIt() {
        val first = openModel()
        startDraft(first.model, "An entry to delete")
        val noteId = saveNote(first.model)
        val deleted = AtomicBoolean()
        main {
            assertTrue(first.model.editNote(noteId))
            first.model.updateDraft(first.model.draft.value!!.copy(title = "An unfinished edit"))
            // Delete before the 250 ms debounce can publish that edit after the deletion.
            first.model.deleteNote(noteId) { deleted.set(true) }
        }
        await { deleted.get() && !first.model.busy.value }
        assertNull(first.model.draft.value)
        assertNull(first.model.localState.value.draft)
        assertTrue(first.model.localState.value.notes.isEmpty())
        runBlocking { delay(400) }
        assertNull("A delayed autosave cannot restore the deleted note's draft", first.model.localState.value.draft)

        val unexpectedlySaved = AtomicBoolean()
        main { first.model.saveDraftAsNote { unexpectedlySaved.set(true) } }
        await { !first.model.busy.value }
        assertFalse(unexpectedlySaved.get())
        assertNotNull("Saving a deleted draft reports failure instead of inventing another note", first.model.saveError.value)
        assertTrue(first.model.localState.value.notes.isEmpty())
        close(first)
        val reopened = openModel().model
        assertNull(reopened.draft.value)
        assertTrue(reopened.localState.value.notes.isEmpty())
    }

    @Test fun retryPersistsTheNewerInMemoryDraftBeforeAnotherViewModelReopens() {
        val first = openModel()
        val draft = startDraft(first.model, "A draft newer than disk").copy(title = "The last keystroke before retry")
        main {
            first.model.updateDraft(draft)
            // The retry occurs in the same Main turn, before this update's debounce can run.
            first.model.retry()
        }
        await { !first.model.loading.value }
        assertNull(first.model.loadError.value)
        assertEquals(draft, first.model.draft.value)
        assertEquals("Successful retry must also recover the newest draft durably", draft,
            first.model.localState.value.draft)
        close(first)
        assertEquals(draft, openModel().model.draft.value)
    }

    private fun startDraft(model: FieldNotesViewModel, title: String): FieldNoteDraft {
        val source = model.entries.value.first { it.speciesId == 1 }
        return main {
            assertTrue(model.beginDraft(source))
            val updated = model.draft.value!!.copy(title = title,
                lines = listOf("One original opening", "The other line remains"), author = "")
            model.updateDraft(updated)
            updated
        }
    }

    private fun saveNote(model: FieldNotesViewModel): String {
        val savedId = AtomicReference<String?>()
        main { model.saveDraftAsNote { savedId.set(it) } }
        await { savedId.get() != null && !model.busy.value }
        assertNull(model.saveError.value)
        return savedId.get()!!
    }

    private fun openModel(): ModelHandle {
        val handle = main {
            val store = ViewModelStore()
            val model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[FieldNotesViewModel::class.java]
            ModelHandle(store, model)
        }
        models += handle
        await { !handle.model.loading.value }
        assertNull("The actual catalogue and saved journal must load", handle.model.loadError.value)
        return handle
    }

    private fun close(handle: ModelHandle) {
        if (handle.closed) return
        val job = main {
            val scopeJob = handle.model.viewModelScope.coroutineContext[Job]
            handle.store.clear()
            scopeJob
        }
        runBlocking { withTimeout(15_000) { job?.join() } }
        handle.closed = true
    }

    private fun await(condition: () -> Boolean) = runBlocking {
        withTimeout(15_000) { while (!condition()) delay(20) }
    }

    private fun <T> main(block: () -> T): T {
        val result = AtomicReference<Result<T>>()
        instrumentation.runOnMainSync { result.set(runCatching(block)) }
        return result.get().getOrThrow()
    }

    private fun restorePreferences(preferences: SharedPreferences, snapshot: Map<String, Any?>) {
        val editor = preferences.edit().clear()
        snapshot.forEach { (key, value) ->
            when (value) {
                null -> editor.remove(key)
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.map { it as String }.toSet())
                else -> error("Unsupported original preference type")
            }
        }
        assertTrue("Restore the emulator's original preferences", editor.commit())
    }

    private data class ModelHandle(val store: ViewModelStore, val model: FieldNotesViewModel, var closed: Boolean = false)
}
