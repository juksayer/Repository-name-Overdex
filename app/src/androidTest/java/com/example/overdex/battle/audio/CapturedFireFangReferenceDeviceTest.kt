package com.example.overdex.battle.audio

import androidx.test.platform.app.InstrumentationRegistry
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Checks packaged references against held-out PCM; it does not certify live attack counts. */
class CapturedFireFangReferenceDeviceTest {
    @Test fun actualLoaderComparesFireFangAndThunderFangWithoutDroppingLegalAlternatives() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets
        val samples = assets.open("fast_move_20261108/manifest.json").bufferedReader().use {
            JSONObject(it.readText()).getJSONArray("samples")
        }
        val matcher = FastMoveSoundReferences.load(instrumentation.targetContext)
        var fireFangComparisons = 0
        var thunderFangComparisons = 0
        var rejectedComparisons = 0
        for (index in 0 until samples.length()) {
            val sample = samples.getJSONObject(index)
            val id = sample.getString("id")
            val bytes = assets.open("fast_move_20261108/${sample.getString("file")}").use { it.readBytes() }
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            assertEquals("$id source bytes", sample.getJSONObject("derived").getString("sha256"), digest)
            val names = sample.getJSONArray("legalMoves")
            val legal = (0 until names.length()).map { names.getString(it) }.toSet()
            val ranked = matcher.measure(requireNotNull(WavPcm16.decode(bytes)), legal)
            assertEquals("$id must compare every legal alternative", legal, ranked.map { it.moveName }.toSet())
            val expected = when (id) {
                "fire-fang-distinct-attack", "fire-fang-separate-battle" -> "Fire Fang"
                "arcanine-first", "arcanine-distinct" -> "Thunder Fang"
                "arcanine-overlap", "arcanine-weak", "arcanine-qte-adjacent" -> null
                else -> error("Unreviewed fixture: $id")
            }
            assertEquals("$id: $ranked", expected != null, FastMoveReferenceMatcher.accepts(ranked))
            if (expected != null) assertEquals(id, expected, ranked.first().moveName)
            when (expected) {
                "Fire Fang" -> fireFangComparisons++
                "Thunder Fang" -> thunderFangComparisons++
                else -> rejectedComparisons++
            }
        }
        assertEquals(2, fireFangComparisons)
        assertEquals(2, thunderFangComparisons)
        assertEquals(3, rejectedComparisons)
        // The overlapping query also contains a strong opponent fit, so the
        // global reader withholds a decision. Side support and duplicate guards
        // remain the responsibility of source-time inference, checked by the JVM test.
    }
}
