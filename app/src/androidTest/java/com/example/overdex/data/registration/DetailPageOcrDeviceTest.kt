package com.example.overdex.data.registration

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.data.GameMasterLoader
import com.example.overdex.data.observation.SharedLatinTextRecognizer
import com.example.overdex.model.Pokemon
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Real GO screenshot -> production OCR -> semantic fields at the service's capture scale. */
class DetailPageOcrDeviceTest {
    @Test fun ceruledgeDetailReadsNameCpCandyTypesAndVisibleMoves() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val reference = GameMasterLoader(instrumentation.targetContext)
        val catalogue = buildRegistrationCatalogue(reference.allPokemon().distinctBy { it.dex }.map {
            Pokemon(it.dex, it.speciesName.substringBefore(" ("), emptyList(), "", fastMoves = emptyList(), chargedMoves = emptyList())
        }, reference.allPokemon(), reference::getMove)
        val source = instrumentation.context.assets.open("registration/ceruledge-detail.png").use(BitmapFactory::decodeStream)
        val capture = Bitmap.createScaledBitmap(source, 672, 1493, true)
        val prepared = Bitmap.createScaledBitmap(capture, 1008, 2240, true)
        try {
            val text = SharedLatinTextRecognizer.read(prepared)
            val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { line -> line.boundingBox?.let { b ->
                DetailTextLine(line.text, b.left.toFloat() / prepared.width, b.top.toFloat() / prepared.height,
                    b.right.toFloat() / prepared.width, b.bottom.toFloat() / prepared.height)
            } }
            val read = DetailPageReader(catalogue).read(lines)
            android.util.Log.i("REGISTRATION_TEST", "lines=${lines.map { it.text }}")
            android.util.Log.i("REGISTRATION_TEST", "fields=${read.fields.map { it.field to it.value }}")
            assertTrue(read.isDetailPage)
            assertEquals("2486", read.fields.singleOrNull { it.field == "CP" }?.value)
            assertEquals("Ceruledge", read.fields.singleOrNull { it.field == "TITLE" }?.value)
            assertEquals(setOf("FIRE", "GHOST"), read.fields.filter { it.field == "TYPE" }.map { it.value }.toSet())
            assertEquals("Charcadet", read.fields.singleOrNull { it.field == "CANDY" }?.value)
            assertEquals("Incinerate", read.fields.singleOrNull { it.field == "FAST" }?.value)
            assertEquals("Shadow Ball", read.fields.singleOrNull { it.field == "CHARGED" }?.value)
            val draft = RegistrationDraft().accept(read, 1000, capture.width, capture.height).accept(read, 1700, capture.width, capture.height)
            assertEquals(937, draft.resolve(catalogue).species?.id)
            // The second charged move is below the viewport. Never save a premature one-move card.
            assertFalse(draft.resolve(catalogue).ready)
        } finally { prepared.recycle(); capture.recycle(); source.recycle() }
    }
}
