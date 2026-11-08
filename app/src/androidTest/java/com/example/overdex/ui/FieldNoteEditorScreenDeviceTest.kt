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
import com.example.overdex.diagnostics.DiagnosticLogger
import com.example.overdex.model.FieldNote
import com.example.overdex.model.FieldNoteDraft
import com.example.overdex.model.FieldNoteJournalEntry
import com.example.overdex.model.SyllableCorrection
import com.example.overdex.ui.components.FilterSettings
import com.example.overdex.ui.screens.FieldNoteEditorScreen
import com.example.overdex.ui.screens.SyllableCorrectionEditorScreen
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Real LCD input and shell controls; callbacks stay in memory and never write a note or annotation. */
class FieldNoteEditorScreenDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var modelCreated = false

    @Before fun requireDisposableEmulator() {
        assertTrue("Run editor integration tests on a disposable emulator, never a trainer's phone",
            Build.HARDWARE in setOf("ranchu", "goldfish") || Build.FINGERPRINT.contains("generic"))
    }

    @After fun releaseModel() {
        if (!modelCreated) return
        compose.runOnIdle {
            compose.activity.setContent { }
            compose.activity.viewModelStore.clear()
        }
    }

    @Test fun lcdLettersPunctuationAndCaseProduceProseWhileBackAndDiscardKeepTheirMeanings() {
        val originalLines = listOf("First existing line.", "Second existing line.", "Third existing line.", "A fourth line remains.")
        val draft = mutableStateOf(FieldNoteDraft(id = "editor-test-draft", speciesId = 25,
            speciesName = "Pikachu", lines = originalLines))
        val saved = AtomicReference<FieldNoteDraft?>()
        val changes = AtomicInteger()
        val returns = AtomicInteger()
        val discards = AtomicInteger()
        val model = pokedex()
        compose.setContent {
            CompositionLocalProvider(DiagnosticLogger.LocalCurrentRoute provides "field-note-editor-test") {
                Box(Modifier.size(360.dp, 760.dp)) {
                    FieldNoteEditorScreen(model, FilterSettings(isEnabled = false), {}, draft.value,
                        onDraftChange = { draft.value = it; changes.incrementAndGet() },
                        onSave = { saved.set(draft.value) }, onDiscard = { discards.incrementAndGet() },
                        onBack = { returns.incrementAndGet() }, saving = false, message = null)
                }
            }
        }
        compose.onNodeWithTag("field-note-editor-species").assertTextEquals("Pikachu")
        press("A")
        "RAIN".forEach { key(it.toString()) }
        compose.runOnIdle { assertEquals("rain", draft.value.title) }
        capture("field-note-writing-keyboard.png")
        press("SELECT")
        key("."); key("SPACE"); key("a/A"); key("letters"); key("S")
        press("SELECT")
        key("a/A"); key("letters"); key("U"); key("N")
        press("SELECT")
        key("!"); key("END")
        compose.runOnIdle {
            assertEquals("rain. Sun!", draft.value.title)
            assertTrue("Each edit reaches the draft owner before saving", changes.get() > 1)
            assertEquals(originalLines, draft.value.lines)
            assertEquals("Unknown Author", draft.value.author)
            assertNull(saved.get())
        }
        press("SAVE")
        compose.runOnIdle { assertEquals(draft.value, saved.get()) }
        press("B")
        compose.runOnIdle {
            assertEquals(1, returns.get())
            assertEquals("rain. Sun!", draft.value.title)
            assertEquals(0, discards.get())
        }

        action("field-note-editor", "discard")
        compose.runOnIdle { assertEquals("Opening confirmation must not discard work", 0, discards.get()) }
        press("B")
        compose.runOnIdle { assertEquals(0, discards.get()) }
        action("field-note-editor", "discard")
        action("field-note-editor", "confirm_discard")
        compose.runOnIdle { assertEquals(1, discards.get()) }
    }

    @Test fun aLineAnnotationCanOmitTheCountAndNumericZeroDoesNotAlterItsOriginalSource() {
        val source = FieldNoteJournalEntry(25, 25, "Pikachu", FieldNote(
            id = "editor-test-source", title = "Reading aloud",
            lines = listOf("Rain crosses the quiet field.", "Wind shakes leaves."),
            author = "Unknown Author", revision = 7,
        ))
        val originalSnapshot = source.copy(note = source.note.copy(lines = source.note.lines.toList()))
        val first = AtomicReference<SyllableCorrection?>()
        val latest = AtomicReference<SyllableCorrection?>()
        val saves = AtomicInteger()
        val startedAt = System.currentTimeMillis()
        val model = pokedex()
        compose.setContent {
            CompositionLocalProvider(DiagnosticLogger.LocalCurrentRoute provides "syllable-editor-test") {
                Box(Modifier.size(360.dp, 760.dp)) {
                    SyllableCorrectionEditorScreen(model, FilterSettings(isEnabled = false), {}, source,
                        onSave = { annotation ->
                            if (saves.getAndIncrement() == 0) first.set(annotation)
                            latest.set(annotation)
                        }, onBack = {}, saving = false, message = null)
                }
            }
        }
        press("Down")
        compose.onNodeWithTag("syllable-editor-original-line")
            .assertTextEquals("line 2 · Wind shakes leaves.").assertIsDisplayed()
        capture("field-note-syllable-editor.png")
        action("syllable-editor", "pronunciation")
        "SOFT".forEach { key(it.toString()) }
        press("SELECT"); key("."); key("END")
        press("SAVE")
        compose.runOnIdle {
            val annotation = requireNotNull(first.get())
            assertEquals("editor-test-source", annotation.entryId)
            assertEquals(7, annotation.entryRevision)
            assertEquals(1, annotation.lineIndex)
            assertEquals("Wind shakes leaves.", annotation.originalLine)
            assertNull("An explanation is valid without an invented count", annotation.proposedCount)
            assertEquals("soft.", annotation.pronunciation)
            assertEquals("", annotation.replacementLine)
            assertTrue(annotation.id.isNotBlank())
            assertTrue(annotation.createdAt in startedAt..System.currentTimeMillis())
        }

        action("syllable-editor", "count")
        compose.onNodeWithTag("syllable-editor-keyboard-status").assertDoesNotExist()
        keyboardNode("a/A").assertDoesNotExist()
        keyboardNode(".").assertDoesNotExist()
        key("0"); key("END")
        press("SAVE")
        compose.runOnIdle {
            val annotation = requireNotNull(latest.get())
            assertEquals(2, saves.get())
            assertEquals(0, annotation.proposedCount)
            assertEquals(first.get()!!.id, annotation.id)
            assertEquals(first.get()!!.createdAt, annotation.createdAt)
            assertEquals("Wind shakes leaves.", annotation.originalLine)
            assertEquals(7, annotation.entryRevision)
            assertEquals("Annotations never overwrite source lines or advance the source revision", originalSnapshot, source)
        }
    }

    private fun pokedex(): PokedexViewModel {
        lateinit var model: PokedexViewModel
        compose.runOnIdle {
            model = ViewModelProvider(compose.activity)[PokedexViewModel::class.java]
            modelCreated = true
        }
        return model
    }

    private fun capture(name: String) {
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name).outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private fun action(prefix: String, id: String) {
        compose.onNodeWithTag("$prefix-actions").performScrollToNode(hasTestTag("$prefix-action:$id"))
        compose.onNodeWithTag("$prefix-action:$id").performTouchInput { click() }
        compose.waitForIdle()
    }

    private fun keyboardNode(value: String): SemanticsNodeInteraction {
        val label = when (value) { "SPACE" -> "_"; "DELETE" -> "⌫"; else -> value }
        return compose.onNode(hasText(label, substring = false) and
            hasAnyAncestor(hasTestTag("Terminal key grid")) and
            !hasAnyAncestor(hasContentDescription("End typing")), useUnmergedTree = true)
    }

    private fun key(value: String) {
        if (value == "END") compose.onNodeWithContentDescription("End typing").performTouchInput { click() }
        else keyboardNode(value).assertIsDisplayed().performTouchInput { click() }
        compose.waitForIdle()
    }

    private fun press(label: String) {
        val target = if (label in listOf("Up", "Down", "Left", "Right"))
            compose.onNodeWithContentDescription(label) else compose.onNodeWithText(label)
        target.performTouchInput { click() }
        compose.waitForIdle()
    }
}
