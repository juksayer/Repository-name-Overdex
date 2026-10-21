package com.example.overdex.battle.audio

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class StatChangeSoundMatcherTest {
    private val references = StatChangeCue.entries.map { cue ->
        val file = File("src/main/assets/stat_change_sounds/${cue.fileName}")
        StatSoundReference(cue, cue.fileName, requireNotNull(WavPcm16.decode(file.readBytes())))
    }
    private val matcher = StatChangeSoundMatcher(references)

    @Test fun `diagnostic ranking cannot turn an ambiguous cue into accepted evidence`() {
        val reference = references.first()
        val ambiguous = StatChangeSoundMatcher(listOf(reference,
            reference.copy(cue = StatChangeCue.DEFENSE_UP)))
        val candidate = requireNotNull(ambiguous.bestCandidate(reference.audio))
        assertTrue(candidate.similarity > .99f)
        assertEquals(candidate.similarity, candidate.runnerUpSimilarity, .0001f)
        assertFalse(StatChangeSoundMatcher.accepts(candidate))
        assertNull(ambiguous.match(reference.audio))
    }

    @Test fun `all four actual assets remain distinct at quiet volume with arbitrary window offset`() {
        for (reference in references) for (gain in listOf(1f, .12f)) {
            val offset = 2_013
            val samples = ShortArray(12_000)
            StatChangeSoundMatcher.mono8k(reference.audio).forEachIndexed { i, value ->
                samples[offset + i] = (value * gain).toInt().toShort()
            }
            val found = matcher.match(Pcm16Audio(8_000, 1, samples))
            assertNotNull("${reference.cue} gain=$gain", found)
            assertEquals(reference.cue, found!!.cue)
            assertTrue(abs(found.offsetNanos - offset * 125_000L) <= 16_000_000L)
        }
    }

    @Test fun `48k stereo capture matches after resampling with low background noise`() {
        val random = Random(100)
        for (reference in references) {
            val input = reference.audio.samples
            val frames = 72_000
            val offset = 12_537
            val samples = ShortArray(frames * 2) { random.nextInt(-70, 71).toShort() }
            for (i in 0 until input.size * 48_000 / reference.audio.sampleRateHz) {
                val source = i.toDouble() * reference.audio.sampleRateHz / 48_000
                val left = source.toInt()
                val value = (input[left] * (1 - source + left) +
                    input[minOf(left + 1, input.lastIndex)] * (source - left)).toInt()
                repeat(2) { samples[(offset + i) * 2 + it] = (value / 2 + random.nextInt(-70, 71)).toShort() }
            }
            assertEquals(reference.cue, matcher.match(Pcm16Audio(48_000, 2, samples))?.cue)
        }
    }

    @Test fun `silence noise tones and a partial cue do not invent stat changes`() {
        val random = Random(22)
        val queries = listOf(
            ShortArray(12_000),
            ShortArray(12_000) { random.nextInt(-5_000, 5_000).toShort() },
            ShortArray(12_000) { (sin(it * 2 * Math.PI * 440 / 8_000) * 4_000).toInt().toShort() },
            StatChangeSoundMatcher.mono8k(references.first().audio).take(3_000).toShortArray(),
        )
        queries.forEach { assertNull(matcher.match(Pcm16Audio(8_000, 1, it))) }
    }

    @Test fun `rolling window stays bounded and cannot splice across dropped input`() {
        val window = StatSoundWindow()
        fun chunk(sequence: Long) = LivePcmChunk(Pcm16Audio(48_000, 1, ShortArray(960)), sequence,
            sequence * 20_000_000L, sequence * 20, "TEST")
        repeat(59) { assertNull(window.ingest(chunk(it.toLong()))) }
        assertEquals(9_600, window.ingest(chunk(59))?.samples?.size)
        assertNull(window.ingest(chunk(61)))
        repeat(58) { assertNull(window.ingest(chunk(62L + it))) }
        assertEquals(9_600, window.ingest(chunk(120))?.samples?.size)
        var largest = 0
        repeat(100) { window.ingest(chunk(121L + it))?.let { audio -> largest = maxOf(largest, audio.samples.size) } }
        assertEquals(12_000, largest)
    }
}
