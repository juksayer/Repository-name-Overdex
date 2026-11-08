package com.example.overdex.data

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Disposable-emulator preferences only: no real favorite, owned-card or battle data is modified. */
class FieldNoteFavoritesStoreDeviceTest {
    @Test fun favoritesCommitToDiskAndSurviveAReopenedStore() = runBlocking {
        assertTrue("Run this isolated test on an emulator, never a trainer's phone",
            Build.HARDWARE in setOf("ranchu", "goldfish") || Build.FINGERPRINT.contains("generic"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test_field_notes_${UUID.randomUUID()}"
        try {
            val original = FieldNoteFavoritesStore(context, name)
            original.toggle("181-001")
            original.toggle("181-002")
            original.toggle("1026-004")
            original.toggle("181-001")
            val expected = setOf("181-002", "1026-004")
            assertEquals(expected, FieldNoteFavoritesStore(context, name).favorites.value)
            val disk = File(context.applicationInfo.dataDir, "shared_prefs/$name.xml").readText()
            assertTrue("A successful commit must have reached the preference file", disk.contains("181-002"))
            assertTrue(disk.contains("1026-004"))
            assertFalse(disk.contains("181-001"))
        } finally {
            context.deleteSharedPreferences(name)
        }
    }
}
