package com.example.overdex.battle.observation

import com.example.overdex.model.observation.OcrBox
import com.example.overdex.model.observation.OcrTextEvidence
import com.example.overdex.model.observation.OcrWordEvidence
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.*
import org.junit.Test

class PartialOutcomeTextResolverTest {
    private val provenance = BattleCropProvenance("MatchOutcomeCrop", 672, 1493, BattleCropBounds(100, 641, 564, 858))

    @Test fun `recorded clipped result labels resolve and every retained unrelated real reading stays unknown`() {
        val json = javaClass.getResourceAsStream("/battle/outcome/nov11-moondrop-result-ocr.json")!!
            .bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
        val cases = json.getValue("cases").jsonArray
        var positive = 0
        var negative = 0
        for (case in cases) {
            val row = case.jsonObject
            val id = row.getValue("id").jsonPrimitive.content
            val source = row.getValue("source").jsonObject
            fun number(name: String) = source.getValue(name).jsonPrimitive.int
            val captured = BattleCropProvenance(source.getValue("cropName").jsonPrimitive.content,
                number("sourceWidth"), number("sourceHeight"), BattleCropBounds(
                    number("cropLeft"), number("cropTop"), number("cropRight"), number("cropBottom")))
            val reading = Json.decodeFromJsonElement<OcrTextEvidence>(row.getValue("ocr"))
            val expected = row.getValue("expected").jsonPrimitive.contentOrNull
            val decision = PartialOutcomeTextResolver.resolve(reading, captured)
            assertEquals(id, expected, decision?.phrase?.name)
            if (expected != null) {
                positive++
                assertFalse(id, decision!!.needsConfirmation)
                assertEquals(id, PartialOutcomeTextResolver.Basis.PARTIAL_EXPECTED_LABEL_WITH_RESULT_GEOMETRY, decision.basis)
            } else negative++
        }
        assertEquals(5, positive)
        assertEquals(17, negative)
    }

    @Test fun `whole expected labels allow several absent characters and a wrong or extra glyph`() {
        val expected = mapOf(
            "OOD EFFOR" to MatchOutcomePhrase.LOSS,
            "GOD EFOR" to MatchOutcomePhrase.LOSS,
            "OD EFOR" to MatchOutcomePhrase.LOSS,
            "GOOD EFF0RT!" to MatchOutcomePhrase.LOSS,
            "oU WINy" to MatchOutcomePhrase.WIN,
            "Y WIN" to MatchOutcomePhrase.WIN,
            "YOU WIM!" to MatchOutcomePhrase.WIN,
        )
        for ((text, phrase) in expected) {
            val reading = title(text)
            val original = reading.copy(words = reading.words.toList())
            val decision = PartialOutcomeTextResolver.resolve(reading, provenance)!!
            assertEquals(text, phrase, decision.phrase)
            assertFalse(text, decision.needsConfirmation)
            assertEquals("Source testimony remains verbatim", original, reading)
        }
    }

    @Test fun `ordinary battle words mixed labels and unrelated praise cannot be explained away as a result`() {
        for (text in listOf("SUPER EFFECTIVE!", "SUPER EFFECTIVE! FF", "WATER PULSE W", "GOOD WORK",
                "GOOD WIN", "YOU WIN SOMETHING", "YOU WIN GOOD EFFORT", "Hero Purifier", "CHOOSE YOUR PARTY")) {
            // Even result-sized boxes cannot rescue unrelated whole text.
            assertNull(text, PartialOutcomeTextResolver.resolve(title(text), provenance))
        }
    }

    @Test fun `missing or small geometry cannot promote incomplete labels`() {
        val reading = title("OOD EFFOR")
        assertNull(PartialOutcomeTextResolver.resolve(reading.copy(words = emptyList()), provenance))
        assertNull(PartialOutcomeTextResolver.resolve(reading.copy(words = reading.words.map { it.copy(bounds = null) }), provenance))
        val small = reading.copy(words = reading.words.map { word ->
            word.copy(bounds = word.bounds!!.let { it.copy(bottom = it.top + 25) })
        })
        assertNull(PartialOutcomeTextResolver.resolve(small, provenance))
        assertNull(PartialOutcomeTextResolver.resolve(reading, provenance.copy(sourceWidth = 0)))
        assertNull(PartialOutcomeTextResolver.resolve(title("OOD EFFOR"),
            provenance.copy(bounds = BattleCropBounds(100, 150, 564, 367))))
    }

