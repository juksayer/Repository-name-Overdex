package com.example.overdex.data

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.model.FieldNoteDraft
import com.example.overdex.model.PersonalFieldNote
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** An isolated cache directory exercises AtomicFile without opening the user's actual journal. */
class PersonalFieldNotesStoreDeviceTest {
    @Test fun atomicJournalReopensAndKeepsCorruptSourceForAnExplicitRetry() = runBlocking {
        assertTrue("Disposable emulator only", Build.HARDWARE in setOf("ranchu", "goldfish") || Build.FINGERPRINT.contains("generic"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "test_personal_notes_${UUID.randomUUID()}")
        val file = File(folder, "journal.json")
        try {
            val store = PersonalFieldNotesStore(file)
            store.load()
            val draft = FieldNoteDraft(speciesId = 1, speciesName = "Bulbasaur", title = "An unfinished draft")
            store.saveDraft(draft)
            assertEquals(draft, PersonalFieldNotesStore(file).load().draft)
            val saved = store.saveNote(PersonalFieldNote(id = "personal-${draft.id}", speciesId = 1,
                speciesName = "Bulbasaur", title = "A saved note", lines = listOf("One original line"),
                createdAt = 1, updatedAt = 2, originTrainerId = "isolated-test-trainer"))
            val good = file.readBytes()
            val reopened = PersonalFieldNotesStore(file)
            assertEquals(saved, reopened.load().notes.single())
            assertNull(reopened.state.value.draft)
            file.writeText("corrupt source kept for recovery")
            assertTrue(runCatching { reopened.load() }.isFailure)
            assertTrue(runCatching { reopened.discardDraft() }.isFailure)
            assertEquals("corrupt source kept for recovery", file.readText())
            file.writeBytes(good)
            assertEquals(saved, reopened.load().notes.single())
        } finally { folder.deleteRecursively() }
    }
}
