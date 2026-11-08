package com.example.overdex.ui

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.data.FieldNoteImportCandidate
import com.example.overdex.data.FieldNoteImportDecision
import com.example.overdex.data.FieldNoteTransferPackage
import com.example.overdex.diagnostics.DiagnosticLogger
import com.example.overdex.model.PersonalFieldNote
import com.example.overdex.model.SyllableCorrection
import com.example.overdex.ui.components.FilterSettings
import com.example.overdex.ui.screens.FieldNotesExchangeScreen
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Pure callback exchange UI inside the real protected shell; no picker or transport is opened. */
class FieldNotesExchangeScreenDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var contentInstalled = false
    private val shared = mutableListOf<Pair<String, Boolean>>()
    private val exported = mutableListOf<Boolean>()
    private val saved = mutableListOf<Pair<Set<String>, Boolean>>()
    private var imported = 0
    private var discarded = 0
    private var exited = 0

    @Before fun requireDisposableEmulator() {
        assertTrue("Run exchange UI tests only on a disposable emulator, never a trainer's phone",
            Build.HARDWARE in setOf("ranchu", "goldfish") || Build.FINGERPRINT.contains("generic"))
    }

    @After fun disposeReaders() {
        if (!contentInstalled) return
        compose.runOnIdle {
            compose.activity.setContent { }
            compose.activity.viewModelStore.clear()
        }
    }

    @Test fun selectingAReceivedNoteChangesOnlyItsLocalAvailabilityAndAnnotationsAreOptIn() {
        val received = note(1).copy(received = true, revision = 4)
        val original = received.copy()
        val local = note(2).copy(originTrainerId = LOCAL_TRAINER)
        val annotation = correction(received)
        val notes = mutableStateOf(listOf(received, local))
        openExchange(notes = { notes.value }, corrections = listOf(annotation), onShare = { id, enabled ->
            notes.value = notes.value.map { if (it.id == id) it.copy(shareable = enabled) else it }
        })

        compose.onNodeWithTag("field-notes-exchange-action:nearby").assertDoesNotExist()
        compose.onNodeWithTag("field-notes-exchange-origin")
            .assertTextEquals("Original trainer: ${received.originTrainerId}")
        compose.onNodeWithText("${received.author} · revision 4").assertExists()
        action("share:${received.id}")
        compose.onNodeWithText("✓ share ${received.title} · received").assertExists()
        compose.runOnIdle {
            assertEquals(listOf(received.id to true), shared)
            assertEquals(original.copy(shareable = true), notes.value.first())
            assertEquals("Other notes are not implicitly selected", local, notes.value.last())
            assertTrue("Marking availability never exports or imports", exported.isEmpty() && saved.isEmpty())
        }

        action("export")
        compose.runOnIdle { assertEquals(listOf(false), exported) }
        action("annotations")
        action("export")
        compose.runOnIdle {
            assertEquals(listOf(false, true), exported)
            assertEquals("Exporting does not rewrite the source or attribution", original.copy(shareable = true), notes.value.first())
        }
    }

    @Test fun physicalPreviewControlsSaveOnlyTheCurrentEligibleIdAndBulkSaveNamesEveryEligibleId() {
        val fresh = note(1)
        val update = note(2).copy(revision = 3)
        val duplicate = note(3)
        val conflict = note(4)
        val incoming = incoming(listOf(fresh, update, duplicate, conflict))
        val candidates = listOf(
            candidate(fresh, FieldNoteImportDecision.NEW),
            candidate(update, FieldNoteImportDecision.NEWER),
            candidate(duplicate, FieldNoteImportDecision.DUPLICATE),
            candidate(conflict, FieldNoteImportDecision.CONFLICT),
        )
        openExchange(incoming = incoming, candidates = candidates)
        assertPreview(fresh)
        press("A")
        compose.runOnIdle { assertEquals(listOf(setOf(fresh.id) to false), saved) }

        press("Right")
        assertPreview(update)
        compose.onNodeWithTag("field-notes-exchange-decision")
            .assertTextEquals("newer received revision · review before saving")
        compose.runOnIdle { assertEquals("Moving the preview never saves a record", 1, saved.size) }
        press("A")
        compose.runOnIdle { assertEquals(setOf(update.id) to false, saved.last()) }
        File(context.cacheDir, "field-notes-exchange-preview.png").outputStream().use { output ->
            assertTrue("Keep the complete shell and incoming preview for visual review",
                compose.onRoot().captureToImage().asAndroidBitmap()
                    .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
        }

        press("Left")
        assertPreview(fresh)
        action("save-eligible")
        compose.runOnIdle {
            assertEquals(listOf(setOf(fresh.id) to false, setOf(update.id) to false,
                setOf(fresh.id, update.id) to false), saved)
            assertEquals("Reviewing and saving never mutates the sender's package", listOf(fresh, update, duplicate, conflict), incoming.notes)
        }
    }

    @Test fun duplicateOlderConflictingAndOwnOriginalPagesCannotBeOverwrittenByTouchOrA() {
        val decisions = listOf(FieldNoteImportDecision.DUPLICATE, FieldNoteImportDecision.OLDER,
            FieldNoteImportDecision.CONFLICT, FieldNoteImportDecision.OWNED_CONFLICT)
        val notes = decisions.indices.map { note(it + 1) }
        openExchange(incoming = incoming(notes), candidates = notes.zip(decisions).map { (note, decision) ->
            candidate(note, decision)
        })
        decisions.forEachIndexed { index, _ ->
            assertPreview(notes[index])
            visibleAction("save-current").assertIsNotEnabled().performTouchInput { click() }
            press("A")
            compose.runOnIdle { assertTrue("A protected or duplicate page is never saved", saved.isEmpty()) }
            if (index != decisions.lastIndex) press("Right")
        }
        visibleAction("save-eligible").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertTrue("An entirely ineligible package has no bulk overwrite path", saved.isEmpty()) }
    }

    @Test fun correctionOnlyPackagesNeedExplicitInclusionBeforeAnySave() {
        val annotation = correction(note(1))
        val incoming = incoming(emptyList()).copy(corrections = listOf(annotation))
        openExchange(incoming = incoming)
        compose.onNodeWithTag("field-notes-exchange-decision").assertTextEquals("incoming syllable notes")
        compose.onNodeWithTag("field-notes-exchange-action:save-current").assertDoesNotExist()
        visibleAction("save-eligible").assertIsNotEnabled()
        visibleAction("save-corrections").assertIsNotEnabled().performTouchInput { click() }
        press("A")
        compose.runOnIdle { assertTrue("Opening a package never accepts its annotations", saved.isEmpty()) }

        action("annotations")
        visibleAction("save-corrections").assertIsEnabled()
        action("save-corrections")
        compose.runOnIdle {
            assertEquals(listOf(emptySet<String>() to true), saved)
            assertEquals(listOf(annotation), incoming.corrections)
        }
        action("discard")
        compose.runOnIdle { assertEquals(1, discarded) }
    }

    @Test fun incomingAnnotationsStayOutOfCurrentAndBulkSavesUntilSelected() {
        val fresh = note(1)
        val update = note(2).copy(revision = 2)
        openExchange(incoming = incoming(listOf(fresh, update)).copy(corrections = listOf(correction(fresh))),
            candidates = listOf(candidate(fresh, FieldNoteImportDecision.NEW), candidate(update, FieldNoteImportDecision.NEWER)))
        press("A")
        action("save-eligible")
        compose.runOnIdle { assertEquals(listOf(setOf(fresh.id) to false, setOf(fresh.id, update.id) to false), saved) }
        action("annotations")
        action("save-current")
        action("save-eligible")
        compose.runOnIdle {
            assertEquals(setOf(fresh.id) to true, saved[2])
            assertEquals(setOf(fresh.id, update.id) to true, saved[3])
        }
    }

    @Test fun touchingOrDraggingTheCrtDoesNotChooseOrSaveButPhysicalControlsRemainUsable() {
        val first = note(1)
        val second = note(2)
        openExchange(incoming = incoming(listOf(first, second)), candidates = listOf(
            candidate(first, FieldNoteImportDecision.NEW), candidate(second, FieldNoteImportDecision.NEW)))
        assertPreview(first)
        compose.onNodeWithTag("field-notes-exchange-crt").performTouchInput {
            click(center)
            swipeLeft()
            swipeUp()
        }
        assertPreview(first)
        compose.runOnIdle {
            assertTrue(saved.isEmpty() && shared.isEmpty() && exported.isEmpty())
            assertEquals(0, imported + discarded + exited)
        }
        press("Right")
        assertPreview(second)
        press("A")
        compose.runOnIdle { assertEquals(listOf(setOf(second.id) to false), saved) }
    }

    private fun openExchange(
        notes: () -> List<PersonalFieldNote> = { emptyList() },
        corrections: List<SyllableCorrection> = emptyList(),
        incoming: FieldNoteTransferPackage? = null,
        candidates: List<FieldNoteImportCandidate> = emptyList(),
        onShare: (String, Boolean) -> Unit = { _, _ -> },
    ) {
        lateinit var pokedex: PokedexViewModel
        compose.runOnIdle { pokedex = ViewModelProvider(compose.activity)[PokedexViewModel::class.java] }
        contentInstalled = true
        compose.setContent {
            CompositionLocalProvider(DiagnosticLogger.LocalCurrentRoute provides "field-notes-exchange-device-test") {
                Box(Modifier.size(360.dp, 760.dp)) {
                    FieldNotesExchangeScreen(
                        viewModel = pokedex,
                        filterSettings = FilterSettings(isEnabled = false),
                        onFilterSettingsChange = {},
                        notes = notes(),
                        corrections = corrections,
                        incoming = incoming,
                        candidates = candidates,
                        busy = false,
                        message = null,
                        onSetShareable = { id, enabled -> shared += id to enabled; onShare(id, enabled) },
                        onExportFile = { exported += it },
                        onImportFile = { imported++ },
                        onSaveIncoming = { ids, annotations -> saved += ids.toSet() to annotations },
                        onClearIncoming = { discarded++ },
                        onBack = { exited++ },
                    )
                }
            }
        }
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithTag("field-notes-exchange-lcd").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun visibleAction(id: String): SemanticsNodeInteraction {
        compose.onNode(hasScrollAction() and hasAnyAncestor(hasTestTag("field-notes-exchange-lcd")))
            .performScrollToNode(hasTestTag("field-notes-exchange-action:$id"))
        return compose.onNodeWithTag("field-notes-exchange-action:$id").assertIsDisplayed()
    }

    private fun action(id: String) {
        visibleAction(id).performTouchInput { click() }
        compose.waitForIdle()
    }

    private fun assertPreview(note: PersonalFieldNote) {
        compose.onNodeWithTag("field-notes-exchange-note-id").assertTextEquals("entry ${note.id}")
        compose.onNodeWithTag("field-notes-exchange-origin").assertTextEquals("Original trainer: ${note.originTrainerId}")
    }

    private fun press(label: String) {
        val target = if (label in listOf("Up", "Down", "Left", "Right"))
            compose.onNodeWithContentDescription(label) else compose.onNodeWithText(label)
        target.performTouchInput { click() }
        compose.waitForIdle()
    }

    private fun candidate(note: PersonalFieldNote, decision: FieldNoteImportDecision) = FieldNoteImportCandidate(note, decision)

    private fun note(index: Int) = PersonalFieldNote(
        id = "personal-00000000-0000-4000-8000-${index.toString().padStart(12, '0')}",
        speciesId = index,
        speciesName = if (index == 1) "Bulbasaur" else "Species $index",
        title = "Field page $index",
        lines = listOf("Leaves wait for the rain.", "A quiet trainer listens.", "One small step begins."),
        lesson = "Wait for evidence.",
        author = "Guest author $index",
        createdAt = 1_000L + index,
        updatedAt = 2_000L + index,
        originTrainerId = REMOTE_TRAINER,
    )

    private fun correction(note: PersonalFieldNote) = SyllableCorrection(
        id = "00000000-0000-4000-8000-000000000100",
        entryId = note.id,
        entryRevision = note.revision,
        lineIndex = 0,
        proposedCount = 5,
        pronunciation = "leaves / wait / for / the / rain",
        replacementLine = "Leaves wait for the rain.",
        originalLine = note.lines.first(),
        author = "Guest reader",
        originTrainerId = REMOTE_TRAINER,
        createdAt = 3_000L,
    )

    private fun incoming(notes: List<PersonalFieldNote>) = FieldNoteTransferPackage(
        packageId = "00000000-0000-4000-8000-000000000200",
        senderTrainerId = REMOTE_TRAINER,
        createdAt = 4_000L,
        notes = notes,
    )

    private companion object {
        const val LOCAL_TRAINER = "00000000-0000-4000-8000-000000000300"
        const val REMOTE_TRAINER = "00000000-0000-4000-8000-000000000400"
    }
}
