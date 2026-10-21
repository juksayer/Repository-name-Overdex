package com.example.overdex.data.observation

import com.example.overdex.battle.observation.BattleCropBounds
import com.example.overdex.battle.observation.BattleCropProvenance
import com.example.overdex.battle.observation.overlayBounds
import com.example.overdex.model.AnchorRegion
import com.example.overdex.model.observation.OcrBox
import com.example.overdex.model.observation.OcrWordEvidence
import org.junit.Assert.*
import org.junit.Test

class OcrSourceFilterTest {
    private val hud = OcrBox(0f, 0f, 150f, 150f)
    private val context = OcrSourceContext(672, hud)
    private val profile = OcrHeightProfile(OcrField.ANNOUNCEMENT)
    private val ownWord = OcrWordEvidence("Foul", 0, OcrBox(10f, 10f, 50f, 23f), violetFraction = .20f)
    private fun filter(word: OcrWordEvidence, source: OcrSourceContext? = context) =
        OcrSourceFilter.apply(word.text, listOf(word), "RAW", source, profile)

    @Test fun `rejecting a word requires size color and captured HUD position together`() {
        assertEquals("", filter(ownWord).acceptedText)
        assertEquals("Foul", filter(ownWord.copy(violetFraction = 0f)).acceptedText)
        assertEquals("Foul", filter(ownWord.copy(bounds = OcrBox(10f, 10f, 90f, 38f))).acceptedText)
        assertEquals("Foul", filter(ownWord.copy(bounds = OcrBox(200f, 10f, 240f, 23f))).acceptedText)
        assertEquals("Foul", filter(ownWord, null).acceptedText)
        assertEquals("Foul", filter(ownWord, context.copy(overlayBounds = null)).acceptedText)
        assertEquals("Foul", filter(ownWord, context.copy(frameWidth = 0)).acceptedText)
        assertEquals("Foul", filter(ownWord.copy(bounds = null)).acceptedText)
    }

    @Test fun `the same physical lettering is classified equally at both capture resolutions`() {
        val scale = 1080f / 672f
        fun OcrBox.scaled() = OcrBox(left * scale, top * scale, right * scale, bottom * scale)
        val native = filter(ownWord.copy(bounds = ownWord.bounds!!.scaled()), OcrSourceContext(1080, hud.scaled()))
        assertEquals(filter(ownWord).words.single().decision, native.words.single().decision)
        assertEquals("", native.acceptedText)
    }

    @Test fun `teal species labels and red danger text are also HUD evidence`() {
        assertEquals("", filter(ownWord.copy(violetFraction = 0f, tealFraction = .2f)).acceptedText)
        assertEquals("", filter(ownWord.copy(violetFraction = 0f, dangerRedFraction = .2f)).acceptedText)
        // A game-sized word remains available even through similarly colored pixels.
        assertEquals("Foul", filter(ownWord.copy(bounds = OcrBox(10f, 10f, 90f, 38f), tealFraction = .3f)).acceptedText)
    }

    @Test fun `rejected HUD text stays in the evidence and missing game letters are not filled in`() {
        val fragment = OcrWordEvidence("le!", 1, OcrBox(100f, 60f, 130f, 90f))
        val result = OcrSourceFilter.apply("Foul\nle!", listOf(ownWord, fragment), "RAW", context, profile)
        assertEquals("Foul\nle!", result.rawText)
        assertEquals("le!", result.acceptedText)
        assertEquals("HUD_SIZE_COLOR_POSITION", result.words.first().decision)
        assertEquals(ownWord.bounds, result.words.first().bounds)
        assertEquals(.2f, result.words.first().violetFraction, 0f)
        assertEquals("RETAINED", result.words.last().decision)
    }

    @Test fun `unfiltered text retains ML Kit spacing and line breaks`() {
        val raw = "Go,  Pikachu!\n"
        assertEquals(raw, OcrSourceFilter.apply(raw, listOf(ownWord), "RAW", null, profile).acceptedText)
    }

    @Test fun `profiles learn only clean confirmed game sized text`() {
        val clear = context.copy(overlayBounds = null)
        val gameWord = ownWord.copy(text = "Pikachu!", bounds = OcrBox(0f, 0f, 95f, 26f), violetFraction = 0f)
        val reading = filter(gameWord, clear)
        profile.learn(reading, clear, confirmed = false)
        profile.learn(reading.copy(treatment = "THRESHOLD_190_PADDED"), clear, confirmed = true)
        profile.learn(reading, context, confirmed = true)
        profile.learn(filter(ownWord), clear, confirmed = true)
        profile.learn(filter(gameWord.copy(bounds = OcrBox(0f, 0f, 95f, 100f)), clear), clear, confirmed = true)
        assertEquals(0, profile.sampleCount)
        profile.learn(reading, clear, confirmed = true)
        assertEquals(1, profile.sampleCount)
        assertEquals(26f, profile.expectedHeightAt672, .01f)
        repeat(15) { profile.learn(reading, clear, confirmed = true) }
        assertEquals(7, profile.sampleCount)
    }

    @Test fun `each field and side can keep an independent height profile`() {
        val species = OcrHeightProfile(OcrField.SPECIES)
        val clear = OcrSourceContext(672)
        val word = ownWord.copy(bounds = OcrBox(0f, 0f, 100f, 23f), violetFraction = 0f)
        species.learn(filter(word, clear), clear, true)
        assertEquals(23f, species.expectedHeightAt672, .01f)
        assertEquals(28f, profile.expectedHeightAt672, .01f)
        assertEquals(0, profile.sampleCount)
    }

    @Test fun `HUD placement is translated into original crop coordinates without shifting the crop`() {
        val crop = BattleCropProvenance("announcement", 1080, 2400, BattleCropBounds(100, 700, 1000, 850))
        val captureHud = AnchorRegion(.5f, .25f, .4f, .2f)
        val saved = crop.overlayBounds(captureHud)!!
        assertEquals(440f, saved.left, .01f)
        assertEquals(-100f, saved.top, .01f)
        assertEquals(380f, saved.bottom, .01f)
        assertNull(crop.overlayBounds(AnchorRegion(.5f, .5f, .4f, .2f)))
        assertEquals(440f, saved.left, .01f)
        assertEquals(BattleCropBounds(100, 700, 1000, 850), crop.bounds)
    }
}
