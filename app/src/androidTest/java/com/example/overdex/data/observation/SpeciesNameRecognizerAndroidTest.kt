package com.example.overdex.data.observation

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Guards the two-pass OCR ownership boundary used by live and archive replay. */
@RunWith(AndroidJUnit4::class)
class SpeciesNameRecognizerAndroidTest {
    @Test
    fun recognition_keeps_the_source_badge_available_for_the_threshold_pass() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().context
        val badge = context.assets.open("species/camerupt_badge.png").use(BitmapFactory::decodeStream)
        requireNotNull(badge)
        try {
            val candidates = SpeciesNameRecognizer.recognizeCandidates(badge)

            assertFalse("recognition must not recycle the preserved source crop", badge.isRecycled)
            assertTrue("real badge should produce an OCR candidate", candidates.isNotEmpty())
            // A second invocation reproduces the old crash if the first pass
            // accidentally recycled the source bitmap.
            SpeciesNameRecognizer.recognizeCandidates(badge)
            assertFalse(badge.isRecycled)
        } finally {
            if (!badge.isRecycled) badge.recycle()
        }
    }
    @Test
    fun recorded_badges_resolve_twice_and_report_species_latency() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().context
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val names = com.example.overdex.data.GameMasterLoader(target).allPokemonNames() +
            com.example.overdex.data.PokemonJsonLoader(target).loadPokemon().pokemon.map { it.name }
        for (name in listOf("Camerupt", "Sealeo", "Vaporeon")) {
            val bitmap = context.assets.open("species/${name.lowercase()}_badge.png").use(BitmapFactory::decodeStream)!!
            try {
                val started = System.nanoTime()
                repeat(2) {
                    val reads = SpeciesNameRecognizer.recognizeCandidates(bitmap) {
                        com.example.overdex.battle.observation.SpeciesTextResolver.resolve(it, names) != null
                    }
                    val resolved = reads.firstNotNullOfOrNull {
                        com.example.overdex.battle.observation.SpeciesTextResolver.resolve(it.text, names)
                    }
                    org.junit.Assert.assertEquals(name, resolved)
                }
                android.util.Log.i("SPECIES_LATENCY_TEST", "$name two-reads-ms=${(System.nanoTime() - started) / 1_000_000}")
            } finally { bitmap.recycle() }
        }
    }

    @Test
    fun both_sides_share_OCR_without_losing_badge_identity() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val names = com.example.overdex.data.GameMasterLoader(instrumentation.targetContext).allPokemonNames() +
            com.example.overdex.data.PokemonJsonLoader(instrumentation.targetContext).loadPokemon().pokemon.map { it.name }
        val warmingAt = System.nanoTime()
        SpeciesNameRecognizer.warmUp()
        android.util.Log.i("SPECIES_LATENCY_TEST", "deployment-warmup-ms=${(System.nanoTime() - warmingAt) / 1_000_000}")
        kotlinx.coroutines.coroutineScope {
            listOf("Camerupt", "Sealeo").map { name ->
                async(kotlinx.coroutines.Dispatchers.Default) {
                    val bitmap = instrumentation.context.assets.open("species/${name.lowercase()}_badge.png").use(BitmapFactory::decodeStream)!!
                    try {
                        val started = System.nanoTime()
                        repeat(2) {
                            val reads = SpeciesNameRecognizer.recognizeCandidates(bitmap) {
                                com.example.overdex.battle.observation.SpeciesTextResolver.resolve(it, names) != null
                            }
                            val resolved = reads.firstNotNullOfOrNull {
                                com.example.overdex.battle.observation.SpeciesTextResolver.resolve(it.text, names)
                            }
                            org.junit.Assert.assertEquals(name, resolved)
                        }
                        android.util.Log.i("SPECIES_LATENCY_TEST", "concurrent $name two-reads-ms=${(System.nanoTime() - started) / 1_000_000} catalogue=${names.size}")
                    } finally { bitmap.recycle() }
                }
            }.forEach { it.await() }
        }
    }

}
