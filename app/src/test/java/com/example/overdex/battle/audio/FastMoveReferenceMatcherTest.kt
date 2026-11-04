package com.example.overdex.battle.audio

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FastMoveReferenceMatcherTest {
    @Test fun `off pool winner is excluded while eligible comparisons retain their measured scores`() {
        val audio = references.first { it.moveName == "Water Gun" }.audio
        val full = matcher.measure(audio)
        assertEquals("Water Gun", full.first().moveName)
        val allowed = setOf("Spark", "Tackle")
        val scoped = matcher.measure(audio, allowed)
        assertEquals(allowed, scoped.map { it.moveName }.toSet())
        assertEquals(full.filter { it.moveName in allowed }, scoped)
        assertFalse(scoped.any { it.moveName == "Water Gun" })
    }

    @Test fun `empty and unavailable allowlists never fall back to the catalogue`() {
        val audio = references.first { it.moveName == "Water Gun" }.audio
        val mono = StatChangeSoundMatcher.mono8k(audio)
        for (allowed in listOf(emptySet(), setOf("", " -_ "), setOf("Uncatalogued Move"))) {
            assertTrue(matcher.measure(audio, allowed).isEmpty())
            assertTrue(matcher.measureMono8k(mono, allowed).isEmpty())
        }
    }

    @Test fun `allowlist names normalize case spaces and punctuation without leaking other references`() {
        val audio = references.first { it.moveName == "Water Gun" }.audio
        val expected = matcher.measure(audio, setOf("Water Gun", "Thunder Shock"))
        val allowed = setOf("  water_GUN ", "THUNDER-shock", "Water Gun", "Uncatalogued Move")
        assertEquals(setOf("Water Gun", "Thunder Shock"), expected.map { it.moveName }.toSet())
        assertEquals(expected, matcher.measure(audio, allowed))
        assertEquals(expected, matcher.measureMono8k(StatChangeSoundMatcher.mono8k(audio), allowed))
    }

    @Test fun `a single plausible legal reference is retained but cannot force strict acceptance`() {
        val audio = references.first { it.moveName == "Water Gun" }.audio
        val ranked = matcher.measure(audio, setOf("Water Gun"))
        assertEquals(1, ranked.size)
        assertEquals("Water Gun", FastMoveReferenceMatcher.plausibleCandidate(ranked)?.moveName)
        assertFalse(FastMoveReferenceMatcher.accepts(ranked))
        assertNull(FastMoveReferenceMatcher.plausibleCandidate(listOf(ranked.single().copy(similarity = .84f))))
        assertNull(FastMoveReferenceMatcher.plausibleCandidate(listOf(ranked.single().copy(changingSimilarity = .64f))))
        assertNull(FastMoveReferenceMatcher.plausibleCandidate(emptyList()))
    }

    @Test fun `narrowed comparison rejects silence undersized query and genuinely undersized reference`() {
        assertTrue(matcher.measureMono8k(ShortArray(9_600), setOf("Water Gun")).isEmpty())
        val bite = references.first { it.moveName == "Bite" }
        // Query windows remain at least the original 416 ms, independent of a short reference.
        assertTrue(matcher.measure(bite.audio, setOf("Bite")).isEmpty())
        val tiny = bite.copy(audio = Pcm16Audio(8_000, 1,
            StatChangeSoundMatcher.mono8k(bite.audio).copyOf(2_000)))
        assertTrue(FastMoveReferenceMatcher(listOf(tiny))
            .measure(references.first { it.moveName == "Water Gun" }.audio, setOf("Bite")).isEmpty())
    }

    @Test fun `clean target sounds survive volume changes and reject the competing move`() {
        for (name in listOf("Water Gun", "Powder Snow", "Spark", "Tackle", "Incinerate", "Ember", "Fire Spin")) {
            val ref = references.first { it.moveName == name }
            val mono = StatChangeSoundMatcher.mono8k(ref.audio)
            val clipped = mono.take(9_600).map { (it * .4f).toInt().toShort() }.toShortArray()
            val ranked = matcher.measure(Pcm16Audio(8_000, 1, clipped))
            assertEquals(name, ranked.first().moveName)
            assertTrue("$name ${ranked.take(2)}", FastMoveReferenceMatcher.accepts(ranked))
        }
    }

    @Test fun `original and changing maxima from different passages cannot become one good fit`() {
        fun spectrum(bin: Int) = FloatArray(64).apply { this[bin] = 1f }
        val reference = List(25) { spectrum(0) }
        val queryOriginal = List(50) { spectrum(if (it < 25) 0 else 1) }
        val queryChanging = List(50) { spectrum(if (it < 25) 1 else 0) }
        // Each component has a perfect 25-frame match, but in opposite halves of the query.
        val fit = requireNotNull(FastMoveReferenceMatcher.fitCoherentSequences(
            queryOriginal, queryChanging, reference, reference, 25))
        assertEquals(1f, fit.originalSimilarity + fit.changingSimilarity, .00001f)
        assertFalse(fit.originalSimilarity >= .93f && fit.changingSimilarity >= .80f)
        assertEquals(0, fit.referenceStart)
        assertEquals(25, fit.frames)
    }

    @Test fun `coherent fit chooses the strongest joint passage and reports its actual offsets`() {
        fun spectrum(similarity: Float) = FloatArray(64).apply {
            this[0] = similarity
            this[1] = kotlin.math.sqrt(1f - similarity * similarity)
        }
        val reference = List(25) { spectrum(1f) }
        val queryOriginal = List(75) { spectrum(when { it < 25 -> 1f; it < 50 -> 0f; else -> .9f }) }
        val queryChanging = List(75) { spectrum(when { it < 25 -> 0f; it < 50 -> 1f; else -> .9f }) }
        val fit = requireNotNull(FastMoveReferenceMatcher.fitCoherentSequences(
            queryOriginal, queryChanging, reference, reference, 25))
        assertEquals(50, fit.queryStart)
        assertEquals(0, fit.referenceStart)
        assertEquals(.9f, fit.originalSimilarity, .00001f)
        assertEquals(.9f, fit.changingSimilarity, .00001f)
    }

    @Test fun `historic versions remain readable but only v3 guarantees shared source alignment`() {
        assertEquals("spectral-sequence-v3", FastMoveReferenceMatcher.VERSION)
        for (version in listOf("spectral-sequence-v1", "spectral-sequence-v2", "spectral-sequence-v3")) {
            assertTrue(FastMoveReferenceMatcher.supportsAnalysisVersion(version))
        }
        for (version in listOf(null, "", "spectral-sequence-v4")) {
            assertFalse(FastMoveReferenceMatcher.supportsAnalysisVersion(version))
        }
    }

    @Test fun `recorded mixtures retain separated spectra and reject ambiguous samples`() {
        val root = File("src/androidTest/assets/fast_move_20261015")
        val manifest = Json.parseToJsonElement(File(root, "manifest.json").readText()).jsonArray
        for (entry in manifest) {
            val p = entry.jsonObject
            val file = p.getValue("file").jsonPrimitive.content
            val expected = p.getValue("expectedReference").jsonPrimitive.contentOrNull
            val ranked = matcher.measure(WavPcm16.decode(File(root, file).readBytes())!!)
            assertEquals("$file: ${ranked.take(3)}", expected != null, FastMoveReferenceMatcher.accepts(ranked))
            if (expected != null) assertEquals(file, expected, ranked.first().moveName)
        }
    }

    @Test fun `complete short Bite reference remains eligible in Blastoise comparisons`() {
        val bite = references.first { it.moveName == "Bite" }
        val ranked = matcher.measureMono8k(StatChangeSoundMatcher.mono8k(bite.audio).copyOf(9_600),
            setOf("Bite", "Rollout", "Water Gun"))
        assertEquals(setOf("Bite", "Rollout", "Water Gun"), ranked.map { it.moveName }.toSet())
        assertEquals("Bite", ranked.first().moveName)
        assertEquals(352_000_000L, ranked.first().matchedDurationNanos)
        assertEquals("Bite", FastMoveReferenceMatcher.plausibleCandidate(ranked)?.moveName)
        // Full coverage does not waive the independent changing-spectrum margin.
        assertFalse("${ranked.take(3)}", FastMoveReferenceMatcher.accepts(ranked))
    }

    @Test fun `short Bite matching preserves volume alignment and excludes off pool names`() {
        val mono = StatChangeSoundMatcher.mono8k(references.first { it.moveName == "Bite" }.audio)
        for (volume in listOf(.4f, 1f)) {
            val query = ShortArray(9_600)
            mono.forEachIndexed { index, sample -> query[1_024 + index] = (sample * volume).toInt().toShort() }
            val ranked = matcher.measureMono8k(query, setOf("Bite", "Rollout", "Water Gun"))
            val best = ranked.first()
            assertEquals("Bite", best.moveName)
            assertEquals(1_024L * 1_000_000_000L / 8_000, best.matchingOffsetNanos)
            assertEquals(0L, best.referenceOffsetNanos)
            assertEquals(352_000_000L, best.matchedDurationNanos)
            assertEquals("Bite", FastMoveReferenceMatcher.plausibleCandidate(ranked)?.moveName)
            assertFalse("$volume ${ranked.take(3)}", FastMoveReferenceMatcher.accepts(ranked))
        }
    }

    @Test fun `short Bite candidate does not force a negative or competing sound into that identity`() {
        val allowed = setOf("Bite", "Rollout", "Water Gun")
        val waterGun = matcher.measure(references.first { it.moveName == "Water Gun" }.audio, allowed)
        assertEquals(allowed, waterGun.map { it.moveName }.toSet())
        assertEquals("Water Gun", waterGun.first().moveName)
        assertTrue(FastMoveReferenceMatcher.accepts(waterGun))
        val tone = ShortArray(9_600) { (10_000 * kotlin.math.sin(it * 2 * Math.PI * 900 / 8000)).toInt().toShort() }
        assertFalse(FastMoveReferenceMatcher.accepts(matcher.measureMono8k(tone, allowed)))
    }

    @Test fun `silence music only and a tone do not manufacture move identity`() {
        assertTrue(matcher.measure(Pcm16Audio(48_000, 1, ShortArray(57_600))).isEmpty())
        val tone = ShortArray(9_600) { (10_000 * kotlin.math.sin(it * 2 * Math.PI * 900 / 8000)).toInt().toShort() }
        assertFalse(FastMoveReferenceMatcher.accepts(matcher.measure(Pcm16Audio(8_000, 1, tone))))
        for (file in File("src/main/assets/result_music").listFiles()!!.filter { it.extension == "wav" }) {
            val music = StatChangeSoundMatcher.mono8k(WavPcm16.decode(file.readBytes())!!)
            for (seconds in listOf(0, 4, 12, 20)) {
                val start = seconds * 8_000
                if (start + 9_600 > music.size) continue
                val rank = matcher.measure(Pcm16Audio(8_000, 1, music.copyOfRange(start, start + 9_600)))
                assertFalse("${file.name} @$seconds ${rank.take(2)}", FastMoveReferenceMatcher.accepts(rank))
            }
        }
    }

    @Test fun `battle cries and stat sounds cannot masquerade as fast moves`() {
        val files = listOf("pv215.wav", "pv364.wav", "pv134.wav", "pv460.wav", "pv101.wav")
            .map { File("src/main/assets/cry_references/$it") } +
            File("src/main/assets/stat_change_sounds").listFiles()!!.filter { it.extension == "wav" }
        files.forEach { file ->
            val ranked = matcher.measure(WavPcm16.decode(file.readBytes())!!)
            assertFalse("${file.name}: ${ranked.take(2)}", FastMoveReferenceMatcher.accepts(ranked))
        }
    }

    companion object {
        private val references by lazy {
            val root = File("src/main/assets/fast_move_sounds")
            Json.parseToJsonElement(File(root, "manifest.json").readText()).jsonObject.getValue("references").jsonArray.map {
                val p = it.jsonObject
                FastMoveSoundReference(p.getValue("moveName").jsonPrimitive.content,
                    p.getValue("sha256").jsonPrimitive.content,
                    WavPcm16.decode(File(root, p.getValue("file").jsonPrimitive.content).readBytes())!!)
            }
        }
        private val matcher by lazy { FastMoveReferenceMatcher(references) }
    }
}