    @Test fun `both single line and two line result title layouts are accepted`() {
        val twoLines = title("GOOD\nEFFORT!").copy(words = listOf(
            OcrWordEvidence("GOOD", 0, OcrBox(90f, 10f, 330f, 82f)),
            OcrWordEvidence("EFFORT!", 1, OcrBox(45f, 128f, 400f, 200f)),
        ))
        assertEquals(MatchOutcomePhrase.LOSS, PartialOutcomeTextResolver.resolve(twoLines, provenance)?.phrase)
        assertEquals(MatchOutcomePhrase.LOSS, PartialOutcomeTextResolver.resolve(title("GOOD EFFORT!"), provenance)?.phrase)
    }

    @Test fun `isolated distinctive signatures need independent confirmation and their own title positions`() {
        fun fragment(text: String, bounds: OcrBox) = title(text).copy(words = listOf(OcrWordEvidence(text, 0, bounds)))
        val w = fragment("W", OcrBox(248f, 74f, 315f, 146f))
        val ff = fragment("FF", OcrBox(280f, 74f, 350f, 146f))
        val stackedFf = fragment("FF", OcrBox(140f, 144f, 210f, 216f))
        for ((reading, expected) in listOf(w to MatchOutcomePhrase.WIN, ff to MatchOutcomePhrase.LOSS,
                stackedFf to MatchOutcomePhrase.LOSS)) {
            val decision = PartialOutcomeTextResolver.resolve(reading, provenance)!!
            assertEquals(expected, decision.phrase)
            assertTrue(decision.needsConfirmation)
            assertEquals(PartialOutcomeTextResolver.Basis.DISTINCTIVE_RESULT_SIGNATURE_WITH_POSITION, decision.basis)
        }
        assertNull(PartialOutcomeTextResolver.resolve(fragment("W", OcrBox(5f, 74f, 72f, 146f)), provenance))
        assertNull(PartialOutcomeTextResolver.resolve(fragment("FF", OcrBox(5f, 74f, 75f, 146f)), provenance))
        assertNull(PartialOutcomeTextResolver.resolve(fragment("W", OcrBox(248f, 74f, 315f, 99f)), provenance))
        assertNull(PartialOutcomeTextResolver.resolve(fragment("FF", OcrBox(280f, 74f, 350f, 99f)), provenance))
    }

    @Test fun `geometry and signature positions scale with original frame rather than OCR enlargement or crop width`() {
        val reading = title("W").copy(words = listOf(OcrWordEvidence("W", 0, OcrBox(248f, 74f, 315f, 146f))))
        val base = PartialOutcomeTextResolver.resolve(reading, provenance)!!
        fun OcrBox.twice() = OcrBox(left * 2, top * 2, right * 2, bottom * 2)
        val scaled = PartialOutcomeTextResolver.resolve(reading.copy(words = reading.words.map { it.copy(bounds = it.bounds!!.twice()) }),
            BattleCropProvenance("MatchOutcomeCrop", 1344, 2986, BattleCropBounds(200, 1282, 1128, 1716)))!!
        assertEquals(base.phrase, scaled.phrase)
        assertEquals(base.minimumHeightAt672, scaled.minimumHeightAt672, 0f)
        assertEquals(base.bounds.twice(), scaled.bounds)
        assertTrue(scaled.needsConfirmation)
    }

    @Test fun `small OCR crop edge overrun is tolerated but impossible word boxes are refused`() {
        val reading = title("OOD EFFOR").copy(words = listOf(
            OcrWordEvidence("OOD", 0, OcrBox(47f, 63f, 252f, 142f)),
            OcrWordEvidence("EFFOR", 0, OcrBox(251f, 65f, 468f, 144f)),
        ))
        assertEquals(MatchOutcomePhrase.LOSS, PartialOutcomeTextResolver.resolve(reading, provenance)?.phrase)
        assertNull(PartialOutcomeTextResolver.resolve(reading.copy(words = listOf(
            reading.words[0], reading.words[1].copy(bounds = OcrBox(251f, 65f, 900f, 144f)))), provenance))
    }

    private fun title(text: String): OcrTextEvidence {
        val tokens = text.split(Regex("\\s+")).filter(String::isNotBlank)
        val width = 380f / tokens.size.coerceAtLeast(1)
        return OcrTextEvidence("RAW", text, text, tokens.mapIndexed { index, token ->
            OcrWordEvidence(token, 0, OcrBox(40f + index * width, 74f, 40f + (index + 1) * width, 146f))
        }, null, null)
    }
}
