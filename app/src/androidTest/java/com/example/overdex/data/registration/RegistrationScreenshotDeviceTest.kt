package com.example.overdex.data.registration

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.CaptureTemplateManager
import com.example.overdex.data.GameMasterLoader
import com.example.overdex.model.Pokemon
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RegistrationScreenshotDeviceTest {
    @Test fun screenshotTemplateReadsFeedTheSameDraftWithoutDuplicatingFrameEvidence() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefsName = "test_screenshot_${UUID.randomUUID()}"
        val source = instrumentation.context.assets.open("registration/ceruledge-detail.png").use(BitmapFactory::decodeStream)
        try {
            val reference = GameMasterLoader(context)
            val catalogue = buildRegistrationCatalogue(reference.allPokemon().distinctBy { it.dex }.map {
                Pokemon(it.dex, it.speciesName.substringBefore(" ("), emptyList(), "", fastMoves = emptyList(), chargedMoves = emptyList())
            }, reference.allPokemon(), reference::getMove)
            val template = CaptureTemplateManager(context, prefsName).getPokemonDetailTemplate()
            val read = DetailPageReader(catalogue).read(RegistrationScreenshotReader.read(source, template))
            assertTrue(read.isDetailPage)
            assertEquals(setOf("2486"), read.fields.filter { it.field == "CP" }.map { it.value }.toSet())
            assertTrue(read.fields.any { it.field == "TITLE" && it.value == "Ceruledge" })
            // A cropped name is enough when the user explicitly selects the Name box.
            // CP, HP and all other anchors are outside this crop and must not leak in.
            val title = read.fields.first { it.field == "TITLE" }.line
            val nameBox = com.example.overdex.model.CaptureRegion("SpeciesName", title.left - .01f, title.top - .008f,
                title.right - title.left + .02f, title.bottom - title.top + .016f, com.example.overdex.model.ObservationType.OCR_TEXT)
            val fieldLines = RegistrationScreenshotReader.readField(source, nameBox)
            val isolated = RegistrationFieldReader(catalogue).read(nameBox.id, fieldLines)
            assertEquals(listOf("TITLE"), isolated.fields.map { it.field })
            assertEquals("Ceruledge", isolated.fields.single().value)
            assertTrue(fieldLines.none { it.text.contains("CP", ignoreCase = true) })
            val reviewed = RegistrationDraft().accept(read, 1000, source.width, source.height, "photo-a", confirmedByUser = true)
            assertEquals(937, reviewed.resolve(catalogue).species?.id)
            assertFalse(reviewed.resolve(catalogue).ready) // The complete moves are not yet visible.
            assertEquals(reviewed.evidence, reviewed.accept(read, 2000, source.width, source.height, "photo-a", confirmedByUser = true).evidence)
        } finally { source.recycle(); context.deleteSharedPreferences(prefsName) }
    }
    @Test fun legacyFastMoveAliasLoadsWithoutReplacingCalibratedFastMoveRow() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-template-alias-${UUID.randomUUID()}"
        try {
            val prefs = context.getSharedPreferences(name, android.content.Context.MODE_PRIVATE)
            val legacy = "PokemonGoSummaryTemplate_SummaryFastMove"
            prefs.edit().putFloat("${legacy}_x", .12f).putFloat("${legacy}_y", .42f)
                .putFloat("${legacy}_w", .75f).putFloat("${legacy}_h", .04f).commit()
            val manager = CaptureTemplateManager(context, name)
            val migrated = manager.getPokemonDetailTemplate().regions.first { it.id == "FastMoveRow" }
            assertEquals(.42f, migrated.y, .0001f)
            assertFalse(manager.getPokemonDetailTemplate().regions.any { it.id == "SummaryFastMove" })
            manager.saveAdjustment("PokemonGoDetailTemplate", migrated.copy(y = .81f))
            assertEquals(.81f, CaptureTemplateManager(context, name).getPokemonDetailTemplate().regions.first { it.id == "FastMoveRow" }.y, .0001f)
            assertEquals(.42f, prefs.getFloat("${legacy}_y", -1f), .0001f)
        } finally { context.deleteSharedPreferences(name) }
    }

}
