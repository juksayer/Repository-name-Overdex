package com.example.overdex.battle.observation

import android.graphics.BitmapFactory
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.data.BattleCalibration
import com.example.overdex.data.observation.OcrField
import com.example.overdex.data.observation.OcrHeightProfile
import com.example.overdex.data.observation.OcrOverlayPolicy
import com.example.overdex.data.observation.OcrPriority
import com.example.overdex.data.observation.OcrSourceContext
import com.example.overdex.data.observation.OcrWordGeometry
import com.example.overdex.data.observation.SharedLatinTextRecognizer
import com.example.overdex.model.observation.OcrTextEvidence
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/** Real bundled screenshot pixels -> default calibrated strip -> native shared OCR -> phrase policy. */
class ForcedReplacementMessageDeviceTest {
    @Test fun defaultMoondropPromptCropRecognizesTheActualReplacementMessage() = runBlocking {
        requireEmulator()
        val reading = readBundled(PROMPT)
        save(PROMPT, listOf(reading))
        assertTrue("Native OCR must retain actual words: ${reading.rawText}", reading.words.isNotEmpty())
        assertTrue("Native word geometry is required", reading.words.any { it.bounds != null })
        assertEquals("Without an overlay the native text stays unchanged", reading.rawText, reading.acceptedText)
        val resolution = ForcedReplacementPhrasePolicy().observe(reading.acceptedText, 1_000_000_000L,
            listOf("bundled:$PROMPT"))
        assertNotNull("Default prompt crop did not recognize the real text: ${reading.rawText}", resolution)
        assertEquals(listOf("bundled:$PROMPT"), resolution!!.evidenceReferences)
    }

    @Test fun ordinaryBattleAndShieldChoiceNeverBecomeReplacementPromptsEvenWhenRepeated() = runBlocking {
        requireEmulator()
        for (filename in listOf(SHIELD_CHOICE, ORDINARY_BATTLE)) {
            // Two real native reads exercise even the repeated near-phrase path;
            // rejection must not depend on the ordinary area simply being blank.
            val readings = listOf(readBundled(filename), readBundled(filename))
            save(filename, readings)
            if (filename == SHIELD_CHOICE) assertTrue(
                "The competing shield-choice text must actually be read: ${readings.map { it.rawText }}",
                readings.any { it.rawText.contains("Shield", ignoreCase = true) })
            val policy = ForcedReplacementPhrasePolicy()
            readings.forEachIndexed { index, reading ->
                assertNull("Incorrect replacement prompt from $filename: ${reading.rawText}",
                    policy.observe(reading.acceptedText, 1_000_000_000L + index * 500_000_000L,
                        listOf("bundled:$filename:$index")))
            }
        }
    }

    private suspend fun readBundled(filename: String): OcrTextEvidence {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = requireNotNull(context.assets.open("battle_samples/$filename").use(BitmapFactory::decodeStream))
        try {
            assertEquals("Native Moondrop source width", 1080, source.width)
            assertEquals("Native Moondrop source height", 2400, source.height)
            val calibration = BattleCalibration()
            val contract = BattleCropContracts.forcedReplacementMessage
            val crop = requireNotNull(contract.resolve(calibration, source))
            try {
                assertEquals("ForcedReplacementMessageCrop", crop.provenance.cropName)
                // Use the same single native pass, shared queue priority, word
                // geometry and source filtering as ArtifactForcedReplacementCropReader.
                return SharedLatinTextRecognizer.withReadingTurn(OcrPriority.NORMAL) { read ->
                    OcrWordGeometry.read(crop.bitmap, crop.bitmap, 0, "RAW",
                        OcrSourceContext(source.width, overlayPolicy = OcrOverlayPolicy.SMALL_WORDS),
                        OcrHeightProfile(OcrField.ANNOUNCEMENT), read = read)
                }
            } finally { crop.bitmap.recycle() }
        } finally { source.recycle() }
    }

    private fun save(filename: String, readings: List<OcrTextEvidence>) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "forced-replacement-native-${filename.removeSuffix(".jpg")}.json").writeText(
            Json { prettyPrint = true }.encodeToString(ListSerializer(OcrTextEvidence.serializer()), readings))
    }

    private fun requireEmulator() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish") || Build.FINGERPRINT.contains("generic") ||
            Build.MODEL.contains("Emulator")) {
            "Recorded replacement-message checks must use a disposable emulator, never a user phone"
        }
    }

    private companion object {
        const val PROMPT = "moondrop-20261110-14-switchinanewpokemon.jpg"
        const val SHIELD_CHOICE = "moondrop-20261110-13-protectshieldtimer.jpg"
        const val ORDINARY_BATTLE = "moondrop-20261110-15-teaminfoexithpinactivechargemoves.jpg"
    }
}
