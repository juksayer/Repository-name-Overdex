package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.data.GameMasterLoader
import com.example.overdex.data.PokemonJsonLoader
import com.example.overdex.data.observation.SpeciesNameRecognizer
import com.example.overdex.model.AnchorRegion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real MOONDROP frames/crops, not rendered text. No database or calibration writes. */
class October10RecordedBattleDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun image(name: String): Bitmap = instrumentation.context.assets
        .open("species/oct10/$name.png").use(BitmapFactory::decodeStream)!!

    @Test fun alignedBadgeCropsReadBothSidesAndAllThreeOpponentSpecies() = runBlocking {
        val target = instrumentation.targetContext
        val names = GameMasterLoader(target).allPokemonNames() + PokemonJsonLoader(target).loadPokemon().pokemon.map { it.name }
        SpeciesNameRecognizer.warmUp()
        suspend fun check(bitmap: Bitmap, expected: String) {
            val started = System.nanoTime()
            val reads = SpeciesNameRecognizer.recognizeCandidates(bitmap) { FastOverlaySpeciesResolver.resolve(it, names) != null }
            val resolved = reads.firstNotNullOfOrNull { FastOverlaySpeciesResolver.resolve(it.text, names) }
            Log.i("Oct10RecordedBattle", "$expected resolved=$resolved ms=${(System.nanoTime() - started) / 1_000_000} reads=$reads")
            assertEquals(expected, resolved)
        }
        val frame = image("lead_frame")
        try {
            for ((left, right, expected) in listOf(Triple(22, 178, "Raichu"), Triple(493, 650, "Sneasel"))) {
                val region = AnchorRegion(left / 672f, 96 / 1493f, (right - left) / 672f, 32 / 1493f)
                val crop = BattleCropResolver.resolve("RecordedSpecies", region, frame, 16)!!
                try { check(crop.bitmap, expected) } finally { crop.bitmap.recycle() }
            }
        } finally { frame.recycle() }
        for ((file, expected) in listOf("sealeo" to "Sealeo", "vaporeon" to "Vaporeon", "obstagoon" to "Obstagoon")) {
            val bitmap = image(file)
            try { check(bitmap, expected) } finally { bitmap.recycle() }
        }
    }

    @Test fun alignedPokeBallsShowThreeThenTwoThenOne() {
        val frame = image("lead_frame")
        val balls = Bitmap.createBitmap(frame, 546, 128, 105, 32)
        try { assertEquals(3, OpponentBattleResourceCounter.pokeBallCount(balls)) }
        finally { balls.recycle(); frame.recycle() }
        for ((file, expected) in listOf("sealeo_balls" to 2, "vaporeon_balls" to 1)) {
            val bitmap = image(file)
            try { assertEquals(expected, OpponentBattleResourceCounter.pokeBallCount(bitmap)) }
            finally { bitmap.recycle() }
        }
    }

    @Test fun completeEntryAndGoCropsPreserveTheirText() = runBlocking {
        val entry = image("entry")
        try {
            val reads = AnnouncementRecognizer.recognizeCandidates(entry)
            Log.i("Oct10RecordedBattle", "entry=$reads")
            assertTrue(reads.any { it.contains("Go", true) && it.contains("Sealeo", true) })
        } finally { entry.recycle() }
        CountdownGlyphMatcher.initialize(instrumentation.targetContext)
        val go = image("go")
        try { assertEquals("GO", CountdownGlyphMatcher.match(go).candidate) }
        finally { go.recycle() }
    }

    @Test fun recordedResultIsReadableOnceTheGateOpens() = runBlocking {
        val bitmap = image("result")
        try {
            val text = com.example.overdex.data.observation.OutcomeTextRecognizer.readText(bitmap)
            Log.i("Oct10RecordedBattle", "result=$text")
            assertEquals(MatchOutcomePhrase.WIN, MatchOutcomeTextResolver.resolve(text))
        } finally { bitmap.recycle() }
    }
}
