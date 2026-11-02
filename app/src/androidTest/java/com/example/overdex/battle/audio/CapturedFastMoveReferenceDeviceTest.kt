package com.example.overdex.battle.audio

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/** Recorded queries are separate from the bundled references; this is not a live accuracy estimate. */
class CapturedFastMoveReferenceDeviceTest {
    @Test fun independentCapturedEffectsMatchWithinTheLegalPoolAndQuietWindowsAreRejected() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets
        val entries = assets.open("$FIXTURES/manifest.json").bufferedReader().use {
            JSONObject(it.readText()).getJSONArray("samples")
        }
        assertEquals("Expected six held-out effects and two quiet comparisons", 8, entries.length())

        val loadStarted = System.nanoTime()
        val matcher = FastMoveSoundReferences.load(instrumentation.targetContext)
        val loadMillis = (System.nanoTime() - loadStarted) / 1_000_000
        val timings = mutableListOf<Long>()
        var acceptedEffects = 0
        var rejectedQuietWindows = 0

        for (index in 0 until entries.length()) {
            val entry = entries.getJSONObject(index)
            val id = entry.getString("id")
            val bytes = assets.open("$FIXTURES/${entry.getString("file")}").use { it.readBytes() }
            val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                (it.toInt() and 0xff).toString(16).padStart(2, '0')
            }
            assertEquals("$id fixture bytes", entry.getJSONObject("derived").getString("sha256"), sha256)
            val audio = requireNotNull(WavPcm16.decode(bytes)) { "$id must decode as captured PCM16 WAV" }
            val legalEntries = entry.getJSONArray("legalMoves")
            val legalMoves = (0 until legalEntries.length()).map { legalEntries.getString(it) }.toSet()
            assertTrue("$id needs a nonempty active-species comparison pool", legalMoves.isNotEmpty())
            val expected = if (entry.isNull("expectedMove")) null else entry.getString("expectedMove")
            if (expected != null) {
                assertTrue("$id expected move must be legal", expected in legalMoves)
                assertEquals("$id must remain held out", "holdout", entry.getString("role"))
            }

            val started = System.nanoTime()
            val candidates = matcher.measure(audio, legalMoves)
            val elapsedMillis = (System.nanoTime() - started) / 1_000_000
            timings += elapsedMillis
            val accepted = FastMoveReferenceMatcher.accepts(candidates)
            val detail = candidates.joinToString { "${it.moveName}=${it.similarity}/${it.changingSimilarity}" }
            instrumentation.sendStatus(0, Bundle().apply {
                putString("captured_fast_move", "$id accepted=$accepted top=${candidates.firstOrNull()?.moveName} ms=$elapsedMillis")
            })
            assertTrue("$id returned an impossible move: $detail", candidates.all { it.moveName in legalMoves })
            assertEquals("$id acceptance; $detail", expected != null, accepted)
            if (expected == null) {
                rejectedQuietWindows++
            } else {
                assertEquals("$id identity; $detail", expected, candidates.firstOrNull()?.moveName)
                acceptedEffects++
            }
        }

        assertEquals("Held-out positive count", 6, acceptedEffects)
        assertEquals("Quiet rejection count", 2, rejectedQuietWindows)
        instrumentation.sendStatus(0, Bundle().apply {
            putString("stream", "Captured Fast Move audio: $acceptedEffects held-out effects accepted, " +
                "$rejectedQuietWindows quiet windows rejected; load ${loadMillis}ms, " +
                "median ${timings.sorted()[timings.size / 2]}ms, max ${timings.maxOrNull()}ms.\n")
        })
    }

    private companion object {
        const val FIXTURES = "fast_move_20261101"
    }
}
