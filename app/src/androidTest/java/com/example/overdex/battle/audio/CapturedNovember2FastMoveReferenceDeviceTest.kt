package com.example.overdex.battle.audio

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

/** Exercises the actual Android reference loader with independent attack windows and quiet audio. */
class CapturedNovember2FastMoveReferenceDeviceTest {
    @Test fun actualLoaderRetainsLegalMatchesAndWithholdsAmbiguousOrQuietAudio() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets
        val entries = assets.open("fast_move_20261102/manifest.json").bufferedReader().use {
            JSONObject(it.readText()).getJSONArray("samples")
        }
        val matcher = FastMoveSoundReferences.load(instrumentation.targetContext)
        var acceptedCount = 0
        for (index in 0 until entries.length()) {
            val entry = entries.getJSONObject(index)
            val id = entry.getString("id")
            val bytes = assets.open("fast_move_20261102/${entry.getString("file")}").use { it.readBytes() }
            val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                (it.toInt() and 0xff).toString(16).padStart(2, '0')
            }
            assertEquals("$id fixture bytes", entry.getJSONObject("derived").getString("sha256"), sha)
            val audio = requireNotNull(WavPcm16.decode(bytes))
            val names = entry.getJSONArray("legalMoves")
            val legal = (0 until names.length()).map { names.getString(it) }.toSet()
            val started = System.nanoTime()
            val ranked = matcher.measure(audio, legal)
            val elapsed = (System.nanoTime() - started) / 1_000_000
            val expected = if (entry.isNull("expectedMove")) null else entry.getString("expectedMove")
            val accepted = FastMoveReferenceMatcher.accepts(ranked)
            assertTrue("$id scored an impossible move", ranked.all { it.moveName in legal })
            assertEquals("$id: $ranked", expected != null, accepted)
            if (expected != null) { assertEquals(id, expected, ranked.first().moveName); acceptedCount++ }
            instrumentation.sendStatus(0, Bundle().apply {
                putString("captured_november2", "$id accepted=$accepted top=${ranked.firstOrNull()?.moveName} ms=$elapsed")
            })
        }
        assertTrue("No positive comparison exercised", acceptedCount > 0)
        assertTrue("No rejection comparison exercised", acceptedCount < entries.length())
    }
}
