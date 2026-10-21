package com.example.overdex.battle.audio

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import kotlin.math.sin
import kotlin.random.Random

class ResultMusicMatcherTest {
    @Test fun `actual victory and defeat tracks match excerpts at arbitrary phase and quiet volume`() {
        for (reference in references.filter { it.theme.isResult }) {
            for (offset in listOf(0, 8_107, 42_073, 96_985)) for (gain in listOf(1f, .10f)) {
                val found = matcher.match(excerpt(reference, offset, gain))
                assertNotNull("${reference.theme} offset=$offset gain=$gain", found)
                assertEquals(reference.theme, found!!.theme)
                assertEquals(reference.sha256, found.referenceSha256)
            }
        }
    }

    @Test fun `normal battle and team selection music are not victory or defeat`() {
        for (reference in references.filter { !it.theme.isResult }) for (seconds in listOf(0, 5, 12, 23, 40)) {
            val query = excerpt(reference, seconds * 8000 + 53)
            val best = requireNotNull(matcher.bestCandidate(query))
            assertEquals(reference.theme, best.theme)
            assertNull(matcher.match(query))
        }
    }

    @Test fun `48k stereo playback with modest mixed noise remains recognizable`() {
        val random = Random(93)
        for (reference in references.filter { it.theme.isResult }) {
            val mono = excerpt(reference, 24_031, .5f).samples
            val stereo = ShortArray(mono.size * 6 * 2) { index ->
                val at = index / 2.0 / 6.0
                val left = at.toInt().coerceAtMost(mono.lastIndex)
                val fraction = at - left
                (mono[left] * (1 - fraction) + mono[minOf(left + 1, mono.lastIndex)] * fraction +
                    random.nextInt(-40, 41)).toInt().toShort()
            }
            assertEquals(reference.theme, matcher.match(Pcm16Audio(48_000, 2, stereo))?.theme)
        }
    }

    @Test fun `silence noise tones brief fragments and ambiguous mixtures never declare a result`() {
        val random = Random(9)
        val win = excerpt(references[0], 40_000).samples
        val loss = excerpt(references[1], 40_000).samples
        val queries = listOf(
            ShortArray(24_000), ShortArray(24_000) { random.nextInt(-6000, 6000).toShort() },
            ShortArray(24_000) { (sin(it * 2 * Math.PI * 440 / 8000) * 4000).toInt().toShort() },
            win.copyOfRange(0, 16_000),
            ShortArray(24_000) { ((win[it].toInt() + loss[it]) / 2).toShort() },
        )
        queries.forEach { assertNull(matcher.match(Pcm16Audio(8000, 1, it))) }
        val duplicate = ResultMusicMatcher(listOf(references[0], references[0].copy(theme = ResultMusicTheme.LOSS)))
        assertNull(duplicate.match(excerpt(references[0], 0)))
    }

    @Test fun `a recorded sequence requires two progressing windows before it is accepted`() {
        val confirmation = ResultMusicConfirmation()
        val audio = mono.getValue(ResultMusicTheme.WIN)
        fun snapshot(end: Int) = ResultMusicWindowSnapshot(Pcm16Audio(8000, 1, audio.copyOfRange(0, end)),
            end * 125_000L, end / 8L, "PLAYBACK_POKEMON_GO", 1)
        val first = snapshot(24_000)
        assertFalse(confirmation.accept(first, matcher.match(first.audio)))
        val next = snapshot(32_000)
        assertTrue(confirmation.accept(next, matcher.match(next.audio)))
    }

    private fun excerpt(reference: ResultMusicReference, offset: Int, gain: Float = 1f): Pcm16Audio {
        val input = mono.getValue(reference.theme)
        return Pcm16Audio(8000, 1, ShortArray(24_000) { (input[offset + it] * gain).toInt().toShort() })
    }

    companion object {
        private val references by lazy {
            listOf("126_pvp_win.wav", "127_pvp_lose.wav", "128_pvp.wav", "pvp_select.wav")
                .zip(ResultMusicTheme.entries).map { (fileName, theme) ->
                    val bytes = File("src/main/assets/result_music/$fileName").readBytes()
                    val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
                    ResultMusicReference(theme, sha, requireNotNull(WavPcm16.decode(bytes)))
                }
        }
        private val mono by lazy { references.associate { it.theme to StatChangeSoundMatcher.mono8k(it.audio) } }
        private val matcher by lazy { ResultMusicMatcher(references) }
    }
}
