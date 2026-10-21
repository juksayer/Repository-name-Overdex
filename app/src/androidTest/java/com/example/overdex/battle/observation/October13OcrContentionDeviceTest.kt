package com.example.overdex.battle.observation

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.data.observation.*
import kotlinx.coroutines.*
import org.junit.Assert.assertEquals
import org.junit.Test

/** Actual Oct 13 badge/result pixels competing with the real button readers. */
class October13OcrContentionDeviceTest {
    @Test fun recordedSpeciesAndResultRemainReadableDuringCompetingTextChecks() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun bitmap(path: String) = instrumentation.context.assets.open(path).use(BitmapFactory::decodeStream)!!
        val species = bitmap("battle/oct13/species.png")
        val result = bitmap("battle/oct13/result.png")
        val left = bitmap("player_controls/left-label.png")
        val right = bitmap("player_controls/right-label.png")
        val pointer = bitmap("player_controls/pointer-down.png")
        val workers = mutableListOf<Job>()
        try {
            SpeciesNameRecognizer.warmUp()
            OutcomeTextRecognizer.warmUp()
            SharedLatinTextRecognizer.warmUp()
            PlayerControlTextRecognizer.read(pointer)
            listOf(left, right, pointer).forEach { input ->
                workers += launch(Dispatchers.Default) { repeat(12) { PlayerControlTextRecognizer.read(input) } }
            }
            workers += launch(Dispatchers.Default) { repeat(12) { SharedLatinTextRecognizer.readText(left) } }
            workers += launch(Dispatchers.Default) { repeat(12) { SharedLatinTextRecognizer.readText(right) } }
            val speciesMillis = mutableListOf<Long>()
            val resultMillis = mutableListOf<Long>()
            withTimeout(90_000) {
                coroutineScope {
                    launch(Dispatchers.Default) {
                        repeat(8) {
                            val start = System.nanoTime()
                            val readings = SpeciesNameRecognizer.recognizeCandidates(species) { it.trim() == "Sneasel" }
                            speciesMillis += (System.nanoTime() - start) / 1_000_000
                            assertEquals("Sneasel", readings.first().text.trim())
                            delay(100)
                        }
                    }
                    launch(Dispatchers.Default) {
                        repeat(8) {
                            val start = System.nanoTime()
                            val text = OutcomeTextRecognizer.readText(result)
                            resultMillis += (System.nanoTime() - start) / 1_000_000
                            assertEquals(text, MatchOutcomePhrase.WIN, MatchOutcomeTextResolver.resolve(text))
                            delay(100)
                        }
                    }
                }
            }
            instrumentation.sendStatus(0, Bundle().apply {
                putString("species_ocr_ms", speciesMillis.joinToString())
                putString("result_ocr_ms", resultMillis.joinToString())
            })
        } finally {
            workers.forEach { it.cancelAndJoin() }
            listOf(species, result, left, right, pointer).forEach { it.recycle() }
        }
    }
}
