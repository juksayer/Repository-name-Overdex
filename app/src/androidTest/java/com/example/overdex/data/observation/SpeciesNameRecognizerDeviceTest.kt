package com.example.overdex.data.observation

import android.graphics.BitmapFactory
import com.example.overdex.battle.observation.SpeciesTextResolver
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Exercises the production candidate pipeline against a crop preserved from a real battle. */
class SpeciesNameRecognizerDeviceTest {
    @Test
    fun resolvesPreservedBadgeCropsForCameruptSealeoAndVaporeon() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().context
        listOf(
            "camerupt_badge.png" to "Camerupt",
            "sealeo_badge.png" to "Sealeo",
            "vaporeon_badge.png" to "Vaporeon"
        ).forEach { (asset, speciesName) ->
            val bitmap = context.assets.open("species/$asset").use(BitmapFactory::decodeStream)
                ?: error("Fixture $asset could not be decoded")
            try {
                val candidates = SpeciesNameRecognizer.recognizeCandidates(bitmap)
                val padded = candidates.firstOrNull { it.treatment == "THRESHOLD_190_PADDED" }
                assertNotNull("Missing padded fallback for $asset", padded)
                assertEquals(
                    speciesName,
                    SpeciesTextResolver.resolve(padded!!.text, setOf(speciesName))
                )
            } finally {
                bitmap.recycle()
            }
        }
    }

}
