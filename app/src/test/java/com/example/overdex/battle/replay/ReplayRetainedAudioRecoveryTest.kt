package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.audio.Pcm16Audio
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random

class ReplayRetainedAudioRecoveryTest {
    private val effect = noise(20_000, 1)
    private val ref = ReplayAudioReference("Example charge", true, null, "reference", Pcm16Audio(8000, 1, effect))
    private val other = ReplayAudioReference("Competing fast", false, null, "other", Pcm16Audio(8000, 1, noise(16_000, 2)))
    private val first = wav(effect.copyOfRange(0, 9600))
    private val second = wav(effect.copyOfRange(9600, 19200))
    private fun archive() = MatchArchive(matchId = "test", articles = listOf(event("start", 0, ArchivedMatchStarted),
        capture("one", 10*S, first), capture("two", 10*S + 1_200_000_000, second), event("end", 16*S, ArchivedMatchEnded("WIN"))))
    private fun recover(a: MatchArchive = archive(), refs: List<ReplayAudioReference> = listOf(ref, other),
                        read: (ArchivedAudioCaptured) -> ByteArray? = { if (it.artifactPath == "one") first else second }) =
        ReplayRetainedAudioRecovery.recover(a, refs, read = read)
    @Test fun `independent nonoverlapping segments recover one effect without source mutation`() {
        val a = archive(); val before = MatchArchiveSerializer.serialize(a)
        val result = recover(a)
        assertEquals(1, result.charges.size)
        assertEquals(10*S, result.charges.single().estimatedOnsetNanos)
        assertEquals(setOf("one", "two"), result.charges.single().fragments.map { it.articleId }.toSet())
        assertEquals(result, recover(a.copy(articles = a.articles.reversed())))
        assertEquals(before, MatchArchiveSerializer.serialize(a))
    }
    @Test fun `fast reference winner remains available to causal inference without inventing an attack`() {
        val result = recover(refs = listOf(ref.copy(charged = false), other))
        assertTrue(result.charges.isEmpty())
        assertEquals("Example charge", result.fastEffects.single().moveName)
        assertEquals(2, result.fastEffects.single().fragments.size)
    }
    @Test fun `one clip duplicate clips and ambiguous reference do not make a charge`() {
        val a = archive()
        assertTrue(recover(a.copy(articles = a.articles.filter { it.articleId != "two" })).charges.isEmpty())
        val duplicate = capture("duplicate", 10*S, first)
        assertTrue(recover(a.copy(articles = a.articles.filter { it.articleId != "two" } + duplicate), read = { first }).charges.isEmpty())
        assertTrue(recover(refs = listOf(ref, ref.copy(name = "Different possible move"))).charges.isEmpty())
        assertTrue(recover(refs = listOf(ref)).charges.isEmpty())
    }
    @Test fun `missing corrupt and wrongly timed artifacts are explicit and cannot become recoveries`() {
        assertEquals(2, recover(read = { null }).unavailableArtifacts.size)
        assertTrue(recover(read = { it.let { first.copyOf().apply { this[50] = (this[50] + 1).toByte() } } }).charges.isEmpty())
        val a = archive()
        val late = a.copy(articles = a.articles.map { if (it.articleId == "two") it.copy(monotonicTimeNanos = 14*S) else it })
        assertTrue(recover(late).charges.isEmpty())
        assertTrue(recover(a.copy(articles = a.articles.filter { it.articleId != "end" })).charges.isEmpty())
    }
    @Test fun `partial cry has its own provenance and cannot supply its reason`() {
        val cry = ReplayAudioReference("Cry 148", false, 148, "cry", Pcm16Audio(8000, 1, effect.copyOfRange(0, 8000)))
        val tail = wav(effect.copyOfRange(3200, 8000) + noise(4800, 4))
        val a = archive().copy(articles = listOf(event("start", 0, ArchivedMatchStarted), capture("cry", 12*S, tail), event("end", 16*S, ArchivedMatchEnded("WIN"))))
        val recovered = recover(a, listOf(cry, other)) { tail }
        assertEquals(148, recovered.cries.single().speciesId)
        assertEquals(11_600_000_000L, recovered.cries.single().estimatedOnsetNanos)
        assertTrue(recovered.charges.isEmpty())
    }
    private fun capture(id: String, at: Long, bytes: ByteArray) = event(id, at, ArchivedAudioCaptured(id,
        ReplayRetainedAudioRecovery.sha(bytes), bytes.size.toLong(), "audio/wav", 8000, 1, 1_200_000_000,
        "FAST_MOVE", "PLAYBACK_POKEMON_GO"))
    private fun event(id: String, at: Long, p: ArchivedTestimonyPayload) = ArchivedRealityArticle(id, "test", 0, 0, "TEST", p,
        emptyList(), null, null, emptyList(), at)
    private fun noise(n: Int, seed: Long) = Random(seed).let { r -> ShortArray(n) { (r.nextInt(16000) - 8000).toShort() } }
    private fun wav(samples: ShortArray): ByteArray = ByteBuffer.allocate(44 + samples.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt(36 + samples.size*2); put("WAVEfmt ".toByteArray()); putInt(16)
        putShort(1); putShort(1); putInt(8000); putInt(16000); putShort(2); putShort(16)
        put("data".toByteArray()); putInt(samples.size*2); samples.forEach { putShort(it) }
    }.array()
    private companion object { const val S = 1_000_000_000L }
}
